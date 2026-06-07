<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { fetchGroups } from '@/api/group'
import {
  getQaRecord,
  listQaRecords,
  streamAskQuestion,
  type CitationItem,
  type EvidenceOverview,
  type QaRecordCitation,
  type QaRecordDetail,
  type QaRecordListItem,
} from '@/api/qa'
import { extractApiError } from '@/api/http'
import type { DocumentItem } from '@/api/document'
import DocumentPreviewModal from '@/components/DocumentPreviewModal.vue'
import QaSidebar from './components/QaSidebar.vue'
import QaTranscript from './components/QaTranscript.vue'
import QaComposer from './components/QaComposer.vue'
import QaEmptyHero from './components/QaEmptyHero.vue'
import { useQaSessions, type QaMessage, type QaSession } from './composables/useQaSessions'

const appStore = useAppStore()
const authStore = useAuthStore()
const {
  sessions,
  activeSession,
  activeSessionId,
  createSession,
  selectSession,
  deleteSession,
  appendMessage,
  updateMessage,
  uid,
} = useQaSessions()

// ── Group state ──
const groupsLoading = ref(false)
const groupsError = ref('')
const selectedGroupId = ref<number | null>(appStore.currentGroupId)

const selectedGroupName = computed(() => {
  const g = appStore.visibleGroups.find((x) => x.groupId === selectedGroupId.value)
  return g?.groupName ?? ''
})

const hasGroup = computed(() => selectedGroupId.value !== null)

async function loadGroups() {
  groupsLoading.value = true
  groupsError.value = ''
  try {
    const result = await fetchGroups()
    appStore.applyGroupQueryResult(result)
    if (selectedGroupId.value === null || !appStore.visibleGroups.some((g) => g.groupId === selectedGroupId.value)) {
      selectedGroupId.value = appStore.currentGroupId ?? appStore.visibleGroups[0]?.groupId ?? null
    }
    await loadHistoryForSelectedGroup()
  } catch (err) {
    groupsError.value = extractApiError(err, '加载群组失败')
  } finally {
    groupsLoading.value = false
  }
}

watch(selectedGroupId, (v) => {
  appStore.setCurrentGroupId(v)
  // If active session is bound to a different group, leave it intact — user may be reviewing history.
  // When the user asks a new question, we'll rebind if needed.
  loadHistoryForSelectedGroup()
})

// ── Ask flow ──
const asking = ref(false)
const historyLoading = ref(false)
const historyError = ref('')
let historyRequestSeq = 0

function recordSessionId(recordId: number): string {
  return `qa-record-${recordId}`
}

function timestampOf(value: string): number {
  const time = Date.parse(value)
  return Number.isFinite(time) ? time : Date.now()
}

function groupNameOf(groupId: number | null): string {
  if (groupId === null) return ''
  return appStore.visibleGroups.find((group) => group.groupId === groupId)?.groupName ?? selectedGroupName.value
}

function mapCitation(citation: QaRecordCitation): CitationItem {
  return {
    documentId: citation.documentId,
    chunkId: citation.chunkId,
    chunkIndex: citation.chunkIndex,
    fileName: citation.fileName,
    score: citation.score ?? 0,
    snippet: citation.snippet,
  }
}

function buildHistorySession(record: QaRecordListItem): QaSession {
  const createdAt = timestampOf(record.createdAt)
  return {
    id: recordSessionId(record.id),
    title: record.question,
    groupId: record.groupId,
    groupName: groupNameOf(record.groupId),
    recordId: record.id,
    hydrated: false,
    createdAt,
    updatedAt: createdAt,
    messages: [
      {
        id: `qa-record-${record.id}-question`,
        role: 'user',
        content: record.question,
        createdAt,
      },
      {
        id: `qa-record-${record.id}-answer`,
        role: 'assistant',
        content: record.answerPreview || record.reasonCode || '',
        createdAt,
        pending: false,
        answered: record.answered,
        reasonCode: record.reasonCode,
        reasonMessage: null,
        citations: [],
        evidenceOverview: null,
        recordId: record.id,
      },
    ],
  }
}

