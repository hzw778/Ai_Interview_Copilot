import { request } from './request';

export interface CategoryDTO {
  key: string;
  label: string;
  priority: 'CORE' | 'NORMAL' | 'ALWAYS_ONE';
  ref?: string;
  shared?: boolean;
}

export interface DisplayDTO {
  icon: string;
  gradient: string;
  iconBg: string;
  iconColor: string;
}

export interface SkillDTO {
  id: string;
  name: string;
  description: string;
  categories: CategoryDTO[];
  isPreset: boolean;
  sourceJd: string | null;
  persona?: string;
  display?: DisplayDTO;
}

export const skillApi = {
  listSkills() {
    return request.get<SkillDTO[]>('/api/interview/skills');
  },
  getSkill(id: string) {
    return request.get<SkillDTO>(`/api/interview/skills/${id}`);
  },
  parseJd(jdText: string) {
    return request.post<CategoryDTO[]>('/api/interview/skills/parse-jd', { jdText });
  },
};
