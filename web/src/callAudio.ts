// Web Audio API synthesizer for PulseCast incoming ringtone & outgoing ringback
// Works across desktop & mobile browsers with explicit autoplay unlock tracking.

type BlockedListener = (blocked: boolean) => void;

class WebCallAudioController {
  private ctx: AudioContext | null = null;
  private incomingTimer: number | null = null;
  private outgoingTimer: number | null = null;
  private activeIncomingCallId: string | null = null;
  private activeRingbackCallId: string | null = null;
  private blocked = false;
  private listeners = new Set<BlockedListener>();

  subscribeBlocked(listener: BlockedListener): () => void {
    this.listeners.add(listener);
    listener(this.blocked);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private setBlocked(next: boolean) {
    if (this.blocked !== next) {
      this.blocked = next;
      this.listeners.forEach((fn) => fn(next));
    }
  }

  private ensureContext(): AudioContext | null {
    if (typeof window === "undefined") return null;
    const AudioCtx =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtx) return null;
    if (!this.ctx) {
      this.ctx = new AudioCtx();
    }
    return this.ctx;
  }

  async unlock(): Promise<boolean> {
    const ctx = this.ensureContext();
    if (!ctx) return false;
    if (ctx.state === "suspended") {
      try {
        await ctx.resume();
      } catch {
        this.setBlocked(true);
        return false;
      }
    }
    const ok = ctx.state === "running";
    this.setBlocked(!ok);
    return ok;
  }

  private playDualTone(freqA: number, freqB: number, durationMs: number, volume: number) {
    const ctx = this.ensureContext();
    if (!ctx) return;
    if (ctx.state === "suspended") {
      ctx.resume().catch(() => {
        this.setBlocked(true);
      });
    }
    if (ctx.state !== "running") {
      this.setBlocked(true);
      return;
    }
    this.setBlocked(false);

    const now = ctx.currentTime;
    const dur = durationMs / 1000;
    const oscA = ctx.createOscillator();
    const oscB = ctx.createOscillator();
    const gain = ctx.createGain();

    oscA.type = "sine";
    oscB.type = "sine";
    oscA.frequency.setValueAtTime(freqA, now);
    oscB.frequency.setValueAtTime(freqB, now);

    gain.gain.setValueAtTime(0.0001, now);
    gain.gain.exponentialRampToValueAtTime(volume, now + 0.025);
    gain.gain.setValueAtTime(volume, Math.max(now + 0.03, now + dur - 0.04));
    gain.gain.exponentialRampToValueAtTime(0.0001, now + dur);

    oscA.connect(gain);
    oscB.connect(gain);
    gain.connect(ctx.destination);

    oscA.start(now);
    oscB.start(now);
    oscA.stop(now + dur + 0.02);
    oscB.stop(now + dur + 0.02);
  }

  startIncomingRingtone(callId: string) {
    if (this.activeIncomingCallId === callId && this.incomingTimer !== null) return;
    this.stopIncomingRingtone();
    this.activeIncomingCallId = callId;

    const triggerRingCycle = () => {
      if (this.activeIncomingCallId !== callId) return;
      this.playDualTone(523.25, 659.25, 340, 0.14);
      window.setTimeout(() => {
        if (this.activeIncomingCallId === callId) {
          this.playDualTone(659.25, 783.99, 440, 0.14);
        }
      }, 420);
      if (typeof navigator !== "undefined" && typeof navigator.vibrate === "function") {
        try {
          navigator.vibrate([450, 250, 450]);
        } catch {
          // Ignore vibrate errors on unsupported browsers
        }
      }
    };

    this.unlock().finally(() => {
      if (this.activeIncomingCallId === callId) {
        triggerRingCycle();
      }
    });
    this.incomingTimer = window.setInterval(triggerRingCycle, 2100);
  }

  stopIncomingRingtone() {
    this.activeIncomingCallId = null;
    if (this.incomingTimer !== null) {
      window.clearInterval(this.incomingTimer);
      this.incomingTimer = null;
    }
    if (typeof navigator !== "undefined" && typeof navigator.vibrate === "function") {
      try {
        navigator.vibrate(0);
      } catch {
        // Ignore
      }
    }
  }

  startOutgoingRingback(callId: string) {
    if (this.activeRingbackCallId === callId && this.outgoingTimer !== null) return;
    this.stopOutgoingRingback();
    this.activeRingbackCallId = callId;

    const triggerRingback = () => {
      if (this.activeRingbackCallId !== callId) return;
      this.playDualTone(440, 480, 1050, 0.08);
    };

    this.unlock().finally(() => {
      if (this.activeRingbackCallId === callId) {
        triggerRingback();
      }
    });
    this.outgoingTimer = window.setInterval(triggerRingback, 3100);
  }

  stopOutgoingRingback() {
    this.activeRingbackCallId = null;
    if (this.outgoingTimer !== null) {
      window.clearInterval(this.outgoingTimer);
      this.outgoingTimer = null;
    }
  }
}

export const callAudio = new WebCallAudioController();
