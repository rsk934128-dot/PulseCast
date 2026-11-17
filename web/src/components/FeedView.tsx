import { useEffect, useState } from "react";
import {
  Film,
  Heart,
  MessageCircle,
  Play,
  Plus,
  Send,
  Trash2,
  Upload,
  X
} from "lucide-react";
import {
  addComment,
  deleteVideo,
  observeComments,
  observeIsVideoLiked,
  toggleVideoLike,
  uploadAndPublishVideo
} from "../repository";
import {
  extractWebVideoMetadata,
  formatDuration,
  formatFileSize,
  toThumbnailDataUrl
} from "../mediaUtils";
import type { PublishedVideo, VideoCategory, VideoComment } from "../types";

const CATEGORIES: ("All" | VideoCategory)[] = [
  "All",
  "Tech",
  "Studio",
  "Podcast",
  "Music",
  "Vlog",
  "Live"
];

interface FeedViewProps {
  uid: string;
  authorName: string;
  videos: PublishedVideo[];
  selectedVideo: PublishedVideo | null;
  onSelectVideo: (video: PublishedVideo | null) => void;
  showUploadModal: boolean;
  onOpenUploadModal: (open: boolean) => void;
  onNotify: (message: string) => void;
}

export function FeedView({
  uid,
  authorName,
  videos,
  selectedVideo,
  onSelectVideo,
  showUploadModal,
  onOpenUploadModal,
  onNotify
}: FeedViewProps) {
  const [selectedCategory, setSelectedCategory] = useState<"All" | VideoCategory>("All");

  const filteredVideos =
    selectedCategory === "All"
      ? videos
      : videos.filter((v) => v.category === selectedCategory);

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Creator Video Feed</h1>
          <p className="page-subtitle">
            Stream clips published from Android & Web, or upload a new studio recording.
          </p>
        </div>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => onOpenUploadModal(true)}
        >
          <Plus size={18} />
          Upload Video
        </button>
      </div>

      <div className="chip-row">
        {CATEGORIES.map((category) => (
          <button
            key={category}
            type="button"
            className={`chip ${selectedCategory === category ? "active" : ""}`}
            onClick={() => setSelectedCategory(category)}
          >
            {category}
          </button>
        ))}
      </div>

      {filteredVideos.length === 0 ? (
        <div className="empty-state" style={{ marginTop: 22 }}>
          <Film size={34} style={{ marginBottom: 10, color: "#22d3ee" }} />
          <h3 style={{ marginBottom: 6 }}>No public videos in {selectedCategory}</h3>
          <p style={{ margin: "0 0 16px" }}>
            Publish your first clip from your computer or phone to see it here immediately.
          </p>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => onOpenUploadModal(true)}
          >
            <Upload size={16} />
            Upload First Video
          </button>
        </div>
      ) : (
        <div className="video-grid">
          {filteredVideos.map((video) => {
            const thumb = toThumbnailDataUrl(video.thumbnailBase64);
            return (
              <article key={video.id} className="video-card">
                <div className="thumb-wrap" onClick={() => onSelectVideo(video)}>
                  {thumb ? (
                    <img src={thumb} alt={video.title} />
                  ) : (
                    <div className="thumb-fallback">
                      <Film size={30} />
                      <span style={{ fontSize: "0.78rem" }}>{video.category}</span>
                    </div>
                  )}
                  <div className="play-overlay">
                    <div className="play-circle">
                      <Play size={22} fill="currentColor" />
                    </div>
                  </div>
                  <span className="duration-pill">{formatDuration(video.durationSec)}</span>
                </div>

                <div className="video-body">
                  <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8 }}>
                    <span className="badge">{video.category}</span>
                    {video.visibility === "private" && <span className="badge amber">Draft</span>}
                  </div>
                  <div className="video-title" onClick={() => onSelectVideo(video)}>
                    {video.title}
                  </div>
                  <div className="video-meta">
                    <span>@{video.authorName}</span>
                    <span>
                      {video.likesCount} likes · {video.commentsCount} comments
                    </span>
                  </div>
                  <div className="video-actions">
                    <button
                      type="button"
                      className="btn btn-secondary btn-sm"
                      onClick={() => onSelectVideo(video)}
                    >
                      <Play size={14} />
                      Watch
                    </button>
                    {video.authorId === uid && (
                      <button
                        type="button"
                        className="btn btn-ghost btn-sm"
                        title="Delete video"
                        onClick={async () => {
                          if (!window.confirm(`Delete "${video.title}"?`)) return;
                          try {
                            await deleteVideo(video.id, video.videoUri);
                            onNotify("Video deleted.");
                          } catch (err) {
                            onNotify(err instanceof Error ? err.message : "Failed to delete video.");
                          }
                        }}
                      >
                        <Trash2 size={15} />
                      </button>
                    )}
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}

      {selectedVideo && (
        <VideoDetailModal
          uid={uid}
          authorName={authorName}
          video={selectedVideo}
          onClose={() => onSelectVideo(null)}
          onNotify={onNotify}
        />
      )}

      {showUploadModal && (
        <VideoUploadModal
          uid={uid}
          authorName={authorName}
          onClose={() => onOpenUploadModal(false)}
          onNotify={onNotify}
        />
      )}
    </div>
  );
}

