<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppIcon from '../components/ui/AppIcon.vue';
import PageHeading from '../components/ui/PageHeading.vue';
import ScoreBar from '../components/ui/ScoreBar.vue';
import StatusBadge from '../components/ui/StatusBadge.vue';
import { historyApi, type ResumeListItem, type ResumeStats } from '../api/history';
import { getErrorMessage } from '../api/request';
import { formatDateOnly } from '../utils/date';

const router = useRouter();
const resumes = ref<ResumeListItem[]>([]);
const stats = ref<ResumeStats | null>(null);
const loading = ref(true);
const search = ref('');
const error = ref('');

const filteredResumes = computed(() => {
  const keyword = search.value.trim().toLowerCase();
  if (!keyword) {
    return resumes.value;
  }
  return resumes.value.filter((item) => item.filename.toLowerCase().includes(keyword));
});

function formatAnalyzeStatus(status?: string) {
  switch (status) {
    case 'COMPLETED':
      return '已完成';
    case 'PROCESSING':
      return '正在分析';
    case 'FAILED':
      return '分析失败';
    case 'PENDING':
    default:
      return '待分析';
  }
}

async function loadData() {
  loading.value = true;
  error.value = '';
  try {
    const [resumeList, statData] = await Promise.all([
      historyApi.getResumes(),
      historyApi.getStatistics().catch(() => null),
    ]);
    resumes.value = resumeList;
    stats.value = statData;
  } catch (err) {
    error.value = getErrorMessage(err);
  } finally {
    loading.value = false;
  }
}

async function removeResume(id: number) {
  if (!window.confirm('确定删除这份简历吗？')) return;
  await historyApi.deleteResume(id);
  await loadData();
}

onMounted(loadData);
</script>

<template>
  <section class="page">
    <div class="toolbar-row">
      <PageHeading title="简历库" subtitle="管理您已分析过的所有简历及面试记录" />

      <label class="search-box" style="width: min(320px, 100%);">
        <AppIcon name="search" :size="26" />
        <input v-model="search" placeholder="搜索简历..." />
      </label>
    </div>

    <div
      v-if="error"
      class="surface-card surface-card--compact"
      style="border-color: rgba(239, 68, 68, 0.2); color: #dc2626;"
    >
      {{ error }}
    </div>

    <div class="table-shell">
      <table class="data-table">
        <thead>
          <tr>
            <th style="width: 48%;">简历名称</th>
            <th>上传日期</th>
            <th>AI 评分</th>
            <th>分析状态</th>
            <th style="text-align: right;">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="5"><div class="empty-state">正在加载简历列表...</div></td>
          </tr>
          <tr v-else-if="filteredResumes.length === 0">
            <td colspan="5"><div class="empty-state">暂无符合条件的简历。</div></td>
          </tr>
          <tr v-for="item in filteredResumes" v-else :key="item.id">
            <td>
              <div style="display: flex; align-items: center; gap: 14px;">
                <div class="file-chip">
                  <AppIcon name="document" :size="20" />
                </div>
                <div>
                  <div class="table-item-title">{{ item.filename }}</div>
                  <div class="table-item-subtitle">#{{ item.id.toString().padStart(7, '0') }}</div>
                </div>
              </div>
            </td>
            <td style="font-size: 14px; color: var(--text-secondary);">
              {{ formatDateOnly(item.uploadedAt) }}
            </td>
            <td><ScoreBar :score="item.latestScore ?? 0" compact /></td>
            <td>
              <StatusBadge :label="formatAnalyzeStatus(item.analyzeStatus)" />
            </td>
            <td>
              <div style="display: flex; justify-content: flex-end; gap: 8px;">
                <button class="icon-btn" type="button" @click="removeResume(item.id)">
                  <AppIcon name="trash" :size="16" />
                </button>
                <button class="icon-btn" type="button" @click="router.push(`/history/${item.id}`)">
                  <AppIcon name="chevron-right" :size="16" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
