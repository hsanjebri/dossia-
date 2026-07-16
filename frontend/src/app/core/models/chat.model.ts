import { NearbyOffice } from './office.model';

export interface ChatSuggestion {
  id: string;
  label: string;
  prompt: string;
}

export interface ChatChecklistItem {
  id: string;
  label: string;
  hint?: string;
  checked?: boolean;
}

export interface ChatSource {
  id: string;
  slug: string;
  title: string;
  sourceUrl: string | null;
  lastVerifiedAt: string | null;
  similarityScore: number;
  ministry?: string | null;
  category?: string | null;
}

export interface ChatResponse {
  answer: string;
  sources: ChatSource[];
  model: string;
  sessionId: string | null;
  nearbyOffices: NearbyOffice[];
  suggestions?: ChatSuggestion[];
  checklist?: ChatChecklistItem[];
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  sources?: ChatSource[];
  nearbyOffices?: NearbyOffice[];
  suggestions?: ChatSuggestion[];
  checklist?: ChatChecklistItem[];
  streaming?: boolean;
  feedbackSent?: boolean;
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
