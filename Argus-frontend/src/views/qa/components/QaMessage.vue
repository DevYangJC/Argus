<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import type { QaMessage } from '../composables/useQaSessions'
import type { CitationItem } from '@/api/qa'
import CitationRail from './CitationRail.vue'

const props = defineProps<{
  message: QaMessage
}>()

const emit = defineEmits<{
  'inspect-citation': [citation: CitationItem]
}>()

marked.setOptions({
  gfm: true,
  breaks: true,
})

const rendered = computed(() => {
  if (props.message.role === 'user') {
    return ''
  }
  const raw = props.message.content || ''
  if (!raw.trim()) return ''
  try {
    return marked.parse(raw) as string
  } catch {
    return `<p>${escapeHtml(raw)}</p>`
  }
})

function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => {
    const map: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }
    return map[c] ?? c
  })
}

function formatTime(ts: number): string {
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<template>
  <article class="qa-msg" :class="`qa-msg--${message.role}`">
    <div class="qa-msg__avatar">
      <template v-if="message.role === 'user'">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
          <circle cx="12" cy="7" r="4" />
        </svg>
      </template>
      <template v-else>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 3L4 7v5c0 5 3.5 9 8 10 4.5-1 8-5 8-10V7l-8-4z" />
          <path d="M9 12l2 2 4-4" />
        </svg>
      </template>
    </div>

    <div class="qa-msg__body">
      <header class="qa-msg__head">
        <span class="qa-msg__role">{{ message.role === 'user' ? 'You' : 'Argus' }}</span>
        <span class="qa-msg__time">{{ formatTime(message.createdAt) }}</span>
      </header>

      <!-- User message -->
      <div v-if="message.role === 'user'" class="qa-msg__user-text">
        {{ message.content }}
      </div>

      <!-- Assistant pending / thinking (no content yet) -->
      <div v-else-if="message.pending && !message.content" class="qa-msg__thinking">
        <span class="qa-msg__thinking-dot" />
        <span class="qa-msg__thinking-dot" />
        <span class="qa-msg__thinking-dot" />
        <span class="qa-msg__thinking-label">正在检索知识库并生成回答…</span>
      </div>

      <!-- Assistant streaming content -->
      <div v-else-if="message.pending" class="qa-msg__streaming">
        <div class="qa-msg__markdown" v-html="rendered" />
        <span class="qa-msg__cursor" />
      </div>

      <!-- Assistant refused -->
      <div v-else-if="message.answered === false" class="qa-msg__refused">
        <div class="qa-msg__refused-head">
          <div class="qa-msg__refused-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
          </div>
          <span class="qa-msg__refused-title">未能回答</span>
          <code v-if="message.reasonCode" class="qa-msg__refused-code">{{ message.reasonCode }}</code>
        </div>
        <p class="qa-msg__refused-text">
          {{ message.reasonMessage || '检索结果的置信度不足以给出可靠回答。请尝试补充更多上下文，或先在文档中心确认相关资料已上传并完成处理。' }}
        </p>
      </div>

      <!-- Assistant answer markdown -->
      <div v-else class="qa-msg__markdown" v-html="rendered" />

      <!-- Citations -->
      <CitationRail
        v-if="message.role === 'assistant' && message.citations && message.citations.length > 0"
        :citations="message.citations"
        @inspect="(c) => emit('inspect-citation', c)"
      />
    </div>
  </article>
</template>

<style scoped>
.qa-msg {
  display: flex;
  gap: 16px;
  padding: 22px 32px 22px 28px;
  position: relative;
  animation: qa-msg-in 0.35s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes qa-msg-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.qa-msg--user {
  background: transparent;
}

.qa-msg--assistant {
  background: linear-gradient(180deg, rgba(250, 250, 247, 0.6), rgba(250, 250, 247, 0.3));
  border-top: 1px solid rgba(15, 23, 42, 0.05);
  border-bottom: 1px solid rgba(15, 23, 42, 0.05);
}

.qa-msg__avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.qa-msg__avatar svg {
  width: 16px;
  height: 16px;
}

.qa-msg--user .qa-msg__avatar {
  background: linear-gradient(135deg, #1e293b, #0f172a);
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.2);
}

.qa-msg--assistant .qa-msg__avatar {
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-accent));
  box-shadow: 0 2px 8px rgba(74, 144, 217, 0.25);
}

.qa-msg__body {
  flex: 1;
  min-width: 0;
}

.qa-msg__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 8px;
}

.qa-msg__role {
  font-family: 'Poppins', sans-serif;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.02em;
}

.qa-msg__time {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.7rem;
  color: var(--text-muted);
}

/* User text */
.qa-msg__user-text {
  font-size: 0.95rem;
  line-height: 1.65;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

/* Thinking */
.qa-msg__thinking {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: #fff;
  border: 1px solid var(--border-default);
  border-radius: 100px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.qa-msg__thinking-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-primary);
  animation: thinking-bounce 1.3s ease-in-out infinite;
}

