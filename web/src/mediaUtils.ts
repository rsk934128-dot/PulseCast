export interface ExtractedWebVideoMeta {
  durationSec: number;
  thumbnailBase64: string | null;
}

export function formatDuration(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds || 0));
  const mins = Math.floor(safe / 60);
  const secs = safe % 60;
  return `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
}

export function formatFileSize(bytes?: number | null): string {
  if (!bytes || bytes <= 0) return "";
  const kb = bytes / 1024;
  if (kb < 1024) return `${Math.max(1, Math.round(kb))} KB`;
  return `${(kb / 1024).toFixed(1)} MB`;
}

export function toThumbnailDataUrl(base64?: string | null): string | null {
  if (!base64 || !base64.trim()) return null;
  if (base64.startsWith("data:")) return base64;
  return `data:image/jpeg;base64,${base64}`;
}

export async function extractWebVideoMetadata(file: File): Promise<ExtractedWebVideoMeta> {
  return new Promise((resolve) => {
    const video = document.createElement("video");
    const objectUrl = URL.createObjectURL(file);
    let settled = false;

    const finish = (result: ExtractedWebVideoMeta) => {
      if (settled) return;
      settled = true;
      URL.revokeObjectURL(objectUrl);
      video.remove();
      resolve(result);
    };

    const timeout = window.setTimeout(() => {
      finish({ durationSec: 15, thumbnailBase64: null });
    }, 6000);

    video.preload = "metadata";
    video.muted = true;
    video.playsInline = true;

    video.onloadedmetadata = () => {
      const duration = Number.isFinite(video.duration) && video.duration > 0
        ? Math.max(1, Math.round(video.duration))
        : 15;
      video.currentTime = Math.min(1, duration / 2);

      video.onseeked = () => {
        window.clearTimeout(timeout);
        try {
          const maxDim = 520;
          const vw = video.videoWidth || 640;
          const vh = video.videoHeight || 360;
          const scale = Math.min(maxDim / vw, maxDim / vh, 1);
          const canvas = document.createElement("canvas");
          canvas.width = Math.max(1, Math.round(vw * scale));
          canvas.height = Math.max(1, Math.round(vh * scale));
          const ctx = canvas.getContext("2d");
          if (ctx) {
            ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
            const dataUrl = canvas.toDataURL("image/jpeg", 0.7);
            const base64 = dataUrl.split(",")[1] || null;
            finish({
              durationSec: duration,
              thumbnailBase64: base64 && base64.length <= 185000 ? base64 : null
            });
            return;
          }
        } catch {
          // Ignore canvas taint/decode failures
        }
        finish({ durationSec: duration, thumbnailBase64: null });
      };
    };

    video.onerror = () => {
      window.clearTimeout(timeout);
      finish({ durationSec: 15, thumbnailBase64: null });
    };

    video.src = objectUrl;
  });
}

export async function encodeImageFileToBase64(file: File): Promise<string | null> {
  return new Promise((resolve) => {
    const img = new Image();
    const objectUrl = URL.createObjectURL(file);
    img.onload = () => {
      URL.revokeObjectURL(objectUrl);
      try {
        const maxDim = 560;
        const scale = Math.min(maxDim / img.width, maxDim / img.height, 1);
        const canvas = document.createElement("canvas");
        canvas.width = Math.max(1, Math.round(img.width * scale));
        canvas.height = Math.max(1, Math.round(img.height * scale));
        const ctx = canvas.getContext("2d");
        if (!ctx) {
          resolve(null);
          return;
        }
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        const dataUrl = canvas.toDataURL("image/jpeg", 0.72);
        const base64 = dataUrl.split(",")[1] || null;
        resolve(base64 && base64.length <= 185000 ? base64 : null);
      } catch {
        resolve(null);
      }
    };
    img.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      resolve(null);
    };
    img.src = objectUrl;
  });
}
