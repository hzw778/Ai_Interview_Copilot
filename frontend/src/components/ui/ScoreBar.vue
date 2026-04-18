<script setup lang="ts">
const props = withDefaults(defineProps<{
  score?: number | null;
  max?: number;
  compact?: boolean;
}>(), {
  max: 100,
  compact: false,
});

const score = Number(props.score ?? 0);
const percentage = Math.max(0, Math.min(100, (score / props.max) * 100));

const toneClass = score >= 80
  ? 'score-bar__fill--green'
  : score >= 70
    ? 'score-bar__fill--amber'
    : 'score-bar__fill--red';
</script>

<template>
  <div class="score-bar" :class="{ 'score-bar--compact': compact }">
    <div class="score-bar__track">
      <div class="score-bar__fill" :class="toneClass" :style="{ width: `${percentage}%` }"></div>
    </div>
    <span class="score-bar__value">{{ score }}</span>
  </div>
</template>
