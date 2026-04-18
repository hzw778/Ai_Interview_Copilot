<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppIcon from '../components/ui/AppIcon.vue';
import PageHeading from '../components/ui/PageHeading.vue';
import { historyApi } from '../api/history';
import { interviewApi } from '../api/interview';
import { getErrorMessage } from '../api/request';
import type { InterviewQuestion, InterviewSession } from '../types/interview';

interface InterviewDraftConfig {
  skillId: string;
  difficulty: 'junior' | 'mid' | 'senior';
  questionCount: number;
  llmProvider: string;
  resumeId?: number;
  customJdText?: string;
  customCategories?: unknown[];
}

const route = useRoute();
const router = useRouter();

const session = ref<InterviewSession | null>(null);
const currentQuestion = ref<InterviewQuestion | null>(null);
const answer = ref('');
const resumeText = ref('');
const initializing = ref(false);
const actionLoading = ref(false);
const syncingQuestion = ref(false);
const questionSyncFailed = ref(false);
const error = ref('');

const INITIAL_QUESTION_MAX_RETRIES = 8;
const INITIAL_QUESTION_RETRY_DELAY = 1200;

const querySessionId = computed(() => route.query.sessionId?.toString() || '');
const queryResumeId = computed(() => route.query.resumeId?.toString() || '');
const draftConfig = computed<InterviewDraftConfig>(() => {
  const raw = sessionStorage.getItem('interview-config');
  if (!raw) {
    return {
      skillId: 'java-backend',
      difficulty: 'mid',
      questionCount: 8,
      llmProvider: 'dashscope',
    };
  }
  try {
    return JSON.parse(raw) as InterviewDraftConfig;
  } catch {
    return {
      skillId: 'java-backend',
      difficulty: 'mid',
      questionCount: 8,
      llmProvider: 'dashscope',
    };
  }
});

const hasQuestionReady = computed(() => Boolean(currentQuestion.value?.question?.trim()));
const progress = computed(() => {
  if (!session.value || !currentQuestion.value) return 0;
  return Math.max(0, Math.min(100, ((currentQuestion.value.questionIndex + 1) / session.value.totalQuestions) * 100));
});
const totalQuestionCount = computed(() => session.value?.totalQuestions ?? draftConfig.value.questionCount);
const showPreparingState = computed(() =>
  !error.value && !hasQuestionReady.value && (initializing.value || syncingQuestion.value)
);

