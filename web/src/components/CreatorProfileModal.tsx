import { useState } from "react";
import { Edit3, Film, MessageSquare, Phone, Play, Video, X } from "lucide-react";
import { resolveIsUserOnline } from "../repository";
import { formatDuration, toThumbnailDataUrl } from "../mediaUtils";
import type { CreatorProfile, PublishedVideo } from "../types";
import { UserAvatar } from "./UserAvatar";

interface CreatorProfileModalProps {
  creator: CreatorProfile;
  isCurrentUser: boolean;
  videos: PublishedVideo[];
  onClose: () => void;
  onMessage: (creator: CreatorProfile) => void;
  onAudioCall: (creator: CreatorProfile) => void;
  onVideoCall: (creator: CreatorProfile) => void;
  onWatchVideo: (video: PublishedVideo) => void;
  onEditOwnProfile: () => void;
}

export function CreatorProfileModal({
  creator,
  isCurrentUser,
  videos,
  onClose,
  onMessage,
  onAudioCall,
  onVideoCall,
  onWatchVideo,
  onEditOwnProfile
}: CreatorProfileModalProps) {
  const [showPhotoLightbox, setShowPhotoLightbox] = useState(false);
  const isOnline = resolveIsUserOnline(
    creator.isOnline,
    creator.lastActiveAt,
    creator.updatedAt
  );
  const avatarSrc =
    toThumbnailDataUrl(creator.avatarBase64) ||
    (creator.photoUrl?.trim() ? creator.photoUrl.trim() : null);

  return (
    <>
      <div className="modal-backdrop" onClick={onClose}>
        <div className="modal-panel" style={{ maxWidth: 560 }} onClick={(e) => e.stopPropagation()}>
          <div className="modal-header">
            <div style={{ display: "flex", alignItems: "center", gap: 14, minWidth: 0 }}>
              <UserAvatar
                displayName={creator.displayName}
                photoUrl={creator.photoUrl}
                avatarBase64={creator.avatarBase64}
                isOnline={isOnline}
                size={68}
                onClick={avatarSrc ? () => setShowPhotoLightbox(true) : undefined}
                title={avatarSrc ? "Tap to zoom profile photo" : undefined}
              />
              <div style={{ minWidth: 0 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                  <h2 style={{ fontSize: "1.3rem" }}>{creator.displayName}</h2>
                  <span className={`badge ${isOnline ? "green" : "amber"}`}>
                    {isOnline ? "Online" : creator.status || "Offline"}
                  </span>
                </div>
                <div style={{ color: "var(--accent-cyan)", fontSize: "0.9rem", fontWeight: 600 }}>
                  @{creator.handle || "creator"}
                </div>
                {avatarSrc && (
                  <div style={{ fontSize: "0.75rem", color: "var(--text-muted)", marginTop: 2 }}>
                    Tap photo to enlarge
                  </div>
                )}
              </div>
            </div>
            <button type="button" className="btn btn-ghost btn-sm" onClick={onClose} aria-label="Close profile">
              <X size={20} />
            </button>
          </div>

          <p style={{ color: "var(--text-secondary)", lineHeight: 1.5, margin: "0 0 16px" }}>
            {creator.bio || "Audio, video & live studio creator on PulseCast."}
          </p>

          <div style={{ display: "flex", flexWrap: "wrap", gap: 10, marginBottom: 20 }}>
            {isCurrentUser ? (
              <button type="button" className="btn btn-primary btn-sm" onClick={onEditOwnProfile}>
                <Edit3 size={15} />
                Edit My Profile & Photo
              </button>
            ) : (
              <>
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={() => onMessage(creator)}
                >
                  <MessageSquare size={15} />
                  Message
                </button>
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={() => onAudioCall(creator)}
                >
                  <Phone size={15} />
                  Audio Call
                </button>
                <button
                  type="button"
                  className="btn btn-primary btn-sm"
                  onClick={() => onVideoCall(creator)}
                >
                  <Video size={15} />
                  Video Call
                </button>
              </>
            )}
          </div>

          <h3 style={{ fontSize: "1rem", marginBottom: 10 }}>
            Public Videos ({videos.length})
          </h3>
          {videos.length === 0 ? (
            <div className="empty-state" style={{ padding: "24px 14px" }}>
              <Film size={24} style={{ color: "var(--text-muted)", marginBottom: 6 }} />
              <div>No public videos shared yet.</div>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {videos.map((v) => {
                const thumb = toThumbnailDataUrl(v.thumbnailBase64);
                return (
                  <button
                    key={v.id}
                    type="button"
                    className="chat-row"
                    style={{
                      background: "var(--bg-elevated)",
                      border: "1px solid var(--border-subtle)"
                    }}
                    onClick={() => onWatchVideo(v)}
                  >
                    <div
                      style={{
                        width: 72,
                        height: 44,
                        borderRadius: 10,
                        overflow: "hidden",
                        background: "#0b101d",
                        flexShrink: 0,
                        display: "grid",
                        placeItems: "center"
                      }}
                    >
                      {thumb ? (
                        <img
                          src={thumb}
                          alt={v.title}
                          style={{ width: "100%", height: "100%", objectFit: "cover" }}
                        />
                      ) : (
                        <Play size={16} />
                      )}
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div
                        style={{
                          fontWeight: 600,
                          whiteSpace: "nowrap",
                          overflow: "hidden",
                          textOverflow: "ellipsis"
                        }}
                      >
                        {v.title}
                      </div>
                      <div style={{ fontSize: "0.78rem", color: "var(--text-secondary)", marginTop: 2 }}>
                        {v.category} · {formatDuration(v.durationSec)} · {v.likesCount} likes
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {showPhotoLightbox && avatarSrc && (
        <div
          className="modal-backdrop"
          style={{ zIndex: 90 }}
          onClick={() => setShowPhotoLightbox(false)}
        >
          <div
            style={{ position: "relative", maxWidth: "90vw", maxHeight: "88vh" }}
            onClick={(e) => e.stopPropagation()}
          >
            <img
              src={avatarSrc}
              alt={creator.displayName}
              referrerPolicy="no-referrer"
              style={{
                maxWidth: "88vw",
                maxHeight: "82vh",
                borderRadius: 20,
                objectFit: "contain",
                display: "block",
                boxShadow: "var(--shadow-lg)"
              }}
            />
            <button
              type="button"
              className="btn btn-secondary btn-sm"
              style={{ position: "absolute", top: 10, right: 10 }}
              onClick={() => setShowPhotoLightbox(false)}
            >
              <X size={16} />
            </button>
          </div>
        </div>
      )}
    </>
  );
}
