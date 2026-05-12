<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { uploadDocument } from '@/api/document'
import { extractApiError } from '@/api/http'

const props = defineProps<{
  visible: boolean
  groupId: number | null
  groupLabel: string
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  uploaded: []
}>()

const file = ref<File | null>(null)
const progress = ref(0)
const loading = ref(false)
const errorMsg = ref('')
const success = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const dragging = ref(false)

const allowedExts = ['.txt', '.md', '.pdf', '.docx']

function resetState() {
  file.value = null
  progress.value = 0
  loading.value = false
  errorMsg.value = ''
  success.value = false
  dragging.value = false
}

watch(
  () => props.visible,
  (val) => {
    if (val) {
      resetState()
      nextTick(() => fileInputRef.value?.click())
    }
  },
)

function validateFile(f: File): string | null {
  const ext = '.' + f.name.split('.').pop()?.toLowerCase()
  if (!allowedExts.includes(ext)) {
    return `不支持的文件类型（支持: ${allowedExts.join(', ')}）`
  }
  if (f.size > 10 * 1024 * 1024) {
    return '文件大小不能超过 10MB（大文件请使用分片上传）'
  }
  return null
}

function onFilePicked(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  if (!f) {
    file.value = null
    return
  }
  const err = validateFile(f)
  if (err) {
    errorMsg.value = err
    file.value = null
    return
  }
  errorMsg.value = ''
  file.value = f
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  dragging.value = false
  const f = e.dataTransfer?.files?.[0]
  if (!f) return
  const err = validateFile(f)
  if (err) {
    errorMsg.value = err
    file.value = null
    return
  }
  errorMsg.value = ''
  file.value = f
}

function onDragOver(e: DragEvent) {
  e.preventDefault()
  dragging.value = true
}

function onDragLeave() {
  dragging.value = false
}

async function handleUpload() {
  if (!file.value || props.groupId === null) return

  loading.value = true
  errorMsg.value = ''
  progress.value = 0
  try {
    await uploadDocument({
      groupId: props.groupId,
      file: file.value,
      onProgress: (loaded, total) => {
        if (total) {
          progress.value = Math.round((loaded / total) * 100)
        }
      },
    })
    success.value = true
    file.value = null
    setTimeout(() => {
      emit('uploaded')
      emit('update:visible', false)
    }, 800)
  } catch (err) {
    errorMsg.value = extractApiError(err, '上传文件失败')
  } finally {
    loading.value = false
  }
}

async function retryUpload() {
  errorMsg.value = ''
  await handleUpload()
}

function close() {
  if (loading.value) return
  emit('update:visible', false)
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :close-on-click-modal="false"
    :close-on-press-escape="!loading"
    :show-close="!loading"
    width="460px"
    top="13vh"
    @update:model-value="(val: boolean) => { if (!val) close() }"
  >
    <template #header>
      <div class="upload-dialog__header">
        <span class="upload-dialog__eyebrow">Upload</span>
        <h2 class="upload-dialog__title">添加新文档</h2>
      </div>
    </template>

    <div class="upload-dialog">
      <p class="upload-dialog__group">
        目标群组：<strong>{{ groupLabel }}</strong>
      </p>

      <!-- Drop zone -->
      <div
        v-if="!success"
        class="upload-dialog__zone"
        :class="{ 'is-dragging': dragging, 'is-filled': !!file }"
        @click="fileInputRef?.click()"
        @drop="onDrop"
        @dragover="onDragOver"
        @dragleave="onDragLeave"
      >
        <template v-if="!file">
          <div class="upload-dialog__zone-icon">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="17 8 12 3 7 8" />
              <line x1="12" y1="3" x2="12" y2="15" />
            </svg>
          </div>
          <p class="upload-dialog__zone-text">
            <strong>点击选择文件</strong>
            <span>或将文件拖拽到此处</span>
          </p>
          <p class="upload-dialog__zone-hint">支持 TXT · MD · PDF · DOCX，最大 10 MB</p>
        </template>
        <template v-else>
          <div class="upload-dialog__file-icon">
            <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
            </svg>
          </div>
          <p class="upload-dialog__file-name">{{ file.name }}</p>
          <p class="upload-dialog__file-size">{{ formatSize(file.size) }}</p>
          <button
            class="upload-dialog__file-change"
            type="button"
            @click.stop="fileInputRef?.click()"
          >
            更换文件
          </button>
        </template>
      </div>

      <input
        ref="fileInputRef"
        type="file"
        accept=".txt,.md,.pdf,.docx"
        class="upload-dialog__file-input"
        @change="onFilePicked"
      />

      <!-- Progress -->
      <div v-if="loading || success" class="upload-dialog__progress">
        <div class="upload-dialog__progress-track">
          <div
            class="upload-dialog__progress-fill"
            :class="{ 'is-done': success }"
            :style="{ width: progress + '%' }"
          />
        </div>
        <span class="upload-dialog__progress-text">
          {{ success ? '上传成功' : `${progress}%` }}
        </span>
      </div>

      <!-- Error with retry -->
      <div v-if="errorMsg" class="upload-dialog__error">
        <div class="upload-dialog__error-icon">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
        </div>
        <span class="upload-dialog__error-text">{{ errorMsg }}</span>
        <button
          v-if="file && !loading"
          class="upload-dialog__error-retry"
          type="button"
          @click="retryUpload"
        >
          重试
        </button>
      </div>

      <!-- Actions -->
      <div class="upload-dialog__actions">
        <button
          v-if="!success"
          class="upload-dialog__btn upload-dialog__btn--primary"
          :disabled="!file || loading"
          @click="handleUpload"
        >
          <span v-if="!loading">开始上传</span>
          <span v-else>上传中…</span>
        </button>
        <button
          class="upload-dialog__btn upload-dialog__btn--ghost"
          :disabled="loading"
          @click="close"
        >
          {{ success ? '关闭' : '取消' }}
        </button>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.upload-dialog__header {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.upload-dialog__eyebrow {
  font-family: 'Poppins', sans-serif;
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--brand-primary);
}

.upload-dialog__title {
  margin: 0;
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--text-primary);
}

