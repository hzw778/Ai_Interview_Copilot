<script setup lang="ts">
const props = defineProps<{
  label?: string | null;
}>();

const rawLabel = props.label?.trim() || '未知';
const normalized = rawLabel.toUpperCase();

function resolveBadgeClass(label: string) {
  const upper = label.toUpperCase();

  if (
    upper === 'COMPLETED'
    || upper === 'EVALUATED'
    || label.includes('完成')
  ) {
    return 'status-badge status-badge--completed';
  }

  if (
    upper === 'PROCESSING'
    || upper === 'IN_PROGRESS'
    || label.includes('分析中')
    || label.includes('处理中')
    || label.includes('进行中')
    || label.includes('排队')
  ) {
    return 'status-badge status-badge--processing';
  }

  if (
    upper === 'PENDING'
    || upper === 'CREATED'
    || label.includes('待')
  ) {
    return 'status-badge status-badge--pending';
  }

  if (
    upper === 'FAILED'
    || upper === 'CANCELLED'
    || label.includes('失败')
    || label.includes('取消')
  ) {
    return 'status-badge status-badge--danger';
  }

  return 'status-badge';
}

const badgeClass = resolveBadgeClass(normalized === rawLabel.toUpperCase() ? rawLabel : normalized);
</script>

<template>
  <span :class="badgeClass">{{ rawLabel }}</span>
</template>
