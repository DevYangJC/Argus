import http, { type ApiResponse } from './http'
import type { AxiosProgressEvent } from 'axios'

// ─────────────────────────────────────────────
// 类型定义
// ─────────────────────────────────────────────

/**
 * 文档与群组关系枚举
 * - OWNER：当前用户是该文档所属群组的管理员
 * - MEMBER：当前用户是该文档所属群组的普通成员
 */
export type DocumentGroupRelation = 'OWNER' | 'MEMBER'

/**
 * 文档列表查询参数
 * 对应后端 DocumentQuery，所有字段均为可选
 */
export interface DocumentListQuery {
  /** 按群组 ID 过滤 */
  groupId?: number
  /** 按当前用户与群组的关系过滤（OWNER / MEMBER） */
  groupRelation?: DocumentGroupRelation
  /** 按文件名模糊过滤 */
  fileName?: string
  /** 按上传者用户 ID 过滤 */
  uploaderUserId?: number
  /** 按文档状态过滤（如 PROCESSING / READY / FAILED） */
  status?: string
  /** 上传时间范围起点（ISO 8601 格式） */
  uploadedFrom?: string
  /** 上传时间范围终点（ISO 8601 格式） */
  uploadedTo?: string
}

/**
 * 文档列表项
 * 对应后端 DocumentListItemVO
 */
export interface DocumentItem {
  /** 文档 ID */
  documentId: number
  /** 所属群组 ID */
  groupId: number
  /** 文件名（含扩展名） */
  fileName: string
  /** 文件扩展名（如 .pdf），可能为 null */
  fileExt: string | null
  /** MIME 类型（如 application/pdf），可能为 null */
  contentType: string | null
  /** 文件大小（字节） */
  fileSize: number
  /** 文档处理状态：PROCESSING / READY / FAILED */
  status: string
  /** 处理失败时的错误原因，未失败则为 null */
  failureReason: string | null
  /** 上传时间（ISO 8601 字符串） */
  uploadedAt: string
  /** 上传者用户 ID，可能为 null */
  uploaderUserId: number | null
  /** 上传者显示名称，可能为 null */
  uploaderDisplayName: string | null
  /** 上传者用户编码，可能为 null */
  uploaderUserCode: string | null
  /** 文档解析后的完整文本内容，可能为 null */
  previewText: string | null
}

/**
 * 文档预览信息
 * 对应后端 DocumentPreviewVO
 */
export interface DocumentPreview {
  /** 文档 ID */
  documentId: number
  /** 所属群组 ID */
  groupId: number
  /** 文件名 */
  fileName: string
  /** 文档解析后的完整文本内容 */
  previewText: string
  /** 文档状态，可能为 null */
  status: string | null
}

/**
 * 直接上传（小文件）的请求参数，仅前端内部使用
 */
interface UploadDocumentPayload {
  /** 目标群组 ID（需为当前用户管理的群组） */
  groupId: number
  /** 要上传的文件对象 */
  file: File
  /** 上传进度回调：loadedBytes 已上传字节数，totalBytes 总字节数（可能未知） */
  onProgress?: (loadedBytes: number, totalBytes?: number) => void
}

/**
 * 初始化分片上传请求参数
 * 对应后端 UploadInitRequest
 */
export interface InitDocumentUploadPayload {
  /** 目标群组 ID */
  groupId: number
  /** 文件名（含扩展名） */
  fileName: string
  /** 文件总大小（字节） */
  fileSize: number
  /** MIME 类型 */
  contentType: string
  /** 文件整体的 SHA-256 或 MD5 哈希值（用于秒传判断） */
  fileHash: string
  /** 每个分片的大小（字节） */
  chunkSize: number
  /** 分片总数 */
  chunkCount: number
}

/**
 * 上传单个分片的请求参数
 * 对应后端 UploadChunkRequest
 */
