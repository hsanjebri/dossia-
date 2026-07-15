import { DatePipe } from '@angular/common';
import { Component, ElementRef, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { ChatMessage, ChatSessionSummary } from '../../core/models/chat.model';
import { NearbyOffice, RouteInfo } from '../../core/models/office.model';
import { ChatApiService } from '../../core/services/chat-api.service';
import { ChatHistoryService } from '../../core/services/chat-history.service';
import { GeolocationService } from '../../core/services/geolocation.service';
import { RoutingService } from '../../core/services/routing.service';
import { VoiceService } from '../../core/services/voice.service';
import { OfficeLocatorPanelComponent } from '../../shared/components/office-locator-panel/office-locator-panel.component';
import { MarkdownPipe } from '../../shared/pipes/markdown.pipe';

const GUEST_PROMPT_LIMIT = 5;
const GUEST_PROMPT_KEY = 'dosya_guest_prompt_count';

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, OfficeLocatorPanelComponent, MarkdownPipe],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.scss',
})
export class ChatComponent implements OnInit, OnDestroy {
  private readonly chatApi = inject(ChatApiService);
  private readonly history = inject(ChatHistoryService);
  private readonly route = inject(ActivatedRoute);
  private readonly routing = inject(RoutingService);
  readonly auth = inject(AuthService);
  readonly voice = inject(VoiceService);
  readonly geo = inject(GeolocationService);

  @ViewChild('messagesContainer') messagesContainer?: ElementRef<HTMLElement>;

  messages: ChatMessage[] = [];
  sessions: ChatSessionSummary[] = [];
  currentSessionId: string | null = null;
  input = '';
  loading = false;
  error = '';
  sidebarOpen = false;
  historyLoading = false;

  selectedOffice: NearbyOffice | null = null;
  activeRoute: RouteInfo | null = null;
  locationEnabled = false;

  guestPromptCount = 0;
  showAuthGate = false;

  readonly guestPromptLimit = GUEST_PROMPT_LIMIT;

  readonly suggestions = [
    'Comment renouveler ma carte d\'identité ?',
    'Quels documents pour un passeport tunisien ?',
    'Où acheter un timbre fiscal près de chez moi ?',
    'Comment obtenir une attestation de résidence ?',
  ];

  private readonly welcomeMessage: ChatMessage = {
    role: 'assistant',
    content:
      'Ahlan ! Je suis Dosya (دوسيا), votre assistant virtuel dédié aux démarches administratives en Tunisie. Je suis là pour vous aider à y voir plus clair.\n\nComment puis-je vous être utile aujourd\'hui ? N\'hésitez pas à me poser vos questions concernant les procédures (passeport, CIN, équivalence de diplôme, etc.).',
  };

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((params) => {
      const q = params.get('q');
      if (q) this.input = q;

      const sessionId = params.get('session');
      if (sessionId && this.auth.isLoggedIn()) {
        this.openSessionById(sessionId);
      }
    });