.upload-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upload-dialog__group {
  margin: 0;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.upload-dialog__group strong {
  color: var(--text-primary);
  font-weight: 600;
}

/* Drop zone */
.upload-dialog__zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 36px 20px;
  border: 2px dashed var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
  cursor: pointer;
  color: var(--text-muted);
  transition: all 0.2s ease;
}

.upload-dialog__zone:hover,
.upload-dialog__zone.is-dragging {
  border-color: var(--brand-primary);
  background: rgba(74, 144, 217, 0.04);
  color: var(--brand-primary);
}

.upload-dialog__zone.is-filled {
  border-style: solid;
  border-color: var(--brand-primary);
  background: rgba(74, 144, 217, 0.04);
  color: var(--text-primary);
}

.upload-dialog__zone-icon,
.upload-dialog__file-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(74, 144, 217, 0.1);
  color: var(--brand-primary);
}

.upload-dialog__zone-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: center;
  margin: 0;
  font-size: 0.9rem;
  color: var(--text-primary);
  text-align: center;
}

.upload-dialog__zone-text strong {
  font-weight: 600;
  color: var(--brand-primary);
}

.upload-dialog__zone-text span {
  color: var(--text-muted);
  font-size: 0.82rem;
}

.upload-dialog__zone-hint {
  margin: 0;
  font-size: 0.75rem;
  color: var(--text-muted);
  letter-spacing: 0.02em;
}

.upload-dialog__file-name {
  margin: 4px 0 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-primary);
  word-break: break-all;
  text-align: center;
  max-width: 340px;
}

.upload-dialog__file-size {
  margin: 0;
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.78rem;
  color: var(--text-muted);
}

.upload-dialog__file-change {
  margin-top: 6px;
  padding: 4px 14px;
  font-family: inherit;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--brand-primary);
  background: #fff;
  border: 1px solid rgba(74, 144, 217, 0.3);
  border-radius: 100px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.upload-dialog__file-change:hover {
  background: var(--surface-accent);
  border-color: var(--brand-primary);
}

.upload-dialog__file-input {
  display: none;
}

/* Progress */
.upload-dialog__progress {
  display: flex;
  align-items: center;
  gap: 12px;
}

.upload-dialog__progress-track {
  flex: 1;
  height: 6px;
  background: var(--surface-muted);
  border-radius: 3px;
  overflow: hidden;
}

.upload-dialog__progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--brand-primary), var(--brand-accent));
  border-radius: 3px;
  transition: width 0.3s ease;
}

.upload-dialog__progress-fill.is-done {
  background: var(--el-color-success);
}

.upload-dialog__progress-text {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-secondary);
  min-width: 60px;
  text-align: right;
}

/* Error with retry */
.upload-dialog__error {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px solid rgba(239, 68, 68, 0.15);
  border-radius: var(--radius-sm);
}

.upload-dialog__error-icon {
  display: flex;
  color: #dc2626;
  flex-shrink: 0;
}

.upload-dialog__error-text {
  flex: 1;
  font-size: 0.82rem;
  color: #b91c1c;
  line-height: 1.4;
}

.upload-dialog__error-retry {
  flex-shrink: 0;
  padding: 4px 12px;
  font-family: inherit;
  font-size: 0.78rem;
  font-weight: 600;
  color: #dc2626;
  background: #fff;
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 100px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.upload-dialog__error-retry:hover {
  background: rgba(239, 68, 68, 0.08);
  border-color: #dc2626;
}

/* Action buttons */
.upload-dialog__actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 4px;
}

.upload-dialog__btn {
  padding: 9px 22px;
  font-family: inherit;
  font-size: 0.88rem;
  font-weight: 600;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s ease;
}

.upload-dialog__btn--primary {
  color: #fff;
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-primary-dark));
  box-shadow: 0 4px 14px rgba(74, 144, 217, 0.25);
}

.upload-dialog__btn--primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(74, 144, 217, 0.35);
}

.upload-dialog__btn--primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.upload-dialog__btn--ghost {
  color: var(--text-secondary);
  background: var(--surface-muted);
}

.upload-dialog__btn--ghost:hover:not(:disabled) {
  background: var(--border-default);
  color: var(--text-primary);
}

.upload-dialog__btn--ghost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