export interface UploadChunkPayload {
  /** 分片上传会话 ID（由 initDocumentUpload 返回） */
  uploadId: string
  /** 分片序号（从 0 开始） */
  chunkIndex: number
  /** 该分片的哈希值（用于完整性校验） */
  chunkHash: string
  /** 分片二进制数据 */
  chunk: Blob
}

/**
 * 初始化分片上传的响应结果
 * 对应后端 UploadInitResponse
 */
export interface UploadInitResult {
  /**
   * 是否秒传成功（文件哈希命中，直接复用已有文件）。
   * 为 true 时 documentId 有效，其余字段均为 null，无需后续分片操作。
   */
  instantUpload: boolean
  /** 秒传成功时返回已完成的文档 ID，否则为 null */
  documentId: number | null
  /** 上传会话 ID，后续上传分片和完成操作时使用 */
  uploadId: string | null
  /** 已上传的分片序号列表（断点续传时使用） */
  uploadedChunks: number[]
  /** 每个分片的大小（字节），null 表示尚未确定 */
  chunkSize: number | null
  /** 分片总数，null 表示尚未确定 */
  chunkCount: number | null
}

/**
 * 分片上传状态
 * 对应后端 UploadStatusResponse
 */
export interface UploadStatusResult {
  /** 上传会话状态：UPLOADING / COMPLETED / EXPIRED 等 */
  status: string
  /** 已上传的分片序号列表 */
  uploadedChunks: number[]
  /** 已上传分片数量 */
  uploadedChunkCount: number
  /** 分片总数，null 表示未知 */
  chunkCount: number | null
}

// 内部类型：后端可能以多种格式返回文档列表
type DocumentListPayload =
  | DocumentItem[]
  | ApiResponse<DocumentItem[] | { items?: unknown[]; list?: unknown[]; records?: unknown[] }>

// 内部类型：后端可能以多种格式返回文档预览
type DocumentPreviewPayload = DocumentPreview | ApiResponse<DocumentPreview | Record<string, unknown>>

// ─────────────────────────────────────────────
// API 函数
// ─────────────────────────────────────────────

/**
 * 查询文档列表
 *
 * GET /api/documents
 *
 * 支持按群组、上传者、时间范围、文件名、状态等条件过滤。
 * 仅返回当前用户有权查看的文档。
 *
 * @param query 查询条件（均为可选）
 * @returns 文档列表
 */
export async function fetchDocuments(query: DocumentListQuery = {}): Promise<DocumentItem[]> {
  const { data } = await http.get<DocumentListPayload>('/documents', {
    params: buildDocumentListParams(query),
  })

  return normalizeDocumentListPayload(data)
}

/**
 * 预览文档完整内容
 *
 * GET /api/documents/{documentId}/preview?groupId={groupId}
 *
 * 返回处于 READY 状态的文档的完整解析文本，支持前端全文渲染。
 * 需要当前用户是该群组的成员。
 *
 * @param documentId 文档 ID
 * @param groupId 文档所属群组 ID
 * @returns 文档预览信息（含完整文本内容）
 */
export async function fetchDocumentPreview(
  documentId: number,
  groupId: number,
): Promise<DocumentPreview> {
  const { data } = await http.get<DocumentPreviewPayload>(`/documents/${documentId}/preview`, {
    params: { groupId },
  })

  return normalizeDocumentPreviewPayload(data, documentId, groupId)
}

/**
 * 直接上传文档（适用于小文件，最大 10MB）
 *
 * POST /api/documents/upload（multipart/form-data）
 *
 * 支持 txt / md / pdf / docx 格式。需要当前用户是目标群组的管理员。
 * 上传成功后文档以 PROCESSING 状态开始异步 ETL 处理。
 *
 * @param payload 上传参数（groupId + file + 可选进度回调）
 * @returns 新创建的文档 ID
 */
