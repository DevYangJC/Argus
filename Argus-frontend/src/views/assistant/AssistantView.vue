<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { fetchGroups } from '@/api/group'
import { extractApiError } from '@/api/http'
import {
  createAssistantSession,
  deleteAssistantSession,
  fetchAssistantSessions,
  fetchAssistantSessionDetail,
  fetchAssistantConversationContext,
  renameAssistantSession,
  streamAssistantMessage,
} from '@/api/assistant'
import type {
  AssistantChatStreamEvent,
  AssistantCitationItem,
  AssistantMessageItem,
  AssistantSessionListItem,
  AssistantToolMode,
} from '@/types/assistant'
import type { DocumentItem } from '@/api/document'
import DocumentPreviewModal from '@/components/DocumentPreviewModal.vue'
import AssistantSidebar from './components/AssistantSidebar.vue'
import AssistantTranscript from './components/AssistantTranscript.vue'
import AssistantComposer from './components/AssistantComposer.vue'
import AssistantEmpty from './components/AssistantEmpty.vue'
import type { UiAssistantMessage } from './components/AssistantMessage.vue'

const appStore = useAppStore()
const authStore = useAuthStore()

// ── Sessions ──
const sessions = ref<AssistantSessionListItem[]>([])
const activeSessionId = ref<number | null>(null)
const sessionsLoading = ref(false)
const messages = ref<UiAssistantMessage[]>([])
const loadingHistory = ref(false)

const activeSession = computed(() =>
  sessions.value.find((s) => s.sessionId === activeSessionId.value) ?? null,
)

// ── Mode + KB ──
const mode = ref<AssistantToolMode>('CHAT')
const selectedGroupId = ref<number | null>(appStore.currentGroupId)

const selectedGroupName = computed(() => {
  const g = appStore.visibleGroups.find((x) => x.groupId === selectedGroupId.value)
  return g?.groupName ?? ''
})

// ── Streaming state ──
const streaming = ref(false)
let streamAbort: AbortController | null = null
let lastAskPayload: { text: string; mode: AssistantToolMode; groupId: number | null } | null = null

