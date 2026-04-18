<script setup lang="ts">
import AppIcon from './AppIcon.vue';

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

defineProps<{
  icon: IconName;
  title: string;
  value: string | number;
  subtitle?: string;
  tone?: 'purple' | 'green' | 'amber' | 'blue';
}>();

const toneClassMap = {
  purple: 'metric-card__icon--purple',
  green: 'metric-card__icon--green',
  amber: 'metric-card__icon--amber',
  blue: 'metric-card__icon--blue',
};
</script>

<template>
  <div class="metric-card">
    <div class="metric-card__icon" :class="toneClassMap[tone ?? 'purple']">
      <AppIcon :name="icon" :size="24" />
    </div>
    <div class="metric-card__body">
      <p class="metric-card__title">{{ title }}</p>
      <p class="metric-card__value">{{ value }}</p>
      <p v-if="subtitle" class="metric-card__subtitle">{{ subtitle }}</p>
    </div>
  </div>
</template>
