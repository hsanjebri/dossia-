import { Injectable, NgZone, inject, signal } from '@angular/core';

interface SpeechRecognitionAlternativeLike {
  transcript: string;
  confidence: number;
}

interface SpeechRecognitionResultLike {
  isFinal: boolean;
  length: number;
  [index: number]: SpeechRecognitionAlternativeLike;
}

interface SpeechRecognitionEventLike extends Event {
  resultIndex: number;
  results: {
    length: number;
    [index: number]: SpeechRecognitionResultLike;
  };
}

interface SpeechRecognitionErrorEventLike extends Event {
  error: string;
}

interface SpeechRecognitionLike extends EventTarget {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  start(): void;
  stop(): void;
  abort(): void;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null;
  onend: (() => void) | null;
}

/** Civic keywords — prefer STT alternatives that contain these (Derja / FR / EN). */
const CIVIC_HINTS = [
  'باسبور',
  'پاسپور',
  'باسپور',
  'جواز',
  'بطاقة',
  'تعريف',
  'معادلة',
  'إقامة',
  'اقامة',
  'تجديد',
  'نبدل',
  'تونس',
  'أوراق',
  'passeport',
  'passport',
  'cin',
  'carte',
  'diplome',
  'équivalence',
  'equivalence',
  'permis',
  'residence',
];

/**
 * Browser voice I/O. Greeting MUST call speak() inside the same user-click stack
 * (no setTimeout before the first speak), or Chrome stays silent.
 */
@Injectable({ providedIn: 'root' })
export class VoiceService {
  private readonly zone = inject(NgZone);

  readonly isListening = signal(false);
  readonly isSpeaking = signal(false);
  readonly voiceMode = signal(false);
  readonly lastError = signal('');
  readonly speechSupported = this.detectSpeechSupport();

  private recognition: SpeechRecognitionLike | null = null;
  private onTranscript: ((text: string) => void) | null = null;
  private onInterim: ((text: string) => void) | null = null;
  private speakEndCallback: (() => void) | null = null;
  private speakToken = 0;
  private collected = '';
  private expectCommit = false;
  private silenceTimer: ReturnType<typeof setTimeout> | null = null;
  private hardTimer: ReturnType<typeof setTimeout> | null = null;
  private speakWatchdog: ReturnType<typeof setTimeout> | null = null;
  private resumeTimer: ReturnType<typeof setInterval> | null = null;
  private audioCtx: AudioContext | null = null;

  toggleVoiceMode(): void {
    this.voiceMode.update((v) => !v);
    if (!this.voiceMode()) this.stopSpeaking();
  }

