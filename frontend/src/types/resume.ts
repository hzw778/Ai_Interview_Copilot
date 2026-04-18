export interface ResumeAnalysisResponse {
  overallScore: number;
  scoreDetail: ScoreDetail;
  summary: string;
  strengths: string[];
  suggestions: Suggestion[];
  originalText: string;
}

export interface StorageInfo {
  fileKey: string;
  fileUrl: string;
  resumeId?: number;
}

export interface UploadResponse {
  analysis?: ResumeAnalysisResponse;
  storage: StorageInfo;
  duplicate?: boolean;
  message?: string;
  resume?: {
    id: number;
    filename: string;
    analyzeStatus: string;
  };
}

export interface ScoreDetail {
  contentScore: number;
  structureScore: number;
  skillMatchScore: number;
  expressionScore: number;
  projectScore: number;
}

export interface Suggestion {
  category: string;
  priority: '高' | '中' | '低';
  issue: string;
  recommendation: string;
}

export interface ApiError {
  error: string;
  detectedType?: string;
  allowedTypes?: string[];
}
