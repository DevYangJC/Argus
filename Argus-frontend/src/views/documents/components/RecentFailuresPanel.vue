<script setup lang="ts">
import type { DocumentItem } from '@/api/document'

defineProps<{
  failures: DocumentItem[]
  isGroupOwner: boolean
  retryingId: number | null
}>()

const emit = defineEmits<{
  retry: [doc: DocumentItem]
  inspect: [doc: DocumentItem]
}>()

function formatTime(iso: string): string {
  if (!iso) return '--'
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<template>
  <section class="failures-panel">
    <header class="failures-panel__head">
      <div class="failures-panel__title-row">
        <span class="failures-panel__eyebrow">Recent Failures</span>
        <h3 class="failures-panel__title">最近异常</h3>
      </div>
      <div v-if="failures.length > 0" class="failures-panel__badge">
        {{ failures.length }} 个待处理
      </div>
    </header>

    <!-- Empty state -->
    <div v-if="failures.length === 0" class="failures-panel__empty">
      <div class="failures-panel__empty-icon">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
          <polyline points="22 4 12 14.01 9 11.01"/>
        </svg>
      </div>
      <p>当前筛选结果里没有新的失败文件。</p>
    </div>

    <!-- List of failures -->
    <ul v-else class="failures-panel__list">
      <li v-for="doc in failures" :key="doc.documentId" class="failures-panel__item">
        <div class="failures-panel__item-main">
          <div class="failures-panel__item-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
              <line x1="12" y1="9" x2="12" y2="13"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
          </div>
          <div class="failures-panel__item-body">
            <button class="failures-panel__name" :title="doc.fileName" @click="emit('inspect', doc)">
              {{ doc.fileName }}
            </button>
            <p v-if="doc.failureReason" class="failures-panel__reason" :title="doc.failureReason">
              {{ doc.failureReason }}
            </p>
            <p v-else class="failures-panel__reason failures-panel__reason--muted">
              失败原因未提供
            </p>
          </div>
        </div>
        <div class="failures-panel__item-meta">
          <span class="failures-panel__time">{{ formatTime(doc.uploadedAt) }}</span>
          <button
            v-if="isGroupOwner"
            class="failures-panel__retry"
            :disabled="retryingId === doc.documentId"
            @click="emit('retry', doc)"
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="23 4 23 10 17 10"/>
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
            </svg>
            <span>{{ retryingId === doc.documentId ? '重试中' : '重试' }}</span>
          </button>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.failures-panel {
  margin-bottom: 22px;
  padding: 20px 24px;
  background: var(--surface-white);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
}

.failures-panel__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 14px;
  padding-bottom: 14px;
  border-bottom: 1px dashed var(--border-default);
}

.failures-panel__title-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.failures-panel__eyebrow {
  font-family: 'Poppins', sans-serif;
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #ef4444;
}

.failures-panel__title {
  margin: 0;
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.failures-panel__badge {
  display: inline-flex;
  align-items: center;
  padding: 4px 12px;
  font-size: 0.78rem;
  font-weight: 600;
  color: #dc2626;
  background: rgba(239, 68, 68, 0.08);
  border-radius: 100px;
}

/* Empty state */
.failures-panel__empty {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 4px 2px;
}

.failures-panel__empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: rgba(16, 185, 129, 0.08);
  color: #10b981;
  flex-shrink: 0;
}

.failures-panel__empty p {
  margin: 0;
  font-size: 0.88rem;
  color: var(--text-secondary);
}

/* List */
.failures-panel__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.failures-panel__item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 12px 14px;
  background: rgba(239, 68, 68, 0.025);
  border: 1px solid rgba(239, 68, 68, 0.08);
  border-radius: var(--radius-sm);
  transition: all 0.2s ease;
}

.failures-panel__item:hover {
  background: rgba(239, 68, 68, 0.05);
  border-color: rgba(239, 68, 68, 0.18);
}

.failures-panel__item-main {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.failures-panel__item-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
  flex-shrink: 0;
  margin-top: 1px;
}

.failures-panel__item-body {
  flex: 1;
  min-width: 0;
}

.failures-panel__name {
  display: block;
  width: 100%;
  text-align: left;
  font-family: inherit;
  font-size: 0.92rem;
  font-weight: 600;
  color: var(--text-primary);
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 0.15s ease;
}

.failures-panel__name:hover {
  color: var(--brand-primary);
}

.failures-panel__reason {
  margin: 0;
  font-size: 0.78rem;
  color: #b91c1c;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.failures-panel__reason--muted {
  color: var(--text-muted);
  font-style: italic;
}

.failures-panel__item-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.failures-panel__time {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.76rem;
  color: var(--text-muted);
}

.failures-panel__retry {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  font-family: inherit;
  font-size: 0.78rem;
  font-weight: 600;
  color: #b45309;
  background: #fff;
  border: 1px solid rgba(245, 158, 11, 0.3);
  border-radius: var(--radius-xs);
  cursor: pointer;
  transition: all 0.15s ease;
}

.failures-panel__retry:hover:not(:disabled) {
  background: rgba(245, 158, 11, 0.08);
  border-color: #f59e0b;
}

.failures-panel__retry:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 640px) {
  .failures-panel__item {
    flex-direction: column;
    align-items: flex-start;
  }

  .failures-panel__item-meta {
    width: 100%;
    justify-content: space-between;
    padding-left: 40px;
  }
}
</style>
