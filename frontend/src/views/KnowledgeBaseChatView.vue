<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import AppIcon from '../components/ui/AppIcon.vue';
import ModalDialog from '../components/ui/ModalDialog.vue';
import PageHeading from '../components/ui/PageHeading.vue';
import { knowledgeBaseApi, type KnowledgeBaseItem } from '../api/knowledgebase';
import { getErrorMessage } from '../api/request';
import { ragChatApi, type RagChatSessionDetail, type RagChatSessionListItem } from '../api/ragChat';
import { renderChatMarkdown } from '../utils/chatMarkdown';
import { formatDateTime } from '../utils/date';
import { formatFileSize } from '../utils/format';

interface ChatMessage {
  id?: number;
  type: 'user' | 'assistant';
  content: string;
  createdAt: string;
}

const router = useRouter();
const knowledgeBases = ref<KnowledgeBaseItem[]>([]);
const sessions = ref<RagChatSessionListItem[]>([]);
const selectedKbIds = ref<number[]>([]);
const selectedSessionId = ref<number | null>(null);
const selectedSessionTitle = ref('新会话');
const messages = ref<ChatMessage[]>([]);
const question = ref('');
const loading = ref(false);
const searchKeyword = ref('');
const error = ref('');
const messageScrollRef = ref<HTMLElement | null>(null);
const showRenameModal = ref(false);
const renamingSession = ref<RagChatSessionListItem | null>(null);
const showDeleteModal = ref(false);
const deletingSession = ref<RagChatSessionListItem | null>(null);

const emptyStateTitle = computed(() =>
  selectedKbIds.value.length > 0 ? '开始知识库问答' : '选择知识库后开始提问'
);

const emptyStateText = computed(() =>
  selectedKbIds.value.length > 0
    ? '请直接输入技术问题、面试题或文档总结需求，系统会基于当前知识库返回结构化答案。'
    : '请先在右侧勾选知识库，再输入技术问题、面试题或文档总结需求。'
);

const filteredKnowledgeBases = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase();
  if (!keyword) {
    return knowledgeBases.value;
  }
  return knowledgeBases.value.filter((item) =>
    item.name.toLowerCase().includes(keyword) || item.originalFilename.toLowerCase().includes(keyword)
  );
});

async function loadKnowledgeBases() {
  knowledgeBases.value = await knowledgeBaseApi.getAllKnowledgeBases('time', 'COMPLETED').catch(() => []);
}

async function loadSessions() {
  sessions.value = await ragChatApi.listSessions().catch(() => []);
}

async function openSession(sessionId: number) {
  error.value = '';
  const detail: RagChatSessionDetail = await ragChatApi.getSessionDetail(sessionId);
  selectedSessionId.value = detail.id;
  selectedSessionTitle.value = detail.title;
  selectedKbIds.value = detail.knowledgeBases.map((item) => item.id);
  messages.value = detail.messages.map((item) => ({
    id: item.id,
    type: item.type,
    content: item.content,
    createdAt: item.createdAt,
  }));
}

function toggleKnowledgeBase(id: number) {
  selectedKbIds.value = selectedKbIds.value.includes(id)
    ? selectedKbIds.value.filter((item) => item !== id)
    : [...selectedKbIds.value, id];

  if (selectedSessionId.value) {
    selectedSessionId.value = null;
    selectedSessionTitle.value = '新会话';
    messages.value = [];
  }
}

async function ensureSession() {
  if (selectedSessionId.value) {
    return selectedSessionId.value;
  }
  const created = await ragChatApi.createSession(selectedKbIds.value);
  selectedSessionId.value = created.id;
  selectedSessionTitle.value = created.title;
  await loadSessions();
  return created.id;
}

async function sendQuestion() {
  if (!question.value.trim() || selectedKbIds.value.length === 0 || loading.value) {
    return;
  }

  const content = question.value.trim();
  question.value = '';
  error.value = '';
  loading.value = true;

  messages.value.push({
    type: 'user',
    content,
    createdAt: new Date().toISOString(),
  });
  messages.value.push({
    type: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
  });

  try {
    const sessionId = await ensureSession();
    const replyIndex = messages.value.length - 1;
    await ragChatApi.sendMessageStream(
      sessionId,
      content,
      (chunk) => {
        messages.value[replyIndex].content += chunk;
      },
      async () => {
        loading.value = false;
        await loadSessions();
      },
      (streamError) => {
        messages.value[replyIndex].content = '抱歉，这次回答生成失败了，请稍后再试。';
        loading.value = false;
        error.value = streamError.message;
      }
    );
  } catch (err) {
    loading.value = false;
    error.value = getErrorMessage(err);
  }
}

