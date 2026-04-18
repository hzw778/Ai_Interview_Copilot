<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppIcon from '../components/ui/AppIcon.vue';
import StatusBadge from '../components/ui/StatusBadge.vue';
import { historyApi, type InterviewDetail } from '../api/history';
import { getErrorMessage } from '../api/request';
import { formatDateOnly } from '../utils/date';
import { downloadBlob } from '../utils/format';

const route = useRoute();
const router = useRouter();

const detail = ref<InterviewDetail | null>(null);
const loading = ref(true);
const error = ref('');
const expandedQuestionIndexes = ref<number[]>([]);
let pollTimer: number | null = null;

const scoreRing = computed(() => Number(detail.value?.overallScore ?? 0));
const evaluateStatus = computed(() => detail.value?.evaluateStatus ?? 'PENDING');
const strengthItems = computed(() =>
  (detail.value?.strengths ?? [])
    .filter((item): item is string => typeof item === 'string')
    .map((item) => item.trim())
    .filter(Boolean)
);
const improvementItems = computed(() =>
  (detail.value?.improvements ?? [])
    .filter((item): item is string => typeof item === 'string')
    .map((item) => item.trim())
    .filter(Boolean)
);
const hasStrengths = computed(() => strengthItems.value.length > 0);
const hasImprovements = computed(() => improvementItems.value.length > 0);
const summaryGridClass = computed(() =>
  hasStrengths.value && hasImprovements.value ? 'split-grid split-grid--2' : 'split-grid'
);
const hasEvaluationResult = computed(() =>
  !!detail.value
  && detail.value.overallScore !== null
  && !!detail.value.overallFeedback
);
const isEvaluationPending = computed(() => {
  if (!detail.value) return false;
  return evaluateStatus.value === 'PENDING'
    || evaluateStatus.value === 'PROCESSING'
    || (!hasEvaluationResult.value && evaluateStatus.value !== 'FAILED');
});
const isEvaluationFailed = computed(() => !!detail.value && evaluateStatus.value === 'FAILED');
const allAnswersExpanded = computed(() => {
  if (!detail.value?.answers?.length) {
    return false;
  }
  return detail.value.answers.every((answer) => expandedQuestionIndexes.value.includes(answer.questionIndex));
});
const pendingTitle = computed(() =>
  evaluateStatus.value === 'PROCESSING'
    ? 'AI 正在评估本场面试'
    : '面试已结束，正在排队生成评估'
);
const pendingDescription = computed(() =>
  evaluateStatus.value === 'PROCESSING'
    ? '系统正在综合每道题的回答质量、知识深度与表达效果，生成完整面试报告。'
    : '你的答题记录已经保存成功，我们正在启动评估任务，结果生成后会自动展示。'
);

function clearPolling() {
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer);
    pollTimer = null;
  }
}

function shouldPoll(currentDetail: InterviewDetail | null) {
  if (!currentDetail) {
    return false;
  }
  if (currentDetail.evaluateStatus === 'FAILED') {
    return false;
  }
  if (currentDetail.evaluateStatus === 'COMPLETED' && currentDetail.overallScore !== null) {
    return false;
  }
  return currentDetail.evaluateStatus === 'PENDING'
    || currentDetail.evaluateStatus === 'PROCESSING'
    || currentDetail.overallScore === null;
}

function schedulePolling(currentDetail: InterviewDetail | null = detail.value) {
  clearPolling();
  if (!shouldPoll(currentDetail)) {
    return;
  }
  pollTimer = window.setTimeout(async () => {
    try {
      const latest = await historyApi.getInterviewDetail(String(route.params.sessionId));
      detail.value = latest;
      schedulePolling(latest);
    } catch (err) {
      console.warn('interview detail polling failed', err);
      schedulePolling(currentDetail);
    }
  }, 2500);
}

function syncExpandedQuestions(nextDetail: InterviewDetail | null, forceExpandAll = false) {
  if (!nextDetail?.answers?.length) {
    expandedQuestionIndexes.value = [];
    return;
  }

  const availableIndexes = new Set(
    nextDetail.answers
      .map((answer) => answer.questionIndex)
      .filter((questionIndex): questionIndex is number => typeof questionIndex === 'number')
  );

  if (forceExpandAll || expandedQuestionIndexes.value.length === 0) {
    expandedQuestionIndexes.value = nextDetail.answers
      .map((answer) => answer.questionIndex)
      .filter((questionIndex): questionIndex is number => typeof questionIndex === 'number');
    return;
  }

  expandedQuestionIndexes.value = expandedQuestionIndexes.value.filter((questionIndex) =>
    availableIndexes.has(questionIndex)
  );
}

