import { useEffect, useRef, useState } from "react";
import {
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
import { createCallSession, endCallSession, joinCallSession } from "../repository";
import { formatDuration } from "../mediaUtils";
import type { CallSession } from "../types";

interface CallsViewProps {
  uid: string;
  userName: string;
  sessions: CallSession[];
  onNotify: (msg: string) => void;
}

export function CallsView({ uid, userName, sessions, onNotify }: CallsViewProps) {
  const [activeCall, setActiveCall] = useState<CallSession | null>(null);
  const [roomCodeInput, setRoomCodeInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [elapsedSec, setElapsedSec] = useState(0);
  const [micMuted, setMicMuted] = useState(false);
  const [cameraOff, setCameraOff] = useState(false);
  const [mediaError, setMediaError] = useState<string | null>(null);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const stopMediaStream = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
  };

  useEffect(() => {
    if (!activeCall) {
      stopMediaStream();
      setElapsedSec(0);
      setMediaError(null);
      return;
    }

    const interval = window.setInterval(() => {
      setElapsedSec((prev) => prev + 1);
    }, 1000);

    let cancelled = false;
    const startPreview = async () => {
      if (!navigator.mediaDevices?.getUserMedia) {
        setMediaError("Browser media preview is unavailable on this device.");
        return;
      }
      try {
        const wantVideo = activeCall.callType !== "audio";
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: true,
          video: wantVideo
        });
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }
        streamRef.current = stream;
        if (videoRef.current && wantVideo) {
          videoRef.current.srcObject = stream;
        }
      } catch {
        if (!cancelled) {
          setMediaError("Camera/microphone permission was not granted. Call room signaling remains active.");
        }
      }
    };

    startPreview();
    return () => {
      cancelled = true;
      window.clearInterval(interval);
      stopMediaStream();
    };
  }, [activeCall?.id]);

  // Keep activeCall updated with live Firestore participant changes
  useEffect(() => {
    if (!activeCall) return;
    const latest = sessions.find((s) => s.id === activeCall.id);
    if (latest) {
      if (latest.status === "ended") {
        setActiveCall(null);
      } else {
        setActiveCall(latest);
      }
    }
  }, [sessions, activeCall?.id]);

  const handleStartCall = async (callType: "audio" | "video" | "group") => {
    setBusy(true);
    try {
      const session = await createCallSession(uid, userName, callType);
      setMicMuted(false);
      setCameraOff(callType === "audio");
      setActiveCall(session);
      onNotify(`Room ${session.roomCode} started.`);
    } catch (err) {
      onNotify(err instanceof Error ? err.message : "Could not start call room.");
    } finally {
      setBusy(false);
    }
  };

  const handleJoinCall = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!roomCodeInput.trim()) return;
    setBusy(true);
    try {
      const session = await joinCallSession(roomCodeInput, uid, userName);
      setMicMuted(false);
      setCameraOff(session.callType === "audio");
      setActiveCall(session);
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
    streamRef.current?.getAudioTracks().forEach((t) => {
      t.enabled = !next;
    });
  };

  const handleToggleCamera = () => {
    const next = !cameraOff;
    setCameraOff(next);
    streamRef.current?.getVideoTracks().forEach((t) => {
      t.enabled = !next;
    });
  };

  const handleEndCall = async () => {
    if (!activeCall) return;
    const target = activeCall;
    setActiveCall(null);
    stopMediaStream();
    try {
      await endCallSession(target.id, elapsedSec);
      onNotify(`Call ended (${formatDuration(elapsedSec)}).`);
    } catch (err) {
      onNotify(err instanceof Error ? err.message : "Failed to close room.");
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Studio Call Rooms</h1>
          <p className="page-subtitle">
            Start or join room-coded audio, video, and group studio sessions from any browser.
          </p>
        </div>
      </div>

      {activeCall && (
        <section className="call-stage">
          <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "space-between", gap: 12 }}>
            <div>
              <span className="badge green">
                <Radio size={13} />
                {activeCall.status.toUpperCase()} · {activeCall.callType.toUpperCase()}
              </span>
              <h2 style={{ marginTop: 8 }}>Room {activeCall.roomCode}</h2>
              <div style={{ color: "var(--text-secondary)", fontSize: "0.88rem", marginTop: 4 }}>
                Participants: {(activeCall.participantNames || [userName]).join(", ")}
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

          {activeCall.callType !== "audio" && !cameraOff && !mediaError && (
            <video ref={videoRef} className="camera-preview" autoPlay muted playsInline />
          )}

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

      <div className="calls-grid">
        <section className="card">
          <h3 style={{ marginBottom: 8 }}>Start a New Call Room</h3>
          <p style={{ color: "var(--text-secondary)", fontSize: "0.88rem", margin: "0 0 16px" }}>
            Generate a shareable <code>PULSE-XXXX</code> room code that works across Android and Web.
          </p>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 10 }}>
            <button
              type="button"
              className="btn btn-primary"
              disabled={busy}
              onClick={() => handleStartCall("video")}
            >
              <Video size={16} />
              Video Call
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => handleStartCall("audio")}
            >
              <Phone size={16} />
              Audio Call
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => handleStartCall("group")}
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
            {sessions.map((s) => (
              <div
                key={s.id}
                style={{
                  display: "flex",
                  flexWrap: "wrap",
                  alignItems: "center",
                  justifyContent: "space-between",
                  gap: 10,
                  padding: "12px 14px",
                  borderRadius: 14,
                  background: "var(--bg-elevated)",
                  border: "1px solid var(--border-subtle)"
                }}
              >
                <div>
                  <div style={{ fontWeight: 700 }}>
                    {s.roomCode} · {s.callType.toUpperCase()}
                  </div>
                  <div style={{ fontSize: "0.82rem", color: "var(--text-secondary)", marginTop: 2 }}>
                    Host: {s.hostName} · Duration: {formatDuration(s.durationSec)}
                  </div>
                </div>
                <span className={`badge ${s.status === "ended" ? "amber" : "green"}`}>
                  {s.status}
                </span>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
