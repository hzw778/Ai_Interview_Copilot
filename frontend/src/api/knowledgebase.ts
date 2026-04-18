import axios from 'axios';
import { getErrorMessage, request } from './request';

const API_BASE_URL = import.meta.env.PROD ? '' : 'http://localhost:8080';

export type VectorStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type SortOption = 'time' | 'size' | 'access' | 'question';

export interface KnowledgeBaseItem {
  id: number;
  name: string;
  category: string | null;
  originalFilename: string;
  fileSize: number;
  contentType: string;
  uploadedAt: string;
  lastAccessedAt: string;
  accessCount: number;
  questionCount: number;
  vectorStatus: VectorStatus;
  vectorError: string | null;
  chunkCount: number;
}

export interface KnowledgeBaseStats {
  totalCount: number;
  totalQuestionCount: number;
  totalAccessCount: number;
  completedCount: number;
  processingCount: number;
}

export interface UploadKnowledgeBaseResponse {
  knowledgeBase: {
    id: number;
    name: string;
    category: string;
    fileSize: number;
    contentLength: number;
  };
  storage: {
    fileKey: string;
    fileUrl: string;
  };
  duplicate: boolean;
}

export interface QueryRequest {
  knowledgeBaseIds: number[];
  question: string;
}

export interface QueryResponse {
  answer: string;
  knowledgeBaseId: number;
  knowledgeBaseName: string;
}

export const knowledgeBaseApi = {
  uploadKnowledgeBase(file: File, name?: string, category?: string) {
    const formData = new FormData();
    formData.append('file', file);
    if (name) formData.append('name', name);
    if (category) formData.append('category', category);
    return request.upload<UploadKnowledgeBaseResponse>('/api/knowledgebase/upload', formData);
  },
  async downloadKnowledgeBase(id: number) {
    const response = await axios.get(`${API_BASE_URL}/api/knowledgebase/${id}/download`, { responseType: 'blob' });
    return response.data as Blob;
  },
  async getAllKnowledgeBases(sortBy?: SortOption, vectorStatus?: VectorStatus) {
    const params = new URLSearchParams();
    if (sortBy) params.append('sortBy', sortBy);
    if (vectorStatus) params.append('vectorStatus', vectorStatus);
    const suffix = params.toString() ? `?${params}` : '';
    return request.get<KnowledgeBaseItem[]>(`/api/knowledgebase/list${suffix}`);
  },
  getKnowledgeBase(id: number) {
    return request.get<KnowledgeBaseItem>(`/api/knowledgebase/${id}`);
  },
  deleteKnowledgeBase(id: number) {
    return request.delete<void>(`/api/knowledgebase/${id}`);
  },
  getAllCategories() {
    return request.get<string[]>('/api/knowledgebase/categories');
  },
  getByCategory(category: string) {
    return request.get<KnowledgeBaseItem[]>(`/api/knowledgebase/category/${encodeURIComponent(category)}`);
  },
  getUncategorized() {
    return request.get<KnowledgeBaseItem[]>('/api/knowledgebase/uncategorized');
  },
  updateCategory(id: number, category: string | null) {
    return request.put<void>(`/api/knowledgebase/${id}/category`, { category });
  },
  search(keyword: string) {
    return request.get<KnowledgeBaseItem[]>(`/api/knowledgebase/search?keyword=${encodeURIComponent(keyword)}`);
  },
  getStatistics() {
    return request.get<KnowledgeBaseStats>('/api/knowledgebase/stats');
  },
  revectorize(id: number) {
    return request.post<void>(`/api/knowledgebase/${id}/revectorize`);
  },
  queryKnowledgeBase(req: QueryRequest) {
    return request.post<QueryResponse>('/api/knowledgebase/query', req, { timeout: 180000 });
  },
  async queryKnowledgeBaseStream(
    req: QueryRequest,
    onMessage: (chunk: string) => void,
    onComplete: () => void,
    onError: (error: Error) => void
  ) {
    try {
      const response = await fetch(`${API_BASE_URL}/api/knowledgebase/query/stream`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(req),
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
        const chunk = decoder.decode(value, { stream: true });
        chunk.split('\n').forEach((line) => {
          if (line.startsWith('data:')) {
            onMessage(line.slice(5).trimStart());
          }
        });
      }
    } catch (error) {
      onError(new Error(getErrorMessage(error)));
    }
  },
};
