import { DatePipe } from '@angular/common';
import { Component, ElementRef, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import {
  AGENT_STORAGE_KEY,
  CHAT_AGENTS,
  ChatAgent,
  findChatAgent,
} from '../../core/models/chat-agent.model';
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

  readonly agents = CHAT_AGENTS;
  readonly particleSlots = Array.from({ length: 28 }, (_, i) => i + 1);
  selectedAgent: ChatAgent | null = null;
  showAgentPicker = false;
  voiceStageOpen = false;
  voiceUserLine = '';
  voiceAgentLine = '';
  voiceInterim = '';
  focusedAgentId: ChatAgent['id'] | null = null;
  private listenAfterAgentPick = false;
  private skipVoiceAutoListen = false;
  private voiceNeedsGreeting = true;

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
    this.selectedAgent = this.loadStoredAgent();
    this.focusedAgentId = this.selectedAgent?.id ?? 'sofia';

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
    this.voiceStageOpen = false;
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
    if (!text || this.loading) {
      console.warn('[Dosya voice] send skipped', { text, loading: this.loading });
      return;
    }

    if (this.isComposerLocked) {
      console.warn('[Dosya voice] composer locked → auth gate');
      this.showAuthGate = true;
      return;
    }

    // Never block voice on the location permission dialog.
    if (!this.voiceStageOpen && !this.locationEnabled && !this.geo.location()) {
      await this.enableLocation();
    }

    const loc = this.geo.location();
    const historyPayload = this.buildHistoryPayload();

    this.messages.push({ role: 'user', content: text });
    this.input = '';
    this.loading = true;
    this.error = '';
    this.voice.lastError.set('');
    this.selectedOffice = null;
    this.activeRoute = null;
    if (this.voiceStageOpen) {
      this.voiceUserLine = text;
      this.voiceAgentLine = '';
      this.voiceInterim = '';
    } else {
      this.scrollToBottom();
    }

    const lang = this.selectedAgent?.lang ?? this.detectLang(text, historyPayload);
    const speakLang = this.selectedAgent?.speechLang
      ?? (lang === 'en' ? 'en-US' : lang === 'ar' || lang === 'tn' ? 'ar' : 'fr-FR');

    // Guests must not send localStorage session UUIDs — backend treats them as real sessions → 404.
    // Also wipe a known-stale id before first voice ask.
    let apiSessionId = this.auth.isLoggedIn() ? this.currentSessionId : null;

    const postChat = (sessionId: string | null, isRetry = false) => {
      const payload = {
        message: text,
        sessionId,
        lat: this.voiceStageOpen ? null : loc?.lat ?? null,
        lng: this.voiceStageOpen ? null : loc?.lng ?? null,
        lang,
        speakLang,
        agentId: this.selectedAgent?.id ?? null,
        historyLen: historyPayload.length,
        voiceStageOpen: this.voiceStageOpen,
        loggedIn: this.auth.isLoggedIn(),
        isRetry,
      };
      console.log('[Dosya voice] → POST /chat', payload);

      this.chatApi
        .ask(
          text,
          sessionId,
          this.voiceStageOpen ? null : loc?.lat,
          this.voiceStageOpen ? null : loc?.lng,
          lang,
          historyPayload,
          this.selectedAgent?.id ?? null,
        )
        .subscribe({
          next: (response) => {
            console.log('[Dosya voice] ← /chat OK', {
              answerLen: response?.answer?.length ?? 0,
              answerPreview: (response?.answer ?? '').slice(0, 120),
              sessionId: response?.sessionId,
              sources: response?.sources?.length ?? 0,
              model: response?.model,
            });
            try {
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
              this.voice.lastError.set('');
              const inVoice = this.voiceStageOpen;
              const assistantIndex =
                this.messages.push({
                  role: 'assistant',
                  content: inVoice ? response.answer : '',
                  sources: response.sources ?? [],
                  nearbyOffices: response.nearbyOffices ?? [],
                  suggestions: response.suggestions ?? [],
                  checklist: (response.checklist ?? []).map((item) => ({ ...item, checked: false })),
                  streaming: !inVoice,
                }) - 1;

              if (inVoice) {
                this.voiceAgentLine = response.answer;
                console.log('[Dosya voice] speaking answer…', { speakLang, inVoice });
                this.voice.unlockAudio();
                this.voice.speak(
                  response.answer,
                  speakLang,
                  () => {
                    console.log('[Dosya voice] answer speech ended → listen again');
                    if (this.voiceStageOpen && !this.skipVoiceAutoListen && !this.isComposerLocked) {
                      setTimeout(() => this.beginVoiceListen(), 500);
                    }
                    this.skipVoiceAutoListen = false;
                  },
                  false,
                );
                if (this.isComposerLocked) {
                  this.showAuthGate = true;
                }
                return;
              }

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
            } catch (e) {
              console.error('[Dosya voice] client crash after OK response', e);
              this.loading = false;
              this.recoverVoiceAfterError(
                e instanceof Error ? e.message : 'Unexpected client error after reply.',
              );
            }
          },
          error: (err: HttpErrorResponse) => {
            console.error('[Dosya voice] ← /chat FAIL', {
              status: err.status,
              statusText: err.statusText,
              url: err.url,
              message: err.message,
              errorBody: err.error,
              isRetry,
            });
            const bodyMsg =
              typeof err.error === 'string'
                ? err.error
                : (err.error?.message as string | undefined);
            const isMissingSession =
              err.status === 404 &&
              typeof bodyMsg === 'string' &&
              bodyMsg.toLowerCase().includes('session');

            // Silent auto-retry once without the bad session id.
            if (isMissingSession && !isRetry) {
              console.warn('[Dosya voice] stale session → retry with sessionId=null', sessionId);
              this.currentSessionId = null;
              postChat(null, true);
              return;
            }

            this.loading = false;
            if (this.messages.at(-1)?.role === 'user' && this.messages.at(-1)?.content === text) {
              this.messages.pop();
            }
            if (err.status === 404 && this.currentSessionId) {
              this.currentSessionId = null;
            }
            const msg =
              err.status === 429
                ? 'Too many requests. Sign in or try again later.'
                : err.status === 0
                  ? 'Cannot reach the server. Is the backend running on :8080?'
                  : (bodyMsg ?? `Error ${err.status || 'network'} — ${err.statusText || err.message}`);
            this.recoverVoiceAfterError(msg);
            this.error = msg;
          },
        });
    };

    postChat(apiSessionId);
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

  get focusedAgent(): ChatAgent | null {
    return findChatAgent(this.focusedAgentId);
  }

  get voiceOrbLabel(): string {
    if (this.voice.isSpeaking()) return `${this.selectedAgent?.name ?? 'Dosya'} is speaking…`;
    if (this.loading) return `${this.selectedAgent?.name ?? 'Dosya'} is thinking…`;
    if (this.voice.isListening()) {
      return this.voiceInterim
        ? `Heard: “${this.voiceInterim.slice(0, 60)}${this.voiceInterim.length > 60 ? '…' : ''}”`
        : 'Listening… speak now';
    }
    return 'Tap to talk';
  }

  exitVoiceStage(): void {
    this.skipVoiceAutoListen = true;
    this.voiceStageOpen = false;
    this.voiceInterim = '';
    this.voiceNeedsGreeting = true;
    this.voice.stopListening();
    this.voice.stopSpeaking();
  }

  onVoiceOrbTap(): void {
    if (this.loading) return;
    if (this.voice.isSpeaking()) {
      this.skipVoiceAutoListen = true;
      this.voice.stopSpeaking();
      return;
    }
    // Tap while listening = SEND what was heard (don't discard).
    if (this.voice.isListening()) {
      this.voice.commitListening();
      return;
    }
    this.startVoiceWithAgent(false);
  }

  onVoiceInput(): void {
    if (this.isComposerLocked) {
      this.showAuthGate = true;
      return;
    }
    if (this.voice.isListening()) {
      this.voice.commitListening();
      return;
    }
    if (!this.selectedAgent) {
      this.listenAfterAgentPick = true;
      this.openAgentPicker();
      return;
    }
    // Returning to voice with an already chosen guide → greet again.
    this.voiceNeedsGreeting = true;
    this.voiceStageOpen = true;
    this.startVoiceWithAgent(true);
  }

  confirmAgent(agent: ChatAgent): void {
    this.selectedAgent = agent;
    this.focusedAgentId = agent.id;
    this.persistAgent(agent);
    this.showAgentPicker = false;
    this.listenAfterAgentPick = false;
    this.voiceStageOpen = true;
    this.voiceNeedsGreeting = true;
    this.skipVoiceAutoListen = false;
    this.voiceInterim = '';
    this.voiceUserLine = '';
    this.voiceAgentLine = '';
    this.error = '';
    // Voice chats should not inherit a stale sidebar/guest session id.
    if (this.auth.isLoggedIn()) {
      // Keep only if we'll create a fresh one after first successful answer.
      this.currentSessionId = null;
    }
    if (!this.voice.voiceMode()) {
      this.voice.toggleVoiceMode();
    }

    // CRITICAL: unlock + greet in THIS click stack (Chrome blocks delayed speech).
    this.voice.unlockAudio();
    const greet = this.agentGreeting(agent);
    this.voiceNeedsGreeting = false;
    this.voice.speak(
      greet,
      agent.speechLang,
      () => {
        if (this.voiceStageOpen && !this.skipVoiceAutoListen) {
          this.beginVoiceListen();
        }
      },
      true,
    );
  }

  changeAgent(): void {
    this.listenAfterAgentPick = false;
    this.skipVoiceAutoListen = true;
    this.voiceNeedsGreeting = true;
    this.voice.stopListening();
    this.voice.stopSpeaking();
    this.openAgentPicker();
  }

  private startVoiceWithAgent(forceGreeting = false): void {
    this.voiceStageOpen = true;
    this.skipVoiceAutoListen = false;
    this.voiceInterim = '';
    this.voice.unlockAudio();

    const agent = this.selectedAgent;
    if (!agent) return;

    if (forceGreeting || this.voiceNeedsGreeting) {
      this.voiceNeedsGreeting = false;
      this.voiceUserLine = '';
      this.voiceAgentLine = '';
      this.voice.speak(
        this.agentGreeting(agent),
        agent.speechLang,
        () => {
          if (this.voiceStageOpen && !this.skipVoiceAutoListen) {
            this.beginVoiceListen();
          }
        },
        true,
      );
      return;
    }

    this.beginVoiceListen();
  }

  private beginVoiceListen(): void {
    const agent = this.selectedAgent;
    if (!agent || !this.voiceStageOpen) {
      console.warn('[Dosya voice] beginVoiceListen skipped', {
        agent: agent?.id,
        voiceStageOpen: this.voiceStageOpen,
      });
      return;
    }
    console.log('[Dosya voice] startListening', { agent: agent.id, lang: agent.speechLang });
    this.voice.startListening(
      (transcript) => {
        console.log('[Dosya voice] final transcript', transcript);
        this.voiceInterim = '';
        this.voiceUserLine = transcript;
        this.input = transcript;
        void this.send();
      },
      agent.speechLang,
      (interim) => {
        this.voiceInterim = interim;
      },
    );
  }

  private recoverVoiceAfterError(message: string): void {
    console.error('[Dosya voice] recover after error', message);
    this.voice.lastError.set(message);
    if (!this.voiceStageOpen) return;
    this.voiceAgentLine = '';
    const apology =
      this.selectedAgent?.id === 'yasmine'
        ? 'سامحني، صار مشكل. عاود قولّي شنوة تحب.'
        : this.selectedAgent?.id === 'alex'
          ? "Sorry, something went wrong. Please say that again."
          : "Désolé, une erreur est survenue. Reprenez votre question.";
    this.voice.unlockAudio();
    this.voice.speak(
      apology,
      this.selectedAgent?.speechLang ?? 'fr-FR',
      () => {
        if (this.voiceStageOpen && !this.isComposerLocked) {
          setTimeout(() => this.beginVoiceListen(), 400);
        }
      },
      false,
    );
  }

  private agentGreeting(agent: ChatAgent): string {
    switch (agent.id) {
      case 'yasmine':
        return 'عسلامة، أنا ياسمين. شنوة تحب؟';
      case 'alex':
        return "Hi, I'm Alex. How can I help you today?";
      case 'sofia':
        return 'Bonjour, je suis Sofia. Comment puis-je vous aider ?';
    }
  }

  openAgentPicker(): void {
    this.focusedAgentId = this.selectedAgent?.id ?? this.focusedAgentId ?? 'sofia';
    this.showAgentPicker = true;
    setTimeout(() => {
      const el = document.querySelector('.ps-select') as HTMLElement | null;
      el?.focus();
    }, 50);
  }

  closeAgentPicker(): void {
    this.showAgentPicker = false;
    this.listenAfterAgentPick = false;
  }

  focusAgent(agent: ChatAgent): void {
    this.focusedAgentId = agent.id;
  }

  onAgentPickerKeydown(event: KeyboardEvent): void {
    const ids = this.agents.map((a) => a.id);
    const idx = ids.indexOf(this.focusedAgentId ?? 'sofia');
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
      event.preventDefault();
      this.focusedAgentId = ids[(idx + 1) % ids.length];
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.focusedAgentId = ids[(idx - 1 + ids.length) % ids.length];
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const agent = findChatAgent(this.focusedAgentId);
      if (agent) this.confirmAgent(agent);
    } else if (event.key === 'Escape') {
      this.closeAgentPicker();
    }
  }

  speakMessage(content: string): void {
    const lang = this.selectedAgent?.speechLang ?? this.detectVoiceLang(content);
    this.voice.speak(content, lang);
  }

  private loadStoredAgent(): ChatAgent | null {
    try {
      return findChatAgent(localStorage.getItem(AGENT_STORAGE_KEY));
    } catch {
      return null;
    }
  }

  private persistAgent(agent: ChatAgent): void {
    try {
      localStorage.setItem(AGENT_STORAGE_KEY, agent.id);
    } catch {
      /* ignore */
    }
  }

  toggleChecklistItem(msgIndex: number, itemId: string): void {
    const msg = this.messages[msgIndex];
    if (!msg?.checklist) return;
    const item = msg.checklist.find((c) => c.id === itemId);
    if (item) {
      item.checked = !item.checked;
    }
  }

  reportBadAnswer(msgIndex: number): void {
    const msg = this.messages[msgIndex];
    if (!msg || msg.role !== 'assistant' || msg.feedbackSent) return;

    const reason = window.prompt(
      'Pourquoi cette réponse est incorrecte ou inutile ? (court commentaire)',
      'Réponse incorrecte ou incomplète',
    );
    if (!reason?.trim()) return;

    const priorUser = [...this.messages]
      .slice(0, msgIndex)
      .reverse()
      .find((m) => m.role === 'user');

    this.chatApi
      .reportFeedback({
        sessionId: this.currentSessionId,
        userMessage: priorUser?.content,
        assistantAnswer: msg.content,
        reason: reason.trim(),
      })
      .subscribe({
        next: () => {
          msg.feedbackSent = true;
        },
        error: () => {
          this.error = 'Impossible d\'envoyer le signalement. Réessayez.';
        },
      });
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
