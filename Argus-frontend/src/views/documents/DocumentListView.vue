<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import {
  fetchDocuments,
  deleteDocument,
  downloadDocument,
  retryDocumentIngestion,
  type DocumentItem,
  type DocumentListQuery,
} from '@/api/document'
import { fetchGroups } from '@/api/group'
import { extractApiError } from '@/api/http'
import { ElMessageBox } from 'element-plus'
import DocumentPreviewModal from '@/components/DocumentPreviewModal.vue'
import PageHeaderHero from '@/components/layout/PageHeaderHero.vue'
import DocStatsBar from './components/DocStatsBar.vue'
import RecentFailuresPanel from './components/RecentFailuresPanel.vue'
import DocFiltersBar from './components/DocFiltersBar.vue'
import DocTable from './components/DocTable.vue'
import UploadDialog from './components/UploadDialog.vue'

const router = useRouter()
const appStore = useAppStore()

// ── Group selection ──
const groupsLoading = ref(false)
const groupsError = ref('')
const selectedGroupId = ref<number | null>(appStore.currentGroupId)

const isGroupOwner = computed(() => {
  if (selectedGroupId.value === null) return false
  return appStore.ownedGroups.some((g) => g.groupId === selectedGroupId.value)
})

const selectedGroupLabel = computed(() => {
  const g = appStore.visibleGroups.find((g) => g.groupId === selectedGroupId.value)
  if (!g) return ''
  return g.relation === 'OWNER' ? `${g.groupName} (管理员)` : `${g.groupName} (成员)`
})

async function loadGroups() {
  groupsLoading.value = true
  groupsError.value = ''
  try {
    const result = await fetchGroups()
    appStore.applyGroupQueryResult(result)
    if (
      selectedGroupId.value === null ||
      !appStore.visibleGroups.some((g) => g.groupId === selectedGroupId.value)
    ) {
      selectedGroupId.value = appStore.currentGroupId
    }
  } catch (err) {
    groupsError.value = extractApiError(err, '加载群组失败')
  } finally {
    groupsLoading.value = false
  }
}

// ── Documents ──
const documents = ref<DocumentItem[]>([])
const documentsLoading = ref(false)
const documentsError = ref('')

// ── Filters ──
const searchText = ref('')
const statusFilter = ref('')
let searchTimer: ReturnType<typeof setTimeout> | null = null

function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    loadDocuments()
  }, 300)
}

function onStatusChange() {
  loadDocuments()
}

async function loadDocuments() {
  if (selectedGroupId.value === null) {
    documents.value = []
    return
  }

  documentsLoading.value = true
  documentsError.value = ''
  try {
    const query: DocumentListQuery = { groupId: selectedGroupId.value }
    if (searchText.value.trim()) {
      query.fileName = searchText.value.trim()
    }
    if (statusFilter.value) {
      query.status = statusFilter.value
    }
    documents.value = await fetchDocuments(query)
  } catch (err) {
    documentsError.value = extractApiError(err, '加载文档列表失败')
    documents.value = []
  } finally {
    documentsLoading.value = false
  }
}

watch(selectedGroupId, (newId) => {
  appStore.setCurrentGroupId(newId)
  if (newId !== null) {
    loadDocuments()
  } else {
    documents.value = []
  }
})

// ── Derived stats ──
const readyCount = computed(() => documents.value.filter((d) => d.status === 'READY').length)
const processingCount = computed(
  () => documents.value.filter((d) => d.status === 'PROCESSING' || d.status === 'PENDING').length,
)
const failedDocuments = computed(() => documents.value.filter((d) => d.status === 'FAILED'))
const failedCount = computed(() => failedDocuments.value.length)

const totalSize = computed(() => {
  const sum = documents.value.reduce((acc, d) => acc + (d.fileSize ?? 0), 0)
  return formatFileSize(sum)
})

const failedHint = computed(() => {
  if (failedCount.value === 0) return '当前没有失败文件'
  return `${failedCount.value} 个文件需要重新处理`
})

const hasActiveFilters = computed(() => {
  return searchText.value.trim().length > 0 || statusFilter.value.length > 0
})

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

// ── Upload ──
const uploadVisible = ref(false)

function openUpload() {
  uploadVisible.value = true
}

function onUploadCompleted() {
  loadDocuments()
}

// ── Preview ──
const previewVisible = ref(false)
const previewDocument = ref<DocumentItem | null>(null)

function openPreview(doc: DocumentItem) {
  previewDocument.value = doc
  previewVisible.value = true
}

// ── Download ──
const downloadingId = ref<number | null>(null)

