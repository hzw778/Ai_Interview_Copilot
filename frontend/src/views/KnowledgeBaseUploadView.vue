<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppIcon from '../components/ui/AppIcon.vue';
import PageHeading from '../components/ui/PageHeading.vue';
import { knowledgeBaseApi } from '../api/knowledgebase';
import { getErrorMessage } from '../api/request';

const router = useRouter();
const file = ref<File | null>(null);
const name = ref('');
const category = ref('');
const uploading = ref(false);
const error = ref('');

const fileLabel = computed(() => file.value?.name || '点击选择或拖拽知识文档');

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  file.value = target.files?.[0] ?? null;
  error.value = '';
}

async function submit() {
  if (!file.value) {
    error.value = '请先选择要上传的文件';
    return;
  }
  uploading.value = true;
  error.value = '';
  try {
    await knowledgeBaseApi.uploadKnowledgeBase(file.value, name.value || undefined, category.value || undefined);
    router.push('/knowledgebase');
  } catch (err) {
    error.value = getErrorMessage(err);
  } finally {
    uploading.value = false;
  }
}
</script>

<template>
  <section class="page">
    <PageHeading
      centered
      icon="database"
      title="上传知识库"
      subtitle="导入文档后即可在问答助手中按知识库检索内容"
    />

    <div class="surface-card surface-card--padded glow-card" style="max-width: 1040px; margin: 0 auto;">
      <div class="upload-dropzone">
        <div class="upload-dropzone__icon">
          <AppIcon name="database" :size="56" />
        </div>
        <div style="text-align: center;">
          <p class="upload-dropzone__title">{{ fileLabel }}</p>
          <p class="upload-dropzone__desc">支持 PDF、DOCX、TXT、Markdown 等常见文档</p>
        </div>

        <div class="split-grid" style="grid-template-columns: 1fr 1fr; width: min(640px, 100%);">
          <input v-model="name" class="input-field" placeholder="自定义名称（可选）" />
          <input v-model="category" class="input-field" placeholder="分类（可选）" />
        </div>

        <label class="action-btn action-btn--primary" style="min-width: 220px;">
          <AppIcon name="upload" :size="20" />
          <span>{{ uploading ? '上传中...' : '选择知识文档' }}</span>
          <input class="hidden-file-input" type="file" @change="onFileChange" />
        </label>
      </div>

      <div v-if="error" class="surface-card surface-card--compact" style="margin-top: 18px; border-color: rgba(255,74,88,.18); color: #db4252;">
        {{ error }}
      </div>

      <div class="toolbar-actions" style="justify-content: center; margin-top: 22px;">
        <button class="action-btn action-btn--primary" type="button" :disabled="uploading || !file" @click="submit">
          开始上传
        </button>
        <button class="action-btn" type="button" @click="router.push('/knowledgebase')">返回管理页</button>
      </div>
    </div>
  </section>
</template>
