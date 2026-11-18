package com.example.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.CallSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.ConcurrentHashMap

enum class RtcConnectionState {
    WAITING_FOR_PEER,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    UNAVAILABLE
}

class WebRtcCallClient(
    context: Context,
    private val roomCode: String,
    private val selfUid: String,
    private val initialVideoEnabled: Boolean,
    private val onSendSignal: suspend (
        recipientId: String,
        type: String,
        sdp: String?,
        sdpMid: String?,
        sdpMLineIndex: Int?,
        candidate: String?
    ) -> Unit
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _connectionState = MutableStateFlow(RtcConnectionState.WAITING_FOR_PEER)
    val connectionState: StateFlow<RtcConnectionState> = _connectionState.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _isMediaReady = MutableStateFlow(false)
    val isMediaReady: StateFlow<Boolean> = _isMediaReady.asStateFlow()

    var eglBase: EglBase? = null
        private set

    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localVideoSource: VideoSource? = null

    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val pendingIceCandidates = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private val offeredPeers = ConcurrentHashMap.newKeySet<String>()
    private val processedSignalIds = ConcurrentHashMap.newKeySet<String>()

    private var audioManager: AudioManager? = null
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerphone: Boolean = false
    private var previousMicMute: Boolean = false
    private var audioFocusRequest: AudioFocusRequest? = null

    private var isDisposed = false
    private var isInitialized = false
    private var isMuted = false
    private var isSpeakerOn = initialVideoEnabled
    private var isVideoEnabled = initialVideoEnabled

    fun startIfPermitted() {
        if (isDisposed || isInitialized) return
        val hasMic = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) return

        val ready = runCatching {
            ensureNativeInitialized(appContext)
            configureAudioRouting(initialVideoEnabled)

            val egl = EglBase.create()
            eglBase = egl

            val adm = JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()
            audioDeviceModule = adm

            val factory = PeerConnectionFactory.builder()
                .setOptions(PeerConnectionFactory.Options())
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(
                    DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
                )
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
                .createPeerConnectionFactory()
            peerConnectionFactory = factory

            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            }
            localAudioSource = factory.createAudioSource(audioConstraints)
            localAudioTrack = factory.createAudioTrack("pulsecast_audio_$selfUid", localAudioSource)?.apply {
                setEnabled(!isMuted)
            }

            if (isVideoEnabled) {
                startLocalVideoCaptureIfPermitted(factory, egl)
            }

            true
        }.getOrElse { error ->
            Log.w(TAG, "WebRTC initialization skipped or failed: ${error.message}")
            _connectionState.value = RtcConnectionState.UNAVAILABLE
            false
        }

        isInitialized = ready
        _isMediaReady.value = ready
    }

    private fun startLocalVideoCaptureIfPermitted(factory: PeerConnectionFactory, egl: EglBase) {
        val hasCamera = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCamera) return
        if (_localVideoTrack.value != null) {
            _localVideoTrack.value?.setEnabled(true)
            return
        }

        runCatching {
            val enumerator = Camera2Enumerator(appContext)
            val deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
                ?: return
            val capturer = enumerator.createCapturer(deviceName, null) ?: return
            val texHelper = SurfaceTextureHelper.create("PulseCastCaptureThread", egl.eglBaseContext)
            val videoSource = factory.createVideoSource(capturer.isScreencast)
            capturer.initialize(texHelper, appContext, videoSource.capturerObserver)
            capturer.startCapture(720, 1280, 24)
            val track = factory.createVideoTrack("pulsecast_video_$selfUid", videoSource).apply {
                setEnabled(isVideoEnabled)
            }
            videoCapturer = capturer
            surfaceTextureHelper = texHelper
            localVideoSource = videoSource
            _localVideoTrack.value = track

            peerConnections.values.forEach { pc ->
                runCatching { pc.addTrack(track, listOf("pulsecast_stream_$selfUid")) }
            }
        }.onFailure {
            Log.w(TAG, "Could not start WebRTC camera capture: ${it.message}")
        }
    }

    fun syncParticipants(participantIds: List<String>, sessionStatus: String) {
        if (isDisposed || sessionStatus != "active") return
        if (!isInitialized) {
            startIfPermitted()
            if (!isInitialized) return
        }

        val remotePeers = participantIds.filter { it.isNotBlank() && it != selfUid }.distinct()
        if (remotePeers.isEmpty()) {
            _connectionState.value = RtcConnectionState.WAITING_FOR_PEER
            return
        }

        remotePeers.forEach { peerId ->
            val pc = getOrCreatePeerConnection(peerId) ?: return@forEach
            if (selfUid < peerId && offeredPeers.add(peerId)) {
                _connectionState.value = RtcConnectionState.CONNECTING
                createAndSendOffer(peerId, pc)
            }
        }
    }

    fun handleIncomingSignals(signals: List<CallSignal>) {
        if (isDisposed || !isInitialized) return
        for (signal in signals) {
            val key = signal.id.ifBlank {
                "${signal.senderId}:${signal.type}:${signal.sdp?.hashCode() ?: signal.candidate.hashCode()}"
            }
            if (!processedSignalIds.add(key)) continue
            if (signal.senderId == selfUid || signal.senderId.isBlank()) continue
            if (signal.recipientId != selfUid && signal.recipientId != "all") continue

            when (signal.type) {
                "offer" -> {
                    val sdp = signal.sdp ?: continue
                    val pc = getOrCreatePeerConnection(signal.senderId) ?: continue
                    _connectionState.value = RtcConnectionState.CONNECTING
                    val offerDesc = SessionDescription(SessionDescription.Type.OFFER, sdp)
                    pc.setRemoteDescription(object : SimpleSdpObserver("setRemoteOffer") {
                        override fun onSetSuccess() {
                            flushPendingCandidates(signal.senderId, pc)
                            val constraints = MediaConstraints().apply {
                                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                            }
                            pc.createAnswer(object : SimpleSdpObserver("createAnswer") {
                                override fun onCreateSuccess(answerDesc: SessionDescription?) {
                                    if (answerDesc == null || isDisposed) return
                                    pc.setLocalDescription(object : SimpleSdpObserver("setLocalAnswer") {
                                        override fun onSetSuccess() {
                                            scope.launch {
                                                onSendSignal(
                                                    signal.senderId,
                                                    "answer",
                                                    answerDesc.description,
                                                    null,
                                                    null,
                                                    null
                                                )
                                            }
                                        }
                                    }, answerDesc)
                                }
                            }, constraints)
                        }
                    }, offerDesc)
                }

                "answer" -> {
                    val sdp = signal.sdp ?: continue
                    val pc = peerConnections[signal.senderId] ?: continue
                    val answerDesc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                    pc.setRemoteDescription(object : SimpleSdpObserver("setRemoteAnswer") {
                        override fun onSetSuccess() {
                            flushPendingCandidates(signal.senderId, pc)
                        }
                    }, answerDesc)
                }

                "candidate" -> {
                    val candidateText = signal.candidate ?: continue
                    val sdpMid = signal.sdpMid ?: "0"
                    val sdpMLineIndex = signal.sdpMLineIndex ?: 0
                    val ice = IceCandidate(sdpMid, sdpMLineIndex, candidateText)
                    val pc = peerConnections[signal.senderId]
                    if (pc != null && pc.remoteDescription != null) {
                        pc.addIceCandidate(ice)
                    } else {
                        pendingIceCandidates.getOrPut(signal.senderId) { mutableListOf() }.add(ice)
                    }
                }
            }
        }
    }

    private fun createAndSendOffer(peerId: String, pc: PeerConnection) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createOffer(object : SimpleSdpObserver("createOffer") {
            override fun onCreateSuccess(offerDesc: SessionDescription?) {
                if (offerDesc == null || isDisposed) return
                pc.setLocalDescription(object : SimpleSdpObserver("setLocalOffer") {
                    override fun onSetSuccess() {
                        scope.launch {
                            onSendSignal(
                                peerId,
                                "offer",
                                offerDesc.description,
                                null,
                                null,
                                null
                            )
                        }
                    }
                }, offerDesc)
            }
        }, constraints)
    }

    private fun flushPendingCandidates(peerId: String, pc: PeerConnection) {
        val queued = pendingIceCandidates.remove(peerId) ?: return
        queued.forEach { pc.addIceCandidate(it) }
    }

    private fun getOrCreatePeerConnection(peerId: String): PeerConnection? {
        peerConnections[peerId]?.let { return it }
        val factory = peerConnectionFactory ?: return null

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                when (newState) {
                    PeerConnection.IceConnectionState.CHECKING ->
                        _connectionState.value = RtcConnectionState.CONNECTING
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED ->
                        _connectionState.value = RtcConnectionState.CONNECTED
                    PeerConnection.IceConnectionState.DISCONNECTED ->
                        _connectionState.value = RtcConnectionState.RECONNECTING
                    PeerConnection.IceConnectionState.FAILED ->
                        _connectionState.value = RtcConnectionState.RECONNECTING
                    else -> Unit
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null || isDisposed) return
                scope.launch {
                    onSendSignal(
                        peerId,
                        "candidate",
                        null,
                        candidate.sdpMid,
                        candidate.sdpMLineIndex,
                        candidate.sdp
                    )
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                val remoteVideo = stream?.videoTracks?.firstOrNull()
                if (remoteVideo != null) {
                    _remoteVideoTrack.value = remoteVideo
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(channel: DataChannel?) {}

            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    _remoteVideoTrack.value = track
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    _remoteVideoTrack.value = track
                }
            }
        }

        val pc = factory.createPeerConnection(rtcConfig, observer) ?: return null
        val streamId = "pulsecast_stream_$selfUid"
        localAudioTrack?.let { pc.addTrack(it, listOf(streamId)) }
        _localVideoTrack.value?.let { pc.addTrack(it, listOf(streamId)) }
        peerConnections[peerId] = pc
        return pc
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        runCatching { localAudioTrack?.setEnabled(!muted) }
        runCatching { audioManager?.isMicrophoneMute = muted }
    }

    fun setSpeakerphone(enabled: Boolean) {
        isSpeakerOn = enabled
        applySpeakerRouting(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        isVideoEnabled = enabled
        val track = _localVideoTrack.value
        if (track != null) {
            runCatching { track.setEnabled(enabled) }
        } else if (enabled && isInitialized) {
            val factory = peerConnectionFactory
            val egl = eglBase
            if (factory != null && egl != null) {
                startLocalVideoCaptureIfPermitted(factory, egl)
            }
        }
    }

    fun switchCamera() {
        runCatching { videoCapturer?.switchCamera(null) }
    }

    private fun configureAudioRouting(speakerByDefault: Boolean) {
        val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = manager
        previousAudioMode = manager.mode
        @Suppress("DEPRECATION")
        previousSpeakerphone = manager.isSpeakerphoneOn
        previousMicMute = manager.isMicrophoneMute

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            audioFocusRequest = focusReq
            manager.requestAudioFocus(focusReq)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }

        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        applySpeakerRouting(speakerByDefault)
    }

    private fun applySpeakerRouting(enabled: Boolean) {
        val manager = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val targetType = if (enabled) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                val device = manager.availableCommunicationDevices.firstOrNull { it.type == targetType }
                if (device != null) {
                    manager.setCommunicationDevice(device)
                }
            }
            @Suppress("DEPRECATION")
            manager.isSpeakerphoneOn = enabled
        }
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true

        _remoteVideoTrack.value = null
        _localVideoTrack.value = null
        _isMediaReady.value = false

        peerConnections.values.forEach { pc ->
            runCatching { pc.close() }
            runCatching { pc.dispose() }
        }
        peerConnections.clear()
        pendingIceCandidates.clear()

        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoCapturer = null

        runCatching { localVideoSource?.dispose() }
        localVideoSource = null

        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null

        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null

        runCatching { localAudioSource?.dispose() }
        localAudioSource = null

        runCatching { audioDeviceModule?.release() }
        audioDeviceModule = null

        runCatching { peerConnectionFactory?.dispose() }
        peerConnectionFactory = null

        runCatching { eglBase?.release() }
        eglBase = null

        audioManager?.let { manager ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    manager.clearCommunicationDevice()
                }
                manager.mode = previousAudioMode
                @Suppress("DEPRECATION")
                manager.isSpeakerphoneOn = previousSpeakerphone
                manager.isMicrophoneMute = previousMicMute
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
                }
            }
        }
        audioManager = null
    }

    private open class SimpleSdpObserver(private val tag: String) : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {
            Log.w(TAG, "$tag create failure: $error")
        }
        override fun onSetFailure(error: String?) {
            Log.w(TAG, "$tag set failure: $error")
        }
    }

    companion object {
        private const val TAG = "PulseWebRtc"

        @Volatile
        private var nativeInitialized = false

        private fun ensureNativeInitialized(appContext: Context) {
            if (nativeInitialized) return
            synchronized(this) {
                if (nativeInitialized) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )
                nativeInitialized = true
            }
        }
    }
}
