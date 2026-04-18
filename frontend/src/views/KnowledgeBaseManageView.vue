<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppIcon from '../components/ui/AppIcon.vue';
import MetricCard from '../components/ui/MetricCard.vue';
import PageHeading from '../components/ui/PageHeading.vue';
import StatusBadge from '../components/ui/StatusBadge.vue';
import { knowledgeBaseApi, type KnowledgeBaseItem, type KnowledgeBaseStats, type SortOption, type VectorStatus } from '../api/knowledgebase';
import { getErrorMessage } from '../api/request';
import { formatDateTime } from '../utils/date';
import { formatFileSize } from '../utils/format';

const router = useRouter();
const stats = ref<KnowledgeBaseStats | null>(null);
const items = ref<KnowledgeBaseItem[]>([]);
const loading = ref(true);
const error = ref('');
const search = ref('');
const sortBy = ref<SortOption>('time');
const vectorStatus = ref<VectorStatus | ''>('');

const filteredItems = computed(() => {
  const keyword = search.value.trim().toLowerCase();
  return items.value.filter((item) => {
    if (!keyword) return true;
    return item.name.toLowerCase().includes(keyword) || item.originalFilename.toLowerCase().includes(keyword);
  });
});

async function loadData() {
  loading.value = true;
  error.value = '';
  try {
    const [list, statData] = await Promise.all([
      knowledgeBaseApi.getAllKnowledgeBases(sortBy.value, vectorStatus.value || undefined),
      knowledgeBaseApi.getStatistics().catch(() => null),
    ]);
    items.value = list;
    stats.value = statData;
  } catch (err) {
    error.value = getErrorMessage(err);
  } finally {
    loading.value = false;
  }
}

async function updateCategory(item: KnowledgeBaseItem) {
  const next = window.prompt('请输入新的分类名称', item.category || '');
  if (next === null) return;
  await knowledgeBaseApi.updateCategory(item.id, next.trim() || null);
  await loadData();
}

async function removeItem(id: number) {
  if (!window.confirm('确定删除这份知识库文档吗？')) return;
  await knowledgeBaseApi.deleteKnowledgeBase(id);
  await loadData();
}

async function downloadItem(item: KnowledgeBaseItem) {
  const blob = await knowledgeBaseApi.downloadKnowledgeBase(item.id);
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = item.originalFilename;
  link.click();
  URL.revokeObjectURL(url);
}

onMounted(loadData);
</script>

<template>
  <section class="page">
    <div class="toolbar-row">
      <PageHeading
        icon="database"
        title="知识库管理"
        subtitle="管理知识库文件，查看使用统计"
      />

      <div class="toolbar-actions">
        <button class="action-btn action-btn--primary" type="button" @click="router.push('/knowledgebase/upload')">
          <AppIcon name="upload" :size="20" />
          <span>上传知识库</span>
        </button>
        <button class="action-btn" type="button" @click="router.push('/knowledgebase/chat')">
          <AppIcon name="chat" :size="20" />
          <span>问答助手</span>
        </button>
      </div>
    </div>

    <div class="grid-metrics">
      <MetricCard icon="database" title="知识库总数" :value="stats?.totalCount ?? items.length" />
      <MetricCard icon="chat" title="总提问次数" :value="stats?.totalQuestionCount ?? 0" />
      <MetricCard icon="eye" title="总访问次数" :value="stats?.totalAccessCount ?? 0" tone="green" />
    </div>

    <div class="surface-card surface-card--padded">
      <div class="toolbar-row">
        <label class="search-box" style="flex: 1; min-width: 320px;">
          <AppIcon name="search" :size="24" />
          <input v-model="search" placeholder="搜索知识库名称..." />
        </label>

        <div class="toolbar-actions">
          <select v-model="sortBy" class="select-field" style="width: 176px;" @change="loadData">
            <option value="time">按时间排序</option>
            <option value="size">按大小排序</option>
            <option value="access">按访问排序</option>
            <option value="question">按提问排序</option>
          </select>
          <select v-model="vectorStatus" class="select-field" style="width: 156px;" @change="loadData">
            <option value="">全部状态</option>
            <option value="COMPLETED">已完成</option>
            <option value="PROCESSING">处理中</option>
            <option value="PENDING">待处理</option>
            <option value="FAILED">失败</option>
          </select>
        </div>
      </div>
    </div>

    <div v-if="error" class="surface-card surface-card--compact" style="border-color: rgba(255,74,88,.18); color: #db4252;">
      {{ error }}
    </div>

    <div class="table-shell">
      <table class="data-table">
        <thead>
          <tr>
            <th style="width: 34%;">名称</th>
            <th>分类</th>
            <th>大小</th>
            <th>状态</th>
            <th>提问</th>
            <th>上传时间</th>
            <th style="text-align: right;">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7"><div class="empty-state">正在加载知识库列表...</div></td>
          </tr>
          <tr v-else-if="filteredItems.length === 0">
            <td colspan="7"><div class="empty-state">暂无知识库文档。</div></td>
          </tr>
          <tr v-for="item in filteredItems" v-else :key="item.id">
            <td>
              <div style="display: flex; align-items: center; gap: 18px;">
                <div class="file-chip">
                  <AppIcon name="document" :size="24" />
                </div>
                <div>
                  <div class="table-item-title">{{ item.name }}</div>
                  <div class="table-item-subtitle">{{ item.originalFilename }}</div>
                </div>
              </div>
            </td>
            <td><span class="status-badge">{{ item.category || '未分类' }}</span></td>
            <td style="font-size: 18px; color: #63789b;">{{ formatFileSize(item.fileSize) }}</td>
            <td><StatusBadge :label="item.vectorStatus" /></td>
            <td style="font-size: 18px;">{{ item.questionCount }}</td>
            <td style="font-size: 18px; color: #63789b;">{{ formatDateTime(item.uploadedAt) }}</td>
            <td>
              <div style="display: flex; justify-content: flex-end; gap: 10px;">
                <button class="icon-btn" type="button" @click="downloadItem(item)">
                  <AppIcon name="download" :size="18" />
                </button>
                <button class="icon-btn" type="button" @click="updateCategory(item)">
                  <AppIcon name="edit" :size="18" />
                </button>
                <button class="icon-btn" type="button" @click="removeItem(item.id)">
                  <AppIcon name="trash" :size="18" />
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
