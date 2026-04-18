<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppIcon from '../components/ui/AppIcon.vue';
import ScoreBar from '../components/ui/ScoreBar.vue';
import StatusBadge from '../components/ui/StatusBadge.vue';
import { historyApi, type AnalysisItem, type ResumeDetail } from '../api/history';
import { getErrorMessage } from '../api/request';
import { formatDateOnly, formatDateTime } from '../utils/date';
import { downloadBlob, formatFileSize } from '../utils/format';

type TabKey = 'analysis' | 'interviews';

const route = useRoute();
const router = useRouter();
const detail = ref<ResumeDetail | null>(null);
const loading = ref(true);
const busy = ref(false);
const error = ref('');
const activeTab = ref<TabKey>('analysis');
let pollTimer: number | null = null;

const resumeId = computed(() => Number(route.params.resumeId));
const latestAnalysis = computed<AnalysisItem | null>(() => detail.value?.analyses?.[0] ?? null);
const analyzeStatus = computed(() => detail.value?.analyzeStatus ?? 'PENDING');
const isAnalysisPending = computed(() => {
  if (!detail.value) return false;
  return analyzeStatus.value === 'PENDING'
    || analyzeStatus.value === 'PROCESSING'
    || (!latestAnalysis.value && analyzeStatus.value !== 'FAILED');
});
const isRefreshingAnalysis = computed(() => isAnalysisPending.value && !!latestAnalysis.value);
const isAnalysisFailed = computed(() => !!detail.value && analyzeStatus.value === 'FAILED');
const pendingTitle = computed(() => analyzeStatus.value === 'PROCESSING' ? 'AI 正在深入分析简历' : '简历已上传，正在排队分析');
const pendingDescription = computed(() => analyzeStatus.value === 'PROCESSING'
  ? '系统正在抽取关键信息、生成评分和改进建议，通常只需要几十秒。'
  : '我们已经收到你的简历，正在为这份简历创建分析任务，很快就会返回正式结果。');
const suggestionItems = computed(() => {
  const raw = latestAnalysis.value?.suggestions;
  if (!Array.isArray(raw)) return [];
  return raw.map((item) => item as { category?: string; priority?: string; issue?: string; recommendation?: string });
});

function clearPolling() {
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer);
    pollTimer = null;
  }
}

function shouldPoll(currentDetail: ResumeDetail | null) {
  if (!currentDetail) {
    return false;
  }
  if (currentDetail.analyzeStatus === 'FAILED') {
    return false;
  }
  if (currentDetail.analyzeStatus === 'COMPLETED' && currentDetail.analyses.length > 0) {
    return false;
  }
  return currentDetail.analyzeStatus === 'PENDING'
    || currentDetail.analyzeStatus === 'PROCESSING'
    || currentDetail.analyses.length === 0;
}

function schedulePolling(currentDetail: ResumeDetail | null = detail.value) {
  clearPolling();
  if (!shouldPoll(currentDetail)) {
    return;
  }
  pollTimer = window.setTimeout(async () => {
    try {
      const latest = await historyApi.getResumeDetail(resumeId.value);
      detail.value = latest;
      schedulePolling(latest);
    } catch (err) {
      console.warn('resume detail polling failed', err);
      schedulePolling(currentDetail);
    }
  }, 2500);
}

async function loadData(showLoading = true) {
  if (showLoading) {
    loading.value = true;
  }
  if (!detail.value) {
    error.value = '';
  }
  try {
    const latest = await historyApi.getResumeDetail(resumeId.value);
    detail.value = latest;
    error.value = '';
    schedulePolling(latest);
  } catch (err) {
    error.value = getErrorMessage(err);
    clearPolling();
  } finally {
    if (showLoading) {
      loading.value = false;
    }
  }
}

async function exportPdf() {
  if (!detail.value) return;
  busy.value = true;
  try {
    const blob = await historyApi.exportAnalysisPdf(detail.value.id);
    downloadBlob(blob, `${detail.value.filename}-analysis.pdf`);
  } finally {
    busy.value = false;
  }
}

