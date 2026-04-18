import { createRouter, createWebHistory } from 'vue-router';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/history' },
    { path: '/upload', component: () => import('../views/UploadView.vue') },
    { path: '/history', component: () => import('../views/ResumeHistoryView.vue') },
    { path: '/history/:resumeId', component: () => import('../views/ResumeDetailView.vue') },
    { path: '/interview-hub', component: () => import('../views/InterviewHubView.vue') },
    { path: '/interviews', component: () => import('../views/InterviewHistoryView.vue') },
    { path: '/interviews/:sessionId', component: () => import('../views/InterviewDetailView.vue') },
    { path: '/interview', component: () => import('../views/InterviewView.vue') },
    { path: '/knowledgebase', component: () => import('../views/KnowledgeBaseManageView.vue') },
    { path: '/knowledgebase/upload', component: () => import('../views/KnowledgeBaseUploadView.vue') },
    { path: '/knowledgebase/chat', component: () => import('../views/KnowledgeBaseChatView.vue') },
  ],
  scrollBehavior() {
    return { top: 0 };
  },
});

export default router;
