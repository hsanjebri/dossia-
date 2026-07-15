import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ChatResponse, ChatSessionDetail, ChatSessionSummary, ChatSource } from '../models/chat.model';

@Injectable({ providedIn: 'root' })
export class ChatApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  ask(
    message: string,
    sessionId?: string | null,
    lat?: number | null,
    lng?: number | null,
    lang = 'fr',
    history?: { role: string; content: string }[],
  ): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(
      `${this.base}/chat`,
      {
        message,
        sessionId: sessionId ?? null,
        latitude: lat ?? null,
        longitude: lng ?? null,
        history: history?.length ? history : null,
      },
      { params: { lang } },
    );
  }

  listSessions(): Observable<ChatSessionSummary[]> {
    return this.http.get<ChatSessionSummary[]>(`${this.base}/chat/sessions`);
  }

  getSession(id: string): Observable<ChatSessionDetail> {
    return this.http.get<ChatSessionDetail>(`${this.base}/chat/sessions/${id}`);
  }

  deleteSession(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/chat/sessions/${id}`);
  }

  importGuestSessions(
    sessions: {
      title: string;
      messages: { role: string; content: string; sources?: ChatSource[] | null }[];
    }[],
  ): Observable<ChatSessionSummary[]> {
    return this.http.post<ChatSessionSummary[]>(`${this.base}/chat/sessions/import`, sessions);
  }
}
