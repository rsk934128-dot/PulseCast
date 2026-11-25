import { useEffect, useState } from "react";
import {
  AlertTriangle,
  BellRing,
  CheckCircle2,
  Phone,
  PhoneOff,
  RefreshCw,
  Users,
  Video,
  Volume2
} from "lucide-react";
import { callAudio, type AudioUiState } from "../callAudio";
import { useUser } from "../hooks/useUser";
import type { CallSession, CreatorProfile } from "../types";
import { UserAvatar } from "./UserAvatar";

interface IncomingCallModalProps {
  session: CallSession;
  callerProfile?: CreatorProfile | null;
  audioBlocked: boolean;
  onUnlockAudio?: () => void;
  onAnswer: () => void;
  onDecline: () => void;
}

type UnlockStatus = "idle" | "unlocking" | "unlocked" | "suspended";

export function IncomingCallModal({
  session,
  callerProfile,
  audioBlocked,
  onUnlockAudio,
  onAnswer,
  onDecline
}: IncomingCallModalProps) {
  const [liveBlocked, setLiveBlocked] = useState(() => callAudio.isBlocked());
  const [suspendedAfterAttempt, setSuspendedAfterAttempt] = useState(() =>
    callAudio.isSuspendedAfterAttempt()
  );
  const [audioUiState, setAudioUiState] = useState<AudioUiState>(() => callAudio.getUiState());
  const [unlockStatus, setUnlockStatus] = useState<UnlockStatus>(() =>
    callAudio.getUiState() === "Active" ? "unlocked" : "idle"
  );

  useEffect(() => {
    const unsubBlocked = callAudio.subscribeBlocked((blocked) => {
      setLiveBlocked(blocked);
      if (!blocked) {
        setUnlockStatus("unlocked");
      }
    });
    const unsubSuspended = callAudio.subscribeSuspendedAfterAttempt((suspended) => {
      setSuspendedAfterAttempt(suspended);
      if (suspended) {
        setUnlockStatus("suspended");
      }
    });
    const unsubUiState = callAudio.subscribeUiState((nextState) => {
      setAudioUiState(nextState);
      if (nextState === "Active") {
        setUnlockStatus("unlocked");
      } else if (nextState === "Activating") {
        setUnlockStatus("unlocking");
      }
    });
    return () => {
      unsubBlocked();
      unsubSuspended();
      unsubUiState();
    };
  }, []);

  const isAudioBlocked = audioUiState !== "Active" && (audioBlocked || liveBlocked);
  const remainsSuspendedAfterInteraction =
    isAudioBlocked && (suspendedAfterAttempt || unlockStatus === "suspended");
  const isConfirmedUnlocked = audioUiState === "Active" || !isAudioBlocked;

  const handleUnlockClick = async () => {
    setUnlockStatus("unlocking");
    const ok = await callAudio.unlock();
    if (ok) {
      setUnlockStatus("unlocked");
      onUnlockAudio?.();
    } else {
      setUnlockStatus("suspended");
    }
  };

  const handleRetryAudioClick = async () => {
    setUnlockStatus("unlocking");
    const ok = await callAudio.activateWithRetry({
      fromUserGesture: true,
      maxRetries: 3,
      recreateIfStuck: true
    });
    if (ok) {
      setUnlockStatus("unlocked");
      onUnlockAudio?.();
    } else {
      setUnlockStatus("suspended");
    }
  };

  const callerUser = useUser(
    session.hostId,
    callerProfile,
    session.hostName || "PulseCast Caller"
  );
  const callerName = callerUser.displayName;
  const callLabel =
    session.callType === "audio"
      ? "Incoming Audio Call"
      : session.callType === "group"
      ? "Incoming Group Call"
      : "Incoming Video Call";

  return (
    <div className="incoming-call-backdrop" role="dialog" aria-modal="true" aria-label={callLabel}>
      <div className="incoming-call-card">
        <div className="badge green" style={{ alignSelf: "center" }}>
          <BellRing size={14} />
          {callLabel}
        </div>

        <div className="incoming-avatar-ring">
          <div className="incoming-pulse" />
          <UserAvatar
            displayName={callerName}
            photoUrl={callerUser.photoUrl}
            avatarBase64={callerUser.avatarBase64}
            isOnline={callerUser.isOnline || true}
            isGroup={session.callType === "group"}
            size={96}
          />
        </div>

        <div>
          <h2 style={{ fontSize: "1.65rem", marginBottom: 6 }}>{callerName}</h2>
          <div style={{ color: "var(--accent-cyan)", fontSize: "0.92rem", fontWeight: 600 }}>
            Ringing… · Room {session.roomCode}
          </div>
        </div>

        <button
          type="button"
          className={`btn audio-unlock-btn ${
            isConfirmedUnlocked
              ? "confirmed"
              : remainsSuspendedAfterInteraction
              ? "suspended"
              : "pending"
          }`}
          disabled={unlockStatus === "unlocking"}
          onClick={() => void handleUnlockClick()}
          aria-live="polite"
        >
          {unlockStatus === "unlocking" || audioUiState === "Activating" ? (
            <>
              <RefreshCw size={16} className="spin-icon" />
              Activating Call Audio…
            </>
          ) : isConfirmedUnlocked ? (
            <>
              <CheckCircle2 size={16} />
              Audio Active · Unlocked
            </>
          ) : remainsSuspendedAfterInteraction ? (
            <>
              <AlertTriangle size={16} />
              Audio Still Suspended — Tap to Unlock
            </>
          ) : (
            <>
              <Volume2 size={16} />
              Unlock Ringtone &amp; Call Audio
            </>
          )}
        </button>

        {remainsSuspendedAfterInteraction && (
          <div
            style={{
              fontSize: "0.8rem",
              color: "#fde68a",
              lineHeight: 1.4
            }}
          >
            Browser autoplay kept audio suspended after interaction. Use Retry Audio below or check site sound permissions.
          </div>
        )}

        <div className="incoming-actions">
          <button
            type="button"
            className="call-circle-btn decline"
            onClick={onDecline}
            aria-label="Decline call"
          >
            <PhoneOff size={24} />
            <span>Decline</span>
          </button>

          {remainsSuspendedAfterInteraction && (
            <button
              type="button"
              className="call-circle-btn retry-audio"
              disabled={unlockStatus === "unlocking"}
              onClick={() => void handleRetryAudioClick()}
              aria-label="Retry Audio"
            >
              <RefreshCw size={24} className={unlockStatus === "unlocking" ? "spin-icon" : undefined} />
              <span>Retry Audio</span>
            </button>
          )}

          <button
            type="button"
            className="call-circle-btn answer"
            onClick={() => {
              void callAudio.unlock();
              onAnswer();
            }}
            aria-label="Answer call"
          >
            {session.callType === "video" ? (
              <Video size={24} />
            ) : session.callType === "group" ? (
              <Users size={24} />
            ) : (
              <Phone size={24} />
            )}
            <span>Answer</span>
          </button>
        </div>
      </div>
    </div>
  );
}
