<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { fetchGroups } from '@/api/group'
import { streamAskQuestion, type CitationItem } from '@/api/qa'
import { extractApiError } from '@/api/http'
import type { DocumentItem } from '@/api/document'
import DocumentPreviewModal from '@/components/DocumentPreviewModal.vue'
import QaSidebar from './components/QaSidebar.vue'
import QaTranscript from './components/QaTranscript.vue'
import QaComposer from './components/QaComposer.vue'
import QaEmptyHero from './components/QaEmptyHero.vue'
import { useQaSessions, type QaMessage } from './composables/useQaSessions'

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
})

// ── Ask flow ──
const asking = ref(false)

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
        onError(message: string) {
          updateMessage(sessionId, assistantId, {
            content: streamedContent,
            pending: false,
            answered: false,
            reasonCode: 'STREAM_ERROR',
            reasonMessage: message,
            citations: [],
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
    })
  } finally {
    asking.value = false
  }
}

function handleNewChat() {
  createSession(selectedGroupId.value, selectedGroupName.value)
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
  }
})
</script>

<template>
  <div class="qa-page">
    <QaSidebar
      v-model:selected-group-id="selectedGroupId"
      :groups="appStore.visibleGroups"
      :groups-loading="groupsLoading"
      :sessions="sessions"
      :active-session-id="activeSessionId"
      @new-chat="handleNewChat"
      @select-session="selectSession"
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
