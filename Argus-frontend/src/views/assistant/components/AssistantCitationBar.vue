<script setup lang="ts">
import type { AssistantCitationItem } from '@/types/assistant'

defineProps<{
  citations: AssistantCitationItem[]
}>()

const emit = defineEmits<{
  inspect: [citation: AssistantCitationItem]
}>()

function formatScore(score: number): string {
  if (!Number.isFinite(score)) return '--'
  return (score * 100).toFixed(1) + '%'
}

function fileTag(fileName: string): string {
  const ext = fileName.toLowerCase().split('.').pop() ?? ''
  if (ext === 'pdf') return 'PDF'
  if (ext === 'md') return 'MD'
  if (ext === 'docx' || ext === 'doc') return 'DOC'
  if (ext === 'txt') return 'TXT'
  return ext.toUpperCase() || 'DOC'
}

function tagClass(fileName: string): string {
  const ext = fileName.toLowerCase().split('.').pop() ?? ''
  if (ext === 'pdf') return 'citebar__chip-tag--pdf'
  if (ext === 'md') return 'citebar__chip-tag--md'
  if (ext === 'docx' || ext === 'doc') return 'citebar__chip-tag--doc'
  return 'citebar__chip-tag--txt'
}
</script>

<template>
  <div class="citebar">
    <div class="citebar__head">
      <span class="citebar__eyebrow">Evidence Chain</span>
      <span class="citebar__title">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
          <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
        </svg>
        <strong>引用证据</strong>
        <span class="citebar__count">{{ citations.length }}</span>
      </span>
    </div>

    <div class="citebar__grid">
      <button
        v-for="(c, idx) in citations"
        :key="`${c.documentId ?? 'x'}-${c.chunkId ?? idx}`"
        class="citebar__chip"
        type="button"
        :disabled="c.documentId === null"
        @click="c.documentId !== null && emit('inspect', c)"
      >
        <span class="citebar__chip-no">{{ String(idx + 1).padStart(2, '0') }}</span>
        <span class="citebar__chip-tag" :class="tagClass(c.fileName)">{{ fileTag(c.fileName) }}</span>
        <span class="citebar__chip-name" :title="c.fileName">{{ c.fileName }}</span>
        <span class="citebar__chip-score">{{ formatScore(c.score) }}</span>
        <svg class="citebar__chip-arrow" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
          <line x1="7" y1="17" x2="17" y2="7" />
          <polyline points="7 7 17 7 17 17" />
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.citebar {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px dashed rgba(15, 23, 42, 0.1);
}

.citebar__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 10px;
  padding-left: 2px;
}

.citebar__eyebrow {
  font-family: 'Poppins', sans-serif;
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--brand-primary);
}

.citebar__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.82rem;
  color: var(--text-secondary);
}

.citebar__title svg {
  color: var(--brand-primary);
}

.citebar__title strong {
  font-weight: 600;
  color: var(--text-primary);
}

.citebar__count {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--text-muted);
  background: rgba(148, 163, 184, 0.14);
  padding: 1px 7px;
  border-radius: 100px;
}

.citebar__grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.citebar__chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  padding: 5px 10px 5px 8px;
  background: #fff;
  border: 1px solid var(--border-default);
  border-radius: 100px;
  font-family: inherit;
  color: var(--text-primary);
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

.citebar__chip:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: var(--brand-primary);
  background: var(--surface-accent);
  box-shadow: 0 4px 14px rgba(74, 144, 217, 0.12);
}

.citebar__chip:hover:not(:disabled) .citebar__chip-arrow {
  transform: translate(1px, -1px);
  color: var(--brand-primary);
}

.citebar__chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.citebar__chip-no {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.66rem;
  font-weight: 700;
  color: var(--text-muted);
  letter-spacing: 0.04em;
}

.citebar__chip-tag {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.58rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  padding: 1px 5px;
  border-radius: 3px;
}

.citebar__chip-tag--pdf {
  color: #dc2626;
  background: rgba(239, 68, 68, 0.1);
}

.citebar__chip-tag--md {
  color: var(--brand-primary);
  background: rgba(74, 144, 217, 0.1);
}

.citebar__chip-tag--doc {
  color: #2563eb;
  background: rgba(59, 130, 246, 0.1);
}

.citebar__chip-tag--txt {
  color: #64748b;
  background: rgba(148, 163, 184, 0.15);
}

.citebar__chip-name {
  font-size: 0.78rem;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 260px;
}

.citebar__chip-score {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--brand-accent-dark);
  letter-spacing: 0.02em;
}

.citebar__chip-arrow {
  color: var(--text-muted);
  transition: all 0.2s ease;
  flex-shrink: 0;
}
</style>