async function handleDownload(doc: DocumentItem) {
  downloadingId.value = doc.documentId
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
    console.error('Download failed:', extractApiError(err, ''))
  } finally {
    downloadingId.value = null
  }
}

// ── Delete ──
const deletingId = ref<number | null>(null)

async function handleDelete(doc: DocumentItem) {
  try {
    await ElMessageBox.confirm(
      `确定要删除文档「${doc.fileName}」吗？删除后将清理向量数据和搜索索引，此操作不可撤销。`,
      '删除文档',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  deletingId.value = doc.documentId
  try {
    await deleteDocument(doc.documentId, doc.groupId)
    documents.value = documents.value.filter((d) => d.documentId !== doc.documentId)
  } catch (err) {
    console.error('Delete failed:', extractApiError(err, '删除失败'))
  } finally {
    deletingId.value = null
  }
}

// ── Retry ──
const retryingId = ref<number | null>(null)

async function handleRetry(doc: DocumentItem) {
  retryingId.value = doc.documentId
  try {
    await retryDocumentIngestion(doc.documentId, doc.groupId)
    await loadDocuments()
  } catch (err) {
    console.error('Retry failed:', extractApiError(err, ''))
  } finally {
    retryingId.value = null
  }
}

// ── Init ──
onMounted(() => {
  if (appStore.visibleGroups.length === 0) {
    loadGroups()
  } else if (selectedGroupId.value === null) {
    selectedGroupId.value = appStore.visibleGroups[0]?.groupId ?? null
  } else {
    loadDocuments()
  }
})
</script>

<template>
  <div class="doc-center">
    <!-- Decorative background -->
    <div class="doc-center__backdrop" aria-hidden="true">
      <div class="doc-center__blob doc-center__blob--one" />
      <div class="doc-center__blob doc-center__blob--two" />
    </div>

    <!-- Hero header -->
    <PageHeaderHero
      eyebrow="Document Center"
      title="文档中心"
      description="集中管理知识库文档：上传、解析、预览与检索，所有内容驱动 RAG 问答与协作。"
    />

    <!-- Stats overview -->
    <DocStatsBar
      :ready-count="readyCount"
      :processing-count="processingCount"
      :failed-count="failedCount"
      :total-size="totalSize"
      :failed-hint="failedHint"
    />

    <!-- Recent failures -->
    <RecentFailuresPanel
      :failures="failedDocuments"
      :is-group-owner="isGroupOwner"
      :retrying-id="retryingId"
      @retry="handleRetry"
      @inspect="openPreview"
    />

    <!-- Filters & upload action -->
    <DocFiltersBar
      v-model:selected-group-id="selectedGroupId"
      v-model:search-text="searchText"
      v-model:status-filter="statusFilter"
      :groups="appStore.visibleGroups"
      :groups-loading="groupsLoading"
      :is-group-owner="isGroupOwner"
      :has-group-selected="selectedGroupId !== null"
      @search-input="onSearchInput"
      @status-change="onStatusChange"
      @open-upload="openUpload"
    />

    <!-- ── Groups loading ── -->
    <div v-if="groupsLoading" class="doc-center__state">
      <div class="doc-center__spinner" />
      <p>正在加载群组...</p>
    </div>

    <!-- ── Groups error ── -->
    <div v-else-if="groupsError" class="doc-center__state doc-center__state--error">
      <p>{{ groupsError }}</p>
      <button class="doc-center__btn-retry" @click="loadGroups">重试</button>
    </div>

    <!-- ── No groups ── -->
    <div v-else-if="appStore.visibleGroups.length === 0" class="doc-center__state doc-center__state--empty">
      <div class="doc-center__empty-icon">
        <svg width="42" height="42" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </svg>
      </div>
      <h3>暂无可用群组</h3>
      <p>请先创建或加入一个群组，再上传和管理文档</p>
      <button class="doc-center__btn-primary" @click="router.push('/app/groups')">前往协作小组</button>
    </div>

    <!-- ── No group selected ── -->
    <div v-else-if="selectedGroupId === null" class="doc-center__state doc-center__state--empty">
      <div class="doc-center__empty-icon">
        <svg width="42" height="42" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <polyline points="14 2 14 8 20 8" />
        </svg>
      </div>
      <h3>请选择群组</h3>
      <p>在上方下拉菜单中选择一个群组以查看其文档</p>
    </div>

    <!-- ── Loading docs ── -->
    <div
      v-else-if="documentsLoading && documents.length === 0"
      class="doc-center__state"
    >
      <div class="doc-center__spinner" />
      <p>正在加载文档列表...</p>
    </div>

    <!-- ── Load error ── -->
    <div
      v-else-if="documentsError && documents.length === 0"
      class="doc-center__state doc-center__state--error"
    >
      <p>{{ documentsError }}</p>
      <button class="doc-center__btn-retry" @click="loadDocuments">重试</button>
    </div>

    <!-- ── Empty docs ── -->
    <div
      v-else-if="documents.length === 0 && !documentsLoading"
      class="doc-center__state doc-center__state--empty"
    >
      <div class="doc-center__empty-icon">
        <svg width="42" height="42" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <polyline points="14 2 14 8 20 8" />
          <line x1="9" y1="13" x2="15" y2="13" />
          <line x1="9" y1="17" x2="15" y2="17" />
        </svg>
      </div>
      <h3>暂无文档</h3>
      <p v-if="hasActiveFilters">没有符合条件的文档，请调整搜索或筛选条件</p>
      <p v-else>该群组中还没有文档{{ isGroupOwner ? '，点击下方按钮上传' : '' }}</p>
      <button
        v-if="isGroupOwner && !hasActiveFilters"
        class="doc-center__btn-primary"
        @click="openUpload"
      >
        上传第一篇文档
      </button>
    </div>

    <!-- ── Documents table ── -->
    <DocTable
      v-else
      :documents="documents"
      :documents-loading="documentsLoading"
      :documents-error="documentsError"
      :is-group-owner="isGroupOwner"
      :downloading-id="downloadingId"
      :deleting-id="deletingId"
      :retrying-id="retryingId"
      @preview="openPreview"
      @download="handleDownload"
      @retry="handleRetry"
      @remove="handleDelete"
      @reload="loadDocuments"
    />

    <!-- Upload dialog -->
    <UploadDialog
      v-model:visible="uploadVisible"
      :group-id="selectedGroupId"
      :group-label="selectedGroupLabel"
      @uploaded="onUploadCompleted"
    />

    <!-- Preview modal -->
    <DocumentPreviewModal
      :visible="previewVisible"
      :document="previewDocument"
      @update:visible="(val: boolean) => (previewVisible = val)"
    />
  </div>
</template>

<style scoped>
.doc-center {
  position: relative;
  min-height: 100%;
}

/* Decorative atmosphere */
.doc-center__backdrop {
  position: absolute;
  inset: -40px -20px auto;
  height: 320px;
  pointer-events: none;
  overflow: hidden;
  z-index: 0;
}

.doc-center__blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.35;
}

.doc-center__blob--one {
  top: -60px;
  left: -40px;
  width: 320px;
  height: 320px;
  background: radial-gradient(circle, rgba(74, 144, 217, 0.4) 0%, rgba(74, 144, 217, 0) 70%);
}

.doc-center__blob--two {
  top: -80px;
  right: -40px;
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, rgba(92, 201, 193, 0.35) 0%, rgba(92, 201, 193, 0) 70%);
}

