<script setup lang="ts">
import { RouterView } from 'vue-router';
import AppSidebar from './components/layout/AppSidebar.vue';
</script>

<template>
  <div class="app-frame">
    <AppSidebar />
    <main class="app-main">
      <div class="app-main__glow app-main__glow--one"></div>
      <div class="app-main__glow app-main__glow--two"></div>
      <RouterView />
    </main>
  </div>
</template>
