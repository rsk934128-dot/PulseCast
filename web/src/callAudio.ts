// Web Audio API synthesizer for PulseCast incoming ringtone & outgoing ringback
// Maintains a persistent AudioContext that resumes on user interaction and provides
// incoming call audio diagnostics for autoplay, constraints, and permission denials.

export type AudioUiState = "Active" | "Suspended" | "Activating" | "Unavailable";

type BlockedListener = (blocked: boolean) => void;
type SuspendedAfterAttemptListener = (suspendedAfterAttempt: boolean) => void;
type UiStateListener = (state: AudioUiState) => void;

export interface AudioActivationOptions {
  fromUserGesture?: boolean;
  maxRetries?: number;
  recreateIfStuck?: boolean;
}

export interface AudioDiagnosticReport {
  callId: string;
  timestamp: string;
  secureContext: boolean;
  audioContextSupported: boolean;
  audioContextState: AudioContextState | "unavailable";
  isRunning: boolean;
  isSuspended: boolean;
  autoplayBlocked: boolean;
  suspendedAfterAttempt: boolean;
  microphonePermission: PermissionState | "unsupported" | "unknown";
  audioInputDeviceCount: number;
  audioOutputDeviceCount: number;
  supportedConstraints: {
    echoCancellation: boolean;
    noiseSuppression: boolean;
    autoGainControl: boolean;
  };
  warnings: string[];
}

export function logAudioIssue(
  stage: string,
  error: unknown,
  requestedConstraints?: MediaTrackConstraints | boolean
): void {
  const errObj =
    typeof error === "object" && error !== null
      ? (error as { name?: string; message?: string; constraint?: string })
      : null;
  const errorName = errObj?.name || (error instanceof Error ? error.name : "AudioError");
  const errorMessage =
    errObj?.message || (error instanceof Error ? error.message : String(error ?? "Unknown error"));
  const constraint = errObj?.constraint;

  const isPermissionDenied =
    errorName === "NotAllowedError" ||
    errorName === "PermissionDeniedError" ||
    errorName === "SecurityError";
  const isConstraintError =
    errorName === "OverconstrainedError" || errorName === "ConstraintNotSatisfiedError";

  console.warn(`[PulseCast Audio][${stage}]`, {
    category: isPermissionDenied
      ? "permission_or_autoplay_denied"
      : isConstraintError
      ? "audio_constraint_error"
      : "audio_runtime_error",
    errorName,
    errorMessage,
    ...(constraint ? { failedConstraint: constraint } : {}),
    ...(requestedConstraints !== undefined ? { requestedConstraints } : {})
  });
}

const GLOBAL_AUDIO_CTX_KEY = "__pulsecastPersistentAudioContext";
const RETRY_DELAYS_MS = [120, 280, 550];
const RESUME_TIMEOUT_MS = 450;
const STATE_POLL_INTERVAL_MS = 500;
const MAX_POLL_CYCLES = 60;

