<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import type { AssistantSessionListItem } from '@/types/assistant'

const props = defineProps<{
  sessions: AssistantSessionListItem[]
  activeSessionId: number | null
  loading: boolean
}>()

const emit = defineEmits<{
  'new-session': []
  'select-session': [sessionId: number]
  'rename-session': [sessionId: number, title: string]
  'delete-session': [sessionId: number]
  refresh: []
}>()

const editingId = ref<number | null>(null)
const editingTitle = ref('')
const editInputRef = ref<HTMLInputElement | null>(null)

function startEdit(s: AssistantSessionListItem) {
  editingId.value = s.sessionId
  editingTitle.value = s.title
  nextTick(() => {
    editInputRef.value?.focus()
    editInputRef.value?.select()
  })
}

function commitEdit() {
  if (editingId.value === null) return
  const title = editingTitle.value.trim()
  const original = props.sessions.find((s) => s.sessionId === editingId.value)?.title ?? ''
  if (title && title !== original) {
    emit('rename-session', editingId.value, title)
  }
  editingId.value = null
}

function cancelEdit() {
  editingId.value = null
}

watch(
  () => props.activeSessionId,
  () => {
    editingId.value = null
  },
)

function formatRelative(iso: string | null): string {
  if (!iso) return '新建'
  const t = new Date(iso).getTime()
  const diff = Date.now() - t
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}/${pad(d.getDate())}`
}
</script>

<template>
  <aside class="asst-sidebar">
    <!-- Brand mark -->
    <div class="asst-sidebar__brand">
      <div class="asst-sidebar__brand-mark">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
        </svg>
      </div>
      <div class="asst-sidebar__brand-text">
        <span class="asst-sidebar__brand-eyebrow">Argus Research</span>
        <span class="asst-sidebar__brand-title">Agent Console</span>
      </div>
      <button
        class="asst-sidebar__refresh"
        type="button"
        title="刷新列表"
        :disabled="loading"
        @click="emit('refresh')"
      >
        <svg :class="{ 'is-spinning': loading }" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="23 4 23 10 17 10" />
          <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
        </svg>
      </button>
    </div>

    <!-- Info hint -->
    <div class="asst-sidebar__section">
      <label class="asst-sidebar__label">
        <span>会话同步</span>
        <span class="asst-sidebar__label-dot" />
      </label>
      <p class="asst-sidebar__hint">
        所有会话已持久化至云端，支持跨设备继续对话。
      </p>
    </div>

    <!-- New session -->
    <button class="asst-sidebar__new-chat" type="button" @click="emit('new-session')">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
        <line x1="12" y1="5" x2="12" y2="19" />
        <line x1="5" y1="12" x2="19" y2="12" />
      </svg>
      <span>新建会话</span>
    </button>

    <!-- History -->
    <div class="asst-sidebar__history">
      <div class="asst-sidebar__history-head">
        <span class="asst-sidebar__history-label">会话历史</span>
        <span class="asst-sidebar__history-count">{{ sessions.length }}</span>
      </div>

      <ul v-if="sessions.length > 0" class="asst-sidebar__list">
        <li v-for="s in sessions" :key="s.sessionId">
          <div
            class="asst-sidebar__item"
            :class="{ 'is-active': s.sessionId === activeSessionId }"
            @click="emit('select-session', s.sessionId)"
          >
            <span class="asst-sidebar__item-bar" />
            <div class="asst-sidebar__item-body">
              <input
                v-if="editingId === s.sessionId"
                ref="editInputRef"
                v-model="editingTitle"
                class="asst-sidebar__item-edit"
                maxlength="64"
                @click.stop
                @keydown.enter.prevent="commitEdit"
                @keydown.esc="cancelEdit"
                @blur="commitEdit"
              />
              <span
                v-else
                class="asst-sidebar__item-title"
                :title="s.title"
                @dblclick.stop="startEdit(s)"
              >{{ s.title }}</span>
              <span class="asst-sidebar__item-meta">
                <span class="asst-sidebar__item-time">{{ formatRelative(s.lastMessageAt) }}</span>
              </span>
            </div>

            <div class="asst-sidebar__item-actions">
              <button
                class="asst-sidebar__ibtn"
                title="重命名（或双击标题）"
                type="button"
                @click.stop="startEdit(s)"
              >
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 20h9" />
                  <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
                </svg>
              </button>
              <button
                class="asst-sidebar__ibtn asst-sidebar__ibtn--danger"
                title="删除"
                type="button"
                @click.stop="emit('delete-session', s.sessionId)"
              >
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="3 6 5 6 21 6" />
                  <path d="M19 6l-2 14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2L5 6" />
                </svg>
              </button>
            </div>
          </div>
        </li>
      </ul>

      <div v-else-if="loading" class="asst-sidebar__empty">
        <div class="asst-sidebar__skeleton" />
        <div class="asst-sidebar__skeleton" />
        <div class="asst-sidebar__skeleton" />
      </div>

      <div v-else class="asst-sidebar__empty">
        <p>暂无会话</p>
        <small>点击上方"新建会话"开启一次对话</small>
      </div>
    </div>

    <!-- Footer tip -->
    <div class="asst-sidebar__footer">
      <div class="asst-sidebar__note">
        <span class="asst-sidebar__note-dot" />
        双击标题可重命名
      </div>
    </div>
  </aside>
</template>

<style scoped>
.asst-sidebar {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fbfaf6;
  border-right: 1px solid var(--border-default);
  padding: 22px 18px 16px;
  height: 100%;
  position: relative;
  overflow: hidden;
}

.asst-sidebar::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 1px;
  background: linear-gradient(to bottom, transparent, rgba(74, 144, 217, 0.15), transparent);
}

/* Brand */
.asst-sidebar__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 18px;
  margin-bottom: 18px;
  border-bottom: 1px dashed var(--border-default);
}

.asst-sidebar__brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--brand-primary), var(--brand-accent));
  color: #fff;
  box-shadow: 0 4px 14px rgba(74, 144, 217, 0.25);
  flex-shrink: 0;
}

.asst-sidebar__brand-mark svg {
  width: 18px;
  height: 18px;
}

.asst-sidebar__brand-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.asst-sidebar__brand-eyebrow {
  font-family: 'Poppins', sans-serif;
  font-size: 0.65rem;
  font-weight: 600;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.asst-sidebar__brand-title {
  font-family: 'Poppins', 'Noto Sans SC', sans-serif;
  font-size: 1rem;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.asst-sidebar__refresh {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  background: transparent;
  border: 1px solid var(--border-default);
  border-radius: 7px;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;
}

.asst-sidebar__refresh:hover:not(:disabled) {
  background: rgba(74, 144, 217, 0.08);
  border-color: var(--brand-primary);
  color: var(--brand-primary);
}

.asst-sidebar__refresh:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.asst-sidebar__refresh svg.is-spinning {
  animation: asst-spin 0.9s linear infinite;
}

@keyframes asst-spin {
  to { transform: rotate(360deg); }
}

/* Section */
.asst-sidebar__section {
  margin-bottom: 14px;
}

.asst-sidebar__label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-family: 'Poppins', sans-serif;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-muted);
  margin-bottom: 7px;
  padding: 0 4px;
}

.asst-sidebar__label-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-accent);
  box-shadow: 0 0 0 3px rgba(92, 201, 193, 0.15);
}

.asst-sidebar__hint {
  margin: 0;
  padding: 8px 12px;
  font-size: 0.74rem;
  color: var(--text-secondary);
  line-height: 1.55;
  background: #fff;
  border: 1px solid var(--border-default);
  border-radius: 10px;
}

/* New session */
.asst-sidebar__new-chat {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 100%;
  padding: 10px 14px;
  margin-bottom: 18px;
  font-family: inherit;
  font-size: 0.88rem;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #1e293b, #0f172a);
  border: none;
  border-radius: 10px;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.15);
  transition: all 0.2s ease;
}

.asst-sidebar__new-chat:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.25);
}

.asst-sidebar__new-chat:active {
  transform: translateY(0);
}

/* History */
.asst-sidebar__history {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.asst-sidebar__history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px;
  margin-bottom: 8px;
}

.asst-sidebar__history-label {
  font-family: 'Poppins', sans-serif;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.asst-sidebar__history-count {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--text-muted);
  background: rgba(148, 163, 184, 0.12);
  padding: 1px 7px;
  border-radius: 100px;
}

.asst-sidebar__list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
  overflow-y: auto;
  padding: 0;
  margin: 0;
  list-style: none;
  scrollbar-width: thin;
  scrollbar-color: var(--border-default) transparent;
}

.asst-sidebar__list::-webkit-scrollbar {
  width: 4px;
}

.asst-sidebar__list::-webkit-scrollbar-thumb {
  background: var(--border-default);
  border-radius: 2px;
}

/* Item */
.asst-sidebar__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px 9px 12px;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.asst-sidebar__item:hover {
  background: rgba(74, 144, 217, 0.06);
}

.asst-sidebar__item.is-active {
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.asst-sidebar__item-bar {
  width: 3px;
  height: 28px;
  border-radius: 2px;
  background: transparent;
  flex-shrink: 0;
  transition: background 0.2s ease;
}

.asst-sidebar__item.is-active .asst-sidebar__item-bar {
  background: linear-gradient(to bottom, var(--brand-primary), var(--brand-accent));
}

.asst-sidebar__item-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.asst-sidebar__item-title {
  font-size: 0.84rem;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asst-sidebar__item-meta {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 0.7rem;
  color: var(--text-muted);
}

.asst-sidebar__item-time {
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.68rem;
}

.asst-sidebar__item-edit {
  width: 100%;
  padding: 3px 8px;
  font-family: inherit;
  font-size: 0.84rem;
  font-weight: 600;
  color: var(--text-primary);
  background: #fff;
  border: 1.5px solid var(--brand-primary);
  border-radius: 5px;
  outline: none;
}

.asst-sidebar__item-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s ease;
  flex-shrink: 0;
}

.asst-sidebar__item:hover .asst-sidebar__item-actions,
.asst-sidebar__item.is-active .asst-sidebar__item-actions {
  opacity: 1;
}

.asst-sidebar__ibtn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  background: transparent;
  border: none;
  border-radius: 5px;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s ease;
}

.asst-sidebar__ibtn:hover {
  background: rgba(74, 144, 217, 0.1);
  color: var(--brand-primary);
}

.asst-sidebar__ibtn--danger:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
}

/* Empty / skeletons */
.asst-sidebar__empty {
  padding: 20px 12px;
  text-align: center;
  border: 1px dashed var(--border-default);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.5);
}

.asst-sidebar__empty p {
  margin: 0 0 4px;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-secondary);
}

.asst-sidebar__empty small {
  font-size: 0.72rem;
  color: var(--text-muted);
}

.asst-sidebar__skeleton {
  height: 40px;
  margin-bottom: 6px;
  background: linear-gradient(90deg, rgba(148, 163, 184, 0.08), rgba(148, 163, 184, 0.18), rgba(148, 163, 184, 0.08));
  background-size: 200% 100%;
  border-radius: 8px;
  animation: asst-shimmer 1.6s linear infinite;
}

@keyframes asst-shimmer {
  from { background-position: 200% 0; }
  to   { background-position: -200% 0; }
}

/* Footer */
.asst-sidebar__footer {
  padding-top: 14px;
  border-top: 1px dashed var(--border-default);
  margin-top: 10px;
}

.asst-sidebar__note {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 0.72rem;
  color: var(--text-muted);
  padding: 0 4px;
}

.asst-sidebar__note-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-accent);
}
</style>