async function removeSession(item: RagChatSessionListItem) {
  deletingSession.value = item;
  showDeleteModal.value = true;
}

async function handleDeleteConfirm() {
  if (!deletingSession.value) return;
  await ragChatApi.deleteSession(deletingSession.value.id);
  if (selectedSessionId.value === deletingSession.value.id) {
    selectedSessionId.value = null;
    selectedSessionTitle.value = '新会话';
    messages.value = [];
  }
  await loadSessions();
  deletingSession.value = null;
}

async function renameSession(item: RagChatSessionListItem) {
  renamingSession.value = item;
  showRenameModal.value = true;
}

async function handleRenameConfirm(newTitle?: string) {
  if (!renamingSession.value || !newTitle?.trim()) return;
  await ragChatApi.updateSessionTitle(renamingSession.value.id, newTitle.trim());
  if (selectedSessionId.value === renamingSession.value.id) {
    selectedSessionTitle.value = newTitle.trim();
  }
  await loadSessions();
  renamingSession.value = null;
}

async function togglePin(item: RagChatSessionListItem) {
  await ragChatApi.togglePin(item.id);
  await loadSessions();
}

function resetSession() {
  selectedSessionId.value = null;
  selectedSessionTitle.value = '新会话';
  messages.value = [];
  error.value = '';
  question.value = '';
}

function renderAssistantContent(content: string) {
  return renderChatMarkdown(content);
}

async function scrollToBottom() {
  await nextTick();
  const el = messageScrollRef.value;
  if (!el) {
    return;
  }
  el.scrollTop = el.scrollHeight;
}

watch(messages, scrollToBottom, { deep: true });
watch(selectedSessionId, scrollToBottom);

onMounted(async () => {
  await Promise.all([loadKnowledgeBases(), loadSessions()]);
});
</script>

