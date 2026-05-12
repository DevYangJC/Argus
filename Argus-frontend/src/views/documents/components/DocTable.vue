<script setup lang="ts">
import type { DocumentItem } from '@/api/document'

defineProps<{
  documents: DocumentItem[]
  documentsLoading: boolean
  documentsError: string
  isGroupOwner: boolean
  downloadingId: number | null
  deletingId: number | null
  retryingId: number | null
}>()

const emit = defineEmits<{
  preview: [doc: DocumentItem]
  download: [doc: DocumentItem]
  retry: [doc: DocumentItem]
  remove: [doc: DocumentItem]
  reload: []
}>()

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '--'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatTime(iso: string): string {
  if (!iso) return '--'
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function statusClass(status: string): string {
  return `doc-table__status doc-table__status--${status.toLowerCase()}`
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '待处理',
    PROCESSING: '处理中',
    READY: '就绪',
    FAILED: '失败',
    UPLOADED: '已上传',
  }
  return map[status] ?? status
}

function uploaderName(doc: DocumentItem): string {
  return doc.uploaderDisplayName ?? doc.uploaderUserCode ?? '未知用户'
}
</script>

<template>
  <div class="doc-table-card">
    <!-- Top header with count chip -->
    <header class="doc-table-card__head">
      <div class="doc-table-card__title-row">
        <span class="doc-table-card__eyebrow">Inventory</span>
        <h3 class="doc-table-card__title">
          当前筛选命中
          <span class="doc-table-card__count">共 {{ documents.length }} 个文件</span>
        </h3>
      </div>
    </header>

    <!-- Inline notice strips -->
    <div v-if="documentsLoading" class="doc-table-card__notice">
      <div class="doc-table-card__spinner" />
      <span>刷新中...</span>
    </div>
    <div v-if="documentsError" class="doc-table-card__notice doc-table-card__notice--error">
      <span>{{ documentsError }}</span>
      <button class="doc-table-card__retry-sm" @click="emit('reload')">重试</button>
    </div>

    <div class="doc-table-card__scroll">
      <table class="doc-table">
        <thead>
          <tr>
            <th class="doc-table__col-name">文件名</th>
            <th class="doc-table__col-size">大小</th>
            <th class="doc-table__col-status">状态</th>
            <th class="doc-table__col-uploader">上传者</th>
            <th class="doc-table__col-time">上传时间</th>
            <th class="doc-table__col-actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="doc in documents" :key="doc.documentId">
            <td class="doc-table__col-name">
              <div class="doc-table__file">
                <div class="doc-table__file-icon">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                    <path
                      d="M14 2H7C5.89543 2 5 2.89543 5 4V20C5 21.1046 5.89543 22 7 22H17C18.1046 22 19 21.1046 19 20V7L14 2Z"
                      stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
                    <path d="M14 2V7H19" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
                  </svg>
                </div>
                <span class="doc-table__name" :title="doc.fileName">{{ doc.fileName }}</span>
                <span v-if="doc.fileExt" class="doc-table__ext">{{ doc.fileExt.toUpperCase() }}</span>
              </div>
            </td>
            <td class="doc-table__col-size">{{ formatFileSize(doc.fileSize) }}</td>
            <td class="doc-table__col-status">
              <span :class="statusClass(doc.status)">
                {{ statusLabel(doc.status) }}
              </span>
              <span
                v-if="doc.status === 'FAILED' && doc.failureReason"
                class="doc-table__failure-hint"
                :title="doc.failureReason"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                  <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="1.5" />
                  <path d="M12 8V12M12 16H12.01" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
                </svg>
              </span>
            </td>
            <td class="doc-table__col-uploader">{{ uploaderName(doc) }}</td>
            <td class="doc-table__col-time">{{ formatTime(doc.uploadedAt) }}</td>
            <td class="doc-table__col-actions">
              <div class="doc-table__btns">
                <button
                  class="doc-table__btn"
                  title="预览"
                  :disabled="doc.status !== 'READY'"
                  @click="emit('preview', doc)"
                >
                  预览
                </button>
                <button
                  class="doc-table__btn"
                  title="下载"
                  :disabled="downloadingId === doc.documentId || doc.status === 'PROCESSING'"
                  @click="emit('download', doc)"
                >
                  {{ downloadingId === doc.documentId ? '下载中…' : '下载' }}
                </button>
                <button
                  v-if="isGroupOwner && doc.status === 'FAILED'"
                  class="doc-table__btn doc-table__btn--warn"
                  title="重新处理"
                  :disabled="retryingId === doc.documentId"
                  @click="emit('retry', doc)"
                >
                  {{ retryingId === doc.documentId ? '重试中…' : '重试' }}
                </button>
                <button
                  v-if="isGroupOwner"
                  class="doc-table__btn doc-table__btn--danger"
                  title="删除"
                  :disabled="deletingId === doc.documentId"
                  @click="emit('remove', doc)"
                >
                  {{ deletingId === doc.documentId ? '删除中…' : '删除' }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.doc-table-card {
  background: var(--surface-white);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  overflow: hidden;
}

.doc-table-card__head {
  padding: 18px 24px 14px;
  border-bottom: 1px solid var(--border-subtle);
}

.doc-table-card__title-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.doc-table-card__eyebrow {
  font-family: 'Poppins', sans-serif;
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--brand-primary);
}