.qa-msg__thinking-dot:nth-child(2) { animation-delay: 0.15s; }
.qa-msg__thinking-dot:nth-child(3) { animation-delay: 0.3s; }

@keyframes thinking-bounce {
  0%, 80%, 100% { transform: scale(0.5); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

.qa-msg__thinking-label {
  margin-left: 6px;
  font-size: 0.8rem;
  color: var(--text-secondary);
  letter-spacing: 0.01em;
}

/* Streaming */
.qa-msg__streaming {
  display: inline;
}

.qa-msg__cursor {
  display: inline-block;
  width: 1px;
  height: 1.15em;
  margin-left: 1px;
  background: var(--brand-primary, #3b82f6);
  vertical-align: text-bottom;
  animation: cursor-blink 1s step-end infinite;
}

@keyframes cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* Refused */
.qa-msg__refused {
  padding: 14px 16px;
  background: rgba(239, 68, 68, 0.04);
  border: 1px solid rgba(239, 68, 68, 0.15);
  border-radius: 10px;
}

.qa-msg__refused-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.qa-msg__refused-icon {
  display: flex;
  color: #dc2626;
}

.qa-msg__refused-title {
  font-family: 'Poppins', sans-serif;
  font-size: 0.85rem;
  font-weight: 700;
  color: #dc2626;
}

.qa-msg__refused-code {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.7rem;
  font-weight: 600;
  color: #b91c1c;
  background: rgba(239, 68, 68, 0.1);
  padding: 2px 8px;
  border-radius: 4px;
}

.qa-msg__refused-text {
  margin: 0;
  font-size: 0.87rem;
  color: var(--text-secondary);
  line-height: 1.6;
}

/* Markdown styles — scoped :deep for v-html */
.qa-msg__markdown {
  font-size: 0.93rem;
  line-height: 1.7;
  color: var(--text-primary);
  word-break: break-word;
}

.qa-msg__markdown :deep(h1),
.qa-msg__markdown :deep(h2),
.qa-msg__markdown :deep(h3),
.qa-msg__markdown :deep(h4) {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.3;
  margin: 16px 0 8px;
}

.qa-msg__markdown :deep(h1) { font-size: 1.4rem; }
.qa-msg__markdown :deep(h2) { font-size: 1.2rem; }
.qa-msg__markdown :deep(h3) { font-size: 1.05rem; }
.qa-msg__markdown :deep(h4) { font-size: 0.95rem; }

.qa-msg__markdown :deep(p) {
  margin: 10px 0;
}

.qa-msg__markdown :deep(ul),
.qa-msg__markdown :deep(ol) {
  margin: 10px 0;
  padding-left: 24px;
}

.qa-msg__markdown :deep(ul) { list-style: disc; }
.qa-msg__markdown :deep(ol) { list-style: decimal; }

.qa-msg__markdown :deep(li) {
  margin: 4px 0;
  padding-left: 3px;
}

.qa-msg__markdown :deep(li::marker) {
  color: var(--brand-primary);
}

.qa-msg__markdown :deep(code) {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.84em;
  padding: 1px 6px;
  background: rgba(15, 23, 42, 0.06);
  border-radius: 4px;
  color: var(--brand-primary-dark);
}

.qa-msg__markdown :deep(pre) {
  margin: 12px 0;
  padding: 14px 16px;
  background: #0f172a;
  border-radius: 10px;
  overflow-x: auto;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.82rem;
  line-height: 1.6;
  color: #e2e8f0;
  border: 1px solid #1e293b;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.15);
}

.qa-msg__markdown :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
  font-size: inherit;
}

.qa-msg__markdown :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 16px;
  border-left: 3px solid var(--brand-primary);
  background: rgba(74, 144, 217, 0.06);
  border-radius: 0 6px 6px 0;
  color: var(--text-secondary);
  font-style: italic;
}

.qa-msg__markdown :deep(a) {
  color: var(--brand-primary);
  text-decoration: underline;
  text-underline-offset: 2px;
  text-decoration-thickness: 1px;
  transition: color 0.15s ease;
}

.qa-msg__markdown :deep(a:hover) {
  color: var(--brand-primary-dark);
}

.qa-msg__markdown :deep(strong) {
  font-weight: 700;
  color: var(--text-primary);
}

.qa-msg__markdown :deep(hr) {
  margin: 18px 0;
  border: none;
  border-top: 1px dashed var(--border-default);
}

.qa-msg__markdown :deep(table) {
  width: 100%;
  margin: 12px 0;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.qa-msg__markdown :deep(th),
.qa-msg__markdown :deep(td) {
  padding: 8px 12px;
  border: 1px solid var(--border-default);
  text-align: left;
}

.qa-msg__markdown :deep(th) {
  background: var(--surface-subtle);
  font-weight: 700;
  color: var(--text-primary);
}

.qa-msg__markdown :deep(img) {
  max-width: 100%;
  border-radius: 8px;
  margin: 12px 0;
}

@media (max-width: 720px) {
  .qa-msg {
    padding: 18px 18px 18px 16px;
    gap: 12px;
  }
}
</style>