.doc-center > :not(.doc-center__backdrop) {
  position: relative;
  z-index: 1;
}

/* State containers */
.doc-center__state {
  text-align: center;
  padding: 64px 24px;
  background: var(--surface-white);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
}

.doc-center__state p {
  font-size: 0.92rem;
  color: var(--text-secondary);
  margin: 0 0 14px;
}

.doc-center__state--error p {
  color: var(--el-color-danger);
}

.doc-center__spinner {
  width: 32px;
  height: 32px;
  margin: 0 auto 16px;
  border: 3px solid var(--surface-muted);
  border-top-color: var(--brand-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.doc-center__empty-icon {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(74, 144, 217, 0.08), rgba(92, 201, 193, 0.08));
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--brand-primary);
  margin: 0 auto 18px;
}

.doc-center__state h3 {
  margin: 0 0 8px;
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--text-primary);
}

.doc-center__btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 9px 22px;
  font-family: inherit;
  font-size: 0.88rem;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-dark));
  border: none;
  border-radius: var(--radius-sm);
  box-shadow: 0 4px 14px rgba(74, 144, 217, 0.25);
  cursor: pointer;
  transition: all 0.2s ease;
}

.doc-center__btn-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(74, 144, 217, 0.35);
}

.doc-center__btn-retry {
  padding: 8px 20px;
  font-family: inherit;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--brand-primary);
  background: var(--surface-accent);
  border: 1px solid rgba(74, 144, 217, 0.15);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.15s ease;
}

.doc-center__btn-retry:hover {
  background: rgba(74, 144, 217, 0.12);
}
</style>