function getAudioContextConstructor(): typeof AudioContext | null {
  if (typeof window === "undefined") return null;
  return (
    window.AudioContext ||
    (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext ||
    null
  );
}

function getGlobalPersistentContext(): AudioContext | null {
  if (typeof window === "undefined") return null;
  const host = window as unknown as Record<string, AudioContext | undefined>;
  const existing = host[GLOBAL_AUDIO_CTX_KEY];
  if (existing && existing.state !== "closed") {
    return existing;
  }
  const AudioCtx = getAudioContextConstructor();
  if (!AudioCtx) return null;
  const created = new AudioCtx();
  host[GLOBAL_AUDIO_CTX_KEY] = created;
  return created;
}

function replaceGlobalPersistentContext(previous: AudioContext | null): AudioContext | null {
  if (typeof window === "undefined") return null;
  const host = window as unknown as Record<string, AudioContext | undefined>;
  if (previous && previous.state !== "closed") {
    previous.onstatechange = null;
    previous.close().catch(() => {});
  }
  delete host[GLOBAL_AUDIO_CTX_KEY];
  const AudioCtx = getAudioContextConstructor();
  if (!AudioCtx) return null;
  const created = new AudioCtx();
  host[GLOBAL_AUDIO_CTX_KEY] = created;
  return created;
}

function waitMs(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

class WebCallAudioController {
  private ctx: AudioContext | null = null;
  private primedContext: AudioContext | null = null;
  private boundContext: AudioContext | null = null;
  private incomingTimer: number | null = null;
  private outgoingTimer: number | null = null;
  private activeIncomingCallId: string | null = null;
  private activeRingbackCallId: string | null = null;
  private blocked = false;
  private suspendedAfterAttempt = false;
  private uiState: AudioUiState = "Suspended";
  private listeners = new Set<BlockedListener>();
  private suspendedAfterAttemptListeners = new Set<SuspendedAfterAttemptListener>();
  private uiStateListeners = new Set<UiStateListener>();
  private gestureListenersInstalled = false;
  private mediaElements = new Set<HTMLMediaElement>();
  private inFlightActivation: Promise<boolean> | null = null;
  private activationGeneration = 0;
  private hasInitialInteraction = false;
  private pollTimer: number | null = null;
  private pollAttemptInProgress = false;
  private pollCount = 0;

  constructor() {
    if (typeof window !== "undefined") {
      this.ensureContext();
      this.installGestureUnlock();
    }
  }

  subscribeBlocked(listener: BlockedListener): () => void {
    this.listeners.add(listener);
    listener(this.blocked);
    return () => {
      this.listeners.delete(listener);
    };
  }

  subscribeSuspendedAfterAttempt(listener: SuspendedAfterAttemptListener): () => void {
    this.suspendedAfterAttemptListeners.add(listener);
    listener(this.suspendedAfterAttempt);
    return () => {
      this.suspendedAfterAttemptListeners.delete(listener);
    };
  }

  subscribeUiState(listener: UiStateListener): () => void {
    this.uiStateListeners.add(listener);
    listener(this.uiState);
    return () => {
      this.uiStateListeners.delete(listener);
    };
  }

  isBlocked(): boolean {
    return this.blocked;
  }

  isSuspendedAfterAttempt(): boolean {
    return this.suspendedAfterAttempt;
  }

  getUiState(): AudioUiState {
    return this.uiState;
  }

  getContextState(): AudioContextState | "unavailable" {
    const ctx = this.ensureContext();
    return ctx ? ctx.state : "unavailable";
  }

  getAudioContext(): AudioContext | null {
    return this.ensureContext();
  }

  registerMediaElement(el: HTMLMediaElement | null): () => void {
    if (!el) return () => {};
    this.mediaElements.add(el);
    return () => {
      this.mediaElements.delete(el);
    };
  }

  private setBlocked(next: boolean) {
    if (this.blocked !== next) {
      this.blocked = next;
      this.listeners.forEach((fn) => fn(next));
    }
  }

  private setSuspendedAfterAttempt(next: boolean) {
    if (this.suspendedAfterAttempt !== next) {
      this.suspendedAfterAttempt = next;
      this.suspendedAfterAttemptListeners.forEach((fn) => fn(next));
    }
  }

  private setUiState(next: AudioUiState) {
    if (this.uiState !== next) {
      this.uiState = next;
      this.uiStateListeners.forEach((fn) => fn(next));
    }
  }

  private syncContextState(ctx: AudioContext | null) {
    if (!ctx || ctx.state === "closed") {
      this.setBlocked(true);
      this.setUiState("Unavailable");
      return;
    }
    const running = ctx.state === "running";
    this.setBlocked(!running);
    if (running) {
      this.setSuspendedAfterAttempt(false);
      this.setUiState("Active");
      this.stopSuspendedStatePolling();
    } else {
      this.setUiState(this.inFlightActivation ? "Activating" : "Suspended");
      if (this.hasInitialInteraction && ctx.state === "suspended") {
        this.startSuspendedStatePolling();
      }
    }
  }

  private ensureContext(): AudioContext | null {
    if (typeof window === "undefined") return null;
    if (!this.ctx || this.ctx.state === "closed") {
      this.ctx = getGlobalPersistentContext();
      if (this.ctx !== this.primedContext) {
        this.primedContext = null;
      }
    }
    const ctx = this.ctx;
    if (ctx && this.boundContext !== ctx) {
      this.boundContext = ctx;
      ctx.onstatechange = () => {
        if (this.ctx === ctx) {
          this.syncContextState(ctx);
          if (ctx.state === "running") {
            this.primeAudioOutput(ctx);
          }
        }
      };
      this.syncContextState(ctx);
    } else if (!ctx) {
      this.setUiState("Unavailable");
    }
    return ctx;
  }

  /**
   * Plays a 1-sample silent buffer once per persistent AudioContext so desktop
   * & mobile browsers initialize the hardware audio output pipeline immediately.
   */
  private primeAudioOutput(ctx: AudioContext) {
    if (this.primedContext === ctx || ctx.state !== "running") return;
    try {
      const buffer = ctx.createBuffer(1, 1, 22050);
      const source = ctx.createBufferSource();
      const gain = ctx.createGain();
      gain.gain.setValueAtTime(0.0001, ctx.currentTime);
      source.buffer = buffer;
      source.connect(gain);
      gain.connect(ctx.destination);
      source.start(0);
      this.primedContext = ctx;
    } catch (err) {
      logAudioIssue("primeAudioOutput", err);
    }
  }

  private triggerRegisteredMediaElements() {
    this.mediaElements.forEach((el) => {
      if (el.srcObject && el.paused) {
        el.play().catch((err) => {
          logAudioIssue("mediaElement.play", err);
        });
      }
    });
  }

  private async resumeWithTimeout(
    ctx: AudioContext,
    timeoutMs: number = RESUME_TIMEOUT_MS
  ): Promise<boolean> {
    if (ctx.state === "running") {
      this.primeAudioOutput(ctx);
      this.syncContextState(ctx);
      return true;
    }
    if (ctx.state === "closed") {
      return false;
    }

    let timeoutId: number | null = null;
    try {
      const resumePromise = ctx.resume();
      const timeoutPromise = new Promise<"timeout">((resolve) => {
        timeoutId = window.setTimeout(() => resolve("timeout"), timeoutMs);
      });
      const outcome = await Promise.race([
        resumePromise.then(() => "settled" as const),
        timeoutPromise
      ]);
      if (outcome === "timeout") {
        console.warn(
          `[PulseCast Audio] AudioContext.resume() timed out after ${timeoutMs}ms (state='${ctx.state}').`
        );
      }
    } catch (err) {
      logAudioIssue("AudioContext.resume", err);
    } finally {
      if (timeoutId !== null) {
        window.clearTimeout(timeoutId);
      }
    }

    const currentState = ctx.state as AudioContextState;
    const ok = currentState === "running";
    if (ok) {
      this.primeAudioOutput(ctx);
      this.syncContextState(ctx);
    } else {
      this.setBlocked(true);
    }
    return ok;
  }

  /**
   * Verifies whether the persistent AudioContext is 'running' vs 'suspended' when an
   * incoming call starts and logs any browser-level audio constraints or permission denials.
   */
  async diagnoseIncomingCallAudio(callId: string): Promise<AudioDiagnosticReport> {
    const ctx = this.ensureContext();
    const audioContextState: AudioContextState | "unavailable" = ctx ? ctx.state : "unavailable";
    const isRunning = audioContextState === "running";
    const isSuspended = audioContextState === "suspended";
    const secureContext = typeof window !== "undefined" ? window.isSecureContext : false;

    const warnings: string[] = [];
    if (!ctx) {
      warnings.push("Web Audio API AudioContext is unavailable in this browser.");
    } else if (isSuspended) {
      warnings.push(
        "AudioContext state is 'suspended' (blocked by browser autoplay policy until user gesture)."
      );
    } else if (audioContextState === "closed") {
      warnings.push("AudioContext state is 'closed'.");
    }

    if (!secureContext) {
      warnings.push(
        "Page is not in a secure context (HTTPS); microphone and audio constraints may be blocked."
      );
    }

    let microphonePermission: PermissionState | "unsupported" | "unknown" = "unknown";
    if (typeof navigator !== "undefined" && navigator.permissions?.query) {
      try {
        const status = await navigator.permissions.query({
          name: "microphone" as PermissionName
        });
        microphonePermission = status.state;
        if (status.state === "denied") {
          warnings.push("Browser microphone permission is explicitly 'denied'.");
        }
      } catch (err) {
        microphonePermission = "unsupported";
        logAudioIssue("permissions.query(microphone)", err);
      }
    } else {
      microphonePermission = "unsupported";
    }

    const rawSupported =
      typeof navigator !== "undefined"
        ? navigator.mediaDevices?.getSupportedConstraints?.()
        : undefined;
    const supportedConstraints = {
      echoCancellation: Boolean(rawSupported?.echoCancellation),
      noiseSuppression: Boolean(rawSupported?.noiseSuppression),
      autoGainControl: Boolean(rawSupported?.autoGainControl)
    };

    if (typeof navigator !== "undefined" && !navigator.mediaDevices?.getUserMedia) {
      warnings.push("navigator.mediaDevices.getUserMedia is unavailable in this browser context.");
    } else {
      if (!supportedConstraints.echoCancellation) {
        warnings.push("Audio constraint 'echoCancellation' is not supported by this browser.");
      }
      if (!supportedConstraints.noiseSuppression) {
        warnings.push("Audio constraint 'noiseSuppression' is not supported by this browser.");
      }
      if (!supportedConstraints.autoGainControl) {
        warnings.push("Audio constraint 'autoGainControl' is not supported by this browser.");
      }
    }

    let audioInputDeviceCount = 0;
    let audioOutputDeviceCount = 0;
    if (typeof navigator !== "undefined" && navigator.mediaDevices?.enumerateDevices) {
      try {
        const devices = await navigator.mediaDevices.enumerateDevices();
        audioInputDeviceCount = devices.filter((d) => d.kind === "audioinput").length;
        audioOutputDeviceCount = devices.filter((d) => d.kind === "audiooutput").length;
        if (audioInputDeviceCount === 0) {
          warnings.push("No audio input (microphone) devices detected.");
        }
      } catch (err) {
        logAudioIssue("mediaDevices.enumerateDevices", err);
      }
    }

    const report: AudioDiagnosticReport = {
      callId,
      timestamp: new Date().toISOString(),
      secureContext,
      audioContextSupported: Boolean(ctx),
      audioContextState,
      isRunning,
      isSuspended,
      autoplayBlocked: !isRunning,
      suspendedAfterAttempt: this.suspendedAfterAttempt,
      microphonePermission,
      audioInputDeviceCount,
      audioOutputDeviceCount,
      supportedConstraints,
      warnings
    };

    if (warnings.length > 0 || !isRunning) {
      console.warn(`[PulseCast Audio Diagnostic] Incoming call ${callId} audio check:`, report);
    } else {
      console.info(`[PulseCast Audio Diagnostic] Incoming call ${callId} audio ready:`, report);
    }

    return report;
  }

  /**
   * Starts a 500ms polling loop after the initial user interaction to monitor audioContext.state.
   * While the context remains 'suspended', it automatically calls audioContext.resume(), and once
   * it transitions to 'running', it updates the UI state to 'Active' and stops polling.
   */
  startSuspendedStatePolling(): void {
    if (typeof window === "undefined") return;
    this.pollCount = 0;
    if (this.pollTimer !== null) return;

    this.pollTimer = window.setInterval(() => {
      void this.pollAudioContextState();
    }, STATE_POLL_INTERVAL_MS);
  }

  stopSuspendedStatePolling(): void {
    if (this.pollTimer !== null) {
      window.clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
    this.pollCount = 0;
  }

  private async pollAudioContextState(): Promise<void> {
    if (this.pollAttemptInProgress) return;
    this.pollAttemptInProgress = true;
    try {
      const ctx = this.ensureContext();
      if (!ctx) {
        this.setUiState("Unavailable");
        this.stopSuspendedStatePolling();
        return;
      }

      if (ctx.state === "running") {
        this.primeAudioOutput(ctx);
        this.syncContextState(ctx);
        this.triggerRegisteredMediaElements();
        return;
      }

      if (ctx.state === "suspended") {
        this.pollCount += 1;
        const resumed = await this.resumeWithTimeout(ctx);
        const postState = ctx.state as AudioContextState;
        if (resumed || postState === "running") {
          this.primeAudioOutput(ctx);
          this.syncContextState(ctx);
          this.triggerRegisteredMediaElements();
          if (this.activeIncomingCallId) {
            this.playIncomingRingBurst(this.activeIncomingCallId);
          } else if (this.activeRingbackCallId) {
            this.playDualTone(440, 480, 1050, 0.08);
          }
          return;
        }

        if (this.pollCount >= MAX_POLL_CYCLES) {
          console.warn(
            `[PulseCast Audio] Stopped 500ms AudioContext polling after ${MAX_POLL_CYCLES} cycles; state remains '${postState}'.`
          );
          this.stopSuspendedStatePolling();
          this.setUiState("Suspended");
        }
      } else {
        this.syncContextState(ctx);
        this.stopSuspendedStatePolling();
      }
    } finally {
      this.pollAttemptInProgress = false;
    }
  }

  /**
   * Attempts to activate the persistent AudioContext with bounded retries and timeout protection.
   * Tracks if the context remains 'suspended' after a user interaction attempt so the UI can
   * present an explicit activation button and starts 500ms polling if needed.
   */
  activateWithRetry(options: AudioActivationOptions = {}): Promise<boolean> {
    const {
      fromUserGesture = true,
      maxRetries = 3,
      recreateIfStuck = false
    } = options;

    if (fromUserGesture) {
      this.hasInitialInteraction = true;
    }

    this.triggerRegisteredMediaElements();

    const initialCtx = this.ensureContext();
    if (!initialCtx) {
      this.setBlocked(true);
      this.setUiState("Unavailable");
      return Promise.resolve(false);
    }

    if (initialCtx.state === "running") {
      this.primeAudioOutput(initialCtx);
      this.syncContextState(initialCtx);
      return Promise.resolve(true);
    }

    if (fromUserGesture) {
      this.startSuspendedStatePolling();
    }

    if (this.inFlightActivation && !recreateIfStuck) {
      return this.inFlightActivation;
    }

    this.setUiState("Activating");
    const generation = ++this.activationGeneration;
    const run = (async (): Promise<boolean> => {
      let currentCtx: AudioContext | null = initialCtx;

      for (let attempt = 0; attempt <= maxRetries; attempt++) {
        if (generation !== this.activationGeneration) {
          return this.ctx?.state === "running";
        }

        currentCtx = this.ensureContext();
        if (!currentCtx) {
          this.setBlocked(true);
          this.setUiState("Unavailable");
          return false;
        }

        const ok = await this.resumeWithTimeout(currentCtx);
        if (ok) {
          this.syncContextState(currentCtx);
          return true;
        }

        if (recreateIfStuck && attempt === 0 && fromUserGesture) {
          console.warn(
            "[PulseCast Audio] Persistent AudioContext remained suspended on explicit tap; recreating AudioContext."
          );
          this.ctx = replaceGlobalPersistentContext(currentCtx);
          this.primedContext = null;
          this.boundContext = null;
          const freshCtx = this.ensureContext();
          if (freshCtx && (await this.resumeWithTimeout(freshCtx))) {
            this.syncContextState(freshCtx);
            return true;
          }
        }

        if (attempt < maxRetries) {
          const delay = RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length - 1)];
          await waitMs(delay);
        }
      }

      const finalState = this.ctx?.state ?? "unavailable";
      console.warn(
        `[PulseCast Audio] AudioContext remained '${finalState}' after ${maxRetries + 1} activation attempts.`
      );
      this.setBlocked(true);
      this.setUiState(finalState === "unavailable" ? "Unavailable" : "Suspended");
      if (fromUserGesture && finalState === "suspended") {
        this.setSuspendedAfterAttempt(true);
        this.startSuspendedStatePolling();
      }
      return false;
    })();

    const wrapped = run.finally(() => {
      if (this.inFlightActivation === wrapped) {
        this.inFlightActivation = null;
      }
      if (this.ctx?.state === "running") {
        this.syncContextState(this.ctx);
      }
    });
    this.inFlightActivation = wrapped;
    return wrapped;
  }

  /**
   * Must be invoked synchronously inside a user interaction handler where possible
   * so strict browsers (Safari, iOS WebKit, Chrome) preserve the activation token.
   */
  activateFromGesture(): Promise<boolean> {
    return this.activateWithRetry({ fromUserGesture: true, maxRetries: 3 });
  }

  installGestureUnlock(): () => void {
    if (typeof window === "undefined" || this.gestureListenersInstalled) {
      return () => {};
    }
    this.gestureListenersInstalled = true;

    const handleGesture = () => {
      this.hasInitialInteraction = true;
      const wasBlocked = this.blocked || !this.ctx || this.ctx.state !== "running";
      if (this.ctx?.state === "suspended") {
        this.startSuspendedStatePolling();
      }
      void this.activateWithRetry({ fromUserGesture: true, maxRetries: 2 }).then((ok) => {
        if (!ok || !wasBlocked) return;
        if (this.activeIncomingCallId) {
          this.playIncomingRingBurst(this.activeIncomingCallId);
        } else if (this.activeRingbackCallId) {
          this.playDualTone(440, 480, 1050, 0.08);
        }
      });
    };

    const handleVisibility = () => {
      if (document.visibilityState === "visible" && this.ctx?.state === "suspended") {
        if (this.hasInitialInteraction) {
          this.startSuspendedStatePolling();
        }
        void this.activateWithRetry({ fromUserGesture: false, maxRetries: 2 });
      }
    };

    const events: Array<keyof WindowEventMap> = [
      "pointerdown",
      "touchstart",
      "touchend",
      "click",
      "keydown"
    ];
    events.forEach((evt) =>
      window.addEventListener(evt, handleGesture, { capture: true, passive: true })
    );
    document.addEventListener("visibilitychange", handleVisibility);

    return () => {
      events.forEach((evt) =>
        window.removeEventListener(evt, handleGesture, { capture: true })
      );
      document.removeEventListener("visibilitychange", handleVisibility);
      this.stopSuspendedStatePolling();
      this.gestureListenersInstalled = false;
    };
  }

  unlock(): Promise<boolean> {
    return this.activateFromGesture();
  }

  private scheduleDualTone(
    ctx: AudioContext,
    freqA: number,
    freqB: number,
    durationMs: number,
    volume: number
  ) {
    this.primeAudioOutput(ctx);
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

  private playDualTone(freqA: number, freqB: number, durationMs: number, volume: number) {
    const ctx = this.ensureContext();
    if (!ctx) return;

    if (ctx.state === "running") {
      this.syncContextState(ctx);
      this.scheduleDualTone(ctx, freqA, freqB, durationMs, volume);
      return;
    }

    void this.resumeWithTimeout(ctx).then((ok) => {
      if (ok && this.ctx === ctx && ctx.state === "running") {
        this.scheduleDualTone(ctx, freqA, freqB, durationMs, volume);
      }
    });
  }

  private playIncomingRingBurst(callId: string) {
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
  }

  startIncomingRingtone(callId: string) {
    if (this.activeIncomingCallId === callId && this.incomingTimer !== null) return;
    this.stopIncomingRingtone();
    this.activeIncomingCallId = callId;

    void this.diagnoseIncomingCallAudio(callId);

    const triggerRingCycle = () => {
      this.playIncomingRingBurst(callId);
    };

    void this.activateWithRetry({ fromUserGesture: false, maxRetries: 2 }).finally(() => {
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

    void this.activateWithRetry({ fromUserGesture: false, maxRetries: 2 }).finally(() => {
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

export function diagnoseIncomingCallAudio(callId: string): Promise<AudioDiagnosticReport> {
  return callAudio.diagnoseIncomingCallAudio(callId);
}