  /** Call from click/tap BEFORE speaking. */
  unlockAudio(): void {
    try {
      window.speechSynthesis?.getVoices();
      if (!this.audioCtx) {
        const AC =
          window.AudioContext ||
          (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
        if (AC) this.audioCtx = new AC();
      }
      void this.audioCtx?.resume();
      // Tiny beep keeps the audio pipeline alive on some Chrome builds.
      if (this.audioCtx?.state === 'running') {
        const osc = this.audioCtx.createOscillator();
        const gain = this.audioCtx.createGain();
        gain.gain.value = 0.0001;
        osc.connect(gain);
        gain.connect(this.audioCtx.destination);
        osc.start();
        osc.stop(this.audioCtx.currentTime + 0.01);
      }
    } catch {
      /* ignore */
    }
  }

  /**
   * Speak text. Pass immediate=true from a click handler (greetings).
   * That path never delays, so Chrome allows audio.
   */
  speak(text: string, lang = 'fr-FR', onEnd?: () => void, immediate = false): void {
    console.log('[Dosya TTS] speak()', {
      immediate,
      lang,
      len: text?.length ?? 0,
      preview: (text ?? '').slice(0, 80),
    });
    if (!('speechSynthesis' in window)) {
      this.lastError.set('Browser cannot speak. Use Chrome.');
      onEnd?.();
      return;
    }

    const clean = this.stripForSpeech(text);
    if (!clean) {
      onEnd?.();
      return;
    }

    this.stopListening();
    this.clearSpeakTimers();

    const token = ++this.speakToken;
    this.speakEndCallback = onEnd ?? null;

    const startUtterance = () => {
      if (token !== this.speakToken) return;

      const spoken = clean.length > 900 ? `${clean.slice(0, 900).trim()}…` : clean;
      const ttsLang = this.normalizeSpeakLang(lang);
      const utterance = new SpeechSynthesisUtterance(spoken);
      utterance.lang = ttsLang;
      utterance.rate = 1;
      utterance.volume = 1;
      const voice = this.pickVoice(ttsLang);
      if (voice) {
        utterance.voice = voice;
        utterance.lang = voice.lang || ttsLang;
      }

      let finished = false;
      const done = () => {
        if (finished || token !== this.speakToken) return;
        finished = true;
        this.clearSpeakTimers();
        this.zone.run(() => {
          this.isSpeaking.set(false);
          const cb = this.speakEndCallback;
          this.speakEndCallback = null;
          cb?.();
        });
      };

      utterance.onstart = () => this.zone.run(() => this.isSpeaking.set(true));
      utterance.onend = () => done();
      utterance.onerror = () => done();

      this.zone.run(() => this.isSpeaking.set(true));
      try {
        window.speechSynthesis.resume();
        window.speechSynthesis.speak(utterance);
      } catch {
        done();
        return;
      }

      // Keep Chrome from freezing mid-sentence.
      this.resumeTimer = setInterval(() => {
        if (token !== this.speakToken) return;
        if (window.speechSynthesis.paused) window.speechSynthesis.resume();
      }, 250);

      // If onend never fires, still continue the conversation.
      const waitMs = Math.min(20000, 900 + spoken.length * 70);
      this.speakWatchdog = setTimeout(() => done(), waitMs);
    };

    // IMMEDIATE path for greetings (user click) — critical.
    if (immediate) {
      // Soft cancel without relying on its onend.
      try {
        window.speechSynthesis.cancel();
      } catch {
        /* ignore */
      }
      startUtterance();
      return;
    }

    // Non-immediate (after HTTP): brief cancel settle, then speak + one retry.
    try {
      window.speechSynthesis.cancel();
    } catch {
      /* ignore */
    }
    setTimeout(() => {
      startUtterance();
      // Retry once if nothing actually started.
      setTimeout(() => {
        if (token !== this.speakToken) return;
        if (!window.speechSynthesis.speaking && !window.speechSynthesis.pending) {
          this.lastError.set('Retrying voice…');
          startUtterance();
        }
      }, 600);
    }, 60);
  }

  stopSpeaking(): void {
    this.speakToken++;
    this.speakEndCallback = null;
    this.clearSpeakTimers();
    try {
      window.speechSynthesis?.cancel();
    } catch {
      /* ignore */
    }
    this.isSpeaking.set(false);
  }

  startListening(
    onTranscript: (text: string) => void,
    lang = 'fr-FR',
    onInterim?: (text: string) => void,
  ): void {
    if (!this.speechSupported) {
      this.lastError.set('Mic speech needs Chrome.');
      return;
    }

    this.lastError.set('');
    this.onTranscript = onTranscript;
    this.onInterim = onInterim ?? null;
    this.collected = '';
    this.expectCommit = false;
    this.clearListenTimers();

    const Ctor =
      (window as unknown as { SpeechRecognition?: new () => SpeechRecognitionLike }).SpeechRecognition ??
      (window as unknown as { webkitSpeechRecognition?: new () => SpeechRecognitionLike })
        .webkitSpeechRecognition;
    if (!Ctor) {
      this.lastError.set('SpeechRecognition missing — use Chrome.');
      return;
    }

    try {
      this.recognition?.abort();
    } catch {
      /* ignore */
    }

    this.recognition = new Ctor();
    this.recognition.continuous = true;
    this.recognition.interimResults = true;
    this.recognition.maxAlternatives = 5;
    this.recognition.lang = this.normalizeListenLang(lang);

    this.recognition.onresult = (event) => {
      this.zone.run(() => {
        let interim = '';
        let finals = '';
        for (let i = event.resultIndex; i < event.results.length; i++) {
          const best = this.pickBestAlternative(event.results[i]);
          if (event.results[i].isFinal) finals += best;
          else interim += best;
        }
        if (finals.trim()) this.collected = `${this.collected} ${finals}`.trim();
        const live = `${this.collected} ${interim}`.trim();
        this.onInterim?.(live);
        if (live.length >= 1) {
          // Derja speakers pause longer between words — don't cut early.
          const silenceMs = this.normalizeListenLang(lang).startsWith('ar') ? 1800 : 1300;
          this.armSilenceCommit(silenceMs);
        }
      });
    };

    this.recognition.onerror = (event) => {
      this.zone.run(() => {
        console.error('[Dosya STT] onerror', event.error);
        if (event.error === 'aborted' || event.error === 'no-speech') return;
        this.isListening.set(false);
        this.clearListenTimers();
        if (event.error === 'not-allowed') {
          this.lastError.set('Allow the microphone for this site, then tap the orb.');
        } else if (event.error === 'network') {
          this.lastError.set('Chrome mic needs internet.');
        } else {
          this.lastError.set(`Mic: ${event.error}`);
        }
      });
    };

    this.recognition.onend = () => {
      this.zone.run(() => {
        this.isListening.set(false);
        this.clearListenTimers();
        if (!this.expectCommit) return;
        this.expectCommit = false;
        const said = this.softCorrectCivic(this.collected.trim());
        this.collected = '';
        this.onInterim?.('');
        if (said) {
          this.onTranscript?.(said);
        } else {
          this.lastError.set('Didn’t catch speech — try again.');
          setTimeout(() => {
            if (!this.isSpeaking()) {
              this.startListening(onTranscript, lang, onInterim);
            }
          }, 700);
        }
      });
    };

    this.isListening.set(true);
    try {
      this.recognition.start();
      this.hardTimer = setTimeout(() => this.commitListening(), 18000);
    } catch {
      this.isListening.set(false);
      this.lastError.set('Mic failed to start — tap the orb.');
    }
  }

  commitListening(): void {
    if (!this.isListening()) return;
    this.clearListenTimers();
    this.expectCommit = true;
    try {
      this.recognition?.stop();
    } catch {
      const said = this.softCorrectCivic(this.collected.trim());
      this.isListening.set(false);
      this.collected = '';
      if (said) this.onTranscript?.(said);
    }
  }

  stopListening(): void {
    this.clearListenTimers();
    this.expectCommit = false;
    this.collected = '';
    try {
      this.recognition?.abort();
    } catch {
      /* ignore */
    }
    this.isListening.set(false);
  }

  private stripForSpeech(text: string): string {
    return text
      .replace(/```[\s\S]*?```/g, ' ')
      .replace(/!\[[^\]]*]\([^)]*\)/g, ' ')
      .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
      .replace(/https?:\/\/\S+/g, ' ')
      .replace(/[#>*_`~]/g, ' ')
      .replace(/\*\*/g, '')
      // Soften long French admin titles so Arabic TTS doesn't drone them
      .replace(/\((?:CIN|SUARL)\)/g, ' ')
      .replace(/Renouvellement de la Carte d'Identit[ée] Nationale/gi, 'بطاقة التعريف')
      .replace(/demande de passeport/gi, 'طلب الپاسپور')
      .replace(/passeport tunisien/gi, 'الپاسپور التونسي')
      .replace(/\s+/g, ' ')
      .trim();
  }

  private armSilenceCommit(ms: number): void {
    if (this.silenceTimer) clearTimeout(this.silenceTimer);
    this.silenceTimer = setTimeout(() => this.commitListening(), ms);
  }

  private clearListenTimers(): void {
    if (this.silenceTimer) {
      clearTimeout(this.silenceTimer);
      this.silenceTimer = null;
    }
    if (this.hardTimer) {
      clearTimeout(this.hardTimer);
      this.hardTimer = null;
    }
  }

  private clearSpeakTimers(): void {
    if (this.speakWatchdog) {
      clearTimeout(this.speakWatchdog);
      this.speakWatchdog = null;
    }
    if (this.resumeTimer) {
      clearInterval(this.resumeTimer);
      this.resumeTimer = null;
    }
  }

  private pickVoice(lang: string): SpeechSynthesisVoice | null {
    const voices = window.speechSynthesis.getVoices();
    if (!voices.length) return null;
    const prefix = lang.slice(0, 2).toLowerCase();
    const list = voices.filter((v) => v.lang?.toLowerCase().startsWith(prefix));
    return (
      list.find((v) => /google|microsoft|natural|neural/i.test(v.name)) ?? list[0] ?? null
    );
  }

  private pickBestAlternative(result: SpeechRecognitionResultLike): string {
    const n = Math.max(1, result.length || 1);
    let best = result[0]?.transcript ?? '';
    let bestScore = this.civicScore(best);
    for (let i = 1; i < n; i++) {
      const t = result[i]?.transcript ?? '';
      const s = this.civicScore(t) + (result[i]?.confidence ?? 0) * 0.1;
      if (s > bestScore) {
        best = t;
        bestScore = s;
      }
    }
    return best;
  }

  private civicScore(text: string): number {
    const lower = text.toLowerCase();
    let score = 0;
    for (const hint of CIVIC_HINTS) {
      if (lower.includes(hint.toLowerCase())) score += 3;
    }
    return score;
  }

  /** Light ASR cleanup for common Tunisian civic mishears. */
  private softCorrectCivic(text: string): string {
    if (!text) return text;
    let out = text;
    // Moroccan "ديال" sometimes creeps in — drop it; keep الباسبور etc.
    out = out.replace(/\s*ديال\s*/g, ' ');
    // Frequent "نبدل / نحب نبدّل" mishears → keep if passport already present, else nudge
    if (/باسبور|پاسپور|جواز|passeport/i.test(out) && /حبيبه|نحب|نحبك/.test(out)) {
      out = out.replace(/حبيبه|نحبك/g, 'نبدّل');
    }
    // ة vs ه + CIN phrasing
    out = out.replace(/بطاقه\s*التعريف/g, 'بطاقة التعريف');
    out = out.replace(/بطاقه\s*تعريف/g, 'بطاقة تعريف');
    out = out.replace(/\bسي\s*ان\b/gi, 'CIN');
    return out.replace(/\s+/g, ' ').trim();
  }

  private normalizeListenLang(lang: string): string {
    const lower = lang.toLowerCase();
    // Tunisian / Maghrebi STT — ar-SA mangles Derja badly.
    if (lower === 'tn' || lower === 'ar-tn' || lower.startsWith('ar-tn')) return 'ar-TN';
    if (lower.startsWith('ar-ma') || lower === 'ar-ma') return 'ar-MA';
    if (lower.startsWith('ar')) return 'ar-TN';
    if (lower.startsWith('fr')) return 'fr-FR';
    if (lower.startsWith('en')) return 'en-US';
    return lang;
  }

  private normalizeSpeakLang(lang: string): string {
    const lower = lang.toLowerCase();
    // TTS: ar-TN voices are rare; ar-SA / ar-EG are the usual browser voices.
    if (lower.startsWith('ar') || lower === 'tn') return 'ar-SA';
    if (lower.startsWith('fr')) return 'fr-FR';
    if (lower.startsWith('en')) return 'en-US';
    return lang;
  }

  private detectSpeechSupport(): boolean {
    return (
      typeof window !== 'undefined' &&
      (!!(window as unknown as { SpeechRecognition?: unknown }).SpeechRecognition ||
        !!(window as unknown as { webkitSpeechRecognition?: unknown }).webkitSpeechRecognition) &&
      'speechSynthesis' in window
    );
  }
}
