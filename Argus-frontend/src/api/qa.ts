import http from './http'

// ─────────────────────────────────────────────
// 类型定义
// ─────────────────────────────────────────────

/**
 * 引用来源条目
 * 对应后端 CitationItem（嵌套在 AskQuestionResponse 中）
 */
export interface CitationItem {
  /** 引用文档的 ID，可能为 null（跨库引用或来源丢失时） */
  documentId: number | null
  /** 引用文档块（Chunk）的 ID，可能为 null */
  chunkId: number | null
  /** 引用文档块在文档中的索引，可能为 null */
  chunkIndex: number | null
  /** 来源文档文件名 */
  fileName: string
  /** 相关度得分（0~1，越高越相关） */
  score: number
  /** 引用文本片段（摘录），可能为 null */
  snippet: string | null
}

/**
 * 问答请求参数
 * 对应后端 AskQuestionRequest
 */
export interface AskQuestionPayload {
  /** 在哪个群组的知识库范围内提问 */
  groupId: number
  /** 用户问题文本 */
  question: string
}

/**
 * 问答响应结果
 * 对应后端 AskQuestionResponse
 *
 * 注意：此接口后端不走 ApiResponse 包装，直接返回 AskQuestionResponse。
 */
export interface AskQuestionResponse {
  /** 是否成功回答了问题（检索到有效证据并生成了回答） */
  answered: boolean
  /** 回答内容，未能回答时为 null */
  answer: string | null
  /**
   * 拒答原因码（answered 为 false 时有值），例如：
   * - NO_EVIDENCE：未检索到相关文档
   * - LOW_CONFIDENCE：检索到文档但置信度不足
   */
  reasonCode: string | null
  /** 拒答原因描述（人类可读文本），answered 为 false 时有值 */
  reasonMessage: string | null
  /** 引用来源列表（支持答案溯源） */
  citations: CitationItem[]
}

/**
 * 流式问答事件处理器
 * 对应 SSE 推送的三种事件类型
 */
export interface QaStreamHandlers {
  /** 接收到大模型生成的文本片段 */
  onToken: (text: string) => void
  /** 流式回答结束后接收到的引用来源列表 */
  onCitations: (citations: CitationItem[]) => void
  /** 发生错误时回调 */
  onError: (message: string) => void
  /** 可用于中断流式连接的 AbortSignal */
  signal?: AbortSignal
}

// ─────────────────────────────────────────────
// API 函数
// ─────────────────────────────────────────────

/**
 * 在指定群组知识库中提问
 *
 * POST /api/qa/ask
 *
 * 后端执行：权限校验 → 查询规划 → 混合检索 → 证据评估 → LLM 生成回答 → 引用组装。
 *
 * 注意：此接口后端直接返回 AskQuestionResponse，不走 ApiResponse 包装。
 * HTTP 请求错误（如 4xx/5xx）由 Axios 拦截器统一处理。
 *
 * @param payload 问答请求参数（groupId + question）
 * @returns 问答结果，包含回答内容或拒答原因及引用来源
 */
export async function askQuestion(payload: AskQuestionPayload): Promise<AskQuestionResponse> {
  const { data } = await http.post<AskQuestionResponse>('/qa/ask', payload)

  return {
    answered: data.answered,
    answer: data.answer ?? null,
    reasonCode: data.reasonCode ?? null,
    reasonMessage: data.reasonMessage ?? null,
    citations: data.citations ?? [],
  }
}

/**
 * 流式提问（SSE）
 *
 * POST /api/qa/stream-ask
 *
 * 通过 Fetch API 建立 SSE 连接，后端逐 token 推送大模型生成的文本增量。
 * 使用 AbortSignal 可在需要时中断流。
 *
 * SSE 事件格式：
 * - event: token   → data: 文本片段
 * - event: citations → data: JSON 序列化的 CitationItem 数组
 * - event: error   → data: {"message": "..."}
 *
 * @param payload 问答请求参数（groupId + question）
 * @param handlers 事件处理器：onToken / onCitations / onError + 可选的 signal
 */
export async function streamAskQuestion(
  payload: AskQuestionPayload,
  accessToken: string,
  handlers: QaStreamHandlers,
): Promise<void> {
  const baseUrl = (import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/$/, '')
  const response = await fetch(`${baseUrl}/qa/stream-ask`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(payload),
    signal: handlers.signal,
  })

  if (!response.ok || response.body == null) {
    const message = await response.text().catch(() => '流式问答请求失败')
    throw new Error(message || '流式问答请求失败')
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
      dispatchQaSseEvent(rawEvent, handlers)
      separatorIndex = buffer.indexOf('\n\n')
    }
  }
}

// ─────────────────────────────────────────────
// 内部工具函数
// ─────────────────────────────────────────────

/**
 * 解析并分发单条 QA SSE 事件
 *
 * SSE 原始格式示例：
 * ```
 * event: token
 * data: 这是一段文本
 * ```
 * ```
 * event: citations
 * data: [{"documentId":1,...}]
 * ```
 * ```
 * event: error
 * data: {"message":"错误描述"}
 * ```
 *
 * @param rawEvent 单条 SSE 原始字符串（不含末尾

），可能包含 \r
 或

 * @param handlers 事件处理器
 */
function dispatchQaSseEvent(rawEvent: string, handlers: QaStreamHandlers): void {
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
    return
  }

  const rawData = dataLines.join('\n')

  switch (eventName) {
    case 'token':
      handlers.onToken(rawData)
      break
    case 'citations': {
      try {
        const citations = JSON.parse(rawData) as CitationItem[]
        handlers.onCitations(citations)
      } catch {
        handlers.onCitations([])
      }
      break
    }
    case 'error': {
      try {
        const parsed = JSON.parse(rawData) as { message?: string }
        handlers.onError(parsed.message ?? '流式问答服务内部错误')
      } catch {
        handlers.onError(rawData)
      }
      break
    }
    default:
      break
  }
}
