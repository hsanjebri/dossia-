export type ChatAgentLang = 'fr' | 'ar' | 'tn' | 'en';

export interface ChatAgent {
  id: 'sofia' | 'yasmine' | 'alex';
  name: string;
  tagline: string;
  lang: ChatAgentLang;
  speechLang: string;
  monogram: string;
  accent: string;
}

export const CHAT_AGENTS: ChatAgent[] = [
  {
    id: 'sofia',
    name: 'Sofia',
    tagline: 'Français clair · démarches pas à pas',
    lang: 'fr',
    speechLang: 'fr-FR',
    monogram: 'S',
    accent: '#c8922a',
  },
  {
    id: 'yasmine',
    name: 'Yasmine',
    tagline: 'دارجة تونسية · نعاونك في الأوراق',
    lang: 'tn',
    speechLang: 'ar-TN',
    monogram: 'ي',
    accent: '#3b6d11',
  },
  {
    id: 'alex',
    name: 'Alex',
    tagline: 'Clear English · civic guidance',
    lang: 'en',
    speechLang: 'en-US',
    monogram: 'A',
    accent: '#446274',
  },
];

export const AGENT_STORAGE_KEY = 'dosya_chat_agent';

export function findChatAgent(id: string | null | undefined): ChatAgent | null {
  if (!id) return null;
  return CHAT_AGENTS.find((a) => a.id === id) ?? null;
}