export async function uploadDocument(payload: UploadDocumentPayload): Promise<number> {
  const formData = new FormData()
  formData.append('groupId', String(payload.groupId))
  formData.append('file', payload.file)

  const { data } = await http.post<ApiResponse<number>>('/documents/upload', formData, {
    onUploadProgress: (event: AxiosProgressEvent) => {
      payload.onProgress?.(event.loaded, event.total)
    },
  })

  if (!data.success || typeof data.data !== 'number') {
    throw new Error(data.message ?? '上传文件失败')
  }

  return data.data
}

/**
 * 初始化分片上传会话
 *
 * POST /api/documents/upload/init（application/json）
 *
 * 根据文件哈希判断是否已有相同文件（秒传）；
 * 否则创建新的上传会话，返回 uploadId 供后续分片上传使用。
 * 需要当前用户是目标群组的管理员。
 *
 * @param payload 初始化参数（文件元信息 + 分片参数）
 * @returns 初始化结果（含秒传标志、uploadId 和断点续传信息）
 */
export async function initDocumentUpload(
  payload: InitDocumentUploadPayload,
): Promise<UploadInitResult> {
  const { data } = await http.post<ApiResponse<UploadInitResult>>('/documents/upload/init', payload)

  if (!data.success || !data.data) {
    throw new Error(data.message ?? '初始化上传失败')
  }

  return normalizeUploadInitResult(data.data)
}

/**
 * 上传单个分片
 *
 * POST /api/documents/upload/chunks（multipart/form-data）
 *
 * 每次请求上传一个分片，后端校验序号和大小后存至对象存储。
 * 返回当前会话的最新状态（可用于显示进度）。
 *
 * @param payload 分片上传参数（uploadId + chunkIndex + chunkHash + chunk）
 * @param onProgress 字节级进度回调（loadedBytes）
 * @returns 当前上传状态
 */
export async function uploadDocumentChunk(
  payload: UploadChunkPayload,
  onProgress?: (loadedBytes: number) => void,
): Promise<UploadStatusResult> {
  const formData = new FormData()
  formData.append('uploadId', payload.uploadId)
  formData.append('chunkIndex', String(payload.chunkIndex))
  formData.append('chunkHash', payload.chunkHash)
  formData.append('chunk', payload.chunk)

  const { data } = await http.post<ApiResponse<UploadStatusResult>>('/documents/upload/chunks', formData, {
    onUploadProgress: (event: AxiosProgressEvent) => {
      onProgress?.(event.loaded)
    },
  })

  if (!data.success || !data.data) {
    throw new Error(data.message ?? '上传分片失败')
  }

  return normalizeUploadStatusResult(data.data)
}

/**
 * 查询分片上传会话当前状态
 *
 * GET /api/documents/upload/{uploadId}
 *
 * 返回已上传分片列表和进度信息，支持断点续传场景。
 *
 * @param uploadId 上传会话 ID
 * @returns 当前上传状态
 */
export async function fetchUploadStatus(uploadId: string): Promise<UploadStatusResult> {
  const { data } = await http.get<ApiResponse<UploadStatusResult>>(`/documents/upload/${uploadId}`)

  if (!data.success || !data.data) {
    throw new Error(data.message ?? '获取上传状态失败')
  }

  return normalizeUploadStatusResult(data.data)
}

/**
 * 完成分片上传，合并所有分片为最终文档
 *
 * POST /api/documents/upload/{uploadId}/complete
 *
 * 后端校验所有分片已就位后，合并分片并创建文档记录，触发异步 ETL 处理。
 *
 * @param uploadId 上传会话 ID
 * @returns 新创建的文档 ID
 */
export async function completeDocumentUpload(uploadId: string): Promise<number> {
  const { data } = await http.post<ApiResponse<number>>(`/documents/upload/${uploadId}/complete`)

  if (!data.success || typeof data.data !== 'number') {
    throw new Error(data.message ?? '完成上传失败')
  }

  return data.data
}

/**
 * 删除文档（软删除）
 *
 * DELETE /api/documents/{documentId}?groupId={groupId}
 *
 * 将文档标记为已删除，同时清理向量数据和 ES 索引。
 * 需要当前用户是目标群组的管理员。
 *
 * @param documentId 文档 ID
 * @param groupId 文档所属群组 ID
 */
