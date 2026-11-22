import { useEffect, useState } from "react";
import { Users } from "lucide-react";
import { toThumbnailDataUrl } from "../mediaUtils";

interface UserAvatarProps {
  displayName: string;
  photoUrl?: string | null;
  avatarBase64?: string | null;
  isOnline?: boolean;
  showStatus?: boolean;
  isGroup?: boolean;
  size?: number;
  onClick?: () => void;
  title?: string;
}

export function UserAvatar({
  displayName,
  photoUrl,
  avatarBase64,
  isOnline = false,
  showStatus = true,
  isGroup = false,
  size = 40,
  onClick,
  title
}: UserAvatarProps) {
  const safeName = (displayName || "").trim() || "Creator";
  const customDataUrl = !isGroup ? toThumbnailDataUrl(avatarBase64) : null;
  const cleanPhotoUrl = !isGroup && photoUrl?.trim() ? photoUrl.trim() : null;
  const [photoFailed, setPhotoFailed] = useState(false);

  useEffect(() => {
    setPhotoFailed(false);
  }, [cleanPhotoUrl, customDataUrl]);

  const resolvedSrc = customDataUrl || (!photoFailed ? cleanPhotoUrl : null);
  const dotSize = Math.max(9, Math.min(16, Math.round(size * 0.28)));
  const fontSize = Math.max(12, Math.round(size * 0.38));

  const wrapperStyle: React.CSSProperties = {
    position: "relative",
    width: size,
    height: size,
    flexShrink: 0,
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    cursor: onClick ? "pointer" : "default"
  };

  const innerStyle: React.CSSProperties = {
    width: "100%",
    height: "100%",
    borderRadius: "999px",
    overflow: "hidden",
    background: "linear-gradient(135deg, var(--accent-cyan), var(--accent-violet))",
    color: "#07101b",
    fontWeight: 700,
    fontSize,
    display: "grid",
    placeItems: "center",
    border: "1px solid rgba(255,255,255,0.14)"
  };

  const content = (
    <>
      <div style={innerStyle}>
        {isGroup ? (
          <Users size={Math.max(14, Math.round(size * 0.45))} />
        ) : resolvedSrc ? (
          <img
            src={resolvedSrc}
            alt={safeName}
            referrerPolicy="no-referrer"
            onError={() => setPhotoFailed(true)}
            style={{ width: "100%", height: "100%", objectFit: "cover", display: "block" }}
          />
        ) : (
          safeName.charAt(0).toUpperCase()
        )}
      </div>
      {showStatus && (
        <span
          title={isOnline ? `${safeName} is online` : `${safeName} is offline`}
          style={{
            position: "absolute",
            right: 0,
            bottom: 0,
            width: dotSize,
            height: dotSize,
            borderRadius: "999px",
            background: isOnline ? "var(--accent-green)" : "#64748b",
            border: "2px solid var(--bg-surface)"
          }}
        />
      )}
    </>
  );

  if (onClick) {
    return (
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          onClick();
        }}
        title={title || `View ${safeName}'s profile`}
        style={{
          ...wrapperStyle,
          background: "transparent",
          border: "none",
          padding: 0
        }}
      >
        {content}
      </button>
    );
  }

  return (
    <div style={wrapperStyle} title={title}>
      {content}
    </div>
  );
}
