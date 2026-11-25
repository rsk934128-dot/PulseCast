import { useEffect, useMemo, useRef, useState } from "react";
import {
  Check,
  Clock,
  Copy,
  Disc,
  Download,
  Mic,
  MicOff,
  Phone,
  PhoneOff,
  Radio,
  Square,
  Users,
  Video,
  VideoOff,
  Volume2,
  X
} from "lucide-react";
import {
  endCallSession,
  joinCallSession,
  observeCallSignals,
  resolveIsUserOnline,
  sendCallSignal
} from "../repository";
import { formatDuration } from "../mediaUtils";
import { callAudio, logAudioIssue, type AudioUiState } from "../callAudio";
import { useUser } from "../hooks/useUser";
import type { CallSession, CallSignal, CreatorProfile } from "../types";
import { UserAvatar } from "./UserAvatar";

type RtcConnectionStatus = "waiting" | "connecting" | "connected" | "reconnecting";

interface CallRecordingResult {
  url: string;
  filename: string;
  mimeType: string;
  sizeBytes: number;
  durationSec: number;
  roomCode: string;
}

interface ActiveRecordingSession {
  recorder: MediaRecorder;
  chunks: Blob[];
  audioCtx: AudioContext | null;
  audioDest: MediaStreamAudioDestinationNode | null;
  connectedTrackIds: Set<string>;
  mixedStream: MediaStream;
  canvasTimer: number | null;
  timerInterval: number | null;
  startedAtMs: number;
  roomCode: string;
  mimeType: string;
}

function pickRecordingMimeType(includeVideo: boolean): string {
  if (typeof MediaRecorder === "undefined" || typeof MediaRecorder.isTypeSupported !== "function") {
    return "";
  }
  const candidates = includeVideo
    ? [
        "video/webm;codecs=vp9,opus",
        "video/webm;codecs=vp8,opus",
        "video/webm",
        "video/mp4"
      ]
    : [
        "audio/webm;codecs=opus",
        "audio/webm",
        "audio/ogg;codecs=opus",
        "audio/mp4"
      ];
  return candidates.find((mime) => MediaRecorder.isTypeSupported(mime)) || "";
}

function formatRecordingSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(0)} KB`;
  return `${(kb / 1024).toFixed(1)} MB`;
}

function formatCallDuration(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds || 0));
  const hours = Math.floor(safe / 3600);
  const mins = Math.floor((safe % 3600) / 60);
  const secs = safe % 60;
  if (hours > 0) {
    return `${String(hours).padStart(2, "0")}:${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
  }
  return formatDuration(safe);
}

function drawVideoCover(
  ctx: CanvasRenderingContext2D,
  video: HTMLVideoElement,
  dx: number,
  dy: number,
  dw: number,
  dh: number
) {
  const vw = video.videoWidth;
  const vh = video.videoHeight;
  if (!vw || !vh) return;
  const videoRatio = vw / vh;
  const targetRatio = dw / dh;
  let sx = 0;
  let sy = 0;
  let sw = vw;
  let sh = vh;
  if (videoRatio > targetRatio) {
    sw = vh * targetRatio;
    sx = (vw - sw) / 2;
  } else {
    sh = vw / targetRatio;
    sy = (vh - sh) / 2;
  }
  ctx.drawImage(video, sx, sy, sw, sh, dx, dy, dw, dh);
}

const ICE_SERVERS: RTCIceServer[] = [
  { urls: "stun:stun.l.google.com:19302" },
  { urls: "stun:stun1.l.google.com:19302" },
  { urls: "stun:stun2.l.google.com:19302" }
];

interface CallsViewProps {
  uid: string;
  userName: string;
  sessions: CallSession[];
  creators: CreatorProfile[];
  activeCall: CallSession | null;
  onActiveCallChange: (call: CallSession | null) => void;
  onStartCall: (callType: "audio" | "video" | "group", targetCreator?: CreatorProfile | null) => Promise<void>;
  onAnswerCall: (session: CallSession) => Promise<void>;
  onDeclineCall: (session: CallSession) => Promise<void>;
  onOpenCreatorProfile: (creator: CreatorProfile) => void;
  onNotify: (msg: string) => void;
}