function sleep(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

async function restoreResumeText() {
  const resumeId = draftConfig.value.resumeId ?? (queryResumeId.value ? Number(queryResumeId.value) : undefined);
  if (!resumeId) {
    resumeText.value = '';
    return;
  }
  const detail = await historyApi.getResumeDetail(resumeId);
  resumeText.value = detail.resumeText;
}

function initSession(data: InterviewSession) {
  session.value = data;
  currentQuestion.value = data.questions[data.currentQuestionIndex] ?? data.questions[0] ?? null;
  answer.value = currentQuestion.value?.userAnswer ?? '';
  questionSyncFailed.value = false;
}

function mergeCurrentQuestion(question: InterviewQuestion) {
  if (!session.value) {
    return;
  }

  const questions = [...session.value.questions];
  const existingIndex = questions.findIndex((item) => item.questionIndex === question.questionIndex);
  if (existingIndex >= 0) {
    questions[existingIndex] = question;
  } else {
    questions.unshift(question);
  }

  session.value = {
    ...session.value,
    currentQuestionIndex: question.questionIndex,
    questions,
  };
}

async function ensureCurrentQuestion(sessionId: string) {
  if (hasQuestionReady.value) {
    return true;
  }

  syncingQuestion.value = true;
  questionSyncFailed.value = false;

  try {
    for (let attempt = 0; attempt < INITIAL_QUESTION_MAX_RETRIES; attempt += 1) {
      const latestSession = await interviewApi.getSession(sessionId).catch(() => null);
      if (latestSession) {
        initSession(latestSession);
        if (hasQuestionReady.value) {
          return true;
        }
      }

      const current = await interviewApi.getCurrentQuestion(sessionId).catch(() => null);
      if (current?.question) {
        currentQuestion.value = current.question;
        answer.value = current.question.userAnswer ?? '';
        mergeCurrentQuestion(current.question);
        return true;
      }

      if (current?.completed) {
        router.push(`/interviews/${sessionId}`);
        return false;
      }

      if (attempt < INITIAL_QUESTION_MAX_RETRIES - 1) {
        await sleep(INITIAL_QUESTION_RETRY_DELAY);
      }
    }

    questionSyncFailed.value = true;
    return false;
  } finally {
    syncingQuestion.value = false;
  }
}

async function startInterview() {
  initializing.value = true;
  error.value = '';
  try {
    await restoreResumeText();
    const created = await interviewApi.createSession({
      resumeText: resumeText.value,
      questionCount: draftConfig.value.questionCount,
      resumeId: draftConfig.value.resumeId,
      forceCreate: true,
      llmProvider: draftConfig.value.llmProvider,
      skillId: draftConfig.value.skillId,
      difficulty: draftConfig.value.difficulty,
      customCategories: draftConfig.value.customCategories as never,
      jdText: draftConfig.value.customJdText,
    });
    initSession(created);
    await ensureCurrentQuestion(created.sessionId);
  } catch (err) {
    error.value = getErrorMessage(err);
  } finally {
    initializing.value = false;
  }
}

async function resumeInterview(sessionId: string) {
  initializing.value = true;
  error.value = '';
  try {
    const existing = await interviewApi.getSession(sessionId);
    initSession(existing);
    await ensureCurrentQuestion(existing.sessionId);
  } catch (err) {
    error.value = getErrorMessage(err);
  } finally {
    initializing.value = false;
  }
}

async function submitAnswer() {
  if (!session.value || !currentQuestion.value || !answer.value.trim()) return;
  actionLoading.value = true;
  error.value = '';
  try {
    const response = await interviewApi.submitAnswer({
      sessionId: session.value.sessionId,
      questionIndex: currentQuestion.value.questionIndex,
      answer: answer.value.trim(),
    });
    answer.value = '';
    if (response.hasNextQuestion && response.nextQuestion) {
      currentQuestion.value = response.nextQuestion;
    } else {
      router.push(`/interviews/${session.value.sessionId}`);
    }
  } catch (err) {
    error.value = getErrorMessage(err);
  } finally {
    actionLoading.value = false;
  }
}

async function completeEarly() {
  if (!session.value) return;
  actionLoading.value = true;
  error.value = '';
  try {
    await interviewApi.completeInterview(session.value.sessionId);
    router.push(`/interviews/${session.value.sessionId}`);
  } catch (err) {
    error.value = getErrorMessage(err);
  } finally {
    actionLoading.value = false;
  }
}

async function retryLoadQuestion() {
  error.value = '';
  if (session.value) {
    await ensureCurrentQuestion(session.value.sessionId);
    return;
  }
  if (querySessionId.value) {
    await resumeInterview(querySessionId.value);
    return;
  }
  await startInterview();
}

onMounted(async () => {
  if (querySessionId.value) {
    await resumeInterview(querySessionId.value);
  } else {
    await startInterview();
  }
});
</script>

<template>
  <section class="page interview-shell">
    <PageHeading
      icon="wand"
      centered
      title="模拟面试"
      subtitle="认真回答每个问题，展示您的实力"
    />

    <div v-if="error" class="surface-card surface-card--compact" style="border-color: rgba(255,74,88,.18); color: #db4252;">
      {{ error }}
    </div>

    <div class="interview-stage progress-panel">
      <div class="progress-panel__head">
        <span>
          {{ hasQuestionReady ? `题目 ${(currentQuestion?.questionIndex ?? 0) + 1} / ${totalQuestionCount}` : `正在准备题目 / 共 ${totalQuestionCount} 题` }}
        </span>
        <span>{{ showPreparingState ? '准备中' : `${Math.round(progress)}%` }}</span>
      </div>
      <div class="progress-line">
        <div class="progress-line__value" :style="{ width: `${progress}%` }"></div>
      </div>
    </div>

    <div class="interview-stage">
      <div v-if="showPreparingState" class="interview-preparing">
        <div class="interview-preparing__visual">
          <div class="interview-preparing__ring"></div>
          <div class="interview-preparing__ring interview-preparing__ring--delay"></div>
          <div class="interview-preparing__core">
            <AppIcon name="wand" :size="28" />
          </div>
        </div>
        <div>
          <p class="interview-preparing__eyebrow">MOCK INTERVIEW</p>
          <h3 class="interview-preparing__title">正在生成本场面试题</h3>
          <p class="interview-preparing__text">
            系统正在根据面试方向、难度和简历信息准备题目。题目就绪后会自动展示第一题，无需手动刷新。
          </p>
          <div class="analysis-pending__steps" style="justify-content: center;">
            <span class="analysis-step analysis-step--active">
              <span class="analysis-step__dot"></span>
              匹配岗位方向
            </span>
            <span class="analysis-step">
              <span class="analysis-step__dot"></span>
              生成面试题
            </span>
            <span class="analysis-step">
              <span class="analysis-step__dot"></span>
              准备答题
            </span>
          </div>
        </div>
      </div>

      <template v-else-if="currentQuestion">
        <div style="display: flex; gap: 18px; align-items: flex-start; margin-bottom: 26px;">
          <div class="file-chip" style="width: 48px; height: 48px; border-radius: 999px;">
            <AppIcon name="users" :size="20" />
          </div>
          <div style="flex: 1;">
            <div class="toolbar-actions" style="gap: 10px; margin-bottom: 12px;">
              <span class="table-item-title" style="font-size: 16px;">面试官</span>
              <span class="tag-pill">{{ draftConfig.skillId }}</span>
            </div>
            <div class="question-bubble">
              {{ currentQuestion.question }}
            </div>
          </div>
        </div>

        <div class="answer-panel">
          <textarea
            v-model="answer"
            class="textarea-field"
            placeholder="输入你的回答...（Ctrl/Cmd + Enter 提交）"
            @keydown.ctrl.enter.prevent="submitAnswer"
            @keydown.meta.enter.prevent="submitAnswer"
          ></textarea>

          <div class="answer-panel__actions">
            <button class="action-btn action-btn--primary" type="button" :disabled="actionLoading || !answer.trim()" @click="submitAnswer">
              <AppIcon name="send" :size="18" />
              <span>{{ actionLoading ? '提交中' : '提交' }}</span>
            </button>
            <button class="action-btn action-btn--soft" type="button" :disabled="actionLoading" @click="completeEarly">
              提前交卷
            </button>
          </div>
        </div>
      </template>

      <div v-else class="interview-empty">
        <div class="interview-empty__icon">
          <AppIcon name="document" :size="24" />
        </div>
        <div class="table-item-title" style="font-size: 20px;">
          {{ questionSyncFailed ? '题目准备时间较长' : '暂未获取到面试题' }}
        </div>
        <div class="interview-empty__text">
          {{ questionSyncFailed
            ? '系统还没有成功返回第一题。你可以继续重试获取，或稍后重新进入本场面试。'
            : '当前面试题尚未就绪，请稍后重试。' }}
        </div>
        <div class="toolbar-actions" style="justify-content: center;">
          <button class="action-btn action-btn--primary" type="button" @click="retryLoadQuestion">
            重新获取题目
          </button>
        </div>
      </div>
    </div>
  </section>
</template>
