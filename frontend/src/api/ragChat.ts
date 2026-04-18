import { getErrorMessage, request } from './request';

const API_BASE_URL = import.meta.env.PROD ? '' : 'http://localhost:8080';

export interface RagChatSession {
  id: number;
  title: string;
  knowledgeBaseIds: number[];
  createdAt: string;
}

export interface RagChatSessionListItem {
  id: number;
  title: string;
  messageCount: number;
  knowledgeBaseNames: string[];
  updatedAt: string;
  isPinned: boolean;
}

export interface RagChatMessage {
  id: number;
  type: 'user' | 'assistant';
  content: string;
  createdAt: string;
}

export interface SessionKnowledgeBaseItem {
  id: number;
  name: string;
  originalFilename: string;
  fileSize: number;
  contentType: string;
  uploadedAt: string;
  lastAccessedAt: string;
  accessCount: number;
  questionCount: number;
}

export interface RagChatSessionDetail {
  id: number;
  title: string;
  knowledgeBases: SessionKnowledgeBaseItem[];
  messages: RagChatMessage[];
  createdAt: string;
  updatedAt: string;
}

export const ragChatApi = {
  createSession(knowledgeBaseIds: number[], title?: string) {
    return request.post<RagChatSession>('/api/rag-chat/sessions', { knowledgeBaseIds, title });
  },
  listSessions() {
    return request.get<RagChatSessionListItem[]>('/api/rag-chat/sessions');
  },
  getSessionDetail(sessionId: number) {
    return request.get<RagChatSessionDetail>(`/api/rag-chat/sessions/${sessionId}`);
  },
  updateSessionTitle(sessionId: number, title: string) {
    return request.put<void>(`/api/rag-chat/sessions/${sessionId}/title`, { title });
  },
  updateKnowledgeBases(sessionId: number, knowledgeBaseIds: number[]) {
    return request.put<void>(`/api/rag-chat/sessions/${sessionId}/knowledge-bases`, { knowledgeBaseIds });
  },
  togglePin(sessionId: number) {
    return request.put<void>(`/api/rag-chat/sessions/${sessionId}/pin`);
  },
  deleteSession(sessionId: number) {
    return request.delete<void>(`/api/rag-chat/sessions/${sessionId}`);
  },
  async sendMessageStream(
    sessionId: number,
    question: string,
    onMessage: (chunk: string) => void,
    onComplete: () => void,
    onError: (error: Error) => void
  ) {
    try {
      const response = await fetch(`${API_BASE_URL}/api/rag-chat/sessions/${sessionId}/messages/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question }),
      });
      if (!response.ok) {
        throw new Error(`请求失败 (${response.status})`);
      }
      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error('无法获取响应流');
      }
      const decoder = new TextDecoder();
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          onComplete();
          break;
        }
        const text = decoder.decode(value, { stream: true });
        text.split('\n').forEach((line) => {
          if (line.startsWith('data:')) {
            onMessage(line.slice(5).trimStart().replace(/\\n/g, '\n').replace(/\\r/g, '\r'));
          }
        });
      }
    } catch (error) {
      onError(new Error(getErrorMessage(error)));
    }
  },
};