export function CallsView({
  uid,
  userName,
  sessions,
  creators,
  activeCall,
  onActiveCallChange,
  onStartCall,
  onAnswerCall,
  onDeclineCall,
  onOpenCreatorProfile,
  onNotify
}: CallsViewProps) {
  const [roomCodeInput, setRoomCodeInput] = useState("");
  const [selectedTargetId, setSelectedTargetId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [elapsedSec, setElapsedSec] = useState(0);
  const [micMuted, setMicMuted] = useState(false);
  const [cameraOff, setCameraOff] = useState(false);
  const [mediaError, setMediaError] = useState<string | null>(null);
  const [mediaReadyTick, setMediaReadyTick] = useState(0);
  const [mediaSettledCallId, setMediaSettledCallId] = useState<string | null>(null);
  const [audioPlaybackBlocked, setAudioPlaybackBlocked] = useState(false);
  const [audioContextBlocked, setAudioContextBlocked] = useState(false);
  const [audioSuspendedAfterAttempt, setAudioSuspendedAfterAttempt] = useState(false);
  const [audioUiState, setAudioUiState] = useState<AudioUiState>(() => callAudio.getUiState());
  const [activatingAudio, setActivatingAudio] = useState(false);
  const [signals, setSignals] = useState<CallSignal[]>([]);
  const [rtcStatus, setRtcStatus] = useState<RtcConnectionStatus>("waiting");
  const [hasRemoteVideo, setHasRemoteVideo] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [recordingSec, setRecordingSec] = useState(0);
  const [completedRecording, setCompletedRecording] = useState<CallRecordingResult | null>(null);

  const localVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const remoteStreamRef = useRef<MediaStream | null>(null);
  const peerConnectionsRef = useRef<Map<string, RTCPeerConnection>>(new Map());
  const pendingCandidatesRef = useRef<Map<string, RTCIceCandidateInit[]>>(new Map());
  const offeredPeersRef = useRef<Set<string>>(new Set());
  const processedSignalIdsRef = useRef<Set<string>>(new Set());
  const signalChainRef = useRef<Promise<void>>(Promise.resolve());
  const recordingSessionRef = useRef<ActiveRecordingSession | null>(null);
  const completedRecordingUrlRef = useRef<string | null>(null);
  const activeCallRef = useRef<CallSession | null>(activeCall);
  const activePeerNameRef = useRef<string>("PulseCast Peer");
  const callActiveStartedAtRef = useRef<{ callId: string; startedAtMs: number } | null>(null);

  const creatorsById = useMemo(() => {
    const map = new Map<string, CreatorProfile>();
    creators.forEach((c) => {
      if (c.userId) map.set(c.userId, c);
    });
    return map;
  }, [creators]);

  const otherCreators = useMemo(
    () => creators.filter((c) => c.userId !== uid),
    [creators, uid]
  );

  const selectedTargetCreator = useMemo(
    () => otherCreators.find((c) => c.userId === selectedTargetId) || null,
    [otherCreators, selectedTargetId]
  );

  const connectAudioTracksToRecorder = (
    session: ActiveRecordingSession,
    stream: MediaStream | null
  ) => {
    if (!session.audioCtx || !session.audioDest || !stream) return;
    const newTracks = stream
      .getAudioTracks()
      .filter((t) => t.readyState === "live" && !session.connectedTrackIds.has(t.id));
    if (newTracks.length === 0) return;
    try {
      const trackStream = new MediaStream(newTracks);
      const source = session.audioCtx.createMediaStreamSource(trackStream);
      source.connect(session.audioDest);
      newTracks.forEach((t) => session.connectedTrackIds.add(t.id));
    } catch (err) {
      logAudioIssue("recordingMixer.connect", err);
    }
  };

  const stopActiveRecording = (fromCallEnd = false) => {
    const session = recordingSessionRef.current;
    if (!session) return;
    recordingSessionRef.current = null;
    setIsRecording(false);

    if (session.canvasTimer !== null) {
      window.clearInterval(session.canvasTimer);
      session.canvasTimer = null;
    }
    if (session.timerInterval !== null) {
      window.clearInterval(session.timerInterval);
      session.timerInterval = null;
    }

    if (session.recorder.state !== "inactive") {
      try {
        session.recorder.stop();
      } catch {
        // Ignore if already stopping
      }
      if (fromCallEnd) {
        onNotify("Finalizing call recording for download...");
      }
    } else {
      session.mixedStream.getTracks().forEach((t) => t.stop());
      session.audioCtx?.close().catch(() => {});
    }
  };

  const handleDismissRecording = () => {
    if (completedRecordingUrlRef.current) {
      URL.revokeObjectURL(completedRecordingUrlRef.current);
      completedRecordingUrlRef.current = null;
    }
    setCompletedRecording(null);
  };

  const cleanupWebRtc = () => {
    stopActiveRecording(true);

    peerConnectionsRef.current.forEach((pc) => {
      try {
        pc.onicecandidate = null;
        pc.oniceconnectionstatechange = null;
        pc.onconnectionstatechange = null;
        pc.ontrack = null;
        pc.close();
      } catch {
        // Ignore
      }
    });
    peerConnectionsRef.current.clear();
    pendingCandidatesRef.current.clear();
    offeredPeersRef.current.clear();
    processedSignalIdsRef.current.clear();
    signalChainRef.current = Promise.resolve();

    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((t) => t.stop());
      localStreamRef.current = null;
    }
    if (remoteStreamRef.current) {
      remoteStreamRef.current.getTracks().forEach((t) => t.stop());
      remoteStreamRef.current = null;
    }
    if (localVideoRef.current) localVideoRef.current.srcObject = null;
    if (remoteVideoRef.current) remoteVideoRef.current.srcObject = null;
    if (remoteAudioRef.current) remoteAudioRef.current.srcObject = null;
    setHasRemoteVideo(false);
    setAudioPlaybackBlocked(false);
    setMediaSettledCallId(null);
    setRtcStatus("waiting");
  };

  useEffect(() => {
    activeCallRef.current = activeCall;
  }, [activeCall]);

  useEffect(() => {
    completedRecordingUrlRef.current = completedRecording?.url || null;
  }, [completedRecording?.url]);

  useEffect(() => {
    return () => {
      stopActiveRecording(false);
      if (completedRecordingUrlRef.current) {
        URL.revokeObjectURL(completedRecordingUrlRef.current);
        completedRecordingUrlRef.current = null;
      }
    };
  }, []);

  // Subscribe to Web Audio autoplay state & install global gesture unlock
  useEffect(() => {
    const uninstall = callAudio.installGestureUnlock();
    const unsubBlocked = callAudio.subscribeBlocked(setAudioContextBlocked);
    const unsubSuspended = callAudio.subscribeSuspendedAfterAttempt(
      setAudioSuspendedAfterAttempt
    );
    const unsubUiState = callAudio.subscribeUiState(setAudioUiState);
    return () => {
      uninstall();
      unsubBlocked();
      unsubSuspended();
      unsubUiState();
    };
  }, []);

  // Register remote media elements for gesture-based output activation
  useEffect(() => {
    if (!activeCall) return;
    const unregAudio = callAudio.registerMediaElement(remoteAudioRef.current);
    const unregVideo = callAudio.registerMediaElement(remoteVideoRef.current);
    return () => {
      unregAudio();
      unregVideo();
    };
  }, [activeCall?.id, hasRemoteVideo]);

  // Real-time wall-clock call duration timer, starting when call status transitions to 'active'
  useEffect(() => {
    if (!activeCall || activeCall.status !== "active") {
      callActiveStartedAtRef.current = null;
      setElapsedSec(0);
      return;
    }

    if (callActiveStartedAtRef.current?.callId !== activeCall.id) {
      const seedSec = Math.max(0, activeCall.durationSec || 0);
      callActiveStartedAtRef.current = {
        callId: activeCall.id,
        startedAtMs: Date.now() - seedSec * 1000
      };
    }

    const syncElapsed = () => {
      const started = callActiveStartedAtRef.current;
      if (!started) return;
      setElapsedSec(Math.max(0, Math.floor((Date.now() - started.startedAtMs) / 1000)));
    };

    syncElapsed();
    const interval = window.setInterval(syncElapsed, 250);
    return () => window.clearInterval(interval);
  }, [activeCall?.id, activeCall?.status]);

  const acquireLocalMedia = async (call: CallSession): Promise<MediaStream | null> => {
    const wantVideo = call.callType !== "audio";
    if (!navigator.mediaDevices?.getUserMedia) {
      setMediaError("Browser microphone/camera API is unavailable on this page (HTTPS required).");
      return null;
    }

    const audioConstraints: MediaTrackConstraints = {
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true
    };

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: audioConstraints,
        video: wantVideo
      });
      setMediaError(null);
      return stream;
    } catch (primaryErr) {
      logAudioIssue("getUserMedia(primary)", primaryErr, audioConstraints);
      try {
        const audioOnly = await navigator.mediaDevices.getUserMedia({
          audio: audioConstraints,
          video: false
        });
        setCameraOff(true);
        setMediaError(
          wantVideo
            ? "Camera permission was unavailable; continuing with microphone audio."
            : null
        );
        return audioOnly;
      } catch (audioErr) {
        logAudioIssue("getUserMedia(audioFallback)", audioErr, audioConstraints);
        setMediaError(
          "Microphone permission was not granted. Allow mic access and tap Retry Mic & Camera."
        );
        return null;
      }
    }
  };

  // Capture local audio/video when entering an activeCall
  useEffect(() => {
    if (!activeCall) {
      cleanupWebRtc();
      setMediaError(null);
      setSignals([]);
      return;
    }

    const wantVideo = activeCall.callType !== "audio";
    setMicMuted(false);
    setCameraOff(!wantVideo);
    setMediaError(null);
    setMediaSettledCallId(null);

    let cancelled = false;
    const startMedia = async () => {
      const stream = await acquireLocalMedia(activeCall);
      if (cancelled) {
        stream?.getTracks().forEach((t) => t.stop());
        return;
      }
      if (stream) {
        stream.getAudioTracks().forEach((track) => {
          track.enabled = true;
        });
        localStreamRef.current = stream;
        if (recordingSessionRef.current) {
          connectAudioTracksToRecorder(recordingSessionRef.current, stream);
        }
      }
      setMediaSettledCallId(activeCall.id);
      setMediaReadyTick((t) => t + 1);
    };

    void startMedia();
    return () => {
      cancelled = true;
      cleanupWebRtc();
    };
  }, [activeCall?.id]);

  const unmuteAudioTracks = () => {
    setMicMuted(false);
    localStreamRef.current?.getAudioTracks().forEach((track) => {
      track.enabled = true;
    });
    remoteStreamRef.current?.getAudioTracks().forEach((track) => {
      track.enabled = true;
    });
    if (remoteAudioRef.current) {
      remoteAudioRef.current.muted = false;
      remoteAudioRef.current.volume = 1;
      if (remoteAudioRef.current.srcObject && remoteAudioRef.current.paused) {
        remoteAudioRef.current
          .play()
          .then(() => setAudioPlaybackBlocked(false))
          .catch(() => setAudioPlaybackBlocked(true));
      }
    }
  };

  // Explicitly unmute audio stream tracks when a call enters active state
  useEffect(() => {
    if (activeCall?.status === "active") {
      unmuteAudioTracks();
    }
  }, [activeCall?.id, activeCall?.status]);

  // Attach local & remote MediaStreams whenever video/audio elements mount or update
  useEffect(() => {
    const localStream = localStreamRef.current;
    if (localVideoRef.current && localStream && localStream.getVideoTracks().length > 0) {
      if (localVideoRef.current.srcObject !== localStream) {
        localVideoRef.current.srcObject = localStream;
      }
      localVideoRef.current.play().catch(() => {});
    }

    const remoteStream = remoteStreamRef.current;
    if (remoteStream) {
      remoteStream.getAudioTracks().forEach((track) => {
        track.enabled = true;
      });
      if (remoteVideoRef.current && remoteStream.getVideoTracks().length > 0) {
        if (remoteVideoRef.current.srcObject !== remoteStream) {
          remoteVideoRef.current.srcObject = remoteStream;
        }
        remoteVideoRef.current.play().catch(() => {});
      }
      if (remoteAudioRef.current) {
        remoteAudioRef.current.muted = false;
        remoteAudioRef.current.volume = 1;
        if (remoteAudioRef.current.srcObject !== remoteStream) {
          remoteAudioRef.current.srcObject = remoteStream;
        }
        remoteAudioRef.current
          .play()
          .then(() => setAudioPlaybackBlocked(false))
          .catch(() => setAudioPlaybackBlocked(true));
      }
    }
  }, [activeCall?.id, hasRemoteVideo, cameraOff, mediaReadyTick, rtcStatus]);

  const handleTapToActivateAudio = async () => {
    setActivatingAudio(true);
    try {
      const activated = await callAudio.activateWithRetry({
        fromUserGesture: true,
        maxRetries: 3,
        recreateIfStuck: true
      });
      unmuteAudioTracks();
      if (remoteVideoRef.current && remoteVideoRef.current.srcObject) {
        remoteVideoRef.current.play().catch(() => {});
      }
      if (activated) {
        onNotify("Call audio activated.");
      } else {
        onNotify(
          "Audio is still suspended by the browser. Check site sound permissions and tap again."
        );
      }
    } finally {
      setActivatingAudio(false);
    }
  };

  const handleEnableRemoteAudioPlayback = () => {
    void callAudio.activateFromGesture();
    unmuteAudioTracks();
    if (remoteVideoRef.current && remoteVideoRef.current.srcObject) {
      remoteVideoRef.current.play().catch(() => {});
    }
  };

  // Auto-recover suspended audio output on the next user interaction during a call
  useEffect(() => {
    if (!activeCall || (!audioPlaybackBlocked && !audioContextBlocked)) return;
    const onUserInteraction = () => {
      handleEnableRemoteAudioPlayback();
    };
    const events: Array<keyof WindowEventMap> = ["pointerdown", "touchend", "click", "keydown"];
    events.forEach((evt) =>
      window.addEventListener(evt, onUserInteraction, { capture: true, passive: true })
    );
    return () => {
      events.forEach((evt) =>
        window.removeEventListener(evt, onUserInteraction, { capture: true })
      );
    };
  }, [activeCall?.id, audioPlaybackBlocked, audioContextBlocked]);

  const handleRetryMediaPermissions = async () => {
    void callAudio.activateFromGesture();
    if (!activeCall) return;
    const stream = await acquireLocalMedia(activeCall);
    if (!stream) return;

    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((t) => t.stop());
    }
    localStreamRef.current = stream;
    stream.getAudioTracks().forEach((t) => {
      t.enabled = !micMuted;
    });
    stream.getVideoTracks().forEach((t) => {
      t.enabled = !cameraOff;
    });

    for (const [peerId, pc] of peerConnectionsRef.current.entries()) {
      const senders = pc.getSenders();
      for (const track of stream.getTracks()) {
        const matchingSender = senders.find((s) => s.track?.kind === track.kind || (!s.track && s.dtmf !== undefined && track.kind === "audio"));
        if (matchingSender) {
          await matchingSender.replaceTrack(track).catch(() => {});
        } else {
          pc.addTrack(track, stream);
        }
      }
      if (activeCall.status === "active") {
        try {
          const offer = await pc.createOffer({
            offerToReceiveAudio: true,
            offerToReceiveVideo: true
          });
          await pc.setLocalDescription(offer);
          await sendCallSignal({
            callId: activeCall.roomCode,
            senderId: uid,
            recipientId: peerId,
            type: "offer",
            sdp: offer.sdp || ""
          });
        } catch {
          // Ignore transient renegotiation failure
        }
      }
    }

    if (recordingSessionRef.current) {
      connectAudioTracksToRecorder(recordingSessionRef.current, stream);
    }

    setMediaSettledCallId(activeCall.id);
    setMediaReadyTick((t) => t + 1);
  };

  // Observe Firestore signals for activeCall
  useEffect(() => {
    if (!activeCall || activeCall.status === "ended") {
      setSignals([]);
      return;
    }
    return observeCallSignals(activeCall.roomCode, uid, setSignals);
  }, [activeCall?.roomCode, activeCall?.status, uid]);

  const getOrCreatePeerConnection = (
    peerId: string,
    callRoomCode: string,
    callType: string
  ): RTCPeerConnection | null => {
    const existing = peerConnectionsRef.current.get(peerId);
    if (existing) return existing;
    if (typeof RTCPeerConnection === "undefined") return null;

    const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
    const stream = localStreamRef.current;
    if (stream && stream.getTracks().length > 0) {
      stream.getTracks().forEach((track) => pc.addTrack(track, stream));
    } else {
      try {
        pc.addTransceiver("audio", { direction: "recvonly" });
        if (callType !== "audio") {
          pc.addTransceiver("video", { direction: "recvonly" });
        }
      } catch {
        // Ignore on older browsers
      }
    }

    pc.onicecandidate = (event) => {
      if (!event.candidate) return;
      sendCallSignal({
        callId: callRoomCode,
        senderId: uid,
        recipientId: peerId,
        type: "candidate",
        sdpMid: event.candidate.sdpMid ?? "0",
        sdpMLineIndex: event.candidate.sdpMLineIndex ?? 0,
        candidate: event.candidate.candidate
      }).catch(() => {});
    };

    const syncConnectionState = () => {
      const iceState = pc.iceConnectionState;
      const connState = pc.connectionState;
      if (
        iceState === "connected" ||
        iceState === "completed" ||
        connState === "connected"
      ) {
        setRtcStatus("connected");
      } else if (iceState === "checking" || connState === "connecting") {
        setRtcStatus("connecting");
      } else if (
        iceState === "disconnected" ||
        iceState === "failed" ||
        connState === "disconnected" ||
        connState === "failed"
      ) {
        setRtcStatus("reconnecting");
      }
    };

    pc.oniceconnectionstatechange = syncConnectionState;
    pc.onconnectionstatechange = syncConnectionState;

    pc.ontrack = (event) => {
      let remoteStream = remoteStreamRef.current;
      if (!remoteStream) {
        remoteStream = new MediaStream();
        remoteStreamRef.current = remoteStream;
      }
      if (!remoteStream.getTracks().some((t) => t.id === event.track.id)) {
        remoteStream.addTrack(event.track);
      }
      if (event.track.kind === "audio") {
        event.track.enabled = true;
      }
      if (event.track.kind === "video" || remoteStream.getVideoTracks().length > 0) {
        setHasRemoteVideo(true);
      }
      if (remoteVideoRef.current && remoteStream.getVideoTracks().length > 0) {
        remoteVideoRef.current.srcObject = remoteStream;
        remoteVideoRef.current.play().catch(() => {});
      }
      if (remoteAudioRef.current) {
        remoteAudioRef.current.muted = false;
        remoteAudioRef.current.volume = 1;
        remoteAudioRef.current.srcObject = remoteStream;
        remoteAudioRef.current
          .play()
          .then(() => setAudioPlaybackBlocked(false))
          .catch(() => setAudioPlaybackBlocked(true));
      }
      if (recordingSessionRef.current && event.track.kind === "audio") {
        connectAudioTracksToRecorder(recordingSessionRef.current, remoteStream);
      }
      setMediaReadyTick((t) => t + 1);
      setRtcStatus("connected");
    };

    peerConnectionsRef.current.set(peerId, pc);
    return pc;
  };

  const flushPendingCandidates = async (peerId: string, pc: RTCPeerConnection) => {
    const queue = pendingCandidatesRef.current.get(peerId);
    if (!queue || queue.length === 0) return;
    pendingCandidatesRef.current.delete(peerId);
    for (const candidateInit of queue) {
      await pc.addIceCandidate(new RTCIceCandidate(candidateInit)).catch(() => {});
    }
  };

  // Sync participants and initiate WebRTC offers once local media setup settles
  useEffect(() => {
    if (!activeCall || activeCall.status !== "active") return;
    if (mediaSettledCallId !== activeCall.id) return;

    const remotePeers = (activeCall.participantIds || []).filter((id) => id && id !== uid);
    if (remotePeers.length === 0) {
      setRtcStatus("waiting");
      return;
    }

    remotePeers.forEach(async (peerId) => {
      const pc = getOrCreatePeerConnection(peerId, activeCall.roomCode, activeCall.callType);
      if (!pc) return;
      // Match Android deterministic offerer rule: uid < peerId initiates offer
      if (uid < peerId && !offeredPeersRef.current.has(peerId)) {
        offeredPeersRef.current.add(peerId);
        setRtcStatus("connecting");
        try {
          const offer = await pc.createOffer({
            offerToReceiveAudio: true,
            offerToReceiveVideo: true
          });
          await pc.setLocalDescription(offer);
          await sendCallSignal({
            callId: activeCall.roomCode,
            senderId: uid,
            recipientId: peerId,
            type: "offer",
            sdp: offer.sdp || ""
          });
        } catch {
          offeredPeersRef.current.delete(peerId);
        }
      }
    });
  }, [
    activeCall?.id,
    activeCall?.roomCode,
    activeCall?.status,
    activeCall?.callType,
    activeCall?.participantIds?.join(","),
    mediaSettledCallId,
    uid
  ]);

  // Process incoming WebRTC signals sequentially after local media setup settles
  useEffect(() => {
    if (!activeCall || activeCall.status === "ended" || signals.length === 0) return;
    if (mediaSettledCallId !== activeCall.id) return;

    let cancelled = false;
    const callSnapshot = activeCall;

    signalChainRef.current = signalChainRef.current.then(async () => {
      if (cancelled) return;
      for (const signal of signals) {
        if (cancelled) return;
        const key =
          signal.id ||
          `${signal.senderId}:${signal.type}:${signal.sdp?.length || signal.candidate?.length || 0}`;
        if (processedSignalIdsRef.current.has(key)) continue;
        if (!signal.senderId || signal.senderId === uid) continue;
        if (signal.recipientId !== uid && signal.recipientId !== "all") continue;
        processedSignalIdsRef.current.add(key);

        if (signal.type === "offer" && signal.sdp) {
          const pc = getOrCreatePeerConnection(
            signal.senderId,
            callSnapshot.roomCode,
            callSnapshot.callType
          );
          if (!pc) continue;
          setRtcStatus("connecting");
          try {
            await pc.setRemoteDescription(
              new RTCSessionDescription({ type: "offer", sdp: signal.sdp })
            );
            await flushPendingCandidates(signal.senderId, pc);
            const answer = await pc.createAnswer();
            await pc.setLocalDescription(answer);
            await sendCallSignal({
              callId: callSnapshot.roomCode,
              senderId: uid,
              recipientId: signal.senderId,
              type: "answer",
              sdp: answer.sdp || ""
            });
          } catch {
            // Ignore transient SDP errors
          }
        } else if (signal.type === "answer" && signal.sdp) {
          const pc = peerConnectionsRef.current.get(signal.senderId);
          if (!pc || pc.signalingState !== "have-local-offer") continue;
          try {
            await pc.setRemoteDescription(
              new RTCSessionDescription({ type: "answer", sdp: signal.sdp })
            );
            await flushPendingCandidates(signal.senderId, pc);
          } catch {
            // Ignore transient SDP errors
          }
        } else if (signal.type === "candidate" && signal.candidate) {
          const candidateInit: RTCIceCandidateInit = {
            candidate: signal.candidate,
            sdpMid: signal.sdpMid ?? "0",
            sdpMLineIndex: signal.sdpMLineIndex ?? 0
          };
          const pc = peerConnectionsRef.current.get(signal.senderId);
          if (pc && pc.remoteDescription) {
            await pc.addIceCandidate(new RTCIceCandidate(candidateInit)).catch(() => {});
          } else {
            const list = pendingCandidatesRef.current.get(signal.senderId) || [];
            list.push(candidateInit);
            pendingCandidatesRef.current.set(signal.senderId, list);
          }
        }
      }
    });

    return () => {
      cancelled = true;
    };
  }, [signals, activeCall?.id, activeCall?.roomCode, activeCall?.status, mediaSettledCallId, uid]);

  const handleJoinCall = async (e: React.FormEvent) => {
    e.preventDefault();
    void callAudio.activateFromGesture();
    if (!roomCodeInput.trim()) return;
    setBusy(true);
    try {
      const session = await joinCallSession(roomCodeInput, uid, userName);
      onActiveCallChange(session);
      setRoomCodeInput("");
      onNotify(`Joined ${session.roomCode}.`);
    } catch (err) {
      onNotify(err instanceof Error ? err.message : "Could not join call room.");
    } finally {
      setBusy(false);
    }
  };

  const handleStartCallClick = (
    callType: "audio" | "video" | "group",
    targetCreator?: CreatorProfile | null
  ) => {
    void callAudio.activateFromGesture();
    void onStartCall(callType, targetCreator);
  };

  const handleAnswerCallClick = (session: CallSession) => {
    void callAudio.activateFromGesture();
    unmuteAudioTracks();
    void onAnswerCall(session);
  };

  const handleToggleMic = () => {
    void callAudio.activateFromGesture();
    const next = !micMuted;
    setMicMuted(next);
    localStreamRef.current?.getAudioTracks().forEach((t) => {
      t.enabled = !next;
    });
  };

  const handleToggleCamera = () => {
    void callAudio.activateFromGesture();
    const next = !cameraOff;
    setCameraOff(next);
    localStreamRef.current?.getVideoTracks().forEach((t) => {
      t.enabled = !next;
    });
  };

  const handleToggleRecording = async () => {
    void callAudio.activateFromGesture();
    if (!activeCall) return;

    if (recordingSessionRef.current) {
      stopActiveRecording(false);
      onNotify("Recording stopped. Download link is ready below.");
      return;
    }

    if (typeof MediaRecorder === "undefined") {
      onNotify("MediaRecorder is not supported in this browser.");
      return;
    }

    if (completedRecordingUrlRef.current) {
      URL.revokeObjectURL(completedRecordingUrlRef.current);
      completedRecordingUrlRef.current = null;
      setCompletedRecording(null);
    }

    const includeVideo = activeCall.callType !== "audio";
    const AudioCtx =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;

    let audioCtx: AudioContext | null = null;
    let audioDest: MediaStreamAudioDestinationNode | null = null;
    const mixedStream = new MediaStream();

    if (AudioCtx) {
      try {
        audioCtx = new AudioCtx();
        if (audioCtx.state === "suspended") {
          await audioCtx.resume().catch(() => {});
        }
        audioDest = audioCtx.createMediaStreamDestination();
        audioDest.stream.getAudioTracks().forEach((t) => mixedStream.addTrack(t));
      } catch (err) {
        logAudioIssue("recordingAudioContext", err);
      }
    }

    let canvasTimer: number | null = null;
    if (includeVideo && typeof document !== "undefined") {
      const canvas = document.createElement("canvas");
      canvas.width = 960;
      canvas.height = 540;
      const ctx2d = canvas.getContext("2d");

      const drawFrame = () => {
        if (!ctx2d) return;
        ctx2d.fillStyle = "#070a12";
        ctx2d.fillRect(0, 0, canvas.width, canvas.height);

        const remoteVid = remoteVideoRef.current;
        const localVid = localVideoRef.current;
        const remoteReady = Boolean(
          remoteVid && remoteVid.readyState >= 2 && remoteVid.videoWidth > 0
        );
        const localReady = Boolean(
          localVid && localVid.readyState >= 2 && localVid.videoWidth > 0
        );

        if (remoteReady && remoteVid) {
          drawVideoCover(ctx2d, remoteVid, 0, 0, canvas.width, canvas.height);
          if (localReady && localVid) {
            const pipW = 192;
            const pipH = 120;
            const pipX = canvas.width - pipW - 20;
            const pipY = canvas.height - pipH - 20;
            ctx2d.fillStyle = "#050810";
            ctx2d.fillRect(pipX - 2, pipY - 2, pipW + 4, pipH + 4);
            drawVideoCover(ctx2d, localVid, pipX, pipY, pipW, pipH);
          }
        } else if (localReady && localVid) {
          drawVideoCover(ctx2d, localVid, 0, 0, canvas.width, canvas.height);
        } else {
          ctx2d.fillStyle = "#121a2d";
          ctx2d.fillRect(0, 0, canvas.width, canvas.height);
          ctx2d.fillStyle = "#f1f5f9";
          ctx2d.font = "600 26px Inter, system-ui, sans-serif";
          ctx2d.textAlign = "center";
          ctx2d.fillText(
            `${activePeerNameRef.current} · Room ${activeCall.roomCode}`,
            canvas.width / 2,
            canvas.height / 2
          );
        }
      };

      drawFrame();
      canvasTimer = window.setInterval(drawFrame, Math.round(1000 / 24));
      if (typeof canvas.captureStream === "function") {
        canvas
          .captureStream(24)
          .getVideoTracks()
          .forEach((t) => mixedStream.addTrack(t));
      }
    }

    if (mixedStream.getTracks().length === 0) {
      localStreamRef.current?.getTracks().forEach((t) => mixedStream.addTrack(t));
      remoteStreamRef.current?.getTracks().forEach((t) => mixedStream.addTrack(t));
    }

    if (mixedStream.getTracks().length === 0) {
      if (canvasTimer !== null) window.clearInterval(canvasTimer);
      audioCtx?.close().catch(() => {});
      onNotify("No active audio or video stream available to record yet.");
      return;
    }

    const mimeType = pickRecordingMimeType(mixedStream.getVideoTracks().length > 0);
    let recorder: MediaRecorder;
    try {
      recorder = mimeType
        ? new MediaRecorder(mixedStream, { mimeType })
        : new MediaRecorder(mixedStream);
    } catch (err) {
      if (canvasTimer !== null) window.clearInterval(canvasTimer);
      audioCtx?.close().catch(() => {});
      logAudioIssue("MediaRecorder.init", err);
      onNotify("Could not start call recording in this browser.");
      return;
    }

    const chunks: Blob[] = [];
    const startedAtMs = Date.now();
    const roomCode = activeCall.roomCode;

    const session: ActiveRecordingSession = {
      recorder,
      chunks,
      audioCtx,
      audioDest,
      connectedTrackIds: new Set<string>(),
      mixedStream,
      canvasTimer,
      timerInterval: null,
      startedAtMs,
      roomCode,
      mimeType: recorder.mimeType || mimeType
    };

    connectAudioTracksToRecorder(session, localStreamRef.current);
    connectAudioTracksToRecorder(session, remoteStreamRef.current);

    recorder.ondataavailable = (event) => {
      if (event.data && event.data.size > 0) {
        session.chunks.push(event.data);
      }
    };

    recorder.onerror = () => {
      if (recordingSessionRef.current === session) {
        stopActiveRecording(false);
      }
      onNotify("Call recording encountered an unexpected browser error.");
    };

    recorder.onstop = () => {
      if (session.canvasTimer !== null) {
        window.clearInterval(session.canvasTimer);
        session.canvasTimer = null;
      }
      if (session.timerInterval !== null) {
        window.clearInterval(session.timerInterval);
        session.timerInterval = null;
      }
      session.mixedStream.getTracks().forEach((t) => t.stop());
      session.audioCtx?.close().catch(() => {});

      const finalMime = session.mimeType || session.recorder.mimeType || "video/webm";
      const blob = new Blob(session.chunks, { type: finalMime });
      if (blob.size === 0) {
        onNotify("Recording ended with no media data captured.");
        return;
      }

      if (completedRecordingUrlRef.current) {
        URL.revokeObjectURL(completedRecordingUrlRef.current);
      }
      const objectUrl = URL.createObjectURL(blob);
      completedRecordingUrlRef.current = objectUrl;

      const ext = finalMime.includes("mp4")
        ? "mp4"
        : finalMime.includes("ogg")
        ? "ogg"
        : "webm";
      const timestamp = new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19);
      const durationSec = Math.max(1, Math.round((Date.now() - session.startedAtMs) / 1000));

      setCompletedRecording({
        url: objectUrl,
        filename: `pulsecast-call-${session.roomCode}-${timestamp}.${ext}`,
        mimeType: finalMime,
        sizeBytes: blob.size,
        durationSec,
        roomCode: session.roomCode
      });
    };

    setRecordingSec(0);
    session.timerInterval = window.setInterval(() => {
      setRecordingSec(Math.max(0, Math.floor((Date.now() - startedAtMs) / 1000)));
    }, 500);

    recordingSessionRef.current = session;
    setIsRecording(true);
    recorder.start(1000);
    onNotify("Recording call audio & video locally...");
  };

  const handleEndCall = async () => {
    if (!activeCall) return;
    const target = activeCall;
    onActiveCallChange(null);
    cleanupWebRtc();
    try {
      await endCallSession(target.id, elapsedSec);
      onNotify(`Call ended (${formatDuration(elapsedSec)}).`);
    } catch (err) {
      onNotify(err instanceof Error ? err.message : "Failed to close room.");
    }
  };

  const activePeerId = activeCall
    ? activeCall.participantIds.find((id) => id !== uid) || activeCall.hostId
    : null;
  const activePeerProfile = activePeerId ? creatorsById.get(activePeerId) || null : null;
  const fallbackPeerName =
    activePeerProfile?.displayName ||
    (activeCall && activeCall.hostId !== uid
      ? activeCall.hostName
      : activeCall?.participantNames?.find((n) => n && n !== activeCall.hostName)) ||
    activeCall?.hostName ||
    "PulseCast Peer";

  const callerId = activeCall ? activeCall.hostId : null;
  const receiverId = activeCall
    ? activeCall.participantIds.find((id) => id !== activeCall.hostId) ||
      (activeCall.hostId !== uid ? uid : null)
    : null;

  const callerFallbackName = activeCall?.hostName || "Caller";
  const receiverFallbackName =
    activeCall?.participantNames?.find((n) => n && n !== activeCall.hostName) ||
    (receiverId === uid ? userName : "Waiting for peer");

  const callerUser = useUser(
    callerId,
    callerId ? creatorsById.get(callerId) || null : null,
    callerFallbackName
  );
  const receiverUser = useUser(
    receiverId,
    receiverId ? creatorsById.get(receiverId) || null : null,
    receiverFallbackName
  );
  const activePeerUser = useUser(activePeerId, activePeerProfile, fallbackPeerName);
  const activePeerName = activePeerUser.displayName;
  activePeerNameRef.current = activePeerName;
  const hasVisibleVideoStage = Boolean(
    activeCall &&
      (hasRemoteVideo || (activeCall.callType !== "audio" && !cameraOff && !mediaError))
  );

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Audio & Video Calls</h1>
          <p className="page-subtitle">
            Ring creators across Android & Web with live two-way voice, video, and instant answer controls.
          </p>
        </div>
        <span
          className={`badge ${audioUiState === "Active" ? "green" : "violet"}`}
          aria-live="polite"
        >
          <Volume2 size={14} />
          Audio: {audioUiState}
        </span>
      </div>

      {audioSuspendedAfterAttempt && (
        <section
          className="card"
          style={{
            marginBottom: 18,
            borderColor: "var(--border-accent)",
            background: "rgba(34, 211, 238, 0.1)",
            display: "flex",
            flexWrap: "wrap",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 12
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <div
              style={{
                width: 40,
                height: 40,
                borderRadius: 12,
                background: "rgba(34, 211, 238, 0.18)",
                color: "var(--accent-cyan)",
                display: "grid",
                placeItems: "center",
                flexShrink: 0
              }}
            >
              <Volume2 size={20} />
            </div>
            <div>
              <div style={{ fontWeight: 700, fontSize: "0.95rem" }}>
                Audio Output Suspended
              </div>
              <div style={{ color: "var(--text-secondary)", fontSize: "0.84rem" }}>
                Your browser kept the audio engine suspended after your last tap. Tap to activate ringtones and call audio.
              </div>
            </div>
          </div>
          <button
            type="button"
            className="btn btn-primary"
            disabled={activatingAudio}
            onClick={handleTapToActivateAudio}
          >
            <Volume2 size={16} />
            {activatingAudio ? "Activating Audio..." : "Tap to Activate Audio"}
          </button>
        </section>
      )}

      {completedRecording && (
        <section
          className="card"
          style={{
            marginBottom: 18,
            borderColor: "rgba(16, 185, 129, 0.45)",
            background: "rgba(16, 185, 129, 0.1)",
            display: "flex",
            flexWrap: "wrap",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 12
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <div
              style={{
                width: 40,
                height: 40,
                borderRadius: 12,
                background: "rgba(16, 185, 129, 0.18)",
                color: "#6ee7b7",
                display: "grid",
                placeItems: "center",
                flexShrink: 0
              }}
            >
              <Download size={20} />
            </div>
            <div>
              <div style={{ fontWeight: 700, fontSize: "0.95rem" }}>
                Call Recording Ready ({completedRecording.roomCode})
              </div>
              <div style={{ color: "var(--text-secondary)", fontSize: "0.84rem" }}>
                Duration {formatDuration(completedRecording.durationSec)} ·{" "}
                {formatRecordingSize(completedRecording.sizeBytes)} · Local recording ready for download
              </div>
            </div>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <a
              href={completedRecording.url}
              download={completedRecording.filename}
              className="btn btn-primary btn-sm"
            >
              <Download size={15} />
              Download Recording
            </a>
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              onClick={handleDismissRecording}
              aria-label="Dismiss recording"
            >
              <X size={15} />
              Dismiss
            </button>
          </div>
        </section>
      )}

      {activeCall && (
        <section className="call-stage">
          <audio
            ref={remoteAudioRef}
            autoPlay
            playsInline
            style={{ position: "absolute", width: 0, height: 0, opacity: 0, pointerEvents: "none" }}
          />

          <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "space-between", gap: 12 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
              <UserAvatar
                displayName={activePeerName}
                photoUrl={activePeerUser.photoUrl}
                avatarBase64={activePeerUser.avatarBase64}
                isOnline={activePeerUser.isOnline}
                isGroup={activeCall.callType === "group"}
                size={54}
              />
              <div>
                <span className={`badge ${rtcStatus === "connected" ? "green" : "violet"}`}>
                  <Radio size={13} />
                  {activeCall.status === "ringing"
                    ? `RINGING ${activePeerName.toUpperCase()}...`
                    : rtcStatus === "connected"
                    ? `CONNECTED LIVE · ${activeCall.callType.toUpperCase()}`
                    : `${rtcStatus.toUpperCase()} · ${activeCall.callType.toUpperCase()}`}
                </span>
                <h2 style={{ marginTop: 6 }}>{activePeerName}</h2>
                <div style={{ color: "var(--text-secondary)", fontSize: "0.85rem", marginTop: 2 }}>
                  Room {activeCall.roomCode} · Participants:{" "}
                  {(activeCall.participantNames || [userName]).join(", ")}
                </div>
              </div>
            </div>

            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              {isRecording && (
                <span
                  className="badge"
                  style={{
                    background: "rgba(239, 68, 68, 0.2)",
                    color: "#fca5a5",
                    border: "1px solid rgba(239, 68, 68, 0.45)",
                    fontSize: "0.85rem",
                    padding: "6px 12px"
                  }}
                >
                  <Disc size={13} />
                  REC {formatDuration(recordingSec)}
                </span>
              )}
              <span
                role="timer"
                aria-label="Call duration"
                className={`badge ${activeCall.status === "active" ? "green" : "violet"}`}
                style={{
                  fontSize: "0.95rem",
                  padding: "6px 14px",
                  fontVariantNumeric: "tabular-nums"
                }}
              >
                <Clock size={14} />
                {activeCall.status === "active"
                  ? formatCallDuration(elapsedSec)
                  : "00:00 · Ringing"}
              </span>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                onClick={() => {
                  navigator.clipboard?.writeText(activeCall.roomCode);
                  onNotify(`Copied room code ${activeCall.roomCode}`);
                }}
              >
                <Copy size={14} />
                Copy Code
              </button>
            </div>
          </div>

          <div style={{ position: "relative", marginTop: 14 }}>
            {hasRemoteVideo ? (
              <video
                ref={remoteVideoRef}
                className="camera-preview"
                autoPlay
                playsInline
              />
            ) : activeCall.callType !== "audio" && !cameraOff && !mediaError ? (
              <video
                ref={localVideoRef}
                className="camera-preview"
                autoPlay
                muted
                playsInline
              />
            ) : null}

            {hasRemoteVideo && activeCall.callType !== "audio" && !cameraOff && (
              <video
                ref={localVideoRef}
                autoPlay
                muted
                playsInline
                style={{
                  position: "absolute",
                  right: 14,
                  bottom: 28,
                  width: 128,
                  height: 172,
                  objectFit: "cover",
                  borderRadius: 14,
                  border: "2px solid var(--border-accent)",
                  background: "#050810"
                }}
              />
            )}

            <div
              className={
                hasVisibleVideoStage
                  ? "call-avatar-overlay floating"
                  : "call-avatar-overlay stage"
              }
              aria-label="Call participants avatars"
            >
              <div className="call-avatar-participant">
                <UserAvatar
                  displayName={callerUser.displayName}
                  photoUrl={callerUser.photoUrl}
                  avatarBase64={callerUser.avatarBase64}
                  isOnline={callerUser.isOnline || activeCall.status !== "ended"}
                  size={hasVisibleVideoStage ? 38 : 64}
                />
                <div className="call-avatar-meta">
                  <span className="call-avatar-role">Caller</span>
                  <span className="call-avatar-name">{callerUser.displayName}</span>
                </div>
              </div>

              <div
                className="call-avatar-divider"
                style={{ fontVariantNumeric: "tabular-nums" }}
              >
                {activeCall.status === "active"
                  ? `Live · ${formatCallDuration(elapsedSec)}`
                  : "Ringing…"}
              </div>

              <div className="call-avatar-participant">
                <UserAvatar
                  displayName={receiverId ? receiverUser.displayName : "Open Room"}
                  photoUrl={receiverId ? receiverUser.photoUrl : null}
                  avatarBase64={receiverId ? receiverUser.avatarBase64 : null}
                  isOnline={receiverId ? receiverUser.isOnline || activeCall.status === "active" : false}
                  isGroup={activeCall.callType === "group" && !receiverId}
                  size={hasVisibleVideoStage ? 38 : 64}
                />
                <div className="call-avatar-meta">
                  <span className="call-avatar-role">Receiver</span>
                  <span className="call-avatar-name">
                    {receiverId ? receiverUser.displayName : "Waiting to join"}
                  </span>
                </div>
              </div>
            </div>
          </div>

          {mediaError && (
            <div
              style={{
                marginTop: 14,
                padding: 12,
                borderRadius: 12,
                background: "rgba(245, 158, 11, 0.12)",
                color: "#fde68a",
                fontSize: "0.85rem",
                display: "flex",
                flexWrap: "wrap",
                alignItems: "center",
                justifyContent: "space-between",
                gap: 10
              }}
            >
              <span>{mediaError}</span>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                onClick={handleRetryMediaPermissions}
              >
                <Mic size={14} />
                Retry Mic &amp; Camera
              </button>
            </div>
          )}

          {(audioPlaybackBlocked || audioContextBlocked) && !audioSuspendedAfterAttempt && (
            <div
              style={{
                marginTop: 12,
                padding: 12,
                borderRadius: 12,
                background: "rgba(34, 211, 238, 0.12)",
                border: "1px solid var(--border-accent)",
                display: "flex",
                flexWrap: "wrap",
                alignItems: "center",
                justifyContent: "space-between",
                gap: 10,
                fontSize: "0.85rem"
              }}
            >
              <span>Browser audio output is suspended by autoplay policy.</span>
              <button
                type="button"
                className="btn btn-primary btn-sm"
                disabled={activatingAudio}
                onClick={handleTapToActivateAudio}
              >
                <Volume2 size={14} />
                {activatingAudio ? "Activating Audio..." : "Tap to Activate Audio"}
              </button>
            </div>
          )}

          <div style={{ display: "flex", flexWrap: "wrap", gap: 10, marginTop: 18 }}>
            {activeCall.status === "ringing" && activeCall.hostId !== uid && (
              <>
                <button
                  type="button"
                  className="btn btn-answer"
                  onClick={() => handleAnswerCallClick(activeCall)}
                >
                  <Check size={17} />
                  Answer Call
                </button>
                <button
                  type="button"
                  className="btn btn-danger"
                  onClick={() => onDeclineCall(activeCall)}
                >
                  <PhoneOff size={17} />
                  Decline
                </button>
              </>
            )}
            <button type="button" className="btn btn-secondary" onClick={handleToggleMic}>
              {micMuted ? <MicOff size={17} /> : <Mic size={17} />}
              {micMuted ? "Unmute Mic" : "Mute Mic"}
            </button>
            {activeCall.callType !== "audio" && (
              <button type="button" className="btn btn-secondary" onClick={handleToggleCamera}>
                {cameraOff ? <VideoOff size={17} /> : <Video size={17} />}
                {cameraOff ? "Turn Camera On" : "Turn Camera Off"}
              </button>
            )}
            <button
              type="button"
              className={`btn ${isRecording ? "btn-danger" : "btn-secondary"}`}
              onClick={() => void handleToggleRecording()}
            >
              {isRecording ? <Square size={16} /> : <Disc size={17} />}
              {isRecording
                ? `Stop Recording (${formatDuration(recordingSec)})`
                : "Record Call"}
            </button>
            <button type="button" className="btn btn-danger" onClick={handleEndCall}>
              <PhoneOff size={17} />
              End Call
            </button>
          </div>
        </section>
      )}

      {otherCreators.length > 0 && (
        <section className="card" style={{ marginBottom: 20 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
            <div>
              <h3 style={{ marginBottom: 4 }}>Call People Directly ({otherCreators.length})</h3>
              <div style={{ fontSize: "0.84rem", color: "var(--text-secondary)" }}>
                Tap a creator to view their profile photo or start an Audio / Video call that rings on their device.
              </div>
            </div>
          </div>

          <div className="creator-strip">
            {otherCreators.map((creator) => {
              const online = resolveIsUserOnline(
                creator.isOnline,
                creator.lastActiveAt,
                creator.updatedAt
              );
              return (
                <div key={creator.userId} className="creator-call-card">
                  <div
                    style={{ display: "flex", alignItems: "center", gap: 10, cursor: "pointer" }}
                    onClick={() => onOpenCreatorProfile(creator)}
                  >
                    <UserAvatar
                      displayName={creator.displayName}
                      photoUrl={creator.photoUrl}
                      avatarBase64={creator.avatarBase64}
                      isOnline={online}
                      size={44}
                    />
                    <div style={{ minWidth: 0 }}>
                      <div
                        style={{
                          fontWeight: 700,
                          fontSize: "0.94rem",
                          whiteSpace: "nowrap",
                          overflow: "hidden",
                          textOverflow: "ellipsis"
                        }}
                      >
                        {creator.displayName}
                      </div>
                      <div style={{ fontSize: "0.78rem", color: "var(--accent-cyan)" }}>
                        @{creator.handle} · {online ? "Online" : "Offline"}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
                    <button
                      type="button"
                      className="btn btn-secondary btn-sm"
                      style={{ flex: 1 }}
                      onClick={() => handleStartCallClick("audio", creator)}
                    >
                      <Phone size={14} />
                      Audio
                    </button>
                    <button
                      type="button"
                      className="btn btn-primary btn-sm"
                      style={{ flex: 1 }}
                      onClick={() => handleStartCallClick("video", creator)}
                    >
                      <Video size={14} />
                      Video
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      )}

      <div className="calls-grid">
        <section className="card">
          <h3 style={{ marginBottom: 8 }}>Start a New Call Room</h3>
          <p style={{ color: "var(--text-secondary)", fontSize: "0.88rem", margin: "0 0 12px" }}>
            {selectedTargetCreator
              ? `Ringing ${selectedTargetCreator.displayName} directly.`
              : "Pick a creator to ring them directly, or launch an open room code."}
          </p>

          {otherCreators.length > 0 && (
            <div className="chip-row" style={{ marginBottom: 14 }}>
              <button
                type="button"
                className={`chip ${selectedTargetCreator === null ? "active" : ""}`}
                onClick={() => setSelectedTargetId(null)}
              >
                Open Room
              </button>
              {otherCreators.map((c) => {
                const isSelected = selectedTargetId === c.userId;
                return (
                  <button
                    key={c.userId}
                    type="button"
                    className={`chip ${isSelected ? "active" : ""}`}
                    style={{ display: "inline-flex", alignItems: "center", gap: 6 }}
                    onClick={() => setSelectedTargetId(isSelected ? null : c.userId)}
                  >
                    <UserAvatar
                      displayName={c.displayName}
                      photoUrl={c.photoUrl}
                      avatarBase64={c.avatarBase64}
                      showStatus={false}
                      size={20}
                    />
                    {c.displayName}
                  </button>
                );
              })}
            </div>
          )}

          <div style={{ display: "flex", flexWrap: "wrap", gap: 10 }}>
            <button
              type="button"
              className="btn btn-primary"
              disabled={busy}
              onClick={() => handleStartCallClick("video", selectedTargetCreator)}
            >
              <Video size={16} />
              {selectedTargetCreator ? "Call Video" : "Video Call"}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => handleStartCallClick("audio", selectedTargetCreator)}
            >
              <Phone size={16} />
              {selectedTargetCreator ? "Call Audio" : "Audio Call"}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => handleStartCallClick("group", selectedTargetCreator)}
            >
              <Users size={16} />
              Group Room
            </button>
          </div>
        </section>

        <section className="card">
          <h3 style={{ marginBottom: 8 }}>Join by Room Code</h3>
          <p style={{ color: "var(--text-secondary)", fontSize: "0.88rem", margin: "0 0 16px" }}>
            Enter a room code shared from another PulseCast user.
          </p>
          <form onSubmit={handleJoinCall} style={{ display: "flex", gap: 10 }}>
            <input
              className="input"
              placeholder="e.g. PULSE-8A2K"
              value={roomCodeInput}
              onChange={(e) => setRoomCodeInput(e.target.value.toUpperCase())}
            />
            <button type="submit" className="btn btn-primary" disabled={busy || !roomCodeInput.trim()}>
              Join
            </button>
          </form>
        </section>
      </div>

      <section className="card" style={{ marginTop: 20 }}>
        <h3 style={{ marginBottom: 14 }}>Recent Call Sessions</h3>
        {sessions.length === 0 ? (
          <div style={{ color: "var(--text-secondary)", fontSize: "0.9rem" }}>
            No call sessions recorded yet.
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {sessions.map((s) => {
              const peerId = s.participantIds.find((id) => id !== uid) || s.hostId;
              const peerProfile = creatorsById.get(peerId) || creatorsById.get(s.hostId) || null;
              const isIncomingRinging = s.status === "ringing" && s.hostId !== uid;
              const peerName =
                peerProfile?.displayName ||
                (s.hostId !== uid
                  ? s.hostName
                  : s.participantNames?.find((n) => n && n !== s.hostName)) ||
                s.hostName;
              const peerOnline = peerProfile
                ? resolveIsUserOnline(peerProfile.isOnline, peerProfile.lastActiveAt, peerProfile.updatedAt)
                : s.status !== "ended";

              return (
                <div
                  key={s.id}
                  style={{
                    display: "flex",
                    flexWrap: "wrap",
                    alignItems: "center",
                    justifyContent: "space-between",
                    gap: 12,
                    padding: "12px 14px",
                    borderRadius: 14,
                    background: isIncomingRinging ? "rgba(16, 185, 129, 0.1)" : "var(--bg-elevated)",
                    border: isIncomingRinging
                      ? "1px solid rgba(16, 185, 129, 0.45)"
                      : "1px solid var(--border-subtle)"
                  }}
                >
                  <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                    <UserAvatar
                      displayName={peerName}
                      photoUrl={peerProfile?.photoUrl}
                      avatarBase64={peerProfile?.avatarBase64}
                      isOnline={peerOnline}
                      isGroup={s.callType === "group"}
                      size={42}
                      onClick={peerProfile ? () => onOpenCreatorProfile(peerProfile) : undefined}
                    />
                    <div>
                      <div style={{ fontWeight: 700 }}>
                        {peerName} · {s.callType.toUpperCase()}
                      </div>
                      <div style={{ fontSize: "0.82rem", color: "var(--text-secondary)", marginTop: 2 }}>
                        {s.roomCode} · Duration: {formatDuration(s.durationSec)}
                      </div>
                    </div>
                  </div>

                  {isIncomingRinging ? (
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      <button
                        type="button"
                        className="btn btn-danger btn-sm"
                        onClick={() => onDeclineCall(s)}
                      >
                        <PhoneOff size={14} />
                        Decline
                      </button>
                      <button
                        type="button"
                        className="btn btn-answer btn-sm"
                        onClick={() => handleAnswerCallClick(s)}
                      >
                        <Check size={14} />
                        Answer
                      </button>
                    </div>
                  ) : s.status !== "ended" ? (
                    <button
                      type="button"
                      className="btn btn-primary btn-sm"
                      onClick={() => handleAnswerCallClick(s)}
                    >
                      Join
                    </button>
                  ) : (
                    <span className="badge amber">{s.status}</span>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}
