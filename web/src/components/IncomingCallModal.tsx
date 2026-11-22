import { BellRing, Phone, PhoneOff, Users, Video, Volume2 } from "lucide-react";
import type { CallSession, CreatorProfile } from "../types";
import { UserAvatar } from "./UserAvatar";

interface IncomingCallModalProps {
  session: CallSession;
  callerProfile?: CreatorProfile | null;
  audioBlocked: boolean;
  onUnlockAudio: () => void;
  onAnswer: () => void;
  onDecline: () => void;
}

export function IncomingCallModal({
  session,
  callerProfile,
  audioBlocked,
  onUnlockAudio,
  onAnswer,
  onDecline
}: IncomingCallModalProps) {
  const callerName = callerProfile?.displayName || session.hostName || "PulseCast Caller";
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
            photoUrl={callerProfile?.photoUrl}
            avatarBase64={callerProfile?.avatarBase64}
            isOnline={true}
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

        {audioBlocked && (
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            style={{ alignSelf: "center" }}
            onClick={onUnlockAudio}
          >
            <Volume2 size={15} />
            Tap to Enable Ringtone Sound
          </button>
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

          <button
            type="button"
            className="call-circle-btn answer"
            onClick={onAnswer}
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
