import { request } from './request';
import type {
  CreateInterviewRequest,
  CurrentQuestionResponse,
  InterviewReport,
  InterviewSession,
  SubmitAnswerRequest,
  SubmitAnswerResponse,
} from '../types/interview';

export interface TextSessionMeta {
  sessionId: string;
  skillId: string;
  difficulty: string;
  resumeId: number | null;
  totalQuestions: number;
  status: string;
  evaluateStatus: string | null;
  evaluateError: string | null;
  overallScore: number | null;
  createdAt: string;
  completedAt: string | null;
}

export const interviewApi = {
  listSessions() {
    return request.get<TextSessionMeta[]>('/api/interview/sessions');
  },
  createSession(req: CreateInterviewRequest) {
    return request.post<InterviewSession>('/api/interview/sessions', req, { timeout: 180000 });
  },
  getSession(sessionId: string) {
    return request.get<InterviewSession>(`/api/interview/sessions/${sessionId}`);
  },
  getCurrentQuestion(sessionId: string) {
    return request.get<CurrentQuestionResponse>(`/api/interview/sessions/${sessionId}/question`);
  },
  submitAnswer(req: SubmitAnswerRequest) {
    return request.post<SubmitAnswerResponse>(`/api/interview/sessions/${req.sessionId}/answers`, {
      questionIndex: req.questionIndex,
      answer: req.answer,
    }, { timeout: 180000 });
  },
  getReport(sessionId: string) {
    return request.get<InterviewReport>(`/api/interview/sessions/${sessionId}/report`, { timeout: 180000 });
  },
  async findUnfinishedSession(resumeId: number) {
    try {
      return await request.get<InterviewSession>(`/api/interview/sessions/unfinished/${resumeId}`);
    } catch {
      return null;
    }
  },
  saveAnswer(req: SubmitAnswerRequest) {
    return request.put<void>(`/api/interview/sessions/${req.sessionId}/answers`, {
      questionIndex: req.questionIndex,
      answer: req.answer,
    });
  },
  completeInterview(sessionId: string) {
    return request.post<void>(`/api/interview/sessions/${sessionId}/complete`);
  },
};
