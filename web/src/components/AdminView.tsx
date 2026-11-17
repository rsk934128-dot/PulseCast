import { useState } from "react";
import {
  Eye,
  EyeOff,
  Film,
  PhoneOff,
  Search,
  ShieldCheck,
  Trash2,
  UserPlus,
  Users,
  X
} from "lucide-react";
import {
  adminDeleteCallSession,
  adminGrantRole,
  adminRevokeRole,
  adminUpdateCreatorModeration,
  adminUpdateVideoVisibility,
  deleteVideo,
  endCallSession
} from "../repository";
import { formatDuration, toThumbnailDataUrl } from "../mediaUtils";
import type {
  AdminRecord,
  CallSession,
  CreatorProfile,
  PublishedVideo,
  UserAccount
} from "../types";

type AdminSection = "videos" | "creators" | "calls" | "access";

interface AdminViewProps {
  currentUserId: string;
  currentUserEmail: string;
  videos: PublishedVideo[];
  creators: CreatorProfile[];
  calls: CallSession[];
  users: UserAccount[];
  admins: AdminRecord[];
  onNotify: (msg: string) => void;
}

export function AdminView({
  currentUserId,
  currentUserEmail,
  videos,
  creators,
  calls,
  users,
  admins,
  onNotify
}: AdminViewProps) {
  const [section, setSection] = useState<AdminSection>("videos");
  const [search, setSearch] = useState("");
  const [showGrantModal, setShowGrantModal] = useState(false);
  const [grantUid, setGrantUid] = useState("");
  const [grantName, setGrantName] = useState("");
  const [grantEmail, setGrantEmail] = useState("");
  const [grantRole, setGrantRole] = useState<"moderator" | "super_admin">("moderator");
  const [granting, setGranting] = useState(false);

  const q = search.trim().toLowerCase();
  const activeCallCount = calls.filter((c) => c.status !== "ended").length;
  const adminUidSet = new Set(admins.map((a) => a.userId));

  const filteredVideos = videos.filter((v) =>
    !q ||
    v.title.toLowerCase().includes(q) ||
    v.authorName.toLowerCase().includes(q) ||
    v.category.toLowerCase().includes(q)
  );
  const filteredCreators = creators.filter((c) =>
    !q ||
    c.displayName.toLowerCase().includes(q) ||
    c.handle.toLowerCase().includes(q) ||
    c.userId.toLowerCase().includes(q)
  );
  const filteredCalls = calls.filter((c) =>
    !q ||
    c.roomCode.toLowerCase().includes(q) ||
    c.hostName.toLowerCase().includes(q) ||
    c.status.toLowerCase().includes(q)
  );
  const filteredUsers = users.filter((u) =>
    !q ||
    u.displayName.toLowerCase().includes(q) ||
    u.email.toLowerCase().includes(q) ||
    u.userId.toLowerCase().includes(q)
  );

  const openGrantModal = (prefill?: UserAccount) => {
    setGrantUid(prefill?.userId || "");
    setGrantName(prefill?.displayName || "");
    setGrantEmail(prefill?.email || "");
    setGrantRole("moderator");
    setShowGrantModal(true);
  };

  const handleGrantSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!grantUid.trim() || !grantName.trim()) return;
    setGranting(true);
    try {
      await adminGrantRole({
        targetUid: grantUid,
        displayName: grantName,
        email: grantEmail,
        role: grantRole,
        grantedByUid: currentUserId
      });
      onNotify(`Granted ${grantRole} role to ${grantName}.`);
      setShowGrantModal(false);
    } catch (err) {
      onNotify(err instanceof Error ? err.message : "Failed to grant admin role.");
    } finally {
      setGranting(false);
    }
  };

  return (
    <div>
      <section className="call-stage" style={{ marginBottom: 22 }}>
        <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "space-between", gap: 14 }}>
          <div>
            <span className="badge green">
              <ShieldCheck size={14} />
              RBAC Admin Console Active
            </span>
            <h1 className="page-title" style={{ marginTop: 10 }}>
              PulseCast Admin Panel
            </h1>
            <p className="page-subtitle" style={{ marginBottom: 0 }}>
              Signed in as {currentUserEmail || "Administrator"} · Real-time moderation &amp; role governance
            </p>
          </div>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => openGrantModal()}
          >
            <UserPlus size={17} />
            Add Admin
          </button>
        </div>

        <div className="stats-grid" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", marginBottom: 0 }}>
          <div className="stat-card">
            <div className="stat-value">{videos.length}</div>
            <div className="stat-label">Total Videos</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">{creators.length}</div>
            <div className="stat-label">Creators</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">{activeCallCount}</div>
            <div className="stat-label">Active Calls</div>
          </div>
          <div className="stat-card">
            <div className="stat-value">{Math.max(1, admins.length)}</div>
            <div className="stat-label">Admins</div>
          </div>
        </div>
      </section>

      <div style={{ display: "flex", flexWrap: "wrap", gap: 12, alignItems: "center", justifyContent: "space-between", marginBottom: 18 }}>
        <div className="chip-row">
          <button
            type="button"
            className={`chip ${section === "videos" ? "active" : ""}`}
            onClick={() => setSection("videos")}
          >
            Videos ({videos.length})
          </button>
          <button
            type="button"
            className={`chip ${section === "creators" ? "active" : ""}`}
            onClick={() => setSection("creators")}
          >
            Creators ({creators.length})
          </button>
          <button
            type="button"
            className={`chip ${section === "calls" ? "active" : ""}`}
            onClick={() => setSection("calls")}
          >
            Call Rooms ({calls.length})
          </button>
          <button
            type="button"
            className={`chip ${section === "access" ? "active" : ""}`}
            onClick={() => setSection("access")}
          >
            Users &amp; Admins ({users.length})
          </button>
        </div>

        <div style={{ position: "relative", minWidth: 260, flex: "0 1 340px" }}>
          <Search
            size={16}
            style={{
              position: "absolute",
              left: 12,
              top: "50%",
              transform: "translateY(-50%)",
              color: "var(--text-muted)"
            }}
          />
          <input
            className="input"
            style={{ paddingLeft: 36 }}
            placeholder="Search videos, creators, rooms, users..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      {section === "videos" && (
        filteredVideos.length === 0 ? (
          <div className="empty-state">
            <Film size={32} style={{ color: "var(--accent-cyan)", marginBottom: 8 }} />
            <h3>No videos found</h3>
          </div>
        ) : (
          <div className="video-grid">
            {filteredVideos.map((video) => {
              const thumb = toThumbnailDataUrl(video.thumbnailBase64);
              const isPrivate = video.visibility === "private";
              return (
                <article key={video.id} className="video-card">
                  <div className="thumb-wrap" style={{ cursor: "default" }}>
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
                      <span className={`badge ${isPrivate ? "amber" : "green"}`}>
                        {isPrivate ? "Private" : "Public"}
                      </span>
                    </div>
                    <div className="video-title" style={{ cursor: "default" }}>{video.title}</div>
                    <div className="video-meta">
                      <span>By {video.authorName}</span>
                      <span>{video.likesCount} likes</span>
                    </div>
                    <div className="video-actions">
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        onClick={async () => {
                          try {
                            const next = isPrivate ? "public" : "private";
                            await adminUpdateVideoVisibility(video.id, next);
                            onNotify(`Moved "${video.title}" to ${next}.`);
                          } catch (err) {
                            onNotify(err instanceof Error ? err.message : "Failed to update visibility.");
                          }
                        }}
                      >
                        {isPrivate ? <Eye size={14} /> : <EyeOff size={14} />}
                        {isPrivate ? "Publish" : "Unpublish"}
                      </button>
                      <button
                        type="button"
                        className="btn btn-danger btn-sm"
                        onClick={async () => {
                          if (!window.confirm(`Delete "${video.title}" permanently as Admin?`)) return;
                          try {
                            await deleteVideo(video.id, video.videoUri);
                            onNotify("Video removed by admin.");
                          } catch (err) {
                            onNotify(err instanceof Error ? err.message : "Admin delete failed.");
                          }
                        }}
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )
      )}

      {section === "creators" && (
        <div className="calls-grid">
          {filteredCreators.map((creator) => (
            <div key={creator.userId} className="card">
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10 }}>
                <div>
                  <h3 style={{ fontSize: "1.05rem" }}>{creator.displayName}</h3>
                  <div style={{ color: "var(--accent-cyan)", fontSize: "0.85rem" }}>
                    @{creator.handle}
                  </div>
                </div>
                <span className={`badge ${creator.isDiscoverable ? "green" : "red"}`}>
                  {creator.isDiscoverable ? "Visible" : "Hidden"}
                </span>
              </div>
              <p style={{ color: "var(--text-secondary)", fontSize: "0.86rem", margin: "10px 0 14px" }}>
                {creator.bio || "No bio"}
              </p>
              <div style={{ display: "flex", gap: 10, alignItems: "center", justifyContent: "space-between" }}>
                <select
                  className="select"
                  style={{ width: "auto", minWidth: 120 }}
                  value={creator.status || "online"}
                  onChange={async (e) => {
                    try {
                      await adminUpdateCreatorModeration(
                        creator.userId,
                        creator.isDiscoverable,
                        e.target.value
                      );
                      onNotify("Creator status updated.");
                    } catch (err) {
                      onNotify(err instanceof Error ? err.message : "Update failed.");
                    }
                  }}
                >
                  <option value="online">online</option>
                  <option value="studio">studio</option>
                  <option value="in_call">in_call</option>
                  <option value="away">away</option>
                </select>
                <button
                  type="button"
                  className={`btn btn-sm ${creator.isDiscoverable ? "btn-secondary" : "btn-primary"}`}
                  onClick={async () => {
                    try {
                      await adminUpdateCreatorModeration(
                        creator.userId,
                        !creator.isDiscoverable,
                        creator.status || "online"
                      );
                      onNotify(
                        !creator.isDiscoverable ? "Creator restored to directory." : "Creator hidden from directory."
                      );
                    } catch (err) {
                      onNotify(err instanceof Error ? err.message : "Moderation update failed.");
                    }
                  }}
                >
                  {creator.isDiscoverable ? "Hide Creator" : "Show Creator"}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {section === "calls" && (
        <div className="card">
          {filteredCalls.length === 0 ? (
            <div style={{ color: "var(--text-secondary)" }}>No call sessions found.</div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {filteredCalls.map((s) => {
                const isEnded = s.status === "ended";
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
                      background: "var(--bg-elevated)",
                      border: "1px solid var(--border-subtle)"
                    }}
                  >
                    <div>
                      <div style={{ fontWeight: 700 }}>
                        {s.roomCode} · {s.callType.toUpperCase()}
                      </div>
                      <div style={{ fontSize: "0.82rem", color: "var(--text-secondary)", marginTop: 2 }}>
                        Host: {s.hostName} · {s.participantIds.length} participant(s) · {formatDuration(s.durationSec)}
                      </div>
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                      <span className={`badge ${isEnded ? "amber" : "green"}`}>{s.status}</span>
                      {!isEnded ? (
                        <button
                          type="button"
                          className="btn btn-danger btn-sm"
                          onClick={async () => {
                            try {
                              await endCallSession(s.id, s.durationSec);
                              onNotify(`Ended call ${s.roomCode}.`);
                            } catch (err) {
                              onNotify(err instanceof Error ? err.message : "Failed to end room.");
                            }
                          }}
                        >
                          <PhoneOff size={14} />
                          End Room
                        </button>
                      ) : (
                        <button
                          type="button"
                          className="btn btn-ghost btn-sm"
                          onClick={async () => {
                            try {
                              await adminDeleteCallSession(s.id);
                              onNotify("Call log deleted.");
                            } catch (err) {
                              onNotify(err instanceof Error ? err.message : "Failed to delete call log.");
                            }
                          }}
                        >
                          <Trash2 size={14} />
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {section === "access" && (
        <div className="calls-grid">
          <section className="card">
            <h3 style={{ marginBottom: 14 }}>Assigned Admins &amp; Moderators ({admins.length})</h3>
            {admins.length === 0 ? (
              <div style={{ color: "var(--text-secondary)", fontSize: "0.9rem" }}>
                Bootstrapped super-admin ({currentUserEmail}) is active. Promote users on the right or click Add Admin.
              </div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                {admins.map((a) => (
                  <div
                    key={a.userId}
                    style={{
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "space-between",
                      gap: 10,
                      padding: "10px 12px",
                      borderRadius: 12,
                      background: "var(--bg-elevated)"
                    }}
                  >
                    <div>
                      <div style={{ fontWeight: 600 }}>{a.displayName}</div>
                      <div style={{ fontSize: "0.78rem", color: "var(--text-secondary)" }}>
                        {a.email || a.userId} · {a.role}
                      </div>
                    </div>
                    {a.userId !== currentUserId ? (
                      <button
                        type="button"
                        className="btn btn-danger btn-sm"
                        onClick={async () => {
                          try {
                            await adminRevokeRole(a.userId);
                            onNotify(`Revoked admin role for ${a.displayName}.`);
                          } catch (err) {
                            onNotify(err instanceof Error ? err.message : "Failed to revoke role.");
                          }
                        }}
                      >
                        Revoke
                      </button>
                    ) : (
                      <span className="badge">You</span>
                    )}
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="card">
            <h3 style={{ marginBottom: 14 }}>
              <Users size={18} style={{ verticalAlign: "middle", marginRight: 6 }} />
              Registered Users ({filteredUsers.length})
            </h3>
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {filteredUsers.map((u) => {
                const alreadyAdmin = adminUidSet.has(u.userId);
                return (
                  <div
                    key={u.userId}
                    style={{
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "space-between",
                      gap: 10,
                      padding: "10px 12px",
                      borderRadius: 12,
                      background: "var(--bg-elevated)"
                    }}
                  >
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontWeight: 600 }}>{u.displayName}</div>
                      <div style={{ fontSize: "0.78rem", color: "var(--text-secondary)", overflow: "hidden", textOverflow: "ellipsis" }}>
                        {u.email}
                      </div>
                    </div>
                    {alreadyAdmin ? (
                      <span className="badge green">Admin</span>
                    ) : (
                      <button
                        type="button"
                        className="btn btn-secondary btn-sm"
                        onClick={() => openGrantModal(u)}
                      >
                        <UserPlus size={14} />
                        Make Mod
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          </section>
        </div>
      )}

      {showGrantModal && (
        <div className="modal-backdrop" onClick={() => !granting && setShowGrantModal(false)}>
          <div className="modal-panel" style={{ maxWidth: 460 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Grant Admin Access</h3>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => setShowGrantModal(false)}
              >
                <X size={18} />
              </button>
            </div>
            <form onSubmit={handleGrantSubmit} style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              <div>
                <label className="field-label">User UID</label>
                <input
                  className="input"
                  value={grantUid}
                  onChange={(e) => setGrantUid(e.target.value)}
                  placeholder="Firebase Auth UID"
                />
              </div>
              <div>
                <label className="field-label">Display Name</label>
                <input
                  className="input"
                  value={grantName}
                  onChange={(e) => setGrantName(e.target.value)}
                  placeholder="Moderator Name"
                />
              </div>
              <div>
                <label className="field-label">Email</label>
                <input
                  className="input"
                  value={grantEmail}
                  onChange={(e) => setGrantEmail(e.target.value)}
                  placeholder="mod@example.com"
                />
              </div>
              <div>
                <label className="field-label">Role</label>
                <select
                  className="select"
                  value={grantRole}
                  onChange={(e) => setGrantRole(e.target.value as "moderator" | "super_admin")}
                >
                  <option value="moderator">Moderator</option>
                  <option value="super_admin">Super Admin</option>
                </select>
              </div>
              <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 6 }}>
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={granting}
                  onClick={() => setShowGrantModal(false)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={granting || !grantUid.trim() || !grantName.trim()}
                >
                  {granting ? "Granting..." : "Grant Role"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
