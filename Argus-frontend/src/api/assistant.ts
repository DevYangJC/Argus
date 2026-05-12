import http, { type ApiResponse } from './http'
import type {
  AssistantChatPayload,
  AssistantChatResult,
  AssistantChatStreamEvent,
  AssistantConversationContext,
  AssistantSessionDetail,
  AssistantSessionListItem,
} from '../types/assistant'

// ─────────────────────────────────────────────
// API 函数 —— 会话管理
// ─────────────────────────────────────────────

/**
 * 创建新的助手会话
 *
 * POST /api/assistant/sessions
 *
 * 为当前用户创建空会话，默认标题为"新会话"。
 *
 * @returns 创建成功的会话详情
 */
export async function createAssistantSession(): Promise<AssistantSessionDetail> {
  const { data } = await http.post<ApiResponse<AssistantSessionDetail>>('/assistant/sessions')
  if (!data.success || data.data == null) {
    throw new Error(data.message ?? '创建会话失败')
  }
  return data.data
}

/**
 * 获取当前用户的会话列表
 *
 * GET /api/assistant/sessions
 *
 * 后端直接返回数组（非 ApiResponse 包裹），按最后消息时间降序排列。
 *
 * @returns 会话简要信息列表
 */
export async function fetchAssistantSessions(): Promise<AssistantSessionListItem[]> {
  const { data } = await http.get<AssistantSessionListItem[]>('/assistant/sessions')
  return data
}

/**
 * 获取指定会话的详细信息
 *
 * GET /api/assistant/sessions/{sessionId}
 *
 * 后端直接返回对象（非 ApiResponse 包裹）。
 *
 * @param sessionId 会话 ID
 * @returns 会话详情
 */
export async function fetchAssistantSessionDetail(sessionId: number): Promise<AssistantSessionDetail> {
  const { data } = await http.get<AssistantSessionDetail>(`/assistant/sessions/${sessionId}`)
  return data
}

/**
 * 重命名会话
 *
 * PATCH /api/assistant/sessions/{sessionId}
 *
 * 请求体中携带新标题，标题不能为空且长度不能超过 255 个字符。
 *
 * @param sessionId 会话 ID
 * @param title 新的会话标题
 * @returns 更新后的会话详情
 */
export async function renameAssistantSession(sessionId: number, title: string): Promise<AssistantSessionDetail> {
  const { data } = await http.patch<ApiResponse<AssistantSessionDetail>>(`/assistant/sessions/${sessionId}`, { title })
  if (!data.success || data.data == null) {
    throw new Error(data.message ?? '重命名会话失败')
  }
  return data.data
}

/**
 * 删除指定会话
 *
 * DELETE /api/assistant/sessions/{sessionId}
 *
 * 同时删除会话下所有消息记录和上下文记录。
 *
 * @param sessionId 会话 ID
 */
export async function deleteAssistantSession(sessionId: number): Promise<void> {
  const { data } = await http.delete<ApiResponse<null>>(`/assistant/sessions/${sessionId}`)
  if (!data.success) {
    throw new Error(data.message ?? '删除会话失败')
  }
}

// ─────────────────────────────────────────────
// API 函数 —— 会话上下文
// ─────────────────────────────────────────────

/**
 * 获取指定会话的上下文信息
 *
 * GET /api/assistant/sessions/{sessionId}/context
 *
 * 后端直接返回对象（非 ApiResponse 包裹）。
 * 返回会话的摘要文本和最近消息列表，用于恢复会话上下文或展示会话概览。
 *
 * @param sessionId 会话 ID
 * @param recentLimit 最近消息条数上限，默认值为 12（对应后端默认值）
 * @returns 会话上下文，包含摘要文本和最近消息列表
 */
export async function fetchAssistantConversationContext(
  sessionId: number,
  recentLimit = 12,
): Promise<AssistantConversationContext> {
  const { data } = await http.get<AssistantConversationContext>(`/assistant/sessions/${sessionId}/context`, {
    params: { recentLimit },
  })
  return data
}

// ─────────────────────────────────────────────
// API 函数 —— 聊天
// ─────────────────────────────────────────────

/**
 * 同步聊天（非流式）
 *
 * POST /api/assistant/chat
 *
 * 一次请求完整返回助手回复，包含回复内容和引用来源列表。
 *
 * @param payload 聊天请求体，包含会话 ID、工具模式、知识库组 ID 和用户消息
 * @returns 助手回复结果（包含消息内容和引用列表）
 */
export async function sendAssistantMessage(payload: AssistantChatPayload): Promise<AssistantChatResult> {
  const { data } = await http.post<ApiResponse<AssistantChatResult>>('/assistant/chat', payload)
  if (!data.success || data.data == null) {
    throw new Error(data.message ?? '发送消息失败')
  }
  return data.data
}

/**
 * 流式聊天（SSE）
 *
 * POST /api/assistant/chat/stream
 *
 * 通过 Fetch API 建立 SSE 连接，后端逐段推送模型生成的文本增量。
 * 使用 AbortSignal 可在需要时中断流。
 *
 * SSE 事件格式：
 * - event: delta | end | error
 * - data: JSON 序列化的 AssistantChatStreamEvent
 *
 * @param payload 聊天请求体，包含会话 ID、工具模式、知识库组 ID 和用户消息
 * @param accessToken JWT Access Token（用于 Authorization 请求头）
 * @param handlers 事件处理器：onEvent 接收每个 SSE 事件，signal 用于中断
 */
export async function streamAssistantMessage(
  payload: AssistantChatPayload,
  accessToken: string,
  handlers: {
    onEvent: (event: AssistantChatStreamEvent) => void
    signal?: AbortSignal
  },
): Promise<void> {
  const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/$/, '')
  const response = await fetch(`${baseUrl}/assistant/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(payload),
    signal: handlers.signal,
  })

  if (!response.ok || response.body == null) {
    const message = await response.text()
    throw new Error(message || '发送流式消息失败')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    let separatorIndex = buffer.indexOf('\n\n')
    while (separatorIndex >= 0) {
      const rawEvent = buffer.slice(0, separatorIndex)
      buffer = buffer.slice(separatorIndex + 2)
      const parsed = parseSseEvent(rawEvent)
      if (parsed !== null) {
        handlers.onEvent(parsed)
      }
      separatorIndex = buffer.indexOf('\n\n')
    }
  }
}

// ─────────────────────────────────────────────
// 内部工具函数
// ─────────────────────────────────────────────

/**
 * 解析单条 SSE 原始文本为事件对象
 * SSE 格式示例：
 * ```
 * event: delta
 * data: {"event":"delta","content":"Hello"}
 * ```
 * @param rawEvent 原始 SSE 字符串（不含末尾 \n\n）
 * @returns 解析后的事件对象，数据为空时返回 null
 */
function parseSseEvent(rawEvent: string): AssistantChatStreamEvent | null {
  const lines = rawEvent.split(/\r?\n/)
  let eventName = ''
  const dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }

  if (dataLines.length === 0) {
    return null
  }

  const parsed = JSON.parse(dataLines.join('\n')) as AssistantChatStreamEvent
  return {
    ...parsed,
    event: (eventName || parsed.event) as AssistantChatStreamEvent['event'],
  }
}
