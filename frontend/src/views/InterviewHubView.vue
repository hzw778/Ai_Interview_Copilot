<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppIcon from '../components/ui/AppIcon.vue';
import PageHeading from '../components/ui/PageHeading.vue';
import { historyApi } from '../api/history';
import { getErrorMessage } from '../api/request';
import { skillApi, type CategoryDTO, type SkillDTO } from '../api/skill';

const router = useRouter();
const route = useRoute();

const loading = ref(false);
const error = ref('');
const skills = ref<SkillDTO[]>([]);
const resumeSummary = ref('');

const mode = ref<'text'>('text');
const selectedSkillId = ref('java-backend');
const difficulty = ref<'junior' | 'mid' | 'senior'>('mid');
const questionCount = ref(8);
const llmProvider = ref('dashscope');
const customJdText = ref('');
const customCategories = ref<CategoryDTO[]>([]);
const parsingJd = ref(false);

const resumeId = computed(() => {
  const raw = route.query.resumeId?.toString();
  return raw ? Number(raw) : undefined;
});

const displayedSkills = computed(() => {
  const pinned = ['ai-agent', 'algorithm', 'ali-backend', 'bytedance-backend', 'frontend', 'java-backend', 'tencent-backend', 'python-backend', 'system-design', 'test-development'];
  const mapped = pinned
    .map((id) => skills.value.find((item) => item.id === id))
    .filter(Boolean) as SkillDTO[];
  const remaining = skills.value.filter((item) => !mapped.some((hit) => hit.id === item.id));
  return [...mapped, ...remaining].slice(0, 10);
});

const selectedSkill = computed(() => skills.value.find((item) => item.id === selectedSkillId.value));
const moreOptionsOpen = ref(true);

function getSkillLabel(skill: SkillDTO) {
  const map: Record<string, string> = {
    'ai-agent': 'AI',
    algorithm: 'fx',
    'ali-backend': 'e',
    'bytedance-backend': 'bar',
    frontend: 'fe',
    'java-backend': 'J',
    'tencent-backend': 'TX',
    'python-backend': 'Py',
    'system-design': 'SYS',
    'test-development': 'QA',
    custom: 'JD',
  };
  return map[skill.id] ?? skill.name.slice(0, 2).toUpperCase();
}

async function loadSkills() {
  skills.value = await skillApi.listSkills().catch(() => []);
}

async function loadResumeSummary() {
  if (!resumeId.value) return;
  try {
    const detail = await historyApi.getResumeDetail(resumeId.value);
    resumeSummary.value = detail.filename;
  } catch {
    resumeSummary.value = `简历 #${resumeId.value}`;
  }
}

async function parseJd() {
  if (!customJdText.value.trim()) {
    error.value = '请输入 JD 内容后再解析';
    return;
  }
  parsingJd.value = true;
  error.value = '';
  try {
    customCategories.value = await skillApi.parseJd(customJdText.value.trim());
  } catch (err) {
    error.value = getErrorMessage(err);
  } finally {
    parsingJd.value = false;
  }
}

function startInterview() {
  loading.value = true;
  error.value = '';

  const payload = {
    mode: mode.value,
    skillId: selectedSkillId.value,
    difficulty: difficulty.value,
    questionCount: questionCount.value,
    llmProvider: llmProvider.value,
    resumeId: resumeId.value,
    customJdText: selectedSkillId.value === 'custom' ? customJdText.value : '',
    customCategories: selectedSkillId.value === 'custom' ? customCategories.value : [],
  };
  sessionStorage.setItem('interview-config', JSON.stringify(payload));
  router.push({
    path: '/interview',
    query: resumeId.value ? { resumeId: String(resumeId.value) } : undefined,
  });
}

onMounted(async () => {
  await Promise.all([loadSkills(), loadResumeSummary()]);
});
</script>

