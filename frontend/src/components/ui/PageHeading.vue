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
  icon?: IconName;
  title: string;
  subtitle?: string;
  centered?: boolean;
}>();
</script>

<template>
  <div class="page-heading" :class="{ 'page-heading--centered': centered }">
    <div v-if="icon" class="page-heading__icon">
      <AppIcon :name="icon" :size="30" />
    </div>
    <div class="page-heading__text">
      <h1 class="page-heading__title">{{ title }}</h1>
      <p v-if="subtitle" class="page-heading__subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="page-heading__actions">
      <slot name="actions" />
    </div>
  </div>
</template>
