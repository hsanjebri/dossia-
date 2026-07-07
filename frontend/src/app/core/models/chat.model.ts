import { NearbyOffice } from './office.model';

export interface ChatSource {
  id: string;
  slug: string;
  title: string;
  sourceUrl: string | null;
  lastVerifiedAt: string | null;
  similarityScore: number;
}

export interface ChatResponse {
  answer: string;
  sources: ChatSource[];
  model: string;
  sessionId: string | null;
  nearbyOffices: NearbyOffice[];
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  sources?: ChatSource[];
  nearbyOffices?: NearbyOffice[];
}

export interface ChatSessionSummary {
  id: string;
  title: string;
  preview: string;
  updatedAt: string;
}

export interface ChatSessionDetail {
  id: string;
  title: string;
  updatedAt: string;
  messages: StoredChatMessage[];
}

export interface StoredChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  sources: ChatSource[];
  createdAt: string;
}

export interface LocalChatSession {
  id: string;
  title: string;
  preview: string;
  updatedAt: string;
  messages: ChatMessage[];
}
