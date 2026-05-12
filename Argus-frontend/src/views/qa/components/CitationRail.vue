<script setup lang="ts">
import type { CitationItem } from '@/api/qa'

defineProps<{
  citations: CitationItem[]
}>()

const emit = defineEmits<{
  inspect: [citation: CitationItem]
}>()

function formatScore(score: number): string {
  if (!Number.isFinite(score)) return '--'
  return (score * 100).toFixed(1) + '%'
}

function fileIcon(fileName: string): string {
  const ext = fileName.toLowerCase().split('.').pop() ?? ''
  if (ext === 'pdf') return 'PDF'
  if (ext === 'md') return 'MD'
  if (ext === 'docx' || ext === 'doc') return 'DOC'
  if (ext === 'txt') return 'TXT'
  return '--'
}

function iconClass(fileName: string): string {
  const ext = fileName.toLowerCase().split('.').pop() ?? ''
  if (ext === 'pdf') return 'citation-rail__type--pdf'
  if (ext === 'md') return 'citation-rail__type--md'
  if (ext === 'docx' || ext === 'doc') return 'citation-rail__type--doc'
  return 'citation-rail__type--txt'
}
</script>

<template>
  <div class="citation-rail">
    <header class="citation-rail__head">
      <span class="citation-rail__eyebrow">Evidence Chain</span>
      <span class="citation-rail__title">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
          <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
        </svg>
        <strong>引用证据</strong>
        <span class="citation-rail__count">{{ citations.length }}</span>
      </span>
    </header>

    <div class="citation-rail__scroll">
      <button
        v-for="(cite, idx) in citations"
        :key="`${cite.documentId ?? 'x'}-${cite.chunkId ?? idx}`"
        class="citation-rail__card"
        type="button"
        :disabled="cite.documentId === null"
        @click="cite.documentId !== null && emit('inspect', cite)"
      >
        <div class="citation-rail__card-head">
          <span class="citation-rail__index">{{ String(idx + 1).padStart(2, '0') }}</span>
          <span class="citation-rail__type" :class="iconClass(cite.fileName)">
            {{ fileIcon(cite.fileName) }}
          </span>
          <span class="citation-rail__score">{{ formatScore(cite.score) }}</span>
        </div>
        <h4 class="citation-rail__filename" :title="cite.fileName">
          {{ cite.fileName }}
        </h4>
        <p v-if="cite.snippet" class="citation-rail__snippet">
          {{ cite.snippet }}
        </p>
        <p v-else class="citation-rail__snippet citation-rail__snippet--muted">
          （未提供摘录片段）
        </p>
        <div class="citation-rail__card-foot">
          <div class="citation-rail__meter">
            <span class="citation-rail__meter-fill" :style="{ width: `${Math.min(100, cite.score * 100)}%` }" />
          </div>
          <span v-if="cite.chunkIndex !== null" class="citation-rail__chunk">
            #chunk {{ cite.chunkIndex }}
          </span>
        </div>
      </button>
    </div>
  </div>
</template>

<style scoped>
.citation-rail {
  margin-top: 14px;
  padding: 14px 0 4px;
  border-top: 1px dashed rgba(15, 23, 42, 0.1);
}

.citation-rail__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 10px;
  padding-left: 2px;
}

.citation-rail__eyebrow {
  font-family: 'Poppins', sans-serif;
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--brand-primary);
}

.citation-rail__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.82rem;
  color: var(--text-secondary);
}

.citation-rail__title svg {
  color: var(--brand-primary);
}

.citation-rail__title strong {
  font-weight: 600;
  color: var(--text-primary);
}

.citation-rail__count {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--text-muted);
  background: rgba(148, 163, 184, 0.14);
  padding: 1px 7px;
  border-radius: 100px;
}

.citation-rail__scroll {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding: 4px 2px 8px;
  scrollbar-width: thin;
  scrollbar-color: var(--border-default) transparent;
}

.citation-rail__scroll::-webkit-scrollbar {
  height: 6px;
}

.citation-rail__scroll::-webkit-scrollbar-thumb {
  background: var(--border-default);
  border-radius: 3px;
}

.citation-rail__card {
  flex-shrink: 0;
  width: 260px;
  padding: 12px 14px 12px;
  background: #fff;
  border: 1px solid var(--border-default);
  border-radius: 12px;
  text-align: left;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
  position: relative;
  overflow: hidden;
}

.citation-rail__card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 2px;
  height: 100%;
  background: linear-gradient(to bottom, var(--brand-primary), var(--brand-accent));
  opacity: 0;
  transition: opacity 0.2s ease;
}

.citation-rail__card:hover:not(:disabled) {
  transform: translateY(-2px);
  border-color: var(--brand-primary);
  box-shadow: 0 8px 20px rgba(74, 144, 217, 0.12);
}

.citation-rail__card:hover:not(:disabled)::before {
  opacity: 1;
}

.citation-rail__card:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.citation-rail__card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.citation-rail__index {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--text-muted);
  letter-spacing: 0.05em;
}

.citation-rail__type {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.6rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  padding: 2px 7px;
  border-radius: 4px;
}

.citation-rail__type--pdf {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
}

.citation-rail__type--md {
  background: rgba(74, 144, 217, 0.1);
  color: var(--brand-primary);
}

.citation-rail__type--doc {
  background: rgba(59, 130, 246, 0.1);
  color: #2563eb;
}

.citation-rail__type--txt {
  background: rgba(148, 163, 184, 0.15);
  color: #64748b;
}

.citation-rail__score {
  margin-left: auto;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--brand-accent-dark);
}

.citation-rail__filename {
  margin: 0 0 6px;
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.citation-rail__snippet {
  margin: 0 0 10px;
  font-size: 0.75rem;
  color: var(--text-secondary);
  line-height: 1.5;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.citation-rail__snippet--muted {
  color: var(--text-muted);
  font-style: italic;
}

.citation-rail__card-foot {
  display: flex;
  align-items: center;
  gap: 10px;
}

.citation-rail__meter {
  flex: 1;
  height: 3px;
  background: var(--surface-muted);
  border-radius: 2px;
  overflow: hidden;
}

.citation-rail__meter-fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--brand-primary), var(--brand-accent));
  border-radius: 2px;
  transition: width 0.4s ease;
}

.citation-rail__chunk {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.68rem;
  color: var(--text-muted);
}
</style>
