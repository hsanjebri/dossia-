import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import {
  ChatMessage,
  ChatSessionSummary,
  ChatSource,
  LocalChatSession,
} from '../models/chat.model';
import { ChatApiService } from './chat-api.service';

const STORAGE_KEY = 'dosya.chat.sessions';

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
                role: m.role,
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