function VideoDetailModal({
  uid,
  authorName,
  video,
  onClose,
  onNotify
}: {
  uid: string;
  authorName: string;
  video: PublishedVideo;
  onClose: () => void;
  onNotify: (msg: string) => void;
}) {
  const [liked, setLiked] = useState(false);
  const [comments, setComments] = useState<VideoComment[]>([]);
  const [commentText, setCommentText] = useState("");
  const [posting, setPosting] = useState(false);

  useEffect(() => {
    const unsubLike = observeIsVideoLiked(video.id, uid, setLiked);
    const unsubComments = observeComments(video.id, setComments);
    return () => {
      unsubLike();
      unsubComments();
    };
  }, [video.id, uid]);

  const handleToggleLike = async () => {
    try {
      await toggleVideoLike(video.id, uid);
    } catch (err) {
      onNotify(err instanceof Error ? err.message : "Unable to update like.");
    }
  };

  const handlePostComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!commentText.trim()) return;
    setPosting(true);
    try {
      await addComment(video.id, uid, authorName, commentText);
      setCommentText("");
    } catch (err) {
      onNotify(err instanceof Error ? err.message : "Failed to post comment.");
    } finally {
      setPosting(false);
    }
  };

  const thumbUrl = toThumbnailDataUrl(video.thumbnailBase64) || undefined;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div>
            <span className="badge">{video.category}</span>
            <h2 style={{ marginTop: 8 }}>{video.title}</h2>
            <div style={{ color: "var(--text-secondary)", fontSize: "0.85rem", marginTop: 4 }}>
              Published by {video.authorName} · {formatDuration(video.durationSec)}
            </div>
          </div>
          <button type="button" className="btn btn-ghost btn-sm" onClick={onClose} aria-label="Close">
            <X size={20} />
          </button>
        </div>

        {video.videoUri ? (
          <video
            className="player-video"
            src={video.videoUri}
            poster={thumbUrl}
            controls
            autoPlay
            playsInline
          />
        ) : (
          <div className="empty-state">
            <Film size={32} />
            <p>This video entry does not have a playable stream URL yet.</p>
          </div>
        )}

        {video.description && (
          <p style={{ color: "var(--text-secondary)", lineHeight: 1.55, marginTop: 14 }}>
            {video.description}
          </p>
        )}

        <div style={{ display: "flex", gap: 10, marginTop: 14 }}>
          <button
            type="button"
            className={`btn btn-sm ${liked ? "btn-primary" : "btn-secondary"}`}
            onClick={handleToggleLike}
          >
            <Heart size={15} fill={liked ? "currentColor" : "none"} />
            {liked ? "Liked" : "Like"} ({video.likesCount})
          </button>
          <span className="btn btn-secondary btn-sm" style={{ cursor: "default" }}>
            <MessageCircle size={15} />
            {comments.length || video.commentsCount} Comments
          </span>
        </div>

        <form onSubmit={handlePostComment} style={{ display: "flex", gap: 8, marginTop: 18 }}>
          <input
            className="input"
            placeholder="Write a comment..."
            value={commentText}
            onChange={(e) => setCommentText(e.target.value)}
            maxLength={500}
          />
          <button type="submit" className="btn btn-primary" disabled={posting || !commentText.trim()}>
            <Send size={16} />
          </button>
        </form>

        <div style={{ display: "flex", flexDirection: "column", gap: 10, marginTop: 16 }}>
          {comments.map((c) => (
            <div
              key={c.id}
              style={{
                background: "var(--bg-elevated)",
                border: "1px solid var(--border-subtle)",
                borderRadius: 14,
                padding: "10px 14px"
              }}
            >
              <div style={{ fontSize: "0.8rem", fontWeight: 700, color: "var(--accent-cyan)" }}>
                {c.authorName}
              </div>
              <div style={{ fontSize: "0.9rem", marginTop: 3 }}>{c.text}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function VideoUploadModal({
  uid,
  authorName,
  onClose,
  onNotify
}: {
  uid: string;
  authorName: string;
  onClose: () => void;
  onNotify: (msg: string) => void;
}) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState<VideoCategory>("Studio");
  const [visibility, setVisibility] = useState<"public" | "private">("public");
  const [videoFile, setVideoFile] = useState<File | null>(null);
  const [externalUrl, setExternalUrl] = useState("");
  const [durationSec, setDurationSec] = useState(15);
  const [thumbnailBase64, setThumbnailBase64] = useState<string | null>(null);
  const [uploadStage, setUploadStage] = useState<"idle" | "uploading" | "saving">("idle");
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setVideoFile(file);
    setError(null);
    if (!title.trim()) {
      setTitle(file.name.replace(/\.[^/.]+$/, "").slice(0, 80));
    }
    const meta = await extractWebVideoMetadata(file);
    setDurationSec(meta.durationSec);
    setThumbnailBase64(meta.thumbnailBase64);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      setError("Please enter a video title.");
      return;
    }
    if (!videoFile && !externalUrl.trim()) {
      setError("Select a video file or provide a direct video stream URL.");
      return;
    }
    setError(null);
    try {
      await uploadAndPublishVideo({
        uid,
        authorName,
        title,
        description,
        category,
        videoFile,
        externalVideoUrl: videoFile ? undefined : externalUrl,
        thumbnailBase64,
        visibility,
        durationSec,
        onProgress: (stage, pct) => {
          setUploadStage(stage);
          setProgress(pct);
        }
      });
      onNotify("Video published to PulseCast.");
      onClose();
    } catch (err) {
      setUploadStage("idle");
      setError(err instanceof Error ? err.message : "Upload failed. Check Firebase Storage rules.");
    }
  };

  const isBusy = uploadStage !== "idle";
  const thumbPreview = toThumbnailDataUrl(thumbnailBase64);

  return (
    <div className="modal-backdrop" onClick={() => !isBusy && onClose()}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div>
            <h2>Upload & Publish Video</h2>
            <p style={{ margin: "4px 0 0", color: "var(--text-secondary)", fontSize: "0.86rem" }}>
              Stores video files in Firebase Storage and metadata in Cloud Firestore.
            </p>
          </div>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            disabled={isBusy}
            onClick={onClose}
            aria-label="Close upload form"
          >
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <div>
            <label className="field-label">Video File (MP4 / WebM / MOV)</label>
            <input
              type="file"
              accept="video/*"
              className="input"
              disabled={isBusy}
              onChange={handleFileChange}
            />
            {videoFile && (
              <div style={{ marginTop: 8, fontSize: "0.82rem", color: "var(--accent-cyan)" }}>
                Selected: {videoFile.name} ({formatFileSize(videoFile.size)} · {formatDuration(durationSec)})
              </div>
            )}
            {thumbPreview && (
              <img
                src={thumbPreview}
                alt="Extracted thumbnail"
                style={{
                  marginTop: 10,
                  width: 180,
                  borderRadius: 12,
                  border: "1px solid var(--border-subtle)"
                }}
              />
            )}
          </div>

          {!videoFile && (
            <div>
              <label className="field-label">Or Direct Video Stream URL</label>
              <input
                className="input"
                placeholder="https://example.com/studio-clip.mp4"
                value={externalUrl}
                disabled={isBusy}
                onChange={(e) => setExternalUrl(e.target.value)}
              />
            </div>
          )}

          <div>
            <label className="field-label">Title</label>
            <input
              className="input"
              placeholder="Behind the scenes studio mix"
              value={title}
              disabled={isBusy}
              maxLength={120}
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>

          <div>
            <label className="field-label">Description</label>
            <textarea
              className="textarea"
              rows={3}
              placeholder="Tell viewers what this clip covers..."
              value={description}
              disabled={isBusy}
              maxLength={1000}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div>
              <label className="field-label">Category</label>
              <select
                className="select"
                value={category}
                disabled={isBusy}
                onChange={(e) => setCategory(e.target.value as VideoCategory)}
              >
                {CATEGORIES.filter((c) => c !== "All").map((cat) => (
                  <option key={cat} value={cat}>
                    {cat}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="field-label">Visibility</label>
              <select
                className="select"
                value={visibility}
                disabled={isBusy}
                onChange={(e) => setVisibility(e.target.value as "public" | "private")}
              >
                <option value="public">Public (Visible in Feed)</option>
                <option value="private">Private Draft</option>
              </select>
            </div>
          </div>

          {isBusy && (
            <div>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.84rem", marginBottom: 6 }}>
                <span>
                  {uploadStage === "uploading"
                    ? "Uploading video to Firebase Storage..."
                    : "Saving metadata to Firestore..."}
                </span>
                <span>{Math.round(progress * 100)}%</span>
              </div>
              <div className="progress-track">
                <div className="progress-fill" style={{ width: `${Math.round(progress * 100)}%` }} />
              </div>
            </div>
          )}

          {error && (
            <div style={{ color: "#fca5a5", fontSize: "0.86rem" }}>
              {error}
            </div>
          )}

          <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 6 }}>
            <button type="button" className="btn btn-secondary" disabled={isBusy} onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={isBusy}>
              <Upload size={16} />
              {isBusy ? "Publishing..." : "Publish Video"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