// ── Helpers ──
function localId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`
}

function toUi(m: AssistantMessageItem): UiAssistantMessage {
  return {
    localId: `srv-${m.messageId}`,
    messageId: m.messageId,
    role: m.role,
    content: m.content ?? '',
    toolMode: m.toolMode,
    groupId: m.groupId,
    createdAt: m.createdAt,
  }
}

// ── Data loaders ──
async function loadSessions(silent = false) {
  if (!silent) sessionsLoading.value = true
  try {
    sessions.value = await fetchAssistantSessions()
  } catch (err) {
    console.error('Load sessions failed:', extractApiError(err, ''))
  } finally {
    sessionsLoading.value = false
  }
}

async function loadGroupsIfNeeded() {
  if (appStore.visibleGroups.length > 0) return
  try {
    const result = await fetchGroups()
    appStore.applyGroupQueryResult(result)
  } catch (err) {
    console.error('Load groups failed:', extractApiError(err, ''))
  }
}

async function loadSessionHistory(sessionId: number) {
  loadingHistory.value = true
  try {
    // Prefer context endpoint — returns recent messages (asc by time), plenty for UI.
    const ctx = await fetchAssistantConversationContext(sessionId, 50)
    messages.value = (ctx.recentMessages ?? []).map(toUi)
  } catch (err) {
    console.error('Load session history failed:', extractApiError(err, ''))
    messages.value = []
  } finally {
    loadingHistory.value = false
  }
}

// ── Session actions ──
async function handleNewSession() {
  try {
    const detail = await createAssistantSession()
    const item: AssistantSessionListItem = {
      sessionId: detail.sessionId,
      title: detail.title,
      lastMessageAt: detail.lastMessageAt,
    }
    sessions.value.unshift(item)
    activeSessionId.value = detail.sessionId
    messages.value = []
  } catch (err) {
    console.error('Create session failed:', extractApiError(err, '创建会话失败'))
  }
}

async function handleSelectSession(id: number) {
  if (activeSessionId.value === id) return
  if (streaming.value) abortStream()
  activeSessionId.value = id
  await loadSessionHistory(id)
}

async function handleRenameSession(id: number, title: string) {
  try {
    const detail = await renameAssistantSession(id, title)
    const target = sessions.value.find((s) => s.sessionId === id)
    if (target) target.title = detail.title
  } catch (err) {
    console.error('Rename session failed:', extractApiError(err, '重命名失败'))
  }
}

async function handleDeleteSession(id: number) {
  const target = sessions.value.find((s) => s.sessionId === id)
  if (!target) return
  try {
    await ElMessageBox.confirm(
      `确定删除会话「${target.title}」吗？会话下的所有消息都会被清除。`,
      '删除会话',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteAssistantSession(id)
    sessions.value = sessions.value.filter((s) => s.sessionId !== id)
    if (activeSessionId.value === id) {
      activeSessionId.value = null
      messages.value = []
    }
  } catch (err) {
    console.error('Delete session failed:', extractApiError(err, '删除失败'))
  }
}

// ── Ensure a session exists before asking ──
async function ensureSession(): Promise<number | null> {
  if (activeSessionId.value !== null) return activeSessionId.value
  try {
    const detail = await createAssistantSession()
    const item: AssistantSessionListItem = {
      sessionId: detail.sessionId,
      title: detail.title,
      lastMessageAt: detail.lastMessageAt,
    }
    sessions.value.unshift(item)
    activeSessionId.value = detail.sessionId
    messages.value = []
    return detail.sessionId
  } catch (err) {
    console.error('Create session failed:', extractApiError(err, '创建会话失败'))
    return null
  }
}

// ── Streaming ask ──
async function handleAsk(text: string) {
  if (!text.trim() || streaming.value) return

  // KB mode requires a group
  if (mode.value === 'KB_SEARCH' && selectedGroupId.value === null) {
    return
  }
  if (!authStore.accessToken) {
    console.warn('No access token; cannot stream.')
    return
  }

  const sessionId = await ensureSession()
  if (sessionId === null) return

  const groupIdForPayload = mode.value === 'KB_SEARCH' ? selectedGroupId.value : null

  lastAskPayload = { text, mode: mode.value, groupId: groupIdForPayload }

  // Push user bubble (optimistic)
  const nowIso = new Date().toISOString()
  const userMsg: UiAssistantMessage = {
    localId: localId(),
    messageId: null,
    role: 'USER',
    content: text,
    toolMode: mode.value,
    groupId: groupIdForPayload,
    createdAt: nowIso,
  }
  messages.value.push(userMsg)

  // Push assistant placeholder
  const assistantMsg: UiAssistantMessage = {
    localId: localId(),
    messageId: null,
    role: 'ASSISTANT',
    content: '',
    toolMode: mode.value,
    groupId: groupIdForPayload,
    createdAt: new Date().toISOString(),
    streaming: true,
    citations: [],
  }
  messages.value.push(assistantMsg)
  // 关键：push 后 Vue 会把元素包一层 Proxy，只有通过 Proxy 改属性才会触发响应式。
  // 直接保留 push 前的原始对象会"写到原始内存但 Vue 不知道"，打字机效果失效。
  const target = messages.value[messages.value.length - 1]!

  streaming.value = true
  streamAbort = new AbortController()

  try {
    await streamAssistantMessage(
      {
        sessionId,
        message: text,
        toolMode: mode.value,
        groupId: groupIdForPayload,
      },
      authStore.accessToken,
      {
        signal: streamAbort.signal,
        onEvent: (ev) => onStreamEvent(ev, target),
      },
    )
  } catch (err) {
    if ((err as { name?: string })?.name === 'AbortError') {
      // User aborted — preserve whatever content we already have
      target.streaming = false
      if (!target.content) {
        target.content = '_(已中断)_'
      } else {
        target.content += '\n\n_(已中断)_'
      }
    } else {
      target.streaming = false
      target.failed = true
      target.failureMessage = extractApiError(err, '流式请求失败')
    }
  } finally {
    streaming.value = false
    streamAbort = null
    // Bump the session in the local list (move to top, update lastMessageAt)
    bumpSession(sessionId)
    // Silently reload sessions so auto-rename takes effect
    loadSessions(true)
  }
}

function onStreamEvent(ev: AssistantChatStreamEvent, target: UiAssistantMessage) {
  switch (ev.event) {
    case 'start':
      target.streaming = true
      break
    case 'delta':
      if (ev.delta) target.content += ev.delta
      break
    case 'done':
      if (ev.reply != null && ev.reply.length > 0) {
        // Prefer authoritative final reply
        target.content = ev.reply
      }
      if (ev.messageId != null) {
        target.messageId = ev.messageId
        target.localId = `srv-${ev.messageId}`
      }
      target.citations = ev.citations ?? []
      target.streaming = false
      break
    case 'error':
      target.streaming = false
      target.failed = true
      target.failureMessage = ev.error ?? '生成失败'
      break
  }
}

function abortStream() {
  if (streamAbort) {
    streamAbort.abort()
  }
}

function bumpSession(sessionId: number) {
  const idx = sessions.value.findIndex((s) => s.sessionId === sessionId)
  if (idx < 0) return
  const [item] = sessions.value.splice(idx, 1)
  if (item) {
    item.lastMessageAt = new Date().toISOString()
    sessions.value.unshift(item)
  }
}

async function handleRetry() {
  if (!lastAskPayload || streaming.value) return
  // Drop the trailing failed assistant message; keep user message
  const last = messages.value[messages.value.length - 1]
  if (last && last.role === 'ASSISTANT' && last.failed) {
    messages.value.pop()
  }
  // Restore mode + group (in case user changed them before retry)
  const previous = lastAskPayload
  mode.value = previous.mode
  if (previous.mode === 'KB_SEARCH' && previous.groupId !== null) {
    selectedGroupId.value = previous.groupId
  }
  await handleAsk(previous.text)
}

const composerRef = ref<InstanceType<typeof AssistantComposer> | null>(null)

function handleStarterPick(prompt: string, starterMode: AssistantToolMode) {
  mode.value = starterMode
  composerRef.value?.setText(prompt)
}

// ── Citation preview bridge ──
const previewVisible = ref(false)
const previewDocument = ref<DocumentItem | null>(null)

function openCitation(c: AssistantCitationItem) {
  if (c.documentId === null) return
  // Use currently selected group as best guess — KB_SEARCH tracks it precisely
  const groupId = selectedGroupId.value
  if (groupId === null) return
  const idx = c.fileName.lastIndexOf('.')
  const ext = idx >= 0 ? c.fileName.slice(idx + 1).toLowerCase() : null
  previewDocument.value = {
    documentId: c.documentId,
    groupId,
    fileName: c.fileName,
    fileExt: ext,
    contentType: null,
    fileSize: 0,
    status: 'READY',
    failureReason: null,
    uploadedAt: '',
    uploaderUserId: null,
    uploaderDisplayName: null,
    uploaderUserCode: null,
    previewText: c.snippet,
  }
  previewVisible.value = true
}

// ── Init ──
onMounted(async () => {
  await Promise.all([loadSessions(), loadGroupsIfNeeded()])
  if (sessions.value.length > 0 && activeSessionId.value === null) {
    const firstSession = sessions.value[0]
    if (firstSession) {
      activeSessionId.value = firstSession.sessionId
      await loadSessionHistory(firstSession.sessionId)
    }
  }
  if (selectedGroupId.value === null && appStore.visibleGroups.length > 0) {
    const firstGroup = appStore.visibleGroups[0]
    if (firstGroup) selectedGroupId.value = firstGroup.groupId
  }
})
</script>

<template>
  <div class="asst-page">
    <AssistantSidebar
      :sessions="sessions"
      :active-session-id="activeSessionId"
      :loading="sessionsLoading"
      @new-session="handleNewSession"
      @select-session="handleSelectSession"
      @rename-session="handleRenameSession"
      @delete-session="handleDeleteSession"
      @refresh="() => loadSessions()"
    />

    <main class="asst-page__main">
      <template v-if="activeSessionId !== null && (messages.length > 0 || loadingHistory)">
        <AssistantTranscript
          :messages="messages"
          :session-id="activeSessionId"
          :session-title="activeSession?.title ?? ''"
          :mode="mode"
          :group-name="selectedGroupName"
          :loading-history="loadingHistory"
          @update:mode="(m) => (mode = m)"
          @inspect-citation="openCitation"
          @retry="handleRetry"
        />
      </template>
      <template v-else>
        <AssistantEmpty
          :mode="mode"
          :has-kb="selectedGroupId !== null"
          @pick="(prompt, starterMode) => handleStarterPick(prompt, starterMode)"
        />
      </template>

      <AssistantComposer
        ref="composerRef"
        v-model:mode="mode"
        v-model:selected-group-id="selectedGroupId"
        :disabled="false"
        :streaming="streaming"
        :groups="appStore.visibleGroups"
        @submit="handleAsk"
        @abort="abortStream"
      />
    </main>

    <DocumentPreviewModal
      :visible="previewVisible"
      :document="previewDocument"
      @update:visible="(v: boolean) => (previewVisible = v)"
    />
  </div>
</template>

<style scoped>
.asst-page {
  display: flex;
  height: 100%;
  min-height: 600px;
  background: #fff;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
  position: relative;
}

.asst-page__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
}

@media (max-width: 900px) {
  .asst-page {
    flex-direction: column;
    height: auto;
    min-height: 100vh;
    border-radius: 0;
  }
}
</style>
