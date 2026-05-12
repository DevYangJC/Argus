import http, { type ApiResponse } from './http'

// ─────────────────────────────────────────────
// 类型定义
// ─────────────────────────────────────────────

/** 系统角色枚举：ADMIN = 系统管理员，USER = 普通用户 */
export type SystemRole = 'ADMIN' | 'USER'

/**
 * 当前登录用户的基本信息
 * 对应后端 CurrentUserProfileResponse
 */
export interface CurrentUserProfile {
  /** 用户 ID */
  userId: number
  /** 用户编码（唯一标识，同注册时的 username） */
  userCode: string
  /** 显示名称 */
  displayName: string
  /** 系统角色 */
  systemRole: SystemRole
  /** 是否强制要求修改密码（首次登录或管理员重置后为 true） */
  mustChangePassword: boolean
}

/**
 * 登录请求参数
 * 对应后端 LoginRequest
 */
export interface LoginPayload {
  /** 登录标识（用户名或邮箱，不区分大小写） */
  loginId: string
  /** 用户密码 */
  password: string
}

/**
 * 注册请求参数
 * 对应后端 RegisterRequest
 */
export interface RegisterPayload {
  /** 用户名（唯一，仅允许字母、数字、下划线、短横线） */
  username: string
  /** 邮箱（唯一） */
  email: string
  /** 显示名称 */
  displayName: string
  /** 密码 */
  password: string
}

/**
 * 登录/刷新 Token 的响应体
 * 对应后端 AuthTokensResponse
 */
export interface AuthSessionResponse {
  /** JWT Access Token，前端需在后续请求头中携带 */
  accessToken: string
  /** 当前用户信息（避免登录后再次请求 /auth/me） */
  currentUser: CurrentUserProfile
}

/**
 * 修改密码请求参数
 * 对应后端 ChangePasswordRequest
 */
export interface ChangePasswordPayload {
  /** 当前密码 */
  currentPassword: string
  /** 新密码 */
  newPassword: string
}

/**
 * 通过身份信息重置密码的请求参数
 * ⚠️ 注意：当前后端 AuthController 暂未提供 /auth/reset-password 接口，
 * 此类型保留以备后续扩展使用。
 */
export interface ResetPasswordByIdentityPayload {
  /** 用户名 */
  username: string
  /** 注册邮箱 */
  email: string
  /** 新密码 */
  newPassword: string
}

// ─────────────────────────────────────────────
// API 函数
// ─────────────────────────────────────────────

/**
 * 用户登录
 *
 * POST /api/auth/login
 *
 * 成功后后端会通过 httpOnly Cookie 下发 Refresh Token，
 * 前端无需手动处理 Cookie，保持 withCredentials: true 即可。
 *
 * @param payload 登录参数（loginId + password）
 * @returns 包含 accessToken 和当前用户信息的响应
 */
export async function login(payload: LoginPayload): Promise<AuthSessionResponse> {
  const { data } = await http.post<ApiResponse<AuthSessionResponse>>('/auth/login', payload, {
    withCredentials: true,
  })

  return unwrapApiResponse(data, '登录失败')
}

/**
 * 用户注册
 *
 * POST /api/auth/register
 *
 * @param payload 注册参数（username / email / displayName / password）
 */
export async function register(payload: RegisterPayload): Promise<void> {
  const { data } = await http.post<ApiResponse<null>>('/auth/register', payload)
  unwrapApiResponse(data, '注册失败')
}

/**
 * 刷新 Access Token
 *
 * POST /api/auth/refresh
 *
 * 后端从 httpOnly Cookie 中读取 Refresh Token，验证后签发新的 Access Token。
 * 调用此接口需要携带 Cookie（withCredentials: true）。
 *
 * @returns 新的 accessToken 和当前用户信息
 */
export async function refreshSession(): Promise<AuthSessionResponse> {
  const { data } = await http.post<ApiResponse<AuthSessionResponse>>('/auth/refresh', null, {
    withCredentials: true,
  })

  return unwrapApiResponse(data, '登录状态已过期')
}

/**
 * 用户登出
 *
 * POST /api/auth/logout
 *
 * 后端会吊销当前 Refresh Token 并清除 Cookie。
 */
export async function logout(): Promise<void> {
  const { data } = await http.post<ApiResponse<null>>('/auth/logout', null, {
    withCredentials: true,
  })
  unwrapApiResponse(data, '退出登录失败')
}

/**
 * 获取当前登录用户信息
 *
 * GET /api/auth/me
 *
 * 需要携带有效的 Authorization: Bearer <accessToken> 请求头。
 *
 * @returns 当前用户的基本信息
 */
export async function fetchCurrentUser(): Promise<CurrentUserProfile> {
  const { data } = await http.get<ApiResponse<CurrentUserProfile>>('/auth/me')

  return unwrapApiResponse(data, '获取当前用户失败')
}

/**
 * 修改当前用户密码
 *
 * POST /api/account/change-password
 *
 * 需要提供旧密码进行验证，验证通过后设置新密码。
 *
 * @param payload 修改密码参数（currentPassword + newPassword）
 */
export async function changePassword(payload: ChangePasswordPayload): Promise<void> {
  const { data } = await http.post<ApiResponse<null>>('/account/change-password', payload)
  unwrapApiResponse(data, '修改密码失败')
}

/**
 * 通过用户名和邮箱重置密码（忘记密码场景）
 *
 * POST /api/auth/reset-password
 *
 * ⚠️ 警告：该接口在当前后端版本中尚未实现，调用将返回 404。
 * 保留此函数以备后续后端扩展使用。
 *
 * @param payload 重置参数（username + email + newPassword）
 */
export async function resetPasswordByIdentity(payload: ResetPasswordByIdentityPayload): Promise<void> {
  const { data } = await http.post<ApiResponse<null>>('/auth/reset-password', payload)
  unwrapApiResponse(data, '修改密码失败')
}

// ─────────────────────────────────────────────
// 工具函数
// ─────────────────────────────────────────────

/**
 * 解包 ApiResponse，若 success 为 false 则抛出错误
 * @param payload API 响应体
 * @param fallbackMessage 响应消息为空时的兜底错误信息
 */
function unwrapApiResponse<T>(payload: ApiResponse<T>, fallbackMessage: string): T {
  if (!payload.success) {
    throw new Error(payload.message ?? fallbackMessage)
  }

  return payload.data
}
