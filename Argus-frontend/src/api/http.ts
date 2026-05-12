import axios from 'axios'

// ─────────────────────────────────────────────
// 通用响应结构
// ─────────────────────────────────────────────

/**
 * 统一 API 响应包装结构
 * 对应后端 ApiResponse<T>
 *
 * 大多数接口都会使用此结构包裹返回值，少数接口（如 qa/ask、助手会话列表等）
 * 直接返回数据对象，详见各 API 函数注释。
 */
export interface ApiResponse<T> {
  /** 是否成功：true 表示业务逻辑正常，false 表示业务异常 */
  success: boolean
  /** 响应数据，success 为 false 时可能为 null */
  data: T
  /** 响应消息，成功时通常为 null，失败时包含错误描述 */
  message: string | null
}

/** 错误响应体内部结构（用于 Axios 拦截器提取错误信息） */
interface ApiErrorPayload {
  success?: boolean
  message?: string | null
}

// ─────────────────────────────────────────────
// Axios 实例
// ─────────────────────────────────────────────

/**
 * 全局 HTTP 客户端实例（基于 Axios）
 *
 * - baseURL：优先读取环境变量 VITE_API_BASE_URL，默认为 /api
 * - timeout：15 秒
 * - withCredentials：true（支持跨域携带 Cookie，用于 Refresh Token 自动传递）
 *
 * 使用时直接从此模块导入 http：
 * ```ts
 * import http from './http'
 * const { data } = await http.get('/some/path')
 * ```
 */
const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 15000,
  withCredentials: true,
})

// ─────────────────────────────────────────────
// 工具函数
// ─────────────────────────────────────────────

/**
 * 从 Axios 错误或 Error 对象中提取可读的错误消息
 *
 * 优先级：
 * 1. 后端响应体中的 message 字段
 * 2. Axios 内部错误消息（如 Network Error / timeout）
 * 3. 传入的 error 对象的 message 属性
 * 4. fallbackMessage 兜底文本
 *
 * 用法示例：
 * ```ts
 * try {
 *   await someApiCall()
 * } catch (error) {
 *   toast.error(extractApiError(error, '操作失败'))
 * }
 * ```
 *
 * @param error 捕获到的错误对象（类型未知）
 * @param fallbackMessage 无法提取到消息时的兜底文本，默认为"请求失败"
 * @returns 用户可读的错误消息字符串
 */
export function extractApiError(error: unknown, fallbackMessage = '请求失败'): string {
  if (axios.isAxiosError<ApiErrorPayload>(error)) {
    const responseMessage = error.response?.data?.message

    if (typeof responseMessage === 'string' && responseMessage.trim().length > 0) {
      return responseMessage
    }

    if (typeof error.message === 'string' && error.message.trim().length > 0) {
      return error.message
    }
  }

  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message
  }

  return fallbackMessage
}

export default http