export async function deleteDocument(documentId: number, groupId: number): Promise<void> {
  const { data } = await http.delete<ApiResponse<null>>(`/documents/${documentId}`, {
    params: { groupId },
  })

  if (!data.success) {
    throw new Error(data.message ?? '删除文档失败')
  }
}

/**
 * 重新处理失败的文档
 *
 * POST /api/documents/{documentId}/retry-ingestion?groupId={groupId}
 *
 * 将状态为 FAILED 的文档重置为 PROCESSING 并重新触发异步 ETL 事件。
 * 需要当前用户是目标群组的管理员。
 *
 * @param documentId 文档 ID
 * @param groupId 文档所属群组 ID
 */
export async function retryDocumentIngestion(documentId: number, groupId: number): Promise<void> {
  const { data } = await http.post<ApiResponse<null>>(`/documents/${documentId}/retry-ingestion`, null, {
    params: { groupId },
  })

  if (!data.success) {
    throw new Error(data.message ?? '重新处理文档失败')
  }
}

/**
 * 下载文档原始文件
 *
 * GET /api/documents/{documentId}/download?groupId={groupId}
 *
 * 后端返回 Content-Disposition: attachment 响应，触发浏览器下载。
 * 需要当前用户是目标群组的成员。
 *
 * 注意：此接口返回 Blob 而非 JSON，不经过 ApiResponse 包装。
 * 前端需自行处理文件名（可从 Content-Disposition 响应头中提取）。
 *
 * @param documentId 文档 ID
 * @param groupId 文档所属群组 ID
 * @returns 文件 Blob 数据
 */
export async function downloadDocument(documentId: number, groupId: number): Promise<Blob> {
  const response = await http.get(`/documents/${documentId}/download`, {
    params: { groupId },
    responseType: 'blob',
  })
  return response.data as Blob
}

// ─────────────────────────────────────────────
// 内部工具函数
// ─────────────────────────────────────────────

/** 将查询条件构建为 URL 参数对象（过滤 null/undefined/空字符串） */
function buildDocumentListParams(query: DocumentListQuery) {
  const params: Record<string, number | string> = {}

  assignQueryParam(params, 'groupId', query.groupId)
  assignQueryParam(params, 'groupRelation', query.groupRelation)
  assignQueryParam(params, 'fileName', query.fileName)
  assignQueryParam(params, 'uploaderUserId', query.uploaderUserId)
  assignQueryParam(params, 'status', query.status)
  assignQueryParam(params, 'uploadedFrom', query.uploadedFrom)
  assignQueryParam(params, 'uploadedTo', query.uploadedTo)

  return params
}

/** 仅当值为有效数字或非空字符串时才添加到参数对象中 */
function assignQueryParam(
  params: Record<string, number | string>,
  key: string,
  value: number | string | null | undefined,
) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    params[key] = value
    return
  }

  if (typeof value === 'string' && value.trim().length > 0) {
    params[key] = value.trim()
  }
}

/** 兼容多种后端响应格式，统一转换为 DocumentItem 数组 */
function normalizeDocumentListPayload(payload: unknown): DocumentItem[] {
  const unwrapped = unwrapApiResponse(payload)
  const rawItems = resolveListItems(unwrapped)

  return rawItems.map((item) => normalizeDocumentItem(item)).filter(Boolean) as DocumentItem[]
}

/** 兼容多种后端响应格式，统一转换为 DocumentPreview */
function normalizeDocumentPreviewPayload(
  payload: unknown,
  fallbackDocumentId: number,
  fallbackGroupId: number,
): DocumentPreview {
  const unwrapped = unwrapApiResponse(payload)
  const source = isRecord(unwrapped) ? unwrapped : {}
  const previewText = truncatePreviewText(
    readString(source.previewText) ?? readString(source.text) ?? '',
  )

  return {
    documentId: readNumber(source.documentId) ?? fallbackDocumentId,
    groupId: readNumber(source.groupId) ?? fallbackGroupId,
    fileName: readString(source.fileName) ?? `文档 #${fallbackDocumentId}`,
    previewText,
    status: readString(source.status),
  }
}

