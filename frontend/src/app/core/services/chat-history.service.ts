import { Injectable, inject } from '@angular/core';
import { Observable, of, switchMap, tap, catchError, map } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import {
  ChatMessage,
  ChatSessionSummary,
  ChatSource,
  LocalChatSession,
} from '../models/chat.model';
import { ChatApiService } from './chat-api.service';

const STORAGE_KEY = 'dosya.chat.sessions';
const GUEST_PROMPT_KEY = 'dosya_guest_prompt_count';
const PENDING_SESSION_KEY = 'dosya.chat.pendingSessionId';

@Injectable({ providedIn: 'root' })
export class ChatHistoryService {
  private readonly api = inject(ChatApiService);
  private readonly auth = inject(AuthService);

  loadSessions(): Observable<ChatSessionSummary[]> {
    if (this.auth.isLoggedIn()) {
      return this.api.listSessions();
    }
    return of(this.readLocalSummaries());
  }

  loadSessionMessages(sessionId: string): Observable<ChatMessage[]> {
    if (this.auth.isLoggedIn()) {
      return new Observable((subscriber) => {
        this.api.getSession(sessionId).subscribe({
          next: (detail) => {
            subscriber.next(
              detail.messages.map((m) => ({
                role: m.role as 'user' | 'assistant',
                content: m.content,
                sources: m.sources?.length ? m.sources : undefined,
              })),
            );
            subscriber.complete();
          },
          error: (err) => subscriber.error(err),
        });
      });
    }
    const session = this.readLocal().find((s) => s.id === sessionId);
    return of(session?.messages ?? []);
  }

  deleteSession(sessionId: string): Observable<void> {
    if (this.auth.isLoggedIn()) {
      return this.api.deleteSession(sessionId);
    }
    const sessions = this.readLocal().filter((s) => s.id !== sessionId);
    this.writeLocal(sessions);
    return of(undefined);
  }

  persistLocalExchange(
    sessionId: string | null,
    userText: string,
    assistantText: string,
    sources: ChatSource[],
  ): string {
    const now = new Date().toISOString();
    let sessions = this.readLocal();
    let session: LocalChatSession;

    if (sessionId) {
      const existing = sessions.find((s) => s.id === sessionId);
      session = existing ?? this.newLocalSession(userText, now);
    } else {
      session = this.newLocalSession(userText, now);
    }

    session.messages.push({ role: 'user', content: userText });
    session.messages.push({
      role: 'assistant',
      content: assistantText,
      sources: sources.length ? sources : undefined,
    });
    session.preview = assistantText.slice(0, 120);
    session.updatedAt = now;

    sessions = [session, ...sessions.filter((s) => s.id !== session.id)].slice(0, 30);
    this.writeLocal(sessions);
    return session.id;
  }

  /** Save the in-memory guest thread before leaving for register/login. */
  saveGuestConversation(messages: ChatMessage[], sessionId: string | null): string | null {
    const exchanges = messages.filter(
      (m) =>
        (m.role === 'user' || m.role === 'assistant') &&
        m.content?.trim() &&
        !m.content.startsWith('Ahlan ! Je suis Dosya'),
    );
    if (exchanges.length === 0) {
      return sessionId;
    }

    const now = new Date().toISOString();
    let sessions = this.readLocal();
    const firstUser = exchanges.find((m) => m.role === 'user')?.content ?? 'Conversation d\'essai';
    let session = sessionId ? sessions.find((s) => s.id === sessionId) : undefined;
    if (!session) {
      session = this.newLocalSession(firstUser, now);
    }
    session.messages = exchanges.map((m) => ({
      role: m.role,
      content: m.content,
      sources: m.sources,
    }));
    session.preview = exchanges.at(-1)?.content.slice(0, 120) ?? '';
    session.updatedAt = now;
    session.title = this.buildTitle(firstUser);

    sessions = [session, ...sessions.filter((s) => s.id !== session!.id)].slice(0, 30);
    this.writeLocal(sessions);
    this.setPendingSessionId(session.id);
    return session.id;
  }

  /**
   * After register/login: upload guest trial chats to the account, clear local trial data.
   * Returns the preferred session id to reopen (pending or newest imported).
   */
  migrateGuestChatsToAccount(): Observable<string | null> {
    if (!this.auth.isLoggedIn()) {
      return of(null);
    }

    const local = this.readLocal().filter((s) => s.messages?.length);
    if (!local.length) {
      this.clearGuestPromptCount();
      return of(this.consumePendingSessionId());
    }

    const payload = local.map((s) => ({
      title: s.title || 'Conversation d\'essai',
      messages: s.messages
        .filter((m) => m.role === 'user' || m.role === 'assistant')
        .map((m) => ({
          role: m.role,
          content: m.content,
          sources: m.sources ?? null,
        })),
    }));

    const pendingLocalId = this.consumePendingSessionId();

    return this.api.importGuestSessions(payload).pipe(
      tap(() => {
        this.writeLocal([]);
        this.clearGuestPromptCount();
      }),
      map((imported) => imported[0]?.id ?? null),
      catchError(() => {
        // Keep local data if import fails; still clear prompt gate for logged-in users.
        this.clearGuestPromptCount();
        return of(pendingLocalId);
      }),
      switchMap((importedId) => of(importedId)),
    );
  }

  setPendingSessionId(id: string | null): void {
    try {
      if (id) localStorage.setItem(PENDING_SESSION_KEY, id);
      else localStorage.removeItem(PENDING_SESSION_KEY);
    } catch {
      /* ignore */
    }
  }

  consumePendingSessionId(): string | null {
    try {
      const id = localStorage.getItem(PENDING_SESSION_KEY);
      localStorage.removeItem(PENDING_SESSION_KEY);
      return id;
    } catch {
      return null;
    }
  }

  private clearGuestPromptCount(): void {
    try {
      localStorage.removeItem(GUEST_PROMPT_KEY);
    } catch {
      /* ignore */
    }
  }

  private newLocalSession(firstMessage: string, updatedAt: string): LocalChatSession {
    return {
      id: crypto.randomUUID(),
      title: this.buildTitle(firstMessage),
      preview: '',
      updatedAt,
      messages: [],
    };
  }

  private buildTitle(text: string): string {
    const cleaned = text.trim().replace(/\s+/g, ' ');
    if (cleaned.length <= 80) return cleaned || 'Nouvelle conversation';
    return cleaned.slice(0, 79) + '…';
  }

  private readLocalSummaries(): ChatSessionSummary[] {
    return this.readLocal().map(({ id, title, preview, updatedAt }) => ({
      id,
      title,
      preview,
      updatedAt,
    }));
  }

  private readLocal(): LocalChatSession[] {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    try {
      return JSON.parse(raw) as LocalChatSession[];
    } catch {
      return [];
    }
  }

  private writeLocal(sessions: LocalChatSession[]): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions));
  }
}
