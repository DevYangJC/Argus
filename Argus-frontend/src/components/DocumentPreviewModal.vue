<script setup lang="ts">
import { ref, watch } from 'vue'
import { marked } from 'marked'
import { fetchDocumentPreview, downloadDocument, type DocumentItem } from '@/api/document'
import { extractApiError } from '@/api/http'

const props = defineProps<{
  visible: boolean
  document: DocumentItem | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const loading = ref(false)
const error = ref('')
const htmlContent = ref('')
const textContent = ref('')
const pdfUrl = ref('')

watch(
  () => [props.visible, props.document] as const,
  async ([visible, doc]) => {
    if (!visible || !doc) return

    loading.value = true
    error.value = ''
    htmlContent.value = ''
    textContent.value = ''
    pdfUrl.value = ''

    try {
      const ext = doc.fileExt?.toLowerCase() ?? ''

      if (ext === 'pdf') {
        // PDF：下载 Blob → 生成 Object URL → iframe 预览
        const blob = await downloadDocument(doc.documentId, doc.groupId)
        pdfUrl.value = URL.createObjectURL(blob)
      } else if (ext === 'md') {
        // Markdown：后端预览文本 → marked 渲染
        const preview = await fetchDocumentPreview(doc.documentId, doc.groupId)
        htmlContent.value = await marked.parse(preview.previewText || '*(暂无内容)*')
      } else {
        // txt / docx / 其他：纯文本预览
        const preview = await fetchDocumentPreview(doc.documentId, doc.groupId)
        textContent.value = preview.previewText || '(暂无文本内容)'
      }
    } catch (err) {
      error.value = extractApiError(err, '加载预览失败')
    } finally {
      loading.value = false
    }
  },
)

function close() {
  // 清理 PDF Object URL
  if (pdfUrl.value) {
    URL.revokeObjectURL(pdfUrl.value)
    pdfUrl.value = ''
  }
  emit('update:visible', false)
}

function fileTypeLabel(ext: string | null): string {
  switch (ext?.toLowerCase()) {
    case 'pdf': return 'PDF 文档'
    case 'md': return 'Markdown'
    case 'txt': return '文本文件'
    case 'docx': return 'Word 文档'
    default: return '文档'
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    width="720px"
    top="8vh"
    :close-on-click-modal="true"
    @update:model-value="(val: boolean) => { if (!val) close() }"
  >
    <template #header>
      <div class="modal-header">
        <h2 class="modal-title">{{ document?.fileName ?? '文档预览' }}</h2>
        <span class="modal-badge">{{ fileTypeLabel(document?.fileExt ?? null) }}</span>
      </div>
    </template>

    <div class="preview-body">
      <!-- 加载中 -->
      <div v-if="loading" class="preview-state">
        <div class="spinner"></div>
        <p>加载预览中...</p>
      </div>

      <!-- 错误 -->
      <div v-else-if="error" class="preview-state preview-error">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="1.5"/>
          <path d="M12 8V12M12 16H12.01" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
        <p>{{ error }}</p>
      </div>

      <!-- PDF 内嵌预览 -->
      <div v-else-if="pdfUrl" class="preview-pdf">
        <iframe :src="pdfUrl" class="pdf-frame" frameborder="0"></iframe>
      </div>

      <!-- MD 渲染 -->
      <div v-else-if="htmlContent" class="preview-markdown" v-html="htmlContent"></div>

      <!-- 纯文本 -->
      <div v-else class="preview-text">
        <pre>{{ textContent }}</pre>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.modal-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.modal-title {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.modal-badge {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  background: var(--surface-muted);
  padding: 2px 9px;
  border-radius: 100px;
  flex-shrink: 0;
}

/* ── 预览区 ── */
.preview-body {
  min-height: 280px;
  max-height: 70vh;
  overflow-y: auto;
}

.preview-state {
  text-align: center;
  padding: 64px 24px;
}

.preview-state p {
  margin-top: 14px;
  font-size: 14px;
  color: var(--text-muted);
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
  margin: 0 auto;
  border: 3px solid var(--surface-muted);
  border-top-color: var(--brand-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ── PDF ── */
.preview-pdf {
  width: 100%;
  height: 65vh;
}

.pdf-frame {
  width: 100%;
  height: 100%;
  border-radius: var(--radius-sm);
}

/* ── 纯文本 ── */
.preview-text {
  background: var(--surface-subtle);
  border-radius: var(--radius-sm);
  padding: 20px 24px;
}

.preview-text pre {
  font-family: 'JetBrains Mono', 'Noto Sans SC', monospace;
  font-size: 13.5px;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
  margin: 0;
}

/* ── Markdown 渲染 ── */
.preview-markdown {
  padding: 20px 24px;
  line-height: 1.75;
  color: var(--text-primary);
  font-size: 14px;
}

/* 标题 */
.preview-markdown :deep(h1),
.preview-markdown :deep(h2),
.preview-markdown :deep(h3),
.preview-markdown :deep(h4) {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  margin-top: 1.4em;
  margin-bottom: 0.5em;
  line-height: 1.3;
}

.preview-markdown :deep(h1) { font-size: 1.5em; font-weight: 800; }
.preview-markdown :deep(h2) { font-size: 1.25em; font-weight: 700; border-bottom: 1px solid var(--border-subtle); padding-bottom: 0.3em; }
.preview-markdown :deep(h3) { font-size: 1.1em; font-weight: 600; }

/* 段落 */
.preview-markdown :deep(p) {
  margin: 0.6em 0;
}

/* 代码 */
.preview-markdown :deep(code) {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.88em;
  background: var(--surface-muted);
  padding: 2px 6px;
  border-radius: 4px;
}

.preview-markdown :deep(pre) {
  background: var(--surface-subtle);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  padding: 16px;
  overflow-x: auto;
  margin: 0.8em 0;
}

.preview-markdown :deep(pre code) {
  background: none;
  padding: 0;
}

/* 引用 */
.preview-markdown :deep(blockquote) {
  margin: 0.8em 0;
  padding: 6px 16px;
  border-left: 3px solid var(--brand-primary);
  color: var(--text-secondary);
  background: rgba(74, 144, 217, 0.04);
  border-radius: 0 var(--radius-xs) var(--radius-xs) 0;
}

/* 列表 */
.preview-markdown :deep(ul),
.preview-markdown :deep(ol) {
  padding-left: 1.5em;
  margin: 0.5em 0;
}

.preview-markdown :deep(li) {
  margin: 0.25em 0;
}

/* 表格 */
.preview-markdown :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 1em 0;
  font-size: 13px;
}

.preview-markdown :deep(th),
.preview-markdown :deep(td) {
  border: 1px solid var(--border-default);
  padding: 8px 12px;
  text-align: left;
}

.preview-markdown :deep(th) {
  background: var(--surface-subtle);
  font-weight: 600;
}

/* 链接 */
.preview-markdown :deep(a) {
  color: var(--brand-primary);
}

.preview-markdown :deep(a:hover) {
  text-decoration: underline;
}

/* 分割线 */
.preview-markdown :deep(hr) {
  border: none;
  border-top: 1px solid var(--border-default);
  margin: 1.2em 0;
}

/* 图片 */
.preview-markdown :deep(img) {
  max-width: 100%;
  border-radius: var(--radius-sm);
}
</style>
