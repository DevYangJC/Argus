<script setup lang="ts">
import { ref, computed, nextTick, watch } from 'vue'

const props = defineProps<{
  disabled: boolean
  loading: boolean
  placeholder?: string
  groupName?: string
}>()

const emit = defineEmits<{
  submit: [text: string]
}>()

const text = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)

const hint = computed(() =>
  props.disabled
    ? '请先在左侧选择一个知识库群组'
    : props.loading
      ? '正在等待回答…'
      : props.placeholder || '输入你的问题，按 Enter 发送，Shift + Enter 换行',
)

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  const max = 200
  el.style.height = Math.min(el.scrollHeight, max) + 'px'
}

watch(text, () => {
  nextTick(autoResize)
})

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    submit()
  }
}

function submit() {
  const v = text.value.trim()
  if (!v || props.disabled || props.loading) return
  emit('submit', v)
  text.value = ''
  nextTick(autoResize)
}

function focus() {
  textareaRef.value?.focus()
}

function setText(value: string) {
  text.value = value
  nextTick(() => {
    autoResize()
    textareaRef.value?.focus()
  })
}

defineExpose({ focus, setText })
</script>

<template>
  <div class="qa-composer">
    <div class="qa-composer__wrap">
      <!-- Context chip -->
      <div v-if="groupName" class="qa-composer__context">
        <span class="qa-composer__context-dot" />
        <span class="qa-composer__context-text">正在询问：<strong>{{ groupName }}</strong></span>
      </div>

      <div class="qa-composer__shell" :class="{ 'is-disabled': disabled }">
        <textarea
          ref="textareaRef"
          v-model="text"
          :disabled="disabled || loading"
          class="qa-composer__input"
          rows="1"
          :placeholder="hint"
          @keydown="onKeydown"
        />

        <div class="qa-composer__bar">
          <div class="qa-composer__bar-left">
            <kbd class="qa-composer__kbd">Enter</kbd>
            <span class="qa-composer__kbd-label">发送</span>
            <span class="qa-composer__kbd-sep">·</span>
            <kbd class="qa-composer__kbd">Shift + Enter</kbd>
            <span class="qa-composer__kbd-label">换行</span>
          </div>
          <button
            class="qa-composer__send"
            type="button"
            :disabled="!text.trim() || disabled || loading"
            @click="submit"
          >
            <template v-if="loading">
              <span class="qa-composer__spinner" />
              <span>生成中</span>
            </template>
            <template v-else>
              <span>发送</span>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
                <line x1="5" y1="12" x2="19" y2="12" />
                <polyline points="12 5 19 12 12 19" />
              </svg>
            </template>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.qa-composer {
  flex-shrink: 0;
  padding: 18px 24px 22px;
  background: linear-gradient(to top, #fff 70%, rgba(255, 255, 255, 0));
  position: relative;
}

.qa-composer__wrap {
  max-width: 860px;
  margin: 0 auto;
}

.qa-composer__context {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 8px;
  padding: 3px 12px;
  background: rgba(92, 201, 193, 0.08);
  border: 1px solid rgba(92, 201, 193, 0.2);
  border-radius: 100px;
  font-size: 0.76rem;
  color: var(--text-secondary);
}

.qa-composer__context-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-accent);
  animation: pulse-ctx 2s ease-in-out infinite;
}

@keyframes pulse-ctx {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.qa-composer__context-text strong {
  color: var(--text-primary);
  font-weight: 600;
}

/* Shell */
.qa-composer__shell {
  background: #fff;
  border: 1.5px solid var(--border-default);
  border-radius: 16px;
  padding: 12px 14px 10px;
  box-shadow:
    0 4px 14px rgba(0, 0, 0, 0.04),
    0 1px 2px rgba(0, 0, 0, 0.03);
  transition: all 0.2s ease;
}

.qa-composer__shell:focus-within {
  border-color: var(--brand-primary);
  box-shadow:
    0 0 0 4px rgba(74, 144, 217, 0.08),
    0 8px 20px rgba(74, 144, 217, 0.08);
}

.qa-composer__shell.is-disabled {
  background: var(--surface-subtle);
  opacity: 0.85;
}

.qa-composer__input {
  width: 100%;
  min-height: 44px;
  max-height: 200px;
  padding: 4px 2px;
  border: none;
  outline: none;
  resize: none;
  font-family: inherit;
  font-size: 0.96rem;
  line-height: 1.6;
  color: var(--text-primary);
  background: transparent;
}

.qa-composer__input::placeholder {
  color: var(--text-muted);
}

.qa-composer__input:disabled {
  cursor: not-allowed;
}

/* Bar */
.qa-composer__bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--border-subtle);
}

.qa-composer__bar-left {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 0.72rem;
  color: var(--text-muted);
}

.qa-composer__kbd {
  display: inline-block;
  padding: 2px 7px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.68rem;
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--surface-subtle);
  border: 1px solid var(--border-default);
  border-radius: 4px;
  box-shadow: 0 1px 0 var(--border-default);
}

.qa-composer__kbd-label {
  margin-right: 2px;
}

.qa-composer__kbd-sep {
  opacity: 0.5;
  margin: 0 2px;
}

/* Send */
.qa-composer__send {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 18px;
  font-family: inherit;
  font-size: 0.84rem;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: #fff;
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-dark));
  border: none;
  border-radius: 10px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(74, 144, 217, 0.25);
  transition: all 0.2s ease;
}

.qa-composer__send:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(74, 144, 217, 0.35);
}

.qa-composer__send:active:not(:disabled) {
  transform: translateY(0);
}

.qa-composer__send:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  background: linear-gradient(135deg, #94a3b8, #64748b);
  box-shadow: none;
}

.qa-composer__spinner {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