/** 归一化分片上传初始化结果 */
function normalizeUploadInitResult(payload: unknown): UploadInitResult {
  const source = isRecord(payload) ? payload : {}
  return {
    instantUpload: Boolean(source.instantUpload),
    documentId: readNumber(source.documentId),
    uploadId: readString(source.uploadId),
    uploadedChunks: readNumberArray(source.uploadedChunks),
    chunkSize: readNumber(source.chunkSize),
    chunkCount: readNumber(source.chunkCount),
  }
}

/** 归一化分片上传状态结果 */
function normalizeUploadStatusResult(payload: unknown): UploadStatusResult {
  const source = isRecord(payload) ? payload : {}
  return {
    status: readString(source.status) ?? 'UNKNOWN',
    uploadedChunks: readNumberArray(source.uploadedChunks),
    uploadedChunkCount: readNumber(source.uploadedChunkCount) ?? 0,
    chunkCount: readNumber(source.chunkCount),
  }
}

/** 尝试从 ApiResponse 包裹中解包 data，如果不是 ApiResponse 则原样返回 */
function unwrapApiResponse(payload: unknown) {
  if (isRecord(payload) && 'success' in payload && 'data' in payload) {
    return payload.data
  }

  return payload
}

/** 从多种格式中提取列表数组（直接数组 / items / list / records 字段） */
function resolveListItems(payload: unknown): Record<string, unknown>[] {
  if (Array.isArray(payload)) {
    return payload.filter(isRecord)
  }

  if (!isRecord(payload)) {
    return []
  }

  for (const key of ['items', 'list', 'records']) {
    const value = payload[key]
    if (Array.isArray(value)) {
      return value.filter(isRecord)
    }
  }

  return []
}

/** 将原始对象映射为 DocumentItem，关键字段缺失时返回 null */
function normalizeDocumentItem(source: Record<string, unknown> | null): DocumentItem | null {
  if (source === null) {
    return null
  }

  const documentId = readNumber(source.documentId)

  if (documentId === null) {
    return null
  }

  return {
    documentId,
    groupId: readNumber(source.groupId) ?? 0,
    fileName: readString(source.fileName) ?? `文档 #${documentId}`,
    fileExt: readString(source.fileExt),
    contentType: readString(source.contentType),
    fileSize: readNumber(source.fileSize) ?? 0,
    status: readString(source.status) ?? 'UNKNOWN',
    failureReason: readString(source.failureReason),
    uploadedAt: readString(source.uploadedAt) ?? '',
    uploaderUserId: readNumber(source.uploaderUserId) ?? readNumber(source.uploaderId),
    uploaderDisplayName:
      readString(source.uploaderDisplayName) ?? readString(source.uploaderName),
    uploaderUserCode: readString(source.uploaderUserCode) ?? readString(source.uploaderCode),
    previewText: readString(source.previewText) ?? readString(source.preview),
  }
}

/** 类型守卫：是否为非 null 的普通对象 */
function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

/** 安全读取数字（支持字符串转换），无效时返回 null */
function readNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }

  if (typeof value === 'string' && value.trim().length > 0) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }

  return null
}

/** 安全读取字符串，非字符串类型返回 null */
function readString(value: unknown): string | null {
  return typeof value === 'string' ? value : null
}

/** 安全读取数字数组，过滤无效元素 */
function readNumberArray(value: unknown): number[] {
  if (!Array.isArray(value)) {
    return []
  }
  return value
    .map((item) => readNumber(item))
    .filter((item): item is number => typeof item === 'number')
}

/** 清理预览文本首尾空白 */
function truncatePreviewText(value: string) {
  return value.trim()
}