async function reanalyze() {
  busy.value = true;
  try {
    await historyApi.reanalyze(resumeId.value);
    await loadData(false);
  } finally {
    busy.value = false;
  }
}

onMounted(loadData);
onBeforeUnmount(clearPolling);
</script>

<template>
  <section class="page">
    <div class="detail-topbar">
      <div style="display: flex; align-items: flex-start; gap: 16px;">
        <button class="back-btn" type="button" @click="router.push('/history')">
          <AppIcon name="chevron-left" :size="20" />
        </button>
        <div>
          <h1 class="detail-title">{{ detail?.filename || '简历详情' }}</h1>
          <div class="subtle-row" style="margin-top: 8px;">
            <AppIcon name="clock" :size="16" />
            <span>上传于 {{ detail ? formatDateOnly(detail.uploadedAt) : '--' }}</span>
            <span v-if="detail">·</span>
            <span v-if="detail">{{ formatFileSize(detail.fileSize) }}</span>
            <StatusBadge v-if="detail" :label="detail.analyzeStatus" />
          </div>
        </div>
      </div>

      <div class="toolbar-actions">
        <button class="action-btn" type="button" :disabled="busy || isAnalysisPending" @click="reanalyze">
          <AppIcon name="refresh" :size="18" />
          <span>{{ isAnalysisPending ? '分析中...' : '重新分析' }}</span>
        </button>
        <button class="action-btn action-btn--primary" type="button" @click="router.push({ path: '/interview-hub', query: { resumeId: String(resumeId) } })">
          <AppIcon name="wand" :size="18" />
          <span>开始模拟面试</span>
        </button>
      </div>
    </div>

    <div class="tab-strip">
      <button class="tab-strip__item" :class="{ 'tab-strip__item--active': activeTab === 'analysis' }" type="button" @click="activeTab = 'analysis'">
        <AppIcon name="document" :size="18" />
        <span>简历分析</span>
      </button>
      <button class="tab-strip__item" :class="{ 'tab-strip__item--active': activeTab === 'interviews' }" type="button" @click="activeTab = 'interviews'">
        <AppIcon name="users" :size="18" />
        <span>面试记录</span>
        <span class="tag-pill" style="padding-inline: 10px;">{{ detail?.interviews.length ?? 0 }}</span>
      </button>
    </div>

    <div v-if="loading" class="surface-card surface-card--padded"><div class="empty-state">正在加载详情...</div></div>
    <div v-else-if="error" class="surface-card surface-card--compact" style="border-color: rgba(255,74,88,.18); color: #db4252;">{{ error }}</div>

    <template v-else-if="detail && activeTab === 'analysis'">
      <div v-if="isAnalysisPending && !latestAnalysis" class="analysis-pending surface-card surface-card--padded">
        <div class="analysis-pending__hero">
          <div class="analysis-pending__visual">
            <span class="analysis-pending__ring"></span>
            <span class="analysis-pending__ring analysis-pending__ring--delay"></span>
            <span class="analysis-pending__core"></span>
          </div>
          <div class="analysis-pending__content">
            <p class="analysis-pending__eyebrow">ANALYSIS IN PROGRESS</p>
            <h2 class="analysis-pending__title">{{ pendingTitle }}</h2>
            <p class="analysis-pending__text">{{ pendingDescription }}</p>
            <div class="analysis-pending__steps">
              <div class="analysis-step analysis-step--active">
                <span class="analysis-step__dot"></span>
                <span>解析简历正文</span>
              </div>
              <div class="analysis-step analysis-step--active">
                <span class="analysis-step__dot"></span>
                <span>提取项目与技能重点</span>
              </div>
              <div class="analysis-step">
                <span class="analysis-step__dot"></span>
                <span>生成评分与改进建议</span>
              </div>
            </div>
          </div>
        </div>

        <div class="analysis-pending__skeletons">
          <div class="analysis-skeleton analysis-skeleton--wide"></div>
          <div class="analysis-skeleton"></div>
          <div class="analysis-skeleton"></div>
          <div class="analysis-skeleton analysis-skeleton--card"></div>
        </div>
      </div>

      <div
        v-else-if="isRefreshingAnalysis"
        class="surface-card surface-card--compact"
        style="border-color: rgba(99,102,241,.16); background: rgba(241,243,255,.9); color: #4a57c7;"
      >
        已触发新的分析任务，页面会自动刷新为最新结果。
      </div>

      <div
        v-else-if="isAnalysisFailed && !latestAnalysis"
        class="surface-card surface-card--padded"
        style="border-color: rgba(255,74,88,.18); background: rgba(255,247,248,.96);"
      >
        <div class="section-title" style="color: #d4384c;">本次分析暂未成功</div>
        <p class="table-item-subtitle" style="margin-top: 10px; font-size: 14px; color: #8b4452;">
          简历内容已经保存成功，你可以直接重新发起分析，系统会自动继续处理。
        </p>
        <div class="toolbar-actions" style="margin-top: 16px;">
          <button class="action-btn action-btn--primary" type="button" :disabled="busy" @click="reanalyze">
            重新分析
          </button>
        </div>
      </div>

      <div v-if="!isAnalysisPending || latestAnalysis" class="score-summary-grid">
        <div class="surface-card surface-card--padded">
          <div class="toolbar-row" style="align-items: center;">
            <div class="section-title" style="margin-bottom: 0;">核心评价</div>
            <button class="action-btn" type="button" :disabled="busy" @click="exportPdf">
              <AppIcon name="download" :size="18" />
              <span>导出分析报告</span>
            </button>
          </div>

          <div v-if="latestAnalysis" class="panel-note" style="margin-top: 20px;">
            {{ latestAnalysis.summary }}
          </div>
          <div v-else class="panel-note" style="margin-top: 20px;">
            分析结果生成后，这里会展示简历总结、评分和优化建议。
          </div>

          <div class="score-highlight" style="margin-top: 18px;">
            <div class="score-highlight__box">
              <div class="section-subtitle">总分</div>
              <div class="mini-stat__value" style="font-size: 52px;">{{ latestAnalysis?.overallScore ?? '--' }}<span style="font-size: 20px; color: #7c90b2;"> /100</span></div>
            </div>
            <div class="score-highlight__box">
              <div class="section-subtitle">分析时间</div>
              <div class="mini-stat__value" style="font-size: 28px;">{{ latestAnalysis ? formatDateTime(latestAnalysis.analyzedAt) : '--' }}</div>
            </div>
          </div>

          <div class="surface-card surface-card--compact" style="margin-top: 18px; background: rgba(226, 255, 241, 0.76); border-color: rgba(45, 190, 124, 0.12);">
            <div class="section-title" style="margin-bottom: 14px; color: #169d67;">优势亮点</div>
            <div class="chip-list">
              <span v-for="item in latestAnalysis?.strengths || []" :key="item" class="tag-pill" style="background: rgba(34, 197, 94, 0.12); color: #119f66;">
                {{ item }}
              </span>
            </div>
          </div>
        </div>

        <div class="surface-card surface-card--padded">
          <div class="section-title">多维度评分</div>
          <div class="soft-block" style="margin-top: 12px;">
            <div style="display: grid; gap: 18px;">
              <div>
                <div class="toolbar-row">
                  <span>项目经验</span>
                  <span> {{ latestAnalysis?.projectScore ?? 0 }}/15 </span>
                </div>
                <ScoreBar :score="latestAnalysis?.projectScore ?? 0" :max="15" />
              </div>
              <div>
                <div class="toolbar-row">
                  <span>技能匹配</span>
                  <span> {{ latestAnalysis?.skillMatchScore ?? 0 }}/25 </span>
                </div>
                <ScoreBar :score="latestAnalysis?.skillMatchScore ?? 0" :max="25" />
              </div>
              <div>
                <div class="toolbar-row">
                  <span>内容完整性</span>
                  <span> {{ latestAnalysis?.contentScore ?? 0 }}/25 </span>
                </div>
                <ScoreBar :score="latestAnalysis?.contentScore ?? 0" :max="25" />
              </div>
              <div>
                <div class="toolbar-row">
                  <span>结构清晰度</span>
                  <span> {{ latestAnalysis?.structureScore ?? 0 }}/20 </span>
                </div>
                <ScoreBar :score="latestAnalysis?.structureScore ?? 0" :max="20" />
              </div>
              <div>
                <div class="toolbar-row">
                  <span>表达专业性</span>
                  <span> {{ latestAnalysis?.expressionScore ?? 0 }}/15 </span>
                </div>
                <ScoreBar :score="latestAnalysis?.expressionScore ?? 0" :max="15" />
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="(!isAnalysisPending || latestAnalysis) && suggestionItems.length" class="surface-card surface-card--padded">
        <div class="section-title">改进建议</div>
        <div class="split-grid" style="grid-template-columns: 1fr; gap: 16px;">
          <div
            v-for="(item, index) in suggestionItems"
            :key="`${item.category}-${index}`"
            class="surface-card surface-card--compact"
            :style="item.priority?.includes('高')
              ? 'border-color: rgba(255,74,88,.25); background: rgba(255,245,246,.96);'
              : item.priority?.includes('中')
                ? 'border-color: rgba(255,161,0,.24); background: rgba(255,249,238,.96);'
                : 'border-color: rgba(95,91,248,.18); background: rgba(244,245,255,.98);'"
          >
            <div class="toolbar-row" style="align-items: flex-start;">
              <div>
                <div class="toolbar-actions" style="gap: 10px;">
                  <span class="tag-pill">{{ item.priority || '建议' }}</span>
                  <span class="tag-pill" style="background: rgba(95,91,248,.08);">{{ item.category || '通用' }}</span>
                </div>
                <p class="table-item-title" style="font-size: 20px; margin-top: 16px;">{{ item.issue }}</p>
                <p class="table-item-subtitle" style="margin-top: 12px; font-size: 16px; line-height: 1.9; color: #5c6f90;">
                  {{ item.recommendation }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="!isAnalysisPending || latestAnalysis" class="surface-card surface-card--padded">
        <div class="section-title">原始简历</div>
        <pre class="markdown-surface" style="white-space: pre-wrap;">{{ detail.resumeText }}</pre>
      </div>
    </template>

    <template v-else-if="detail">
      <div class="table-shell">
        <table class="data-table">
          <thead>
            <tr>
              <th>会话 ID</th>
              <th>状态</th>
              <th>题目数</th>
              <th>得分</th>
              <th>创建时间</th>
              <th style="text-align: right;">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="detail.interviews.length === 0">
              <td colspan="6"><div class="empty-state">当前还没有关联面试记录。</div></td>
            </tr>
            <tr v-for="item in detail.interviews" v-else :key="item.sessionId">
              <td>
                <div class="table-item-title" style="font-size: 20px;">{{ item.sessionId }}</div>
              </td>
              <td><StatusBadge :label="item.status" /></td>
              <td><span class="status-badge">{{ item.totalQuestions }} 题</span></td>
              <td><ScoreBar :score="item.overallScore ?? 0" compact /></td>
              <td style="font-size: 18px; color: #63789b;">{{ formatDateTime(item.createdAt) }}</td>
              <td>
                <div style="display: flex; justify-content: flex-end; gap: 10px;">
                  <button class="icon-btn" type="button" @click="router.push(`/interviews/${item.sessionId}`)">
                    <AppIcon name="chevron-right" :size="18" />
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>