<template>
  <section class="page">
    <PageHeading
      icon="wand"
      title="模拟面试"
      subtitle="选择面试模式和方向，快速开始练习"
    />

    <div class="surface-card surface-card--padded">
      <div class="section-title">面试模式</div>
      <div class="split-grid" style="grid-template-columns: 1fr;">
        <button class="selection-card selection-card--active" type="button">
          <div class="selection-card__icon">
            <AppIcon name="document" :size="28" />
          </div>
          <div>
            <p class="selection-card__title">文字面试</p>
            <p class="selection-card__desc">推荐：更稳定，更适合系统化刷题与复盘</p>
          </div>
          <div class="selection-card__meta">
            <span class="recommend-badge">推荐</span>
          </div>
        </button>
      </div>

      <div style="height: 20px;"></div>

      <div class="section-title">面试方向</div>
      <div class="skill-grid">
        <button
          v-for="skill in displayedSkills"
          :key="skill.id"
          class="skill-card"
          :class="{ 'skill-card--active': selectedSkillId === skill.id }"
          type="button"
          @click="selectedSkillId = skill.id"
        >
          <div class="skill-card__icon">
            {{ getSkillLabel(skill) }}
          </div>
          <div class="skill-card__title">{{ skill.name }}</div>
        </button>

        <button
          class="skill-card"
          :class="{ 'skill-card--active': selectedSkillId === 'custom' }"
          type="button"
          @click="selectedSkillId = 'custom'"
        >
          <div class="skill-card__icon">JD</div>
          <div class="skill-card__title">自定义 JD</div>
        </button>
      </div>

      <div v-if="selectedSkillId === 'custom'" class="jd-preview" style="margin-top: 14px;">
        <textarea
          v-model="customJdText"
          class="textarea-field"
          placeholder="粘贴岗位描述，点击“解析面试方向”生成面试标签"
        ></textarea>
        <div class="toolbar-actions" style="margin-top: 12px;">
          <button class="action-btn action-btn--primary" type="button" :disabled="parsingJd" @click="parseJd">
            <AppIcon name="wand" :size="18" />
            <span>{{ parsingJd ? '解析中...' : '解析面试方向' }}</span>
          </button>
        </div>
        <div v-if="customCategories.length" class="chip-list" style="margin-top: 14px;">
          <span v-for="category in customCategories" :key="category.key" class="tag-pill">
            {{ category.label }} ({{ category.priority }})
          </span>
        </div>
      </div>

      <div style="height: 22px;"></div>

      <div class="section-title">难度</div>
      <div class="difficulty-grid">
        <button
          class="difficulty-card"
          :class="{ 'difficulty-card--active': difficulty === 'junior' }"
          type="button"
          @click="difficulty = 'junior'"
        >
          <div class="difficulty-card__title">校招</div>
          <div class="difficulty-card__desc">0-1 年</div>
        </button>
        <button
          class="difficulty-card"
          :class="{ 'difficulty-card--active': difficulty === 'mid' }"
          type="button"
          @click="difficulty = 'mid'"
        >
          <div class="difficulty-card__title">中级</div>
          <div class="difficulty-card__desc">1-3 年</div>
        </button>
        <button
          class="difficulty-card"
          :class="{ 'difficulty-card--active': difficulty === 'senior' }"
          type="button"
          @click="difficulty = 'senior'"
        >
          <div class="difficulty-card__title">高级</div>
          <div class="difficulty-card__desc">3 年+</div>
        </button>
      </div>

      <div style="height: 18px;"></div>

      <button class="action-btn action-btn--soft" type="button" style="width: 100%; justify-content: space-between;" @click="moreOptionsOpen = !moreOptionsOpen">
        <span>更多选项</span>
        <AppIcon :name="moreOptionsOpen ? 'chevron-left' : 'chevron-right'" :size="18" style="transform: rotate(-90deg);" />
      </button>

      <div v-if="moreOptionsOpen" class="split-grid split-grid--2" style="margin-top: 12px;">
        <div class="soft-block">
          <p class="section-subtitle">面试题数量</p>
          <input v-model.number="questionCount" class="input-field" min="3" max="20" type="number" />
        </div>
        <div class="soft-block">
          <p class="section-subtitle">LLM Provider</p>
          <input v-model="llmProvider" class="input-field" />
        </div>
        <div v-if="resumeId" class="soft-block" style="grid-column: 1 / -1;">
          <p class="section-subtitle">当前绑定简历</p>
          <div class="table-item-title" style="font-size: 16px; margin-top: 8px;">{{ resumeSummary }}</div>
        </div>
        <div v-if="selectedSkill" class="soft-block" style="grid-column: 1 / -1;">
          <p class="section-subtitle">方向说明</p>
          <div style="margin-top: 10px; font-size: 14px; line-height: 1.7; color: var(--text-secondary);">
            {{ selectedSkill.description }}
          </div>
        </div>
      </div>

      <div v-if="error" class="surface-card surface-card--compact" style="margin-top: 14px; border-color: rgba(239, 68, 68, 0.2); color: #dc2626;">
        {{ error }}
      </div>

      <div style="height: 24px;"></div>
      <button class="full-cta" type="button" :disabled="loading" @click="startInterview">
        开始文字面试
      </button>
    </div>
  </section>
</template>
