<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import type { QaMessage as QaMessageType } from '../composables/useQaSessions'
import type { CitationItem } from '@/api/qa'
import QaMessage from './QaMessage.vue'

const props = defineProps<{
  messages: QaMessageType[]
  sessionId: string
  groupName: string
}>()

const emit = defineEmits<{
  'inspect-citation': [citation: CitationItem]
}>()

const scrollRef = ref<HTMLElement | null>(null)

function scrollToBottom(smooth = true) {
  const el = scrollRef.value
  if (!el) return
  el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
}

watch(
  () => props.messages.length,
  async () => {
    await nextTick()
    scrollToBottom(true)
  },
)

watch(
  () => props.sessionId,
  async () => {
    await nextTick()
    scrollToBottom(false)
  },
)

// Also react to pending → answered content mutations
watch(
  () => props.messages.map((m) => m.content + (m.pending ? '1' : '0')).join('|'),
  async () => {
    await nextTick()
    scrollToBottom(true)
  },
)
</script>

<template>
  <div class="qa-transcript">
    <header class="qa-transcript__head">
      <div class="qa-transcript__head-left">
        <span class="qa-transcript__eyebrow">Active Thread</span>
        <h2 class="qa-transcript__title">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <ellipse cx="12" cy="5" rx="9" ry="3" />
            <path d="M3 5v7c0 1.7 4 3 9 3s9-1.3 9-3V5" />
            <path d="M3 12v7c0 1.7 4 3 9 3s9-1.3 9-3v-7" />
          </svg>
          <span>{{ groupName || '未指定知识库' }}</span>
        </h2>
      </div>
      <div class="qa-transcript__head-right">
        <span class="qa-transcript__counter">
          <span class="qa-transcript__counter-num">{{ messages.length }}</span>
          <span class="qa-transcript__counter-label">条消息</span>
        </span>
      </div>
    </header>

    <div ref="scrollRef" class="qa-transcript__scroll">
      <div class="qa-transcript__paper">
        <QaMessage
          v-for="msg in messages"
          :key="msg.id"
          :message="msg"
          @inspect-citation="(c) => emit('inspect-citation', c)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.qa-transcript {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fff;
  position: relative;
}

.qa-transcript__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding: 18px 32px 14px;
  border-bottom: 1px solid var(--border-subtle);
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  position: sticky;
  top: 0;
  z-index: 5;
  flex-shrink: 0;
}

.qa-transcript__head-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.qa-transcript__eyebrow {
  font-family: 'Poppins', sans-serif;
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--brand-primary);
}

.qa-transcript__title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 1rem;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.qa-transcript__title svg {
  color: var(--brand-primary);
}

.qa-transcript__counter {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.qa-transcript__counter-num {
  font-family: 'Poppins', sans-serif;
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

.qa-transcript__counter-label {
  font-size: 0.78rem;
  color: var(--text-muted);
}

/* Scrollable area with subtle grid paper background */
.qa-transcript__scroll {
  flex: 1;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--border-default) transparent;
  background-image:
    linear-gradient(rgba(74, 144, 217, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(74, 144, 217, 0.04) 1px, transparent 1px);
  background-size: 28px 28px;
  background-position: 0 0;
}

.qa-transcript__scroll::-webkit-scrollbar {
  width: 6px;
}

.qa-transcript__scroll::-webkit-scrollbar-thumb {
  background: var(--border-default);
  border-radius: 3px;
}

.qa-transcript__scroll::-webkit-scrollbar-thumb:hover {
  background: var(--text-muted);
}

.qa-transcript__paper {
  max-width: 860px;
  margin: 0 auto;
  padding: 8px 0 16px;
}

@media (max-width: 720px) {
  .qa-transcript__head {
    padding: 14px 18px 12px;
  }

  .qa-transcript__title {
    font-size: 0.92rem;
  }
}
</style>
