import { useEffect, useMemo, useRef, useState } from "react";
import {
  Check,
  Copy,
  Mic,
  MicOff,
  Phone,
  PhoneOff,
  Radio,
  Users,
  Video,
  VideoOff
} from "lucide-react";
import {
  endCallSession,
  joinCallSession,
  observeCallSignals,
  resolveIsUserOnline,
  sendCallSignal
} from "../repository";
import { formatDuration } from "../mediaUtils";
import type { CallSession, CallSignal, CreatorProfile } from "../types";
import { UserAvatar } from "./UserAvatar";

type RtcConnectionStatus = "waiting" | "connecting" | "connected" | "reconnecting";

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
  const [signals, setSignals] = useState<CallSignal[]>([]);
  const [rtcStatus, setRtcStatus] = useState<RtcConnectionStatus>("waiting");
  const [hasRemoteVideo, setHasRemoteVideo] = useState(false);

  const localVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const remoteStreamRef = useRef<MediaStream | null>(null);
  const peerConnectionsRef = useRef<Map<string, RTCPeerConnection>>(new Map());
  const pendingCandidatesRef = useRef<Map<string, RTCIceCandidateInit[]>>(new Map());
  const offeredPeersRef = useRef<Set<string>>(new Set());
  const processedSignalIdsRef = useRef<Set<string>>(new Set());

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

  const cleanupWebRtc = () => {
    peerConnectionsRef.current.forEach((pc) => {
      try {
        pc.close();
      } catch {
        // Ignore
      }
    });
    peerConnectionsRef.current.clear();
    pendingCandidatesRef.current.clear();
    offeredPeersRef.current.clear();
    processedSignalIdsRef.current.clear();

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
    setRtcStatus("waiting");
  };

  // Timer counts while call is active
  useEffect(() => {
    if (!activeCall) {
      setElapsedSec(0);
      return;
    }
    if (activeCall.status !== "active") return;
    const interval = window.setInterval(() => {
      setElapsedSec((prev) => prev + 1);
    }, 1000);
    return () => window.clearInterval(interval);
  }, [activeCall?.id, activeCall?.status]);

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

    let cancelled = false;
    const startMedia = async () => {
      if (!navigator.mediaDevices?.getUserMedia) {
        setMediaError("Browser microphone/camera API is unavailable on this page (HTTPS required).");
        setMediaReadyTick((t) => t + 1);
        return;
      }

      let stream: MediaStream | null = null;
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
          video: wantVideo
        });
      } catch {
        // Fallback to audio-only if camera failed or was denied
        try {
          stream = await navigator.mediaDevices.getUserMedia({
            audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
            video: false
          });
          setCameraOff(true);
        } catch {
          if (!cancelled) {
            setMediaError("Microphone permission was not granted. Signaling remains active.");
            setMediaReadyTick((t) => t + 1);
          }
          return;
        }
      }

      if (cancelled) {
        stream.getTracks().forEach((t) => t.stop());
        return;
      }

      localStreamRef.current = stream;
      if (localVideoRef.current && stream.getVideoTracks().length > 0) {
        localVideoRef.current.srcObject = stream;
      }
      setMediaReadyTick((t) => t + 1);
    };

    startMedia();
    return () => {
      cancelled = true;
      cleanupWebRtc();
    };
  }, [activeCall?.id]);

  // Observe Firestore signals for activeCall
  useEffect(() => {
    if (!activeCall || activeCall.status === "ended") {
      setSignals([]);
      return;
    }
    return observeCallSignals(activeCall.roomCode, uid, setSignals);
  }, [activeCall?.roomCode, activeCall?.status, uid]);

  const getOrCreatePeerConnection = (peerId: string, callRoomCode: string): RTCPeerConnection | null => {
    const existing = peerConnectionsRef.current.get(peerId);
    if (existing) return existing;
    if (typeof RTCPeerConnection === "undefined") return null;

    const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
    const stream = localStreamRef.current;
    if (stream) {
      stream.getTracks().forEach((track) => pc.addTrack(track, stream));
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

    pc.oniceconnectionstatechange = () => {
      const state = pc.iceConnectionState;
      if (state === "connected" || state === "completed") {
        setRtcStatus("connected");
      } else if (state === "checking") {
        setRtcStatus("connecting");
      } else if (state === "disconnected" || state === "failed") {
        setRtcStatus("reconnecting");
      }
    };

    pc.ontrack = (event) => {
      let remoteStream = remoteStreamRef.current;
      if (!remoteStream) {
        remoteStream = new MediaStream();
        remoteStreamRef.current = remoteStream;
      }
      if (!remoteStream.getTracks().some((t) => t.id === event.track.id)) {
        remoteStream.addTrack(event.track);
      }
      if (event.track.kind === "video") {
        setHasRemoteVideo(true);
      }
      if (remoteVideoRef.current && remoteStream.getVideoTracks().length > 0) {
        remoteVideoRef.current.srcObject = remoteStream;
        remoteVideoRef.current.play().catch(() => {});
      }
      if (remoteAudioRef.current) {
        remoteAudioRef.current.srcObject = remoteStream;
        remoteAudioRef.current.play().catch(() => {});
      }
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

  // Sync participants and initiate WebRTC offers when session becomes active
  useEffect(() => {
    if (!activeCall || activeCall.status !== "active") return;
    const remotePeers = (activeCall.participantIds || []).filter((id) => id && id !== uid);
    if (remotePeers.length === 0) {
      setRtcStatus("waiting");
      return;
    }

    remotePeers.forEach(async (peerId) => {
      const pc = getOrCreatePeerConnection(peerId, activeCall.roomCode);
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
  }, [activeCall?.roomCode, activeCall?.status, activeCall?.participantIds?.join(","), mediaReadyTick, uid]);

  // Process incoming WebRTC signals
  useEffect(() => {
    if (!activeCall || activeCall.status === "ended" || signals.length === 0) return;

    const processSignals = async () => {
      for (const signal of signals) {
        const key = signal.id || `${signal.senderId}:${signal.type}:${signal.sdp?.length || signal.candidate?.length || 0}`;
        if (processedSignalIdsRef.current.has(key)) continue;
        if (!signal.senderId || signal.senderId === uid) continue;
        if (signal.recipientId !== uid && signal.recipientId !== "all") continue;
        processedSignalIdsRef.current.add(key);

        if (signal.type === "offer" && signal.sdp) {
          const pc = getOrCreatePeerConnection(signal.senderId, activeCall.roomCode);
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
              callId: activeCall.roomCode,
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
          if (!pc) continue;
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
    };

    processSignals();
  }, [signals, activeCall?.roomCode, activeCall?.status, mediaReadyTick, uid]);

  const handleJoinCall = async (e: React.FormEvent) => {
    e.preventDefault();
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

  const handleToggleMic = () => {
    const next = !micMuted;
    setMicMuted(next);
    localStreamRef.current?.getAudioTracks().forEach((t) => {
      t.enabled = !next;
    });
  };

  const handleToggleCamera = () => {
    const next = !cameraOff;
    setCameraOff(next);
    localStreamRef.current?.getVideoTracks().forEach((t) => {
      t.enabled = !next;
    });
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
  const activePeerName =
    activePeerProfile?.displayName ||
    (activeCall && activeCall.hostId !== uid
      ? activeCall.hostName
      : activeCall?.participantNames?.find((n) => n && n !== activeCall.hostName)) ||
    activeCall?.hostName ||
    "PulseCast Peer";

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Audio & Video Calls</h1>
          <p className="page-subtitle">
            Ring creators across Android & Web with live two-way voice, video, and instant answer controls.
          </p>
        </div>
      </div>

      {activeCall && (
        <section className="call-stage">
          <audio ref={remoteAudioRef} autoPlay playsInline style={{ display: "none" }} />

          <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "space-between", gap: 12 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
              <UserAvatar
                displayName={activePeerName}
                photoUrl={activePeerProfile?.photoUrl}
                avatarBase64={activePeerProfile?.avatarBase64}
                isOnline={true}
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
              <span className="badge violet" style={{ fontSize: "0.95rem", padding: "6px 14px" }}>
                {formatDuration(elapsedSec)}
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
          </div>

          {mediaError && (
            <div
              style={{
                marginTop: 14,
                padding: 12,
                borderRadius: 12,
                background: "rgba(245, 158, 11, 0.12)",
                color: "#fde68a",
                fontSize: "0.85rem"
              }}
            >
              {mediaError}
            </div>
          )}

          <div style={{ display: "flex", flexWrap: "wrap", gap: 10, marginTop: 18 }}>
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
                      onClick={() => onStartCall("audio", creator)}
                    >
                      <Phone size={14} />
                      Audio
                    </button>
                    <button
                      type="button"
                      className="btn btn-primary btn-sm"
                      style={{ flex: 1 }}
                      onClick={() => onStartCall("video", creator)}
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
              onClick={() => onStartCall("video", selectedTargetCreator)}
            >
              <Video size={16} />
              {selectedTargetCreator ? "Call Video" : "Video Call"}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => onStartCall("audio", selectedTargetCreator)}
            >
              <Phone size={16} />
              {selectedTargetCreator ? "Call Audio" : "Audio Call"}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => onStartCall("group", selectedTargetCreator)}
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
                        onClick={() => onAnswerCall(s)}
                      >
                        <Check size={14} />
                        Answer
                      </button>
                    </div>
                  ) : s.status !== "ended" ? (
                    <button
                      type="button"
                      className="btn btn-primary btn-sm"
                      onClick={() => onAnswerCall(s)}
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
