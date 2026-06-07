<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { marked, Renderer } from 'marked'
import { fetchDocumentPreview, downloadDocument, type DocumentItem } from '@/api/document'
import { extractApiError } from '@/api/http'

marked.use({
  gfm: true,
  breaks: true,
})

const renderer = new Renderer()
renderer.link = function ({ href, title, text }) {
  const titleAttr = title ? ` title="${title}"` : ''
  return `<a href="${href}"${titleAttr} target="_blank" rel="noopener noreferrer">${text}</a>`
}
marked.use({ renderer })

const props = defineProps<{
  visible: boolean
  document: DocumentItem | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const loading = ref(false)
const downloading = ref(false)
const error = ref('')
const htmlContent = ref('')
const textContent = ref('')
const pdfUrl = ref('')

const normalizedFileExt = computed(() => {
  const ext = props.document?.fileExt?.toLowerCase() ?? ''
  return ext.startsWith('.') ? ext.slice(1) : ext
})

const fileMetaItems = computed(() => {
  if (!props.document) return []
  return [
    fileTypeLabel(normalizedFileExt.value),
    formatFileSize(props.document.fileSize),
    statusLabel(props.document.status),
    props.document.uploaderDisplayName ? `\u4e0a\u4f20\u8005\uff1a${props.document.uploaderDisplayName}` : '',
    props.document.uploadedAt ? formatDateTime(props.document.uploadedAt) : '',
  ].filter(Boolean)
})

watch(
  () => [props.visible, props.document] as const,
  async ([visible, doc]) => {
    if (!visible || !doc) return

    loading.value = true
    error.value = ''
    htmlContent.value = ''
    textContent.value = ''
    revokePdfUrl()

    try {
      const ext = normalizedFileExt.value

      if (ext === 'pdf') {
        const blob = await downloadDocument(doc.documentId, doc.groupId)
        pdfUrl.value = URL.createObjectURL(blob)
      } else if (ext === 'md' || ext === 'markdown') {
        const preview = await fetchDocumentPreview(doc.documentId, doc.groupId)
        const mdText = preview.previewText || '*(\u6682\u65e0\u5185\u5bb9)*'
        htmlContent.value = await marked.parse(mdText)
      } else {
        const preview = await fetchDocumentPreview(doc.documentId, doc.groupId)
        textContent.value = preview.previewText || '(\u6682\u65e0\u6587\u672c\u5185\u5bb9)'
      }
    } catch (err) {
      error.value = extractApiError(err, '\u52a0\u8f7d\u9884\u89c8\u5931\u8d25')
    } finally {
      loading.value = false
    }
  },
)

function close() {
  revokePdfUrl()
  emit('update:visible', false)
}

function revokePdfUrl() {
  if (!pdfUrl.value) return
  URL.revokeObjectURL(pdfUrl.value)
  pdfUrl.value = ''
}

async function downloadCurrentDocument() {
  const doc = props.document
  if (!doc || downloading.value) return

  downloading.value = true
  try {
    const blob = await downloadDocument(doc.documentId, doc.groupId)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = doc.fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (err) {
    error.value = extractApiError(err, '\u4e0b\u8f7d\u6587\u6863\u5931\u8d25')
  } finally {
    downloading.value = false
  }
}

function fileTypeLabel(ext: string | null): string {
  switch (ext?.toLowerCase()) {
    case 'pdf': return 'PDF \u6587\u6863'
    case 'md':
    case 'markdown': return 'Markdown'
    case 'txt': return '\u6587\u672c\u6587\u4ef6'
    case 'docx': return 'Word \u6587\u6863'
    default: return '\u6587\u6863'
  }
}

function statusLabel(status: string | null): string {
  switch (status) {
    case 'READY': return '\u5c31\u7eea'
    case 'PROCESSING': return '\u89e3\u6790\u4e2d'
    case 'PENDING': return '\u7b49\u5f85\u89e3\u6790'
    case 'FAILED': return '\u89e3\u6790\u5931\u8d25'
    default: return status ?? ''
  }
}

function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function formatDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    width="min(1120px, calc(100vw - 48px))"
    top="4vh"
    class="document-reader-dialog"
    :close-on-click-modal="true"
    :append-to-body="false"
    :show-close="false"
    @update:model-value="(val: boolean) => { if (!val) close() }"
  >
    <template #header>
      <div class="reader-header">
        <div class="reader-title-group">
          <h2 class="reader-title">{{ document?.fileName ?? '\u6587\u6863\u9884\u89c8' }}</h2>
          <div v-if="fileMetaItems.length" class="reader-meta">
            <span
              v-for="item in fileMetaItems"
              :key="item"
              class="reader-meta__item"
            >
              {{ item }}
            </span>
          </div>
        </div>

        <div class="reader-actions">
          <button
            class="reader-icon-btn"
            type="button"
            title="&#x4E0B;&#x8F7D;&#x6587;&#x6863;"
            :disabled="!document || downloading"
            @click="downloadCurrentDocument"
          >
            <span v-if="downloading" class="reader-btn-spinner" />
            <svg
              v-else
              width="17"
              height="17"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="7 10 12 15 17 10" />
              <line x1="12" y1="15" x2="12" y2="3" />
            </svg>
          </button>
          <button class="reader-icon-btn" type="button" title="&#x5173;&#x95ED;&#x9884;&#x89C8;" @click="close">
            <svg
              width="17"
              height="17"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path d="M18 6 6 18" />
              <path d="m6 6 12 12" />
            </svg>
          </button>
        </div>
      </div>
    </template>

    <div class="reader-body">
      <div v-if="loading" class="preview-state">
        <div class="spinner"></div>
        <p>&#x52A0;&#x8F7D;&#x9884;&#x89C8;&#x4E2D;...</p>
      </div>

      <div v-else-if="error" class="preview-state preview-error">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="1.5" />
          <path d="M12 8V12M12 16H12.01" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
        </svg>
        <p>{{ error }}</p>
      </div>

      <div v-else-if="pdfUrl" class="preview-pdf">
        <iframe :src="pdfUrl" class="pdf-frame" frameborder="0"></iframe>
      </div>

      <div v-else-if="htmlContent" class="preview-markdown-wrapper">
        <div class="preview-markdown" v-html="htmlContent"></div>
      </div>

      <div v-else class="preview-text">
        <pre>{{ textContent }}</pre>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.reader-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  min-width: 0;
}

.reader-title-group {
  min-width: 0;
}

.reader-title {
  margin: 0;
  color: var(--text-primary);
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reader-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 9px;
}

.reader-meta__item {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 3px 9px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: var(--radius-xs);
  background: rgba(248, 250, 252, 0.9);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
}

.reader-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.reader-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--surface-white);
  color: var(--text-secondary);
  transition:
    border-color var(--ease-out),
    color var(--ease-out),
    background var(--ease-out),
    transform var(--ease-out);
}