function applyRecordDetail(session: QaSession, detail: QaRecordDetail) {
  const createdAt = timestampOf(detail.createdAt)
  session.title = detail.question
  session.groupId = detail.groupId
  session.groupName = groupNameOf(detail.groupId)
  session.createdAt = createdAt
  session.updatedAt = createdAt
  session.hydrated = true
  session.messages = [
    {
      id: `qa-record-${detail.id}-question`,
      role: 'user',
      content: detail.question,
      createdAt,
    },
    {
      id: `qa-record-${detail.id}-answer`,
      role: 'assistant',
      content: detail.answered ? detail.answer ?? '' : detail.reasonMessage ?? '',
      createdAt,
      pending: false,
      answered: detail.answered,
      reasonCode: detail.reasonCode,
      reasonMessage: detail.reasonMessage,
      citations: detail.citations.map(mapCitation),
      evidenceOverview: detail.evidenceOverview ?? null,
      recordId: detail.id,
    },
  ]
}

async function loadHistoryForSelectedGroup() {
  if (!authStore.accessToken || selectedGroupId.value === null) {
    sessions.value = []
    activeSessionId.value = null
    return
  }
  const requestSeq = ++historyRequestSeq
  historyLoading.value = true
  historyError.value = ''
  try {
    const page = await listQaRecords({
      groupId: selectedGroupId.value,
      page: 1,
      pageSize: 20,
    })
    if (requestSeq !== historyRequestSeq) return
    sessions.value = page.items.map(buildHistorySession)
    activeSessionId.value = sessions.value[0]?.id ?? null
    if (activeSessionId.value) {
      await hydrateHistorySession(activeSessionId.value)
    }
  } catch (err) {
    if (requestSeq !== historyRequestSeq) return
    historyError.value = extractApiError(err, '加载历史会话失败')
  } finally {
    if (requestSeq === historyRequestSeq) {
      historyLoading.value = false
    }
  }
}

async function hydrateHistorySession(sessionId: string) {
  const session = sessions.value.find((item) => item.id === sessionId)
  if (!session?.recordId || session.hydrated) return
  try {
    const detail = await getQaRecord(session.recordId)
    applyRecordDetail(session, detail)
  } catch (err) {
    historyError.value = extractApiError(err, '加载历史详情失败')
  }
}

function ensureSessionForAsk(): string {
  if (activeSession.value && activeSession.value.groupId === selectedGroupId.value) {
    return activeSession.value.id
  }
  // If active session is for a different group OR no active session, create a new one
  const s = createSession(selectedGroupId.value, selectedGroupName.value)
  return s.id
}

async function handleAsk(text: string) {
  if (!text.trim() || selectedGroupId.value === null || asking.value) return
  if (!authStore.accessToken) {
    console.warn('No access token; cannot stream.')
    return
  }

  const sessionId = ensureSessionForAsk()
  const now = Date.now()

  // Push user message
  const userMsg: QaMessage = {
    id: uid(),
    role: 'user',
    content: text,
    createdAt: now,
  }
  appendMessage(sessionId, userMsg)

  // Push pending assistant message
  const assistantId = uid()
  appendMessage(sessionId, {
    id: assistantId,
    role: 'assistant',
    content: '',
    createdAt: Date.now(),
    pending: true,
  })

  asking.value = true
  let citationsReceived = false
  let recordId: number | null = null
  try {
    let streamedContent = ''

    await streamAskQuestion(
      {
        groupId: selectedGroupId.value,
        question: text,
      },
      authStore.accessToken!,
      {
        onToken(token: string) {
          streamedContent += token
          updateMessage(sessionId, assistantId, {
            content: streamedContent,
            pending: true,
          })
        },
        onCitations(citations: CitationItem[]) {
          citationsReceived = true
          updateMessage(sessionId, assistantId, {
            content: streamedContent,
            pending: false,
            answered: citations.length > 0 || streamedContent.length > 0,
            reasonCode: null,
            reasonMessage: null,
            citations,
          })
        },
        onEvidenceOverview(overview: EvidenceOverview | null) {
          updateMessage(sessionId, assistantId, {
            evidenceOverview: overview,
          })
        },
        onError(message: string) {
          updateMessage(sessionId, assistantId, {
            content: streamedContent,
            pending: false,
            answered: false,
            reasonCode: 'STREAM_ERROR',
            reasonMessage: message,
            citations: [],
            evidenceOverview: null,
          })
        },
        onRecord(id: number) {
          recordId = id
          updateMessage(sessionId, assistantId, {
            recordId: id,
          })
        },
      },
    )

    // 流正常结束但 onCitations 未被调用时的兜底（避免覆盖已设置的 citations）
    if (!citationsReceived) {
      updateMessage(sessionId, assistantId, {
        content: streamedContent,
        pending: false,
        answered: streamedContent.length > 0,
        reasonCode: null,
        reasonMessage: null,
        citations: [],
        evidenceOverview: null,
        recordId,
      })
    }
  } catch (err) {
    updateMessage(sessionId, assistantId, {
      content: '',
      pending: false,
      answered: false,
      reasonCode: 'REQUEST_FAILED',
      reasonMessage: extractApiError(err, '请求失败，请稍后再试'),
      citations: [],
      evidenceOverview: null,
    })
  } finally {
    asking.value = false
  }
}

