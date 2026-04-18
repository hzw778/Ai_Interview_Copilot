<script setup lang="ts">
interface Props {
  name:
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
    | 'refresh'
    | 'eye'
    | 'target'
    | 'filter'
    | 'mic'
    | 'arrow-up'
    | 'menu';
  size?: number;
  strokeWidth?: number;
}

withDefaults(defineProps<Props>(), {
  size: 22,
  strokeWidth: 1.8,
});
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :stroke-width="strokeWidth"
    stroke="currentColor"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-if="name === 'brand'">
      <path d="M12 3l1.5 4.5L18 9l-4.5 1.5L12 15l-1.5-4.5L6 9l4.5-1.5L12 3Z" />
      <path d="M19 4l.75 2.25L22 7l-2.25.75L19 10l-.75-2.25L16 7l2.25-.75L19 4Z" />
      <path d="M5 14l.75 2.25L8 17l-2.25.75L5 20l-.75-2.25L2 17l2.25-.75L5 14Z" />
    </template>
    <template v-else-if="name === 'moon'">
      <path d="M20 14.5A8.5 8.5 0 0 1 9.5 4 7 7 0 1 0 20 14.5Z" />
    </template>
    <template v-else-if="name === 'upload'">
      <path d="M12 16V4" />
      <path d="M7 9l5-5 5 5" />
      <path d="M4 17v1a3 3 0 0 0 3 3h10a3 3 0 0 0 3-3v-1" />
    </template>
    <template v-else-if="name === 'files'">
      <path d="M9 6a2 2 0 0 1 2-2h7v12a2 2 0 0 1-2 2H9V6Z" />
      <path d="M6 8H5a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h7a2 2 0 0 0 2-2v-1" />
      <path d="M11 4v4h4" />
    </template>
    <template v-else-if="name === 'wand'">
      <path d="M7 20l9-9" />
      <path d="M14 4l1 3 3 1-3 1-1 3-1-3-3-1 3-1 1-3Z" />
      <path d="M5 6l.5 1.5L7 8l-1.5.5L5 10l-.5-1.5L3 8l1.5-.5L5 6Z" />
    </template>
    <template v-else-if="name === 'users'">
      <path d="M16 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2" />
      <circle cx="9.5" cy="7" r="3" />
      <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 4.13A4 4 0 0 1 16 11.87" />
    </template>
    <template v-else-if="name === 'database'">
      <ellipse cx="12" cy="5" rx="7" ry="3" />
      <path d="M5 5v6c0 1.66 3.13 3 7 3s7-1.34 7-3V5" />
      <path d="M5 11v6c0 1.66 3.13 3 7 3s7-1.34 7-3v-6" />
    </template>
    <template v-else-if="name === 'chat'">
      <path d="M21 15a2 2 0 0 1-2 2H8l-5 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v10Z" />
    </template>
    <template v-else-if="name === 'chevron-right'">
      <path d="M9 6l6 6-6 6" />
    </template>
    <template v-else-if="name === 'chevron-left'">
      <path d="M15 6l-6 6 6 6" />
    </template>
    <template v-else-if="name === 'search'">
      <circle cx="11" cy="11" r="7" />
      <path d="M20 20l-3.5-3.5" />
    </template>
    <template v-else-if="name === 'document'">
      <path d="M8 3h6l5 5v12a1 1 0 0 1-1 1H8a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z" />
      <path d="M14 3v5h5" />
    </template>
    <template v-else-if="name === 'download'">
      <path d="M12 3v11" />
      <path d="M8 10l4 4 4-4" />
      <path d="M4 20h16" />
    </template>
    <template v-else-if="name === 'trash'">
      <path d="M4 7h16" />
      <path d="M9 7V4h6v3" />
      <path d="M8 7v12" />
      <path d="M16 7v12" />
      <path d="M6 7l1 13h10l1-13" />
    </template>
    <template v-else-if="name === 'check-circle'">
      <circle cx="12" cy="12" r="9" />
      <path d="M8.5 12.5l2.3 2.3 4.7-5.3" />
    </template>
    <template v-else-if="name === 'clock'">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </template>
    <template v-else-if="name === 'chart'">
      <path d="M4 19h16" />
      <path d="M7 15l4-4 3 3 5-7" />
    </template>
    <template v-else-if="name === 'send'">
      <path d="M21 3L10 14" />
      <path d="M21 3l-7 18-4-7-7-4 18-7Z" />
    </template>
    <template v-else-if="name === 'plus'">
      <path d="M12 5v14" />
      <path d="M5 12h14" />
    </template>
    <template v-else-if="name === 'pin'">
      <path d="M15 3l6 6-3 1-3 5-2-2-5 3-1-1 3-5-2-2 5-3 1-3Z" />
    </template>
    <template v-else-if="name === 'edit'">
      <path d="M4 20l4.5-1 9.5-9.5-3.5-3.5L5 15.5 4 20Z" />
      <path d="M13.5 5.5L17 9" />
    </template>
    <template v-else-if="name === 'refresh'">
      <path d="M20 5v5h-5" />
      <path d="M4 19v-5h5" />
      <path d="M6.8 9A7 7 0 0 1 18 10" />
      <path d="M17.2 15A7 7 0 0 1 6 14" />
    </template>
    <template v-else-if="name === 'eye'">
      <path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6-10-6-10-6Z" />
      <circle cx="12" cy="12" r="2.5" />
    </template>
    <template v-else-if="name === 'target'">
      <circle cx="12" cy="12" r="9" />
      <circle cx="12" cy="12" r="5" />
      <circle cx="12" cy="12" r="1.5" />
    </template>
    <template v-else-if="name === 'filter'">
      <path d="M4 6h16" />
      <path d="M7 12h10" />
      <path d="M10 18h4" />
    </template>
    <template v-else-if="name === 'mic'">
      <path d="M12 16a4 4 0 0 0 4-4V8a4 4 0 0 0-8 0v4a4 4 0 0 0 4 4Z" />
      <path d="M19 12a7 7 0 0 1-14 0" />
      <path d="M12 19v3" />
    </template>
    <template v-else-if="name === 'arrow-up'">
      <path d="M12 20V5" />
      <path d="M6 11l6-6 6 6" />
    </template>
    <template v-else-if="name === 'menu'">
      <path d="M4 7h16" />
      <path d="M4 12h16" />
      <path d="M4 17h16" />
    </template>
  </svg>
</template>