    this.messages = [this.welcomeMessage];
    if (this.auth.isLoggedIn()) {
      this.clearGuestPromptCount();
    } else {
      this.guestPromptCount = this.readGuestPromptCount();
      if (this.guestPromptCount >= GUEST_PROMPT_LIMIT) {
        this.showAuthGate = true;
      }
    }
    this.loadHistory((sessions) => {
      // Continue where you left off (logged-in, no explicit session in URL).
      if (
        this.auth.isLoggedIn() &&
        !this.route.snapshot.queryParamMap.get('session') &&
        sessions.length > 0 &&
        this.messages.length <= 1
      ) {
        this.openSessionById(sessions[0].id);
      }
    });
  }

  prepareAuthLeave(): void {
    const savedId = this.history.saveGuestConversation(this.messages, this.currentSessionId);
    if (savedId) {
      this.currentSessionId = savedId;
    }
  }

  ngOnDestroy(): void {
    this.voice.stopListening();
    this.voice.stopSpeaking();
  }

  get guestRemaining(): number {
    return Math.max(0, GUEST_PROMPT_LIMIT - this.guestPromptCount);
  }

  get isComposerLocked(): boolean {
    return !this.auth.isLoggedIn() && this.guestPromptCount >= GUEST_PROMPT_LIMIT;
  }

  closeAuthGate(): void {
    this.showAuthGate = false;
  }

  async enableLocation(): Promise<void> {
    const loc = await this.geo.requestLocation();
    this.locationEnabled = loc !== null;
  }

  loadHistory(onLoaded?: (sessions: ChatSessionSummary[]) => void): void {
    this.historyLoading = true;
    this.history.loadSessions().subscribe({
      next: (sessions) => {
        this.sessions = sessions;
        this.historyLoading = false;
        onLoaded?.(sessions);
      },
      error: () => {
        this.historyLoading = false;
      },
    });
  }

  async send(): Promise<void> {
    const text = this.input.trim();
    if (!text || this.loading) return;

    if (this.isComposerLocked) {
      this.showAuthGate = true;
      return;
    }

    if (!this.locationEnabled && !this.geo.location()) {
      await this.enableLocation();
    }

    const loc = this.geo.location();
    const historyPayload = this.buildHistoryPayload();

    this.messages.push({ role: 'user', content: text });
    this.input = '';
    this.loading = true;
    this.error = '';
    this.selectedOffice = null;
    this.activeRoute = null;
    this.scrollToBottom();

    const lang = this.detectLang(text, historyPayload);
    const speakLang = lang === 'en' ? 'en-US' : lang === 'ar' ? 'ar-TN' : 'fr-FR';

    this.chatApi
      .ask(text, this.currentSessionId, loc?.lat, loc?.lng, lang, historyPayload)
      .subscribe({
        next: (response) => {
          if (!this.auth.isLoggedIn()) {
            this.incrementGuestPromptCount();
          }

          if (response.sessionId) {
            this.currentSessionId = response.sessionId;
          } else if (!this.auth.isLoggedIn()) {
            this.currentSessionId = this.history.persistLocalExchange(
              this.currentSessionId,
              text,
              response.answer,
              response.sources,
            );
          }

          this.loading = false;
          const assistantIndex =
            this.messages.push({
              role: 'assistant',
              content: '',
              sources: response.sources,
              nearbyOffices: response.nearbyOffices ?? [],
              suggestions: response.suggestions ?? [],
              streaming: true,
            }) - 1;

          void this.revealAnswer(assistantIndex, response.answer).then(() => {
            this.loadHistory();
            this.scrollToBottom();
            if (this.voice.voiceMode()) {
              this.voice.speak(response.answer, speakLang);
            }
            if (this.isComposerLocked) {
              this.showAuthGate = true;
            }
          });
        },
        error: (err: HttpErrorResponse) => {
          this.loading = false;
          if (this.messages.at(-1)?.role === 'user' && this.messages.at(-1)?.content === text) {
            this.messages.pop();
          }
          this.error =
            err.error?.message ??
            'Erreur lors de la communication avec l\'assistant. Réessayez dans un instant.';
        },
      });
  }

  useChip(prompt: string): void {
    if (this.isComposerLocked) {
      this.showAuthGate = true;
      return;
    }
    this.input = prompt;
    void this.send();
  }

  useSuggestion(text: string): void {
    if (this.isComposerLocked) {
      this.showAuthGate = true;
      return;
    }
    this.input = text;
    void this.send();
  }

  newChat(): void {
    this.currentSessionId = null;
    this.messages = [this.welcomeMessage];
    this.error = '';
    this.sidebarOpen = false;
    this.selectedOffice = null;
    this.activeRoute = null;
    this.voice.stopSpeaking();
  }

  openSession(session: ChatSessionSummary): void {
    this.openSessionById(session.id);
  }

  private openSessionById(sessionId: string): void {
    this.currentSessionId = sessionId;
    this.loading = true;
    this.sidebarOpen = false;
    this.showAuthGate = false;

    this.history.loadSessionMessages(sessionId).subscribe({
      next: (messages) => {
        this.messages = messages.length ? messages : [this.welcomeMessage];
        this.loading = false;
        this.scrollToBottom();
      },
      error: () => {
        this.loading = false;
        this.error = 'Impossible de charger cette conversation.';
      },
    });
  }

  deleteSession(event: Event, sessionId: string): void {
    event.stopPropagation();
    this.history.deleteSession(sessionId).subscribe({
      next: () => {
        this.sessions = this.sessions.filter((s) => s.id !== sessionId);
        if (this.currentSessionId === sessionId) {
          this.newChat();
        }
      },
    });
  }

  onOfficeSelected(office: NearbyOffice): void {
    this.selectedOffice = office;
    this.activeRoute = null;
    const loc = this.geo.location();
    if (!loc) return;
    this.routing.getRoute(loc, office).subscribe((route) => {
      this.activeRoute = route;
      this.scrollToBottom();
    });
  }

  toggleSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
  }

  onVoiceInput(): void {
    if (this.isComposerLocked) {
      this.showAuthGate = true;
      return;
    }
    this.voice.toggleListening((transcript) => {
      this.input = transcript;
      void this.send();
    });
  }

  speakMessage(content: string): void {
    this.voice.speak(content, this.detectVoiceLang(content));
  }

  private async revealAnswer(index: number, full: string): Promise<void> {
    const msg = this.messages[index];
    if (!msg) return;
    const step = Math.max(8, Math.floor(full.length / 60));
    for (let i = step; i < full.length; i += step) {
      msg.content = full.slice(0, i);
      this.scrollToBottom();
      await new Promise((r) => setTimeout(r, 16));
    }
    msg.content = full;
    msg.streaming = false;
    this.scrollToBottom();
  }

  private detectVoiceLang(text: string): string {
    if (/[\u0600-\u06FF]/.test(text)) return 'ar-TN';
    if (/\b(the|your|passport|documents|renew)\b/i.test(text)) return 'en-US';
    return 'fr-FR';
  }

  private detectLang(text: string, history: { role: string; content: string }[]): string {
    const sample = /\d{1,2}|^(oui|non|ok|yes|no)$/i.test(text.trim())
      ? [...history].reverse().find((t) => t.role === 'user')?.content ?? text
      : text;
    if (/[\u0600-\u06FF]/.test(sample)) return 'ar';
    const lower = sample.toLowerCase();
    const frHits = (lower.match(
      /\b(je|tu|vous|bonjour|bonsoir|merci|comment|renouveller|renouveler|passeport|carte|identité|diplôme|demarche|démarche|svp|s'il|sil|où|pour)\b/gi,
    ) ?? []).length;
    const enHits = (lower.match(
      /\b(i|my|me|renew|passport|how|what|where|need|want|please|hello|hi|the|a|an|can|could|would|identity|card|diploma)\b/gi,
    ) ?? []).length;
    if (enHits > frHits && enHits > 0) return 'en';
    if (frHits > 0) return 'fr';
    // Informal latin Tunisian/English mix without French markers → English
    if (/[a-z]/i.test(sample) && !/[àâçéèêëïôùûü]/i.test(sample) && enHits >= frHits) {
      return 'en';
    }
    return 'fr';
  }

  private buildHistoryPayload(): { role: string; content: string }[] {
    return this.messages
      .filter((m) => m.role === 'user' || m.role === 'assistant')
      .filter((m) => m.content !== this.welcomeMessage.content)
      .slice(-8)
      .map((m) => ({ role: m.role, content: m.content }));
  }

  private readGuestPromptCount(): number {
    try {
      const raw = localStorage.getItem(GUEST_PROMPT_KEY);
      const n = raw ? Number.parseInt(raw, 10) : 0;
      return Number.isFinite(n) && n > 0 ? n : 0;
    } catch {
      return 0;
    }
  }

  private incrementGuestPromptCount(): void {
    this.guestPromptCount = Math.min(GUEST_PROMPT_LIMIT, this.guestPromptCount + 1);
    try {
      localStorage.setItem(GUEST_PROMPT_KEY, String(this.guestPromptCount));
    } catch {
      /* ignore quota / private mode */
    }
  }

  private clearGuestPromptCount(): void {
    this.guestPromptCount = 0;
    try {
      localStorage.removeItem(GUEST_PROMPT_KEY);
    } catch {
      /* ignore */
    }
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const el = this.messagesContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    }, 50);
  }
}