function handleNewChat() {
  createSession(selectedGroupId.value, selectedGroupName.value)
}

function handleSelectSession(sessionId: string) {
  selectSession(sessionId)
  hydrateHistorySession(sessionId)
}

const composerRef = ref<InstanceType<typeof QaComposer> | null>(null)

function handleStarterPick(prompt: string) {
  composerRef.value?.setText(prompt)
}

// ── Citation preview bridge ──
const previewVisible = ref(false)
const previewDocument = ref<DocumentItem | null>(null)

function openCitation(c: CitationItem) {
  if (c.documentId === null) return
  const groupId = activeSession.value?.groupId ?? selectedGroupId.value
  if (groupId === null) return
  const fileExt = extractExt(c.fileName)
  previewDocument.value = {
    documentId: c.documentId,
    groupId,
    fileName: c.fileName,
    fileExt,
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

function extractExt(fileName: string): string | null {
  const idx = fileName.lastIndexOf('.')
  if (idx < 0) return null
  return fileName.slice(idx + 1).toLowerCase()
}

// ── Lifecycle ──
onMounted(() => {
  if (appStore.visibleGroups.length === 0) {
    loadGroups()
  } else if (selectedGroupId.value === null) {
    selectedGroupId.value = appStore.visibleGroups[0]?.groupId ?? null
  } else {
    loadHistoryForSelectedGroup()
  }
})
</script>

<template>
  <div class="qa-page">
    <QaSidebar
      v-model:selected-group-id="selectedGroupId"
      :groups="appStore.visibleGroups"
      :groups-loading="groupsLoading"
      :history-loading="historyLoading"
      :history-error="historyError"
      :sessions="sessions"
      :active-session-id="activeSessionId"
      @new-chat="handleNewChat"
      @select-session="handleSelectSession"
      @delete-session="deleteSession"
    />

    <main class="qa-page__main">
      <template v-if="activeSession && activeSession.messages.length > 0">
        <QaTranscript
          :messages="activeSession.messages"
          :session-id="activeSession.id"
          :group-name="activeSession.groupName"
          @inspect-citation="openCitation"
        />
      </template>
      <template v-else>
        <QaEmptyHero
          :group-name="selectedGroupName"
          :has-group="hasGroup"
          @pick="handleStarterPick"
        />
      </template>

      <QaComposer
        ref="composerRef"
        :disabled="!hasGroup"
        :loading="asking"
        :group-name="selectedGroupName"
        @submit="handleAsk"
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
.qa-page {
  display: flex;
  height: 100%;
  min-height: 560px;
  background: #fff;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
}

.qa-page__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
}

@media (max-width: 900px) {
  .qa-page {
    flex-direction: column;
    height: auto;
    min-height: 100vh;
    border-radius: 0;
  }
}
</style>