function isExpanded(questionIndex: number) {
  return expandedQuestionIndexes.value.includes(questionIndex);
}

function toggleExpanded(questionIndex: number) {
  expandedQuestionIndexes.value = isExpanded(questionIndex)
    ? expandedQuestionIndexes.value.filter((item) => item !== questionIndex)
    : [...expandedQuestionIndexes.value, questionIndex];
}

function toggleAllAnswers() {
  if (!detail.value?.answers?.length) {
    return;
  }
  expandedQuestionIndexes.value = allAnswersExpanded.value
    ? []
    : detail.value.answers
        .map((answer) => answer.questionIndex)
        .filter((questionIndex): questionIndex is number => typeof questionIndex === 'number');
}

async function loadData(showLoading = true) {
  if (showLoading) {
    loading.value = true;
  }
  if (!detail.value) {
    error.value = '';
  }
  try {
    const latest = await historyApi.getInterviewDetail(String(route.params.sessionId));
    const shouldExpandAll = !detail.value;
    detail.value = latest;
    syncExpandedQuestions(latest, shouldExpandAll);
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
  const blob = await historyApi.exportInterviewPdf(detail.value.sessionId);
  downloadBlob(blob, `${detail.value.sessionId}-report.pdf`);
}

onMounted(loadData);
onBeforeUnmount(clearPolling);
</script>

<template>
  <section class="page">
    <div class="detail-topbar">
      <div style="display: flex; align-items: flex-start; gap: 16px;">
        <button class="back-btn" type="button" @click="router.push('/interviews')">
          <AppIcon name="chevron-left" :size="20" />
        </button>
        <div>
          <h1 class="detail-title">面试详情 #{{ String(route.params.sessionId).slice(-8) }}</h1>
          <div class="subtle-row" style="margin-top: 8px;">
            <AppIcon name="clock" :size="16" />
            <span>完成于 {{ detail ? formatDateOnly(detail.completedAt || detail.createdAt) : '--' }}</span>
            <StatusBadge v-if="detail" :label="detail.evaluateStatus || detail.status" />
          </div>
        </div>
      </div>
      <button class="action-btn" type="button" :disabled="!hasEvaluationResult" @click="exportPdf">
        <AppIcon name="download" :size="18" />
        <span>导出 PDF</span>
      </button>
    </div>

    <div v-if="loading" class="surface-card surface-card--padded">
      <div class="empty-state">正在加载面试详情...</div>
    </div>
    <div
      v-else-if="error"
      class="surface-card surface-card--compact"
      style="border-color: rgba(255,74,88,.18); color: #db4252;"
    >
      {{ error }}
    </div>

    <template v-else-if="detail">
      <div
        v-if="isEvaluationPending && !hasEvaluationResult"
        class="analysis-pending surface-card surface-card--padded"
      >
        <div class="analysis-pending__hero">
          <div class="analysis-pending__visual">
            <span class="analysis-pending__ring"></span>
            <span class="analysis-pending__ring analysis-pending__ring--delay"></span>
            <span class="analysis-pending__core"></span>
          </div>
          <div class="analysis-pending__content">
            <p class="analysis-pending__eyebrow">INTERVIEW REVIEW IN PROGRESS</p>
            <h2 class="analysis-pending__title">{{ pendingTitle }}</h2>
            <p class="analysis-pending__text">{{ pendingDescription }}</p>
            <div class="analysis-pending__steps">
              <div class="analysis-step analysis-step--active">
                <span class="analysis-step__dot"></span>
                <span>汇总答题记录</span>
              </div>
              <div class="analysis-step analysis-step--active">
                <span class="analysis-step__dot"></span>
                <span>评估知识深度与表达</span>
              </div>
              <div class="analysis-step">
                <span class="analysis-step__dot"></span>
                <span>生成总评与改进建议</span>
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
        v-else-if="isEvaluationFailed && !hasEvaluationResult"
        class="surface-card surface-card--padded"
        style="border-color: rgba(255,74,88,.18); background: rgba(255,247,248,.96);"
      >
        <div class="section-title" style="color: #d4384c;">本次面试评估暂未成功</div>
        <p class="table-item-subtitle" style="margin-top: 10px; font-size: 14px; color: #8b4452;">
          作答记录已经保存成功，系统暂时还没有生成完整评估结果。你可以稍后刷新页面再次查看。
        </p>
      </div>

      <template v-else>
        <div class="analysis-hero">
          <div class="analysis-hero__score">
            <div class="analysis-hero__ring">{{ scoreRing }}</div>
            <p class="analysis-hero__score-label">总分</p>
          </div>
          <h2 class="analysis-hero__title">面试评估</h2>
          <p class="analysis-hero__text">{{ detail.overallFeedback || '暂无总体评估。' }}</p>
        </div>

        <div v-if="hasStrengths || hasImprovements" :class="summaryGridClass">
          <div v-if="hasStrengths" class="bullet-panel bullet-panel--green">
            <div class="bullet-panel__heading" style="color: #1aaa67;">
              <AppIcon name="check-circle" :size="24" />
              <span>表现优势</span>
            </div>
            <ul class="bullet-list">
              <li v-for="item in strengthItems" :key="item">{{ item }}</li>
            </ul>
          </div>

          <div v-if="hasImprovements" class="bullet-panel bullet-panel--amber">
            <div class="bullet-panel__heading" style="color: #e99900;">
              <AppIcon name="target" :size="24" />
              <span>改进建议</span>
            </div>
            <ul class="bullet-list">
              <li v-for="item in improvementItems" :key="item">{{ item }}</li>
            </ul>
          </div>
        </div>

        <div class="surface-card surface-card--padded">
          <div class="toolbar-row" style="margin-bottom: 18px;">
            <div class="section-title" style="margin-bottom: 0;">问答记录详情</div>
            <button
              class="action-btn action-btn--soft"
              type="button"
              @click="toggleAllAnswers"
            >
              {{ allAnswersExpanded ? '全部收起' : '全部展开' }}
            </button>
          </div>
          <div class="split-grid" style="grid-template-columns: 1fr; gap: 18px;">
            <div
              v-for="(answer, index) in detail.answers"
              :key="`${answer.questionIndex}-${index}`"
              class="qa-item"
            >
              <div class="toolbar-row" style="align-items: center;">
                <div class="qa-item__meta">
                  <span class="status-badge">{{ answer.questionIndex + 1 }}</span>
                  <span class="tag-pill">{{ answer.category }}</span>
                  <span
                    class="tag-pill"
                    style="background: rgba(34,197,94,.12); color: #159f66;"
                  >
                    得分: {{ answer.score }}
                  </span>
                </div>
                <button
                  class="icon-btn"
                  type="button"
                  :title="isExpanded(answer.questionIndex) ? '收起详情' : '展开详情'"
                  @click="toggleExpanded(answer.questionIndex)"
                >
                  <AppIcon
                    name="chevron-right"
                    :size="18"
                    :style="{ transform: `rotate(${isExpanded(answer.questionIndex) ? '90deg' : '0deg'})` }"
                  />
                </button>
              </div>
              <h3 class="qa-item__title">{{ answer.question }}</h3>

              <template v-if="isExpanded(answer.questionIndex)">
                <div class="split-grid" style="grid-template-columns: 1fr; gap: 16px; margin-top: 18px;">
                  <div class="question-shell">
                    <div class="section-subtitle">你的回答</div>
                    <div style="margin-top: 10px;">{{ answer.userAnswer || '未作答' }}</div>
                  </div>
                  <div class="review-shell">
                    <div class="section-subtitle">AI 深度评价</div>
                    <div style="margin-top: 10px;">{{ answer.feedback }}</div>
                  </div>
                  <div v-if="answer.referenceAnswer" class="reference-shell">
                    <div class="section-subtitle">参考答案</div>
                    <div style="margin-top: 10px;">{{ answer.referenceAnswer }}</div>
                  </div>
                  <div v-if="answer.keyPoints?.length" class="answer-shell">
                    <div class="section-subtitle">关键点</div>
                    <div class="chip-list" style="margin-top: 12px;">
                      <span v-for="point in answer.keyPoints" :key="point" class="tag-pill">
                        {{ point }}
                      </span>
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>
      </template>
    </template>
  </section>
</template>
