import { Injectable, signal } from '@angular/core';

interface SpeechRecognitionResultLike {
  isFinal: boolean;
  0: { transcript: string };
}

interface SpeechRecognitionEventLike extends Event {
  results: SpeechRecognitionResultLike[];
}

interface SpeechRecognitionLike extends EventTarget {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  start(): void;
  stop(): void;
  abort(): void;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: Event) => void) | null;
  onend: (() => void) | null;
}

@Injectable({ providedIn: 'root' })
export class VoiceService {
  readonly isListening = signal(false);
  readonly isSpeaking = signal(false);
  readonly voiceMode = signal(false);
  readonly speechSupported = this.detectSpeechSupport();

  private recognition: SpeechRecognitionLike | null = null;
  private onTranscript: ((text: string) => void) | null = null;

  toggleVoiceMode(): void {
    this.voiceMode.update((v) => !v);
    if (!this.voiceMode()) {
      this.stopSpeaking();
    }
  }

  startListening(onTranscript: (text: string) => void, lang = 'fr-FR'): void {
    if (!this.speechSupported) return;

    this.onTranscript = onTranscript;
    const SpeechRecognitionCtor =
      (window as unknown as { SpeechRecognition?: new () => SpeechRecognitionLike })
        .SpeechRecognition ??
      (window as unknown as { webkitSpeechRecognition?: new () => SpeechRecognitionLike })
        .webkitSpeechRecognition;

    if (!SpeechRecognitionCtor) return;

    if (!this.recognition) {
      this.recognition = new SpeechRecognitionCtor();
      this.recognition.continuous = false;
      this.recognition.interimResults = true;

      this.recognition.onresult = (event) => {
        const last = event.results[event.results.length - 1];
        if (last?.isFinal && last[0]?.transcript) {
          this.onTranscript?.(last[0].transcript.trim());
        }
      };

      this.recognition.onerror = () => this.isListening.set(false);
      this.recognition.onend = () => this.isListening.set(false);
    }

    this.recognition.lang = lang;
    this.isListening.set(true);
    this.recognition.start();
  }

  stopListening(): void {
    this.recognition?.stop();
    this.isListening.set(false);
  }

  toggleListening(onTranscript: (text: string) => void): void {
    if (this.isListening()) {
      this.stopListening();
    } else {
      this.startListening(onTranscript);
    }
  }

  speak(text: string, lang = 'fr-FR'): void {
    if (!('speechSynthesis' in window) || !text.trim()) return;

    this.stopSpeaking();
    const clean = text
      .replace(/```[\s\S]*?```/g, ' ')
      .replace(/!\[[^\]]*]\([^)]*\)/g, ' ')
      .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
      .replace(/[#>*_`~]/g, ' ')
      .replace(/\*\*/g, '')
      .replace(/\s+/g, ' ')
      .trim();
    if (!clean) return;

    const utterance = new SpeechSynthesisUtterance(clean);
    utterance.lang = lang;
    utterance.rate = 1;
    utterance.onstart = () => this.isSpeaking.set(true);
    utterance.onend = () => this.isSpeaking.set(false);
    utterance.onerror = () => this.isSpeaking.set(false);
    window.speechSynthesis.speak(utterance);
  }

  stopSpeaking(): void {
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }
    this.isSpeaking.set(false);
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
