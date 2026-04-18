<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppIcon from '../components/ui/AppIcon.vue';
import MetricCard from '../components/ui/MetricCard.vue';
import PageHeading from '../components/ui/PageHeading.vue';
import ScoreBar from '../components/ui/ScoreBar.vue';
import StatusBadge from '../components/ui/StatusBadge.vue';
import { historyApi } from '../api/history';
import { interviewApi, type TextSessionMeta } from '../api/interview';
import { formatDateOnly } from '../utils/date';

const router = useRouter();
const items = ref<TextSessionMeta[]>([]);
const loading = ref(true);
const search = ref('');

const filteredItems = computed(() => {
  const keyword = search.value.trim().toLowerCase();
  if (!keyword) return items.value;
  return items.value.filter((item) => item.sessionId.toLowerCase().includes(keyword));
});

const completedCount = computed(() => items.value.filter((item) => item.status === 'COMPLETED' || item.status === 'EVALUATED').length);
const averageScore = computed(() => {
  const scored = items.value.filter((item) => typeof item.overallScore === 'number');
  if (scored.length === 0) return 0;
  return Math.round(scored.reduce((sum, item) => sum + Number(item.overallScore || 0), 0) / scored.length);
});

async function loadData() {
  loading.value = true;
  items.value = await interviewApi.listSessions().catch(() => []);
  loading.value = false;
}

async function removeSession(sessionId: string) {
  if (!window.confirm('确定删除这条面试记录吗？')) {
    return;
  }
  await historyApi.deleteInterview(sessionId);
  await loadData();
}

onMounted(loadData);
</script>

<template>
  <section class="page">
    <div class="toolbar-row">
      <PageHeading
        icon="users"
        title="面试记录"
        subtitle="查看和管理所有模拟面试记录"
      />

      <label class="search-box" style="width: min(360px, 100%);">
        <AppIcon name="search" :size="26" />
        <input v-model="search" placeholder="搜索简历名称..." />
      </label>
    </div>

    <div class="grid-metrics">
      <MetricCard icon="users" title="面试总数" :value="items.length" />
      <MetricCard icon="check-circle" title="已完成" :value="completedCount" tone="green" />
      <MetricCard icon="chart" title="平均分数" :value="`${averageScore} 分`" />
    </div>

    <div class="table-shell">
      <table class="data-table">
        <thead>
          <tr>
            <th style="width: 42%;">关联简历</th>
            <th>题目数</th>
            <th>状态</th>
            <th>得分</th>
            <th>创建时间</th>
            <th style="text-align: right;">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="6"><div class="empty-state">正在加载面试记录...</div></td>
          </tr>
          <tr v-else-if="filteredItems.length === 0">
            <td colspan="6"><div class="empty-state">还没有任何面试记录。</div></td>
          </tr>
          <tr v-for="item in filteredItems" v-else :key="item.sessionId">
            <td>
              <div style="display: flex; align-items: center; gap: 18px;">
                <div class="file-chip">
                  <AppIcon name="document" :size="24" />
                </div>
                <div>
                  <div class="table-item-title">{{ item.resumeId ? `简历 #${item.resumeId}` : '通用面试会话' }}</div>
                  <div class="table-item-subtitle">#{{ item.sessionId.slice(-8) }}</div>
                </div>
              </div>
            </td>
            <td>
              <span class="status-badge">{{ item.totalQuestions }} 题</span>
            </td>
            <td>
              <StatusBadge :label="item.status === 'CREATED' ? '已创建' : item.status === 'IN_PROGRESS' ? '进行中' : '已完成'" />
            </td>
            <td>
              <ScoreBar :score="item.overallScore ?? 0" compact />
            </td>
            <td style="font-size: 18px; color: #63789b;">{{ formatDateOnly(item.createdAt) }}</td>
            <td>
              <div style="display: flex; justify-content: flex-end; gap: 10px;">
                <button v-if="item.overallScore !== null" class="icon-btn" type="button" @click="router.push(`/interviews/${item.sessionId}`)">
                  <AppIcon name="download" :size="18" />
                </button>
                <button class="icon-btn" type="button" @click="removeSession(item.sessionId)">
                  <AppIcon name="trash" :size="18" />
                </button>
                <button class="icon-btn" type="button" @click="router.push(`/interviews/${item.sessionId}`)">
                  <AppIcon name="chevron-right" :size="18" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
