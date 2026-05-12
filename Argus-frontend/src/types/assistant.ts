// ─────────────────────────────────────────────
// 助手模块 — TypeScript 类型定义
// ─────────────────────────────────────────────
//
// 领域概述
// ─────────
// "助手"（Assistant）是一个多轮对话 AI Agent，支持两种工具模式：
//   1. CHAT —— 自由对话，Agent 可自主调用工具完成复杂任务
//   2. KB_SEARCH —— 知识库检索，在指定群组知识库范围内搜索并生成带引用的回答
//
// 会话（Session）是对话的顶层容器，一个用户可拥有多个会话。
// 每个会话内包含多条消息（Message），消息角色分为 USER / ASSISTANT / TOOL。
// 每次 ASSISTANT 回复可能携带若干引用条目（Citation），指向知识库中的文档片段。
//
// 对话支持两种协议：
//   - 同步聊天（POST /api/assistant/chat）—— 一次请求返回完整回复
//   - 流式聊天（POST /api/assistant/chat/stream）—— SSE 逐字推送，实时渲染

// ─────────────────────────────────────────────
// 枚举 / 字面量联合类型
// ─────────────────────────────────────────────

/**
 * 助手工具模式
 *
 * Assistant Agent 每次对话需指定一种工具模式，决定其可调用的工具集：
 * - `CHAT`：自由对话模式，Agent 可以搜索知识库、计算、调用外部 API
 * - `KB_SEARCH`：知识库检索模式，限定在指定群组知识库中搜索并生成带引用回答
 */
export type AssistantToolMode = 'CHAT' | 'KB_SEARCH'

/**
 * 消息角色
 *
 * 每条消息的发送者身份：
 * - `USER`：用户发送的消息
 * - `ASSISTANT`：AI 助手生成的回复
 * - `TOOL`：Agent 执行工具调用后产生的中间结果（如搜索到的文档片段）
 */
export type AssistantMessageRole = 'USER' | 'ASSISTANT' | 'TOOL'

// ─────────────────────────────────────────────
// 引用 / 溯源
// ─────────────────────────────────────────────

/**
 * 知识库引用条目
 *
 * AI 回答中引用的文档片段信息，用于回答溯源。
 * 每条引用指向知识库中的一个文档切片（Chunk），
 * 携带文件名、相关度评分和片段摘要，前端可据此渲染引用卡片。
 */
export interface AssistantCitationItem {
  /** 所属文档 ID，可能为空（后端未填充时） */
  documentId: number | null
  /** 切片 ID，可能为空 */
  chunkId: number | null
  /** 切片在文档中的序号，可能为空 */
  chunkIndex: number | null
  /** 文档文件名，用于前端展示 */
  fileName: string
  /** 相关度评分（0~1），值越高表示切片与用户问题越匹配 */
  score: number
  /** 切片文本片段的前若干字符，用于引用预览 */
  snippet: string | null
}

// ─────────────────────────────────────────────
// 会话（Session）
// ─────────────────────────────────────────────

/**
 * 会话列表项（摘要视图）
 *
 * 用于会话侧边栏列表，仅包含最少字段以支撑列表渲染。
 * 完整信息需通过 fetchAssistantSessionDetail 获取。
 */
export interface AssistantSessionListItem {
  /** 会话 ID */
  sessionId: number
  /** 会话标题（创建时默认为"新会话"，可后续重命名） */
  title: string
  /** 最后一条消息的时间（ISO 8601 字符串），可能为空（会话无消息时） */
  lastMessageAt: string | null
}

/**
 * 会话详情
 *
 * 包含会话的全部元数据，由创建、详情查询、重命名接口返回。
 */
export interface AssistantSessionDetail {
  /** 会话 ID */
  sessionId: number
  /** 会话标题 */
  title: string
  /**
   * 会话状态
   *
   * - `ACTIVE`：活跃中（默认状态）
   * - `ARCHIVED`：已归档，不再出现在默认列表中
   */
  status: string
  /** 最后消息时间（ISO 8601），会话无消息时为空 */
  lastMessageAt: string | null
  /** 会话创建时间（ISO 8601） */
  createdAt: string
}

// ─────────────────────────────────────────────
// 消息（Message）
// ─────────────────────────────────────────────

/**
 * 会话中的单条消息
 *
 * 每个会话由多条消息构成对话历史。
 * 消息角色决定其在 UI 中的展示位置和样式（用户靠右、AI 靠左、工具消息折叠）。
 * `structuredPayload` 字段承载 JSON 结构化的工具调用/返回数据，
 * 前端可根据 role 决定如何解析该字段。
 */