<template>
  <section class="page">
    <div class="toolbar-row">
      <PageHeading title="问答助手" subtitle="选择知识库，向 AI 提问" />

      <div class="toolbar-actions">
        <button class="action-btn" type="button" @click="router.push('/knowledgebase/upload')">上传知识库</button>
        <button class="action-btn" type="button" @click="router.push('/knowledgebase')">返回</button>
      </div>
    </div>

    <div class="history-layout">
      <div class="surface-card surface-card--padded">
        <div class="toolbar-row" style="margin-bottom: 18px;">
          <h2 class="section-title" style="margin-bottom: 0;">对话历史</h2>
          <button class="icon-btn" type="button" @click="resetSession">
            <AppIcon name="plus" :size="18" />
          </button>
        </div>

        <div class="history-list">
          <button
            v-for="item in sessions"
            :key="item.id"
            class="history-item"
            :class="{ 'history-item--active': selectedSessionId === item.id }"
            type="button"
            @click="openSession(item.id)"
          >
            <div style="display: flex; align-items: flex-start; gap: 10px;">
              <div style="flex: 1; min-width: 0;">
                <p class="history-item__title" style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">{{ item.title }}</p>
                <p class="history-item__meta">{{ item.messageCount }} 条消息 · {{ formatDateTime(item.updatedAt) }}</p>
              </div>
              <div class="history-item__actions">
                <button 
                  class="history-action-btn history-action-btn--pin" 
                  :class="{ 'is-pinned': item.isPinned }"
                  type="button" 
                  title="置顶"
                  @click.stop="togglePin(item)"
                >
                  <AppIcon name="pin" :size="14" />
                </button>
                <button 
                  class="history-action-btn history-action-btn--edit" 
                  type="button" 
                  title="重命名"
                  @click.stop="renameSession(item)"
                >
                  <AppIcon name="edit" :size="14" />
                </button>
                <button 
                  class="history-action-btn history-action-btn--delete" 
                  type="button" 
                  title="删除"
                  @click.stop="removeSession(item)"
                >
                  <AppIcon name="trash" :size="14" />
                </button>
              </div>
            </div>
          </button>

          <div v-if="sessions.length === 0" class="empty-state">还没有历史对话。</div>
        </div>
      </div>

      <div class="surface-card surface-card--padded doc-viewer" style="height: calc(100vh - 210px);">
        <div class="toolbar-row" style="margin-bottom: 18px;">
          <div>
            <div class="table-item-title">{{ selectedSessionTitle }}</div>
            <div class="chip-list" style="margin-top: 10px;">
              <span
                v-for="item in knowledgeBases.filter((kb) => selectedKbIds.includes(kb.id))"
                :key="item.id"
                class="tag-pill"
              >
                {{ item.name }}
              </span>
            </div>
          </div>
        </div>

        <div ref="messageScrollRef" class="doc-viewer__scroll" style="overflow-y: auto;">
          <div class="split-grid" style="grid-template-columns: 1fr; gap: 16px; padding-bottom: 8px;">
            <div
              v-for="(message, index) in messages"
              :key="message.id ?? `${message.type}-${index}`"
              class="markdown-surface chat-message"
              :class="{
                'chat-message--user': message.type === 'user',
                'chat-message--assistant': message.type === 'assistant',
              }"
            >
              <div class="chat-message__header">
                <span class="chat-message__role">
                  {{ message.type === 'assistant' ? '知识库回答' : '我的问题' }}
                </span>
                <span class="chat-message__meta">{{ formatDateTime(message.createdAt) }}</span>
              </div>

              <div v-if="message.type === 'assistant'" class="chat-message__body">
                <div
                  v-if="!message.content && loading && index === messages.length - 1"
                  class="chat-streaming"
                >
                  <span class="chat-streaming__dot"></span>
                  <span class="chat-streaming__dot"></span>
                  <span class="chat-streaming__dot"></span>
                  <span>正在检索知识库并组织回答...</span>
                </div>
                <div
                  v-else
                  class="chat-markdown"
                  v-html="renderAssistantContent(message.content)"
                ></div>
              </div>

              <div v-else class="chat-message__body chat-user-text">
                {{ message.content }}
              </div>
            </div>

            <div v-if="messages.length === 0" class="chat-empty-state">
              <div class="chat-empty-state__badge">
                {{ selectedKbIds.length > 0 ? '已连接知识库' : '等待选择知识库' }}
              </div>
              <div class="chat-empty-state__title">{{ emptyStateTitle }}</div>
              <div class="chat-empty-state__text">{{ emptyStateText }}</div>
            </div>
          </div>
        </div>

        <div v-if="error" class="surface-card surface-card--compact" style="margin-top: 16px; border-color: rgba(255,74,88,.18); color: #db4252;">
          {{ error }}
        </div>

        <div class="chat-composer">
          <textarea
            v-model="question"
            class="textarea-field"
            placeholder="请输入技术问题、面试题或文档总结需求..."
            @keydown.ctrl.enter.prevent="sendQuestion"
            @keydown.meta.enter.prevent="sendQuestion"
          ></textarea>
          <button
            class="action-btn action-btn--primary"
            type="button"
            :disabled="loading || selectedKbIds.length === 0 || !question.trim()"
            @click="sendQuestion"
          >
            发送
          </button>
        </div>
      </div>

      <div class="surface-card surface-card--padded kb-selector">
        <div class="toolbar-row">
          <h2 class="section-title" style="margin-bottom: 0;">选择知识库</h2>
          <AppIcon name="chevron-left" :size="18" style="transform: rotate(180deg); color: #93a4c1;" />
        </div>

        <input v-model="searchKeyword" class="input-field" placeholder="搜索..." />

        <select class="select-field">
          <option>时间排序</option>
        </select>

        <div class="kb-selector__group">
          <div class="toolbar-row" style="margin-bottom: 8px;">
            <div class="toolbar-actions" style="gap: 8px;">
              <AppIcon name="chevron-left" :size="16" style="transform: rotate(-90deg); color: #8ea0bc;" />
              <span class="table-item-title" style="font-size: 18px;">面试</span>
            </div>
            <span class="table-item-subtitle">{{ filteredKnowledgeBases.length }}</span>
          </div>

          <label
            v-for="item in filteredKnowledgeBases"
            :key="item.id"
            class="kb-item"
            :class="{ 'kb-item--active': selectedKbIds.includes(item.id) }"
          >
            <input
              type="checkbox"
              :checked="selectedKbIds.includes(item.id)"
              style="margin-top: 5px;"
              @change="toggleKnowledgeBase(item.id)"
            />
            <div>
              <p class="kb-item__title">{{ item.name }}</p>
              <p class="kb-item__meta">{{ formatFileSize(item.fileSize) }}</p>
            </div>
          </label>
        </div>
      </div>
    </div>

    <ModalDialog
      v-model="showRenameModal"
      title="重命名会话"
      placeholder="请输入新的会话标题"
      :default-value="renamingSession?.title || ''"
      @confirm="handleRenameConfirm"
    />

    <ModalDialog
      v-model="showDeleteModal"
      type="confirm"
      title="删除会话"
      :message="`确定要删除会话「${deletingSession?.title || ''}」吗？此操作无法撤销。`"
      confirm-text="删除"
      confirm-variant="danger"
      @confirm="handleDeleteConfirm"
    />
  </section>
</template>