.doc-table-card__title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 0;
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.doc-table-card__count {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.78rem;
  font-weight: 500;
  color: var(--text-muted);
  letter-spacing: 0;
}

.doc-table-card__notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 24px;
  font-size: 0.82rem;
  color: var(--text-muted);
  background: var(--surface-subtle);
  border-bottom: 1px solid var(--border-subtle);
}

.doc-table-card__notice--error {
  color: var(--el-color-danger);
  justify-content: space-between;
}

.doc-table-card__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--surface-muted);
  border-top-color: var(--brand-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.doc-table-card__retry-sm {
  padding: 3px 12px;
  font-family: inherit;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--el-color-danger);
  background: transparent;
  border: 1px solid currentColor;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.doc-table-card__retry-sm:hover {
  background: rgba(239, 68, 68, 0.06);
}

.doc-table-card__scroll {
  overflow-x: auto;
}

.doc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.86rem;
  min-width: 780px;
}

.doc-table th {
  text-align: left;
  padding: 13px 20px;
  font-family: 'Poppins', sans-serif;
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.12em;
  background: var(--surface-subtle);
  border-bottom: 1px solid var(--border-default);
  white-space: nowrap;
}

.doc-table td {
  padding: 13px 20px;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text-primary);
  vertical-align: middle;
}

.doc-table tbody tr {
  transition: background 0.15s ease;
}

.doc-table tbody tr:last-child td {
  border-bottom: none;
}

.doc-table tbody tr:hover {
  background: var(--surface-subtle);
}

.doc-table__col-name { min-width: 240px; }
.doc-table__col-size { width: 80px; white-space: nowrap; font-family: 'JetBrains Mono', monospace; font-size: 0.82rem; color: var(--text-secondary); }
.doc-table__col-status { width: 130px; }
.doc-table__col-uploader { width: 110px; color: var(--text-secondary); }
.doc-table__col-time { width: 160px; white-space: nowrap; font-family: 'JetBrains Mono', monospace; font-size: 0.82rem; color: var(--text-secondary); }
.doc-table__col-actions { width: 200px; }

.doc-table__file {
  display: flex;
  align-items: center;
  gap: 10px;
}

.doc-table__file-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--surface-accent);
  color: var(--brand-primary);
  flex-shrink: 0;
}

.doc-table__name {
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 260px;
}

.doc-table__ext {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--brand-primary);
  background: var(--surface-accent);
  padding: 2px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}

/* Status pills */
.doc-table__status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: 100px;
  font-size: 0.74rem;
  font-weight: 600;
  white-space: nowrap;
  letter-spacing: 0.01em;
}

.doc-table__status::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.doc-table__status--ready {
  color: #059669;
  background: rgba(16, 185, 129, 0.1);
}

.doc-table__status--processing {
  color: #b45309;
  background: rgba(245, 158, 11, 0.12);
  animation: status-pulse 1.8s ease-in-out infinite;
}

@keyframes status-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.doc-table__status--failed {
  color: #dc2626;
  background: rgba(239, 68, 68, 0.1);
}

.doc-table__status--pending,
.doc-table__status--uploaded {
  color: #64748b;
  background: rgba(148, 163, 184, 0.12);
}

.doc-table__failure-hint {
  display: inline-flex;
  align-items: center;
  margin-left: 6px;
  color: #dc2626;
  cursor: help;
  vertical-align: middle;
}

/* Action buttons */
.doc-table__btns {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.doc-table__btn {
  padding: 5px 12px;
  font-family: inherit;
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--brand-primary);
  background: transparent;
  border: 1px solid rgba(74, 144, 217, 0.22);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.doc-table__btn:hover:not(:disabled) {
  background: var(--surface-accent);
  border-color: var(--brand-primary);
}

.doc-table__btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.doc-table__btn--warn {
  color: #b45309;
  border-color: rgba(245, 158, 11, 0.25);
}

.doc-table__btn--warn:hover:not(:disabled) {
  background: rgba(245, 158, 11, 0.08);
  border-color: #f59e0b;
}

.doc-table__btn--danger {
  color: var(--el-color-danger);
  border-color: rgba(239, 68, 68, 0.22);
}

.doc-table__btn--danger:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.06);
  border-color: var(--el-color-danger);
}
</style>
