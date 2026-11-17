import { useEffect, useState } from "react";
import { Edit3, Film, LogOut, Play, Plus, Save, Trash2 } from "lucide-react";
import { updateCreatorProfile, updateUserPreferences, deleteVideo } from "../repository";
import { formatDuration, toThumbnailDataUrl } from "../mediaUtils";
import type { CreatorProfile, PublishedVideo, UserAccount } from "../types";

interface ProfileViewProps {
  uid: string;
  email: string;
  photoUrl?: string | null;
  fallbackDisplayName: string;
  account: UserAccount | null;
  creator: CreatorProfile | null;
  myVideos: PublishedVideo[];
  onWatchVideo: (video: PublishedVideo) => void;
  onOpenUpload: () => void;
  onSignOut: () => void;
  onNotify: (msg: string) => void;
}

export function ProfileView({
  uid,
  email,
  photoUrl,
  fallbackDisplayName,
  account,
  creator,
  myVideos,
  onWatchVideo,
  onOpenUpload,
  onSignOut,
  onNotify
}: ProfileViewProps) {
  const [editing, setEditing] = useState(false);
  const [displayName, setDisplayName] = useState("");
  const [handle, setHandle] = useState("");
  const [bio, setBio] = useState("");
  const [status, setStatus] = useState("online");
  const [saving, setSaving] = useState(false);
  const [filter, setFilter] = useState<"all" | "public" | "private">("all");

  const resolvedName = creator?.displayName || account?.displayName || fallbackDisplayName || "Creator";
  const resolvedHandle = creator?.handle || "creator";
  const resolvedBio = creator?.bio || "Audio, video & live studio creator on PulseCast";
  const resolvedStatus = creator?.status || "online";

  useEffect(() => {
    setDisplayName(resolvedName);
    setHandle(resolvedHandle);
    setBio(resolvedBio);
    setStatus(resolvedStatus);
  }, [resolvedName, resolvedHandle, resolvedBio, resolvedStatus]);

  const totalLikes = myVideos.reduce((acc, v) => acc + (v.likesCount || 0), 0);
  const totalComments = myVideos.reduce((acc, v) => acc + (v.commentsCount || 0), 0);

  const visibleVideos = myVideos.filter((v) => {
    if (filter === "public") return v.visibility === "public";
    if (filter === "private") return v.visibility !== "public";
    return true;
  });

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      await updateCreatorProfile(uid, displayName, handle, bio, status);
      setEditing(false);
      onNotify("Creator profile saved.");
    } catch (err) {
      onNotify(err instanceof Error ? err.message : "Failed to save profile.");
    } finally {
      setSaving(false);
    }
  };

  const togglePref = async (
    key: "pushCallAlerts" | "pushMessageAlerts" | "hdVideoDefault",
    value: boolean
  ) => {
    const next = {
      pushCallAlerts: account?.pushCallAlerts ?? true,
      pushMessageAlerts: account?.pushMessageAlerts ?? true,
      hdVideoDefault: account?.hdVideoDefault ?? true,
      [key]: value
    };
    try {
      await updateUserPreferences(
        uid,
        next.pushCallAlerts,
        next.pushMessageAlerts,
        next.hdVideoDefault
      );
    } catch (err) {
      onNotify(err instanceof Error ? err.message : "Failed to update preference.");
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Creator Studio & Profile</h1>
          <p className="page-subtitle">
            Manage your public creator identity, preferences, and uploaded video catalog.
          </p>
        </div>
        <div style={{ display: "flex", gap: 10 }}>
          <button type="button" className="btn btn-primary" onClick={onOpenUpload}>
            <Plus size={17} />
            Upload Video
          </button>
          <button type="button" className="btn btn-secondary" onClick={onSignOut}>
            <LogOut size={16} />
            Sign Out
          </button>
        </div>
      </div>

      <section className="card">
        <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "space-between", gap: 16 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
            <div className="avatar lg">
              {photoUrl ? (
                <img src={photoUrl} alt={resolvedName} />
              ) : (
                resolvedName.charAt(0).toUpperCase()
              )}
            </div>
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
                <h2>{resolvedName}</h2>
                <span className="badge green">{resolvedStatus}</span>
              </div>
              <div style={{ color: "var(--accent-cyan)", fontWeight: 600, marginTop: 3 }}>
                @{resolvedHandle}
              </div>
              <div style={{ color: "var(--text-muted)", fontSize: "0.84rem", marginTop: 2 }}>
                {account?.email || email}
              </div>
            </div>
          </div>

          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={() => setEditing((v) => !v)}
          >
            <Edit3 size={15} />
            {editing ? "Close Editor" : "Edit Profile"}
          </button>
        </div>

        <p style={{ color: "var(--text-secondary)", marginTop: 14, marginBottom: 0 }}>
          {resolvedBio}
        </p>

        <div className="stats-grid">
          <div className="stat-card">
            <div className="stat-value">{myVideos.length}</div>
            <div className="stat-label">Videos</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">{totalLikes}</div>
            <div className="stat-label">Likes</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">{totalComments}</div>
            <div className="stat-label">Comments</div>
          </div>
        </div>

        {editing && (
          <form
            onSubmit={handleSaveProfile}
            style={{
              borderTop: "1px solid var(--border-subtle)",
              paddingTop: 18,
              display: "flex",
              flexDirection: "column",
              gap: 12
            }}
          >
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <div>
                <label className="field-label">Display Name</label>
                <input
                  className="input"
                  value={displayName}
                  maxLength={60}
                  onChange={(e) => setDisplayName(e.target.value)}
                />
              </div>
              <div>
                <label className="field-label">Handle</label>
                <input
                  className="input"
                  value={handle}
                  maxLength={32}
                  onChange={(e) => setHandle(e.target.value)}
                />
              </div>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 12 }}>
              <div>
                <label className="field-label">Bio</label>
                <input
                  className="input"
                  value={bio}
                  maxLength={240}
                  onChange={(e) => setBio(e.target.value)}
                />
              </div>
              <div>
                <label className="field-label">Studio Status</label>
                <select className="select" value={status} onChange={(e) => setStatus(e.target.value)}>
                  <option value="online">online</option>
                  <option value="studio">studio</option>
                  <option value="in_call">in_call</option>
                  <option value="away">away</option>
                </select>
              </div>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end" }}>
              <button type="submit" className="btn btn-primary" disabled={saving}>
                <Save size={16} />
                {saving ? "Saving..." : "Save Profile"}
              </button>
            </div>
          </form>
        )}
      </section>

      <section className="card" style={{ marginTop: 18 }}>
        <h3 style={{ marginBottom: 12 }}>Notification & Playback Preferences</h3>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 18 }}>
          <label style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer" }}>
            <input
              type="checkbox"
              checked={account?.pushCallAlerts ?? true}
              onChange={(e) => togglePref("pushCallAlerts", e.target.checked)}
            />
            Incoming call alerts
          </label>
          <label style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer" }}>
            <input
              type="checkbox"
              checked={account?.pushMessageAlerts ?? true}
              onChange={(e) => togglePref("pushMessageAlerts", e.target.checked)}
            />
            Message alerts
          </label>
          <label style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer" }}>
            <input
              type="checkbox"
              checked={account?.hdVideoDefault ?? true}
              onChange={(e) => togglePref("hdVideoDefault", e.target.checked)}
            />
            HD video streaming default
          </label>
        </div>
      </section>

      <section style={{ marginTop: 24 }}>
        <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", justifyContent: "space-between", gap: 12 }}>
          <h2>My Uploaded Videos ({myVideos.length})</h2>
          <div className="chip-row">
            <button
              type="button"
              className={`chip ${filter === "all" ? "active" : ""}`}
              onClick={() => setFilter("all")}
            >
              All
            </button>
            <button
              type="button"
              className={`chip ${filter === "public" ? "active" : ""}`}
              onClick={() => setFilter("public")}
            >
              Public
            </button>
            <button
              type="button"
              className={`chip ${filter === "private" ? "active" : ""}`}
              onClick={() => setFilter("private")}
            >
              Private Drafts
            </button>
          </div>
        </div>

        {visibleVideos.length === 0 ? (
          <div className="empty-state" style={{ marginTop: 16 }}>
            <Film size={32} style={{ color: "var(--accent-cyan)", marginBottom: 8 }} />
            <h3>No uploaded videos in this filter</h3>
            <p>Upload a video from your computer or phone to build your studio library.</p>
          </div>
        ) : (
          <div className="video-grid">
            {visibleVideos.map((video) => {
              const thumb = toThumbnailDataUrl(video.thumbnailBase64);
              return (
                <article key={video.id} className="video-card">
                  <div className="thumb-wrap" onClick={() => onWatchVideo(video)}>
                    {thumb ? (
                      <img src={thumb} alt={video.title} />
                    ) : (
                      <div className="thumb-fallback">
                        <Film size={28} />
                      </div>
                    )}
                    <span className="duration-pill">{formatDuration(video.durationSec)}</span>
                  </div>
                  <div className="video-body">
                    <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
                      <span className="badge">{video.category}</span>
                      <span className={`badge ${video.visibility === "public" ? "green" : "amber"}`}>
                        {video.visibility === "public" ? "Public" : "Draft"}
                      </span>
                    </div>
                    <div className="video-title" onClick={() => onWatchVideo(video)}>
                      {video.title}
                    </div>
                    <div className="video-meta">
                      <span>{video.likesCount} likes</span>
                      <span>{video.commentsCount} comments</span>
                    </div>
                    <div className="video-actions">
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        onClick={() => onWatchVideo(video)}
                      >
                        <Play size={14} />
                        Play
                      </button>
                      <button
                        type="button"
                        className="btn btn-danger btn-sm"
                        onClick={async () => {
                          if (!window.confirm(`Delete "${video.title}" permanently?`)) return;
                          try {
                            await deleteVideo(video.id, video.videoUri);
                            onNotify("Video deleted.");
                          } catch (err) {
                            onNotify(err instanceof Error ? err.message : "Failed to delete video.");
                          }
                        }}
                      >
                        <Trash2 size={14} />
                        Delete
                      </button>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}
