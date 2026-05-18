<script setup lang="ts">
import { ref, watch } from 'vue'
import { marked, Renderer } from 'marked'
import { fetchDocumentPreview, downloadDocument, type DocumentItem } from '@/api/document'
import { extractApiError } from '@/api/http'

// ── marked 配置：启用 GFM、换行转换、代码高亮基础支持 ──
marked.use({
  gfm: true,
  breaks: true,
})

const renderer = new Renderer()
// 链接在新窗口打开
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
      // 规范化扩展名（去掉前导 "."，统一小写）
      const rawExt = doc.fileExt?.toLowerCase() ?? ''
      const ext = rawExt.startsWith('.') ? rawExt.slice(1) : rawExt

      if (ext === 'pdf') {
        // PDF：下载 Blob → 生成 Object URL → iframe 预览
        const blob = await downloadDocument(doc.documentId, doc.groupId)
        pdfUrl.value = URL.createObjectURL(blob)
      } else if (ext === 'md' || ext === 'markdown') {
        // Markdown：后端预览文本 → marked 渲染为 HTML
        const preview = await fetchDocumentPreview(doc.documentId, doc.groupId)
        const mdText = preview.previewText || '*(暂无内容)*'
        htmlContent.value = await marked.parse(mdText)
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
    :append-to-body="false"
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
      <div v-else-if="htmlContent" class="preview-markdown-wrapper">
        <div class="preview-markdown" v-html="htmlContent"></div>
      </div>

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

/* ── Markdown 渲染 wrapper（仅布局，详细样式见下方非 scoped 块）── */
.preview-markdown-wrapper {
  padding: 20px 24px;
}
</style>

<!-- 非 scoped 样式：确保 v-html 渲染的 Markdown 内容始终正确格式化 -->
<style>
/* ═══════════════════════════════════════════
   Markdown 渲染样式（非 scoped，作用于 v-html 内容）
   使用 .preview-markdown-wrapper 命名空间避免全局污染
   ═══════════════════════════════════════════ */

.preview-markdown-wrapper .preview-markdown {
  line-height: 1.75;
  color: var(--text-primary);
  font-size: 14px;
}

/* ── 标题 ── */
.preview-markdown-wrapper h1,
.preview-markdown-wrapper h2,
.preview-markdown-wrapper h3,
.preview-markdown-wrapper h4,
.preview-markdown-wrapper h5,
.preview-markdown-wrapper h6 {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  margin-top: 1.4em;
  margin-bottom: 0.5em;
  line-height: 1.3;
  color: var(--text-primary);
}

.preview-markdown-wrapper h1 {
  font-size: 1.5em;
  font-weight: 800;
  border-bottom: 1px solid var(--border-subtle, #e5e7eb);
  padding-bottom: 0.3em;
}

.preview-markdown-wrapper h2 {
  font-size: 1.3em;
  font-weight: 700;
  border-bottom: 1px solid var(--border-subtle, #e5e7eb);
  padding-bottom: 0.25em;
}

.preview-markdown-wrapper h3 {
  font-size: 1.15em;
  font-weight: 700;
}

.preview-markdown-wrapper h4 {
  font-size: 1.05em;
  font-weight: 600;
}

.preview-markdown-wrapper h5 {
  font-size: 0.95em;
  font-weight: 600;
}

.preview-markdown-wrapper h6 {
  font-size: 0.88em;
  font-weight: 600;
  color: var(--text-secondary);
}

/* ── 段落 ── */
.preview-markdown-wrapper p {
  margin: 0.6em 0;
}

.preview-markdown-wrapper p:first-child {
  margin-top: 0;
}

.preview-markdown-wrapper p:last-child {
  margin-bottom: 0;
}

/* ── 粗体 ── */
.preview-markdown-wrapper strong {
  font-weight: 700;
  color: var(--text-primary);
}

/* ── 斜体 ── */
.preview-markdown-wrapper em {
  font-style: italic;
}

/* ── 删除线 ── */
.preview-markdown-wrapper del {
  text-decoration: line-through;
  opacity: 0.6;
}

/* ── 行内代码 ── */
.preview-markdown-wrapper code {
  font-family: 'JetBrains Mono', 'Cascadia Code', 'Fira Code', monospace;
  font-size: 0.88em;
  background: var(--surface-muted, #f3f4f6);
  color: #e83e8c;
  padding: 2px 6px;
  border-radius: 4px;
  word-break: break-word;
}

/* ── 代码块 ── */
.preview-markdown-wrapper pre {
  background: #1e1e2e;
  border: 1px solid var(--border-default, #d1d5db);
  border-radius: var(--radius-sm, 8px);
  padding: 16px;
  overflow-x: auto;
  margin: 0.8em 0;
}

.preview-markdown-wrapper pre code {
  background: none;
  color: #cdd6f4;
  padding: 0;
  font-size: 0.85em;
  line-height: 1.6;
}

/* ── 引用块 ── */
.preview-markdown-wrapper blockquote {
  margin: 0.8em 0;
  padding: 8px 16px;
  border-left: 3px solid var(--brand-primary, #4a90d9);
  color: var(--text-secondary, #6b7280);
  background: rgba(74, 144, 217, 0.04);
  border-radius: 0 var(--radius-xs, 4px) var(--radius-xs, 4px) 0;
}

.preview-markdown-wrapper blockquote p {
  margin: 0.3em 0;
}

/* ── 无序列表 ── */
.preview-markdown-wrapper ul {
  padding-left: 1.5em;
  margin: 0.5em 0;
  list-style-type: disc;
}

.preview-markdown-wrapper ul ul {
  list-style-type: circle;
}

.preview-markdown-wrapper ul ul ul {
  list-style-type: square;
}

/* ── 有序列表 ── */
.preview-markdown-wrapper ol {
  padding-left: 1.5em;
  margin: 0.5em 0;
  list-style-type: decimal;
}

.preview-markdown-wrapper li {
  margin: 0.25em 0;
}

/* ── 表格 ── */
.preview-markdown-wrapper table {
  width: 100%;
  border-collapse: collapse;
  margin: 1em 0;
  font-size: 13px;
  display: block;
  overflow-x: auto;
}

.preview-markdown-wrapper thead {
  border-bottom: 2px solid var(--border-default);
}

.preview-markdown-wrapper th,
.preview-markdown-wrapper td {
  border: 1px solid var(--border-default, #d1d5db);
  padding: 8px 12px;
  text-align: left;
}

.preview-markdown-wrapper th {
  background: var(--surface-subtle, #f9fafb);
  font-weight: 600;
  white-space: nowrap;
}

.preview-markdown-wrapper tr:nth-child(even) td {
  background: rgba(0, 0, 0, 0.02);
}

/* ── 链接 ── */
.preview-markdown-wrapper a {
  color: var(--brand-primary, #4a90d9);
  text-decoration: none;
}

.preview-markdown-wrapper a:hover {
  text-decoration: underline;
}

/* ── 图片 ── */
.preview-markdown-wrapper img {
  max-width: 100%;
  height: auto;
  border-radius: var(--radius-sm, 8px);
  margin: 0.5em 0;
}

/* ── 分割线 ── */
.preview-markdown-wrapper hr {
  border: none;
  border-top: 1px solid var(--border-default, #e5e7eb);
  margin: 1.2em 0;
}

/* ── 任务列表 (GFM) ── */
.preview-markdown-wrapper input[type="checkbox"] {
  margin-right: 0.4em;
  accent-color: var(--brand-primary, #4a90d9);
  vertical-align: middle;
}

/* ── 脚注 ── */
.preview-markdown-wrapper sup a {
  font-size: 0.75em;
}

/* ── 键盘标签 ── */
.preview-markdown-wrapper kbd {
  font-family: 'JetBrains Mono', 'Cascadia Code', monospace;
  font-size: 0.82em;
  background: var(--surface-muted);
  border: 1px solid var(--border-default);
  border-bottom-width: 2px;
  border-radius: 4px;
  padding: 1px 6px;
}
</style>
