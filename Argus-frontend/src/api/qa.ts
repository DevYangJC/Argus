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