export interface AssistantMessageItem {
  /** 消息 ID（全局唯一） */
  messageId: number
  /** 所属会话 ID */
  sessionId: number
  /** 消息角色：USER / ASSISTANT / TOOL */
  role: AssistantMessageRole
  /** 当前消息使用的工具模式，非 TOOL 消息时与当时会话模式一致 */
  toolMode: AssistantToolMode | null
  /**
   * 关联的知识库群组 ID
   *
   * 仅在 KB_SEARCH 模式下有效，标识本次搜索限定的群组范围。
   * CHAT 模式下也允许传入 groupId，Agent 可据此搜索特定群组的知识库。
   */
  groupId: number | null
  /** 消息文本内容（Markdown 格式） */
  content: string
  /**
   * JSON 序列化的结构化负载
   *
   * TOOL 角色时为工具调用参数或返回结果，
   * ASSISTANT 角色时可能包含内部的工具编排记录。
   * 前端解析时应对 JSON 解析失败做好兜底。
   */
  structuredPayload: string | null
  /** 消息创建时间（ISO 8601） */
  createdAt: string
}

// ─────────────────────────────────────────────
// 会话上下文（摘要 + 最近消息）
// ─────────────────────────────────────────────

/**
 * 会话上下文
 *
 * 用于恢复会话状态或展示会话概览。
 * 包含 LLM 生成的对话摘要和最近若干条消息，前端可据此：
 * - 在列表页展示每条会话的摘要预览
 * - 进入会话时快速加载最近消息，其余消息按需分页
 */
export interface AssistantConversationContext {
  /** LLM 生成的对话摘要文本，无消息时为空 */
  summaryText: string | null
  /** 最近的消息列表（按时间升序，最多 recentLimit 条） */
  recentMessages: AssistantMessageItem[]
}

// ─────────────────────────────────────────────
// 聊天请求 / 响应
// ─────────────────────────────────────────────

/**
 * 聊天请求体（同步 & 流式共用）
 *
 * 发起一次对话请求时需要提供的参数。
 * toolMode 决定 Agent 的可用工具集，groupId 限定知识库搜索范围。
 */
export interface AssistantChatPayload {
  /** 目标会话 ID */
  sessionId: number
  /** 用户输入的消息文本 */
  message: string
  /** 本次对话使用的工具模式 */
  toolMode: AssistantToolMode
  /**
   * 限定搜索的知识库群组 ID
   *
   * KB_SEARCH 模式下必传，CHAT 模式下可选。
   * 传入后 Agent 在该群组的知识库范围内搜索文档。
   */
  groupId?: number | null
}

/**
 * 同步聊天响应
 *
 * 一次完整的非流式对话结果，包含 AI 生成的回复文本和引用列表。
 * 前端可直接渲染整个回答，无需处理增量流。
 */
export interface AssistantChatResult {
  /** 会话 ID */
  sessionId: number
  /** 本轮回合中 ASSISTANT 消息的 ID */
  messageId: number
  /** AI 生成的回复内容（Markdown 格式） */
  reply: string
  /** 本轮回合使用的工具模式 */
  toolMode: AssistantToolMode
  /** 关联的知识库群组 ID，未关联时为空 */
  groupId: number | null
  /** 回复中引用的文档片段列表（可能为空数组） */
  citations: AssistantCitationItem[]
}

// ─────────────────────────────────────────────
// SSE 流式事件
// ─────────────────────────────────────────────

/**
 * SSE 流式聊天事件
 *
 * 服务端通过 SSE（Server-Sent Events）逐段推送 AI 回复。
 * 每个事件为一个 JSON 对象，event 字段标识事件类型：
 *
 * - `start`：流开始，此时 reply / delta / messageId 均无值
 * - `delta`：文本增量片段，delta 字段包含刚生成的一段文本，前端逐字追加
 * - `done`：流结束，reply 字段包含完整回复文本，messageId 为最终消息 ID
 * - `error`：异常终止，error 字段包含错误描述
 *
 * 参考：POST /api/assistant/chat/stream
 */
export interface AssistantChatStreamEvent {
  /** 事件类型：start / delta / done / error */
  event: 'start' | 'delta' | 'done' | 'error'
  /** 会话 ID */
  sessionId: number
  /** 本轮回合使用的工具模式 */
  toolMode: AssistantToolMode
  /** 关联的知识库群组 ID */
  groupId: number | null
  /** 增量文本片段（仅 delta 事件有值） */
  delta: string | null
  /** 消息 ID（仅 done 事件有值） */
  messageId: number | null
  /** 完整回复文本（仅 done 事件有值） */
  reply: string | null
  /** 引用列表（done 事件时包含全部引用） */
  citations: AssistantCitationItem[]
  /** 错误信息（仅 error 事件有值） */
  error: string | null
}
