<script setup lang="ts">
import { ref, watch } from 'vue';
import AppIcon from './AppIcon.vue';

const props = withDefaults(defineProps<{
  modelValue: boolean;
  title: string;
  message?: string;
  placeholder?: string;
  defaultValue?: string;
  type?: 'input' | 'confirm';
  confirmText?: string;
  confirmVariant?: 'primary' | 'danger';
}>(), {
  type: 'input',
  confirmText: '确认',
  confirmVariant: 'primary',
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'confirm', value?: string): void;
  (e: 'cancel'): void;
}>();

const inputValue = ref(props.defaultValue || '');

watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    inputValue.value = props.defaultValue || '';
  }
});

watch(() => props.defaultValue, (newVal) => {
  inputValue.value = newVal || '';
});

function handleConfirm() {
  if (props.type === 'confirm') {
    emit('confirm');
    emit('update:modelValue', false);
  } else if (inputValue.value.trim()) {
    emit('confirm', inputValue.value.trim());
    emit('update:modelValue', false);
  }
}

function handleCancel() {
  emit('cancel');
  emit('update:modelValue', false);
}

function handleBackdropClick(e: MouseEvent) {
  if (e.target === e.currentTarget) {
    handleCancel();
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="modelValue" class="modal-overlay" @click="handleBackdropClick">
        <div class="modal-container">
          <div class="modal-header">
            <h3 class="modal-title">{{ title }}</h3>
            <button class="modal-close" type="button" @click="handleCancel">
              <AppIcon name="chevron-left" :size="18" style="transform: rotate(45deg);" />
            </button>
          </div>
          
          <div v-if="type === 'confirm'" class="modal-body">
            <p class="modal-message">{{ message }}</p>
          </div>
          
          <div v-else class="modal-body">
            <input
              v-model="inputValue"
              class="modal-input"
              type="text"
              :placeholder="placeholder || '请输入...'"
              @keydown.enter="handleConfirm"
              @keydown.esc="handleCancel"
            />
          </div>
          
          <div class="modal-footer">
            <button class="modal-btn modal-btn--secondary" type="button" @click="handleCancel">
              取消
            </button>
            <button 
              v-if="type === 'confirm'"
              class="modal-btn" 
              :class="confirmVariant === 'danger' ? 'modal-btn--danger' : 'modal-btn--primary'" 
              type="button" 
              @click="handleConfirm"
            >
              {{ confirmText }}
            </button>
            <button 
              v-else
              class="modal-btn modal-btn--primary" 
              type="button" 
              :disabled="!inputValue.trim()"
              @click="handleConfirm"
            >
              {{ confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.modal-container {
  background: var(--bg-card);
  border: 1px solid var(--border-soft);
  border-radius: 16px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
  width: 100%;
  max-width: 420px;
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border-soft);
}

.modal-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.modal-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: all 0.15s ease;
}

.modal-close:hover {
  background: var(--bg-soft);
  color: var(--text-primary);
}

.modal-body {
  padding: 24px;
}

.modal-message {
  margin: 0;
  font-size: 15px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.modal-input {
  width: 100%;
  height: 48px;
  padding: 0 16px;
  border-radius: 10px;
  border: 1px solid var(--border-soft);
  background: var(--bg-soft);
  color: var(--text-primary);
  font-size: 15px;
  transition: all 0.15s ease;
}

.modal-input:focus {
  outline: none;
  border-color: var(--primary);
  background: var(--bg-card);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.modal-input::placeholder {
  color: var(--text-tertiary);
}

.modal-footer {
  display: flex;
  gap: 12px;
  padding: 16px 24px 24px;
  justify-content: flex-end;
}

.modal-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 80px;
  height: 40px;
  padding: 0 20px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.modal-btn--secondary {
  border: 1px solid var(--border-soft);
  background: var(--bg-card);
  color: var(--text-secondary);
}

.modal-btn--secondary:hover {
  background: var(--bg-soft);
  color: var(--text-primary);
}

.modal-btn--primary {
  border: none;
  background: var(--primary);
  color: #fff;
}

.modal-btn--primary:hover {
  background: var(--primary-dark);
}

.modal-btn--danger {
  border: none;
  background: #ef4444;
  color: #fff;
}

.modal-btn--danger:hover {
  background: #dc2626;
}

.modal-btn--primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.modal-enter-active,
.modal-leave-active {
  transition: all 0.2s ease;
}

.modal-enter-active .modal-container,
.modal-leave-active .modal-container {
  transition: all 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .modal-container,
.modal-leave-to .modal-container {
  transform: scale(0.95) translateY(-10px);
  opacity: 0;
}
</style>
