<script setup lang="ts">
import { ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import AppIcon from '../ui/AppIcon.vue';

const route = useRoute();

type IconName =
  | 'brand'
  | 'moon'
  | 'upload'
  | 'files'
  | 'wand'
  | 'users'
  | 'database'
  | 'chat'
  | 'chevron-right'
  | 'chevron-left'
  | 'search'
  | 'document'
  | 'download'
  | 'trash'
  | 'check-circle'
  | 'clock'
  | 'chart'
  | 'send'
  | 'plus'
  | 'pin'
  | 'edit'
  | 'eye'
  | 'target'
  | 'filter'
  | 'mic'
  | 'arrow-up'
  | 'menu';

const sections: Array<{
  title: string;
  items: Array<{ to: string; title: string; desc: string; icon: IconName; matches?: string[] }>;
}> = [
  {
    title: '简历与面试',
    items: [
      { to: '/upload', title: '上传简历', desc: 'AI 分析简历', icon: 'upload' },
      { to: '/history', title: '简历库', desc: '管理所有简历', icon: 'files', matches: ['/history'] },
      { to: '/interview-hub', title: '模拟面试', desc: '文字面试练习', icon: 'wand', matches: ['/interview-hub', '/interview'] },
      { to: '/interviews', title: '面试记录', desc: '查看面试历史', icon: 'users', matches: ['/interviews'] },
    ],
  },
  {
    title: '知识库',
    items: [
      { to: '/knowledgebase', title: '知识库管理', desc: '管理知识文档', icon: 'database', matches: ['/knowledgebase', '/knowledgebase/upload'] },
      { to: '/knowledgebase/chat', title: '问答助手', desc: '基于知识库问答', icon: 'chat', matches: ['/knowledgebase/chat'] },
    ],
  },
];

const isDark = ref(document.documentElement.classList.contains('dark'));

function isActive(path: string) {
  const item = sections.flatMap((section) => section.items).find((nav) => nav.to === path);
  const matches = item?.matches ?? [path];
  return matches.some((matchPath) => {
    if (route.path === matchPath) return true;
    if (matchPath === '/knowledgebase') {
      return route.path === '/knowledgebase/upload';
    }
    return route.path.startsWith(`${matchPath}/`);
  });
}

function toggleTheme() {
  const root = document.documentElement;
  const dark = root.classList.toggle('dark');
  localStorage.setItem('theme', dark ? 'dark' : 'light');
  isDark.value = dark;
}
</script>

<template>
  <aside class="sidebar-shell">
    <div class="sidebar-brand">
      <div class="sidebar-brand__mark">
        <AppIcon name="brand" :size="24" />
      </div>
      <div>
        <h1 class="sidebar-brand__title">AI Interview</h1>
        <p class="sidebar-brand__subtitle">智能面试助手</p>
      </div>
    </div>

    <button class="sidebar-theme" type="button" @click="toggleTheme">
      <AppIcon name="moon" :size="20" />
      <span>{{ isDark ? '浅色模式' : '深色模式' }}</span>
    </button>

    <div v-for="section in sections" :key="section.title" class="sidebar-section">
      <p class="sidebar-section__title">{{ section.title }}</p>
      <RouterLink
        v-for="item in section.items"
        :key="item.to"
        :to="item.to"
        class="sidebar-link"
        :class="{ 'sidebar-link--active': isActive(item.to) }"
      >
        <div class="sidebar-link__icon">
          <AppIcon :name="item.icon" :size="22" />
        </div>
        <div class="sidebar-link__text">
          <p class="sidebar-link__title">{{ item.title }}</p>
          <p class="sidebar-link__desc">{{ item.desc }}</p>
        </div>
        <AppIcon v-if="isActive(item.to)" class="sidebar-link__arrow" name="chevron-right" :size="18" />
      </RouterLink>
    </div>

    <div class="sidebar-footer">
      <p class="sidebar-footer__title">AI 面试助手 v1.0</p>
      <p class="sidebar-footer__desc">Powered by AI</p>
    </div>
  </aside>
</template>