.reader-icon-btn:hover:not(:disabled) {
  border-color: rgba(74, 144, 217, 0.45);
  background: var(--surface-accent);
  color: var(--brand-primary-dark);
  transform: translateY(-1px);
}

.reader-icon-btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.reader-btn-spinner {
  width: 15px;
  height: 15px;
  border: 2px solid rgba(74, 144, 217, 0.2);
  border-top-color: var(--brand-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.reader-body {
  min-height: 420px;
  height: min(76vh, 820px);
  overflow: hidden;
  border-top: 1px solid var(--border-subtle);
  background:
    linear-gradient(90deg, rgba(248, 250, 252, 0.78), rgba(255, 255, 255, 0.98) 24%, rgba(255, 255, 255, 0.98) 76%, rgba(248, 250, 252, 0.78)),
    var(--surface-subtle);
}

.preview-state {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  text-align: center;
}

.preview-state p {
  margin-top: 14px;
  color: var(--text-muted);
  font-size: 14px;
}

.preview-error {
  color: var(--el-color-danger);
}

.preview-error svg {
  margin: 0 auto;
}

.preview-error p {
  color: var(--el-color-danger);
}

.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--surface-muted);
  border-top-color: var(--brand-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.preview-pdf {
  width: 100%;
  height: 100%;
  padding: 18px;
}

.pdf-frame {
  width: 100%;
  height: 100%;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--surface-white);
}

.preview-text,
.preview-markdown-wrapper {
  height: 100%;
  overflow-y: auto;
  padding: 34px clamp(22px, 5vw, 64px) 56px;
}

.preview-text pre,
.preview-markdown {
  width: min(760px, 100%);
  margin: 0 auto;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: var(--radius-md);
  background: var(--surface-white);
  box-shadow: var(--shadow-sm);
}

.preview-text pre {
  padding: 26px 30px;
  color: var(--text-primary);
  font-family: 'JetBrains Mono', 'Noto Sans SC', monospace;
  font-size: 13.5px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.preview-markdown {
  padding: 34px clamp(24px, 4vw, 48px) 54px;
}

@media (max-width: 720px) {
  .reader-header {
    align-items: stretch;
    flex-direction: column;
    gap: 14px;
  }

  .reader-actions {
    align-self: flex-end;
  }

  .reader-title {
    white-space: normal;
  }

  .reader-body {
    height: 78vh;
  }

  .preview-text,
  .preview-markdown-wrapper {
    padding: 18px 12px 32px;
  }

  .preview-markdown {
    padding: 24px 18px 36px;
  }
}
</style>

<style>
.document-reader-dialog.el-dialog {
  overflow: hidden;
  border: 1px solid rgba(226, 232, 240, 0.92);
  border-radius: var(--radius-md);
  background: var(--surface-white);
  box-shadow: 0 24px 70px rgba(30, 41, 59, 0.18);
}

.document-reader-dialog .el-dialog__header {
  margin: 0;
  padding: 20px 24px 18px;
  border-bottom: 0;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
}

.document-reader-dialog .el-dialog__body {
  padding: 0;
}

.preview-markdown-wrapper .preview-markdown {
  color: var(--text-primary);
  font-size: 15px;
  line-height: 1.85;
}

.preview-markdown-wrapper h1,
.preview-markdown-wrapper h2,
.preview-markdown-wrapper h3,
.preview-markdown-wrapper h4,
.preview-markdown-wrapper h5,
.preview-markdown-wrapper h6 {
  color: var(--text-primary);
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  letter-spacing: 0;
  line-height: 1.35;
}

.preview-markdown-wrapper h1:first-child,
.preview-markdown-wrapper h2:first-child,
.preview-markdown-wrapper h3:first-child {
  margin-top: 0;
}

.preview-markdown-wrapper h1 {
  margin: 0 0 0.8em;
  padding-bottom: 0.35em;
  border-bottom: 1px solid var(--border-default);
  font-size: 1.75em;
  font-weight: 800;
}

.preview-markdown-wrapper h2 {
  margin: 1.8em 0 0.7em;
  padding-bottom: 0.28em;
  border-bottom: 1px solid var(--border-subtle);
  font-size: 1.42em;
  font-weight: 760;
}

.preview-markdown-wrapper h3 {
  margin: 1.5em 0 0.55em;
  font-size: 1.18em;
  font-weight: 720;
}

.preview-markdown-wrapper h4 {
  margin: 1.35em 0 0.45em;
  font-size: 1.06em;
  font-weight: 680;
}

.preview-markdown-wrapper h5,
.preview-markdown-wrapper h6 {
  margin: 1.1em 0 0.4em;
  color: var(--text-secondary);
  font-size: 0.96em;
  font-weight: 650;
}

.preview-markdown-wrapper p {
  margin: 0.78em 0;
}

.preview-markdown-wrapper p:first-child {
  margin-top: 0;
}

.preview-markdown-wrapper p:last-child {
  margin-bottom: 0;
}

.preview-markdown-wrapper strong {
  color: var(--text-primary);
  font-weight: 700;
}

.preview-markdown-wrapper em {
  font-style: italic;
}

.preview-markdown-wrapper del {
  opacity: 0.62;
  text-decoration: line-through;
}

.preview-markdown-wrapper code {
  padding: 2px 6px;
  border-radius: var(--radius-xs);
  background: #eef6ff;
  color: #1f6fb8;
  font-family: 'JetBrains Mono', 'Cascadia Code', 'Fira Code', monospace;
  font-size: 0.88em;
  word-break: break-word;
}

.preview-markdown-wrapper pre {
  margin: 1.1em 0;
  overflow-x: auto;
  border: 1px solid rgba(30, 41, 59, 0.16);
  border-radius: var(--radius-sm);
  background: #182235;
  padding: 18px;
}

.preview-markdown-wrapper pre code {
  padding: 0;
  background: none;
  color: #e2e8f0;
  font-size: 0.86em;
  line-height: 1.7;
}

.preview-markdown-wrapper blockquote {
  margin: 1.1em 0;
  padding: 12px 16px;
  border-left: 3px solid var(--brand-primary);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  background: rgba(74, 144, 217, 0.06);
  color: var(--text-secondary);
}

.preview-markdown-wrapper blockquote p {
  margin: 0.35em 0;
}

.preview-markdown-wrapper ul,
.preview-markdown-wrapper ol {
  margin: 0.75em 0;
  padding-left: 1.55em;
}

.preview-markdown-wrapper ul {
  list-style: disc;
}

.preview-markdown-wrapper ol {
  list-style: decimal;
}

.preview-markdown-wrapper li {
  margin: 0.32em 0;
  padding-left: 0.12em;
}

.preview-markdown-wrapper table {
  display: block;
  width: 100%;
  margin: 1.25em 0;
  overflow-x: auto;
  border-collapse: collapse;
  font-size: 13px;
}

.preview-markdown-wrapper th,
.preview-markdown-wrapper td {
  border: 1px solid var(--border-default);
  padding: 9px 12px;
  text-align: left;
}

.preview-markdown-wrapper th {
  background: var(--surface-subtle);
  font-weight: 700;
  white-space: nowrap;
}

.preview-markdown-wrapper tr:nth-child(even) td {
  background: rgba(248, 250, 252, 0.72);
}

.preview-markdown-wrapper a {
  color: var(--brand-primary-dark);
  font-weight: 650;
  text-decoration: none;
  text-underline-offset: 3px;
}

.preview-markdown-wrapper a:hover {
  text-decoration: underline;
}

.preview-markdown-wrapper img {
  max-width: 100%;
  height: auto;
  margin: 0.9em 0;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
}

.preview-markdown-wrapper hr {
  margin: 1.6em 0;
  border: none;
  border-top: 1px solid var(--border-default);
}

.preview-markdown-wrapper input[type="checkbox"] {
  margin-right: 0.45em;
  accent-color: var(--brand-primary);
  vertical-align: middle;
}

.preview-markdown-wrapper kbd {
  padding: 1px 6px;
  border: 1px solid var(--border-default);
  border-bottom-width: 2px;
  border-radius: var(--radius-xs);
  background: var(--surface-muted);
  font-family: 'JetBrains Mono', 'Cascadia Code', monospace;
  font-size: 0.82em;
}
</style>
