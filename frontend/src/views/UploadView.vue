<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import PageHeading from '../components/ui/PageHeading.vue';
import AppIcon from '../components/ui/AppIcon.vue';
import { resumeApi } from '../api/resume';
import { getErrorMessage } from '../api/request';

const router = useRouter();
const selectedFile = ref<File | null>(null);
const uploading = ref(false);
const error = ref('');
const success = ref('');

const filename = computed(() => selectedFile.value?.name || '点击或拖拽文件至此处');

function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  selectedFile.value = target.files?.[0] ?? null;
  error.value = '';
}

async function submit() {
  if (!selectedFile.value) {
    error.value = '请先选择简历文件';
    return;
  }
  uploading.value = true;
  error.value = '';
  success.value = '';

  try {
    const result = await resumeApi.uploadAndAnalyze(selectedFile.value);
    const resumeId = result.storage.resumeId ?? result.resume?.id;
    if (result.duplicate) {
      success.value = '已上传过相同简历，无需重复上传。';
      selectedFile.value = null;
      return;
    }

    success.value = '上传成功，AI 正在生成分析结果。';

    if (resumeId) {
      router.push(`/history/${resumeId}`);
      return;
    }
    router.push('/history');
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
      title="开始您的 AI 模拟面试"
      subtitle="上传 PDF 或 Word 简历，AI 将为您定制专属面试方案"
    />

    <div class="surface-card surface-card--padded glow-card" style="max-width: 1000px; width: 100%; margin: 0 auto;">
      <div class="upload-dropzone upload-dropzone--wide">
        <div class="upload-dropzone__icon">
          <AppIcon name="upload" :size="56" />
        </div>
        <div style="text-align: center;">
          <p class="upload-dropzone__title">{{ filename }}</p>
          <p class="upload-dropzone__desc">支持 PDF、DOCX、TXT（最大 10MB）</p>
        </div>

        <label class="action-btn action-btn--primary" style="min-width: 200px;">
          <AppIcon name="document" :size="20" />
          <span>{{ uploading ? '上传中...' : '选择简历文件' }}</span>
          <input class="hidden-file-input" accept=".pdf,.doc,.docx,.txt" type="file" @change="handleFileChange" />
        </label>
      </div>

      <div v-if="error" class="surface-card surface-card--compact" style="margin-top: 16px; border-color: rgba(239, 68, 68, 0.2); color: #dc2626;">
        {{ error }}
      </div>
      <div v-if="success" class="surface-card surface-card--compact" style="margin-top: 16px; border-color: rgba(16, 185, 129, 0.2); color: #059669;">
        {{ success }}
      </div>

      <div class="toolbar-actions" style="justify-content: center; margin-top: 20px;">
        <button class="action-btn action-btn--primary" type="button" :disabled="uploading || !selectedFile" @click="submit">
          {{ uploading ? '正在上传...' : '开始上传并分析' }}
        </button>
      </div>
    </div>
  </section>
</template>
