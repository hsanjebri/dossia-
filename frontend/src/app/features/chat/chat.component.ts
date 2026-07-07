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

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, OfficeLocatorPanelComponent],
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

  thinkingStep = 0;
  thinkingDone = false;

  readonly thinkingLabels = [
    'Analyse de votre question…',
    'Recherche dans les procédures vérifiées…',
    'Localisation des bureaux les plus proches…',
    'Préparation de la réponse…',
  ];

  readonly suggestions = [
    'Comment renouveler ma carte d\'identité ?',
    'Quels documents pour un passeport tunisien ?',
    'Où acheter un timbre fiscal près de chez moi ?',
    'Comment obtenir une attestation de résidence ?',
  ];

  private thinkingTimer: ReturnType<typeof setInterval> | null = null;
  private thinkingDoneTimer: ReturnType<typeof setTimeout> | null = null;

  private readonly welcomeMessage: ChatMessage = {
    role: 'assistant',
    content:
      'Bonjour ! Je suis l\'assistant Dosya. Posez-moi une question sur une démarche administrative tunisienne — activez votre position pour voir les bureaux les plus proches sur la carte.',
  };

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((params) => {
      const q = params.get('q');
      if (q) this.input = q;
    });

    this.messages = [this.welcomeMessage];
    this.loadHistory();
  }

  ngOnDestroy(): void {
    this.clearThinkingTimers();
    this.voice.stopListening();
    this.voice.stopSpeaking();
  }

  async enableLocation(): Promise<void> {
    const loc = await this.geo.requestLocation();
    this.locationEnabled = loc !== null;
  }

  loadHistory(): void {
    this.historyLoading = true;
    this.history.loadSessions().subscribe({
      next: (sessions) => {
        this.sessions = sessions;
        this.historyLoading = false;
      },
      error: () => {
        this.historyLoading = false;
      },
    });
  }

  async send(): Promise<void> {
    const text = this.input.trim();
    if (!text || this.loading) return;

    if (!this.locationEnabled && !this.geo.location()) {
      await this.enableLocation();
    }

    const loc = this.geo.location();

    this.messages.push({ role: 'user', content: text });
    this.input = '';
    this.loading = true;
    this.error = '';
    this.selectedOffice = null;
    this.activeRoute = null;
    this.startThinking();
    this.scrollToBottom();

    this.chatApi.ask(text, this.currentSessionId, loc?.lat, loc?.lng).subscribe({
      next: (response) => {
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

        this.finishThinking(() => {
          this.messages.push({
            role: 'assistant',
            content: response.answer,
            sources: response.sources,
            nearbyOffices: response.nearbyOffices ?? [],
          });
          this.loadHistory();
          this.scrollToBottom();

          if (this.voice.voiceMode()) {
            this.voice.speak(response.answer);
          }
        });
      },
      error: (err: HttpErrorResponse) => {
        this.clearThinkingTimers();
        this.loading = false;
        this.thinkingDone = false;
        this.error =
          err.error?.message ??
          'Erreur lors de la communication avec l\'assistant. Réessayez dans un instant.';
      },
    });
  }

  useSuggestion(text: string): void {
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
    this.currentSessionId = session.id;
    this.loading = true;
    this.sidebarOpen = false;

    this.history.loadSessionMessages(session.id).subscribe({
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
    this.voice.toggleListening((transcript) => {
      this.input = transcript;
      void this.send();
    });
  }

  speakMessage(content: string): void {
    this.voice.speak(content);
  }

  thinkingLabel(): string {
    if (this.thinkingDone) return 'Terminé ✓';
    return this.thinkingLabels[this.thinkingStep] ?? this.thinkingLabels[0];
  }

  private startThinking(): void {
    this.thinkingStep = 0;
    this.thinkingDone = false;
    this.clearThinkingTimers();
    this.thinkingTimer = setInterval(() => {
      if (this.thinkingStep < this.thinkingLabels.length - 1) {
        this.thinkingStep += 1;
      }
    }, 1200);
  }

  private finishThinking(onDone: () => void): void {
    if (this.thinkingTimer) {
      clearInterval(this.thinkingTimer);
      this.thinkingTimer = null;
    }
    this.thinkingDone = true;
    this.thinkingDoneTimer = setTimeout(() => {
      this.loading = false;
      this.thinkingDone = false;
      onDone();
    }, 500);
  }

  private clearThinkingTimers(): void {
    if (this.thinkingTimer) clearInterval(this.thinkingTimer);
    if (this.thinkingDoneTimer) clearTimeout(this.thinkingDoneTimer);
    this.thinkingTimer = null;
    this.thinkingDoneTimer = null;
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const el = this.messagesContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    }, 50);
  }
}
