import http from './http'
import type { ApiResponse } from './http'
import type { SystemRole } from './auth'

// ─────────────────────────────────────────────
// 类型定义
// ─────────────────────────────────────────────

/** 用户状态枚举：ACTIVE = 正常，DISABLED = 已禁用 */
export type UserStatus = 'ACTIVE' | 'DISABLED'

/**
 * 管理员用户列表项
 * 对应后端 AdminUserItemResponse
 */
export interface AdminUserItem {
  /** 用户 ID */
  userId: number
  /** 用户编码（唯一标识） */
  userCode: string
  /** 用户名（登录用） */
  username: string
  /** 注册邮箱 */
  email: string
  /** 显示名称 */
  displayName: string
  /** 系统角色 */
  systemRole: SystemRole
  /** 账号状态 */
  status: UserStatus
  /** 是否强制要求修改密码 */
  mustChangePassword: boolean
  /** 最后登录时间（ISO 8601 字符串），从未登录则为 null */
  lastLoginAt: string | null
}

// ─────────────────────────────────────────────
// API 函数
// ─────────────────────────────────────────────

/**
 * 获取全部用户列表（仅系统管理员可调用）
 *
 * GET /api/admin/users
 *
 * @returns 用户列表
 */
export async function fetchAdminUsers(): Promise<AdminUserItem[]> {
  const { data } = await http.get<ApiResponse<AdminUserItem[]>>('/admin/users')
  return unwrapApiResponse(data, '加载用户列表失败')
}

/**
 * 根据用户 ID 获取单个用户详情（仅系统管理员可调用）
 *
 * GET /api/admin/users/{userId}
 *
 * @param userId 目标用户 ID
 * @returns 用户详情
 */
export async function fetchAdminUserDetail(userId: number): Promise<AdminUserItem> {
  const { data } = await http.get<ApiResponse<AdminUserItem>>(`/admin/users/${userId}`)
  return unwrapApiResponse(data, '加载用户详情失败')
}

/**
 * 修改用户状态（启用/禁用），仅系统管理员可调用
 *
 * PATCH /api/admin/users/{userId}/status
 *
 * @param userId 目标用户 ID
 * @param status 新状态（'ACTIVE' | 'DISABLED'）
 */
export async function updateAdminUserStatus(userId: number, status: UserStatus): Promise<void> {
  const { data } = await http.patch<ApiResponse<null>>(`/admin/users/${userId}/status`, { status })
  unwrapApiResponse(data, '更新用户状态失败')
}

// ─────────────────────────────────────────────
// 工具函数
// ─────────────────────────────────────────────

/**
 * 解包 ApiResponse，若 success 为 false 则抛出错误
 */
function unwrapApiResponse<T>(payload: ApiResponse<T>, fallbackMessage: string): T {
  if (!payload.success) {
    throw new Error(payload.message ?? fallbackMessage)
  }
  return payload.data
}
