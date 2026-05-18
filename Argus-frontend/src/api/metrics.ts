import http from './http'
import type { ApiResponse } from './http'

// ─────────────────────────────────────────────
// 类型定义
// ─────────────────────────────────────────────

/** 时间段枚举 */
export type Period = 'TODAY' | 'LAST_7_DAYS' | 'LAST_14_DAYS' | 'LAST_30_DAYS'

/** 仪表盘概览 */
export interface MetricsOverview {
  todayRequests: number
  todayTokens: number
  todayCost: number
  todaySuccessRate: number
  dailyTrend: Array<{
    date: string
    requests: number
    tokens: number
    cost: number
  }>
}

/** 使用统计 */
export interface UsageStats {
  totalPromptTokens: number
  totalCompletionTokens: number
  totalTokens: number
  totalCost: number
  totalRequests: number
  successRequests: number
  failedRequests: number
  successRate: number
  avgLatencyMs: number
  avgRpm: number
  avgTpm: number
}

/** 使用排行项 */
export interface UsageRankItem {
  id: number
  name: string
  totalRequests: number
  totalTokens: number
  totalCost: number
}

/** 趋势数据项 */
export interface TrendItem {
  date: string
  requests: number
  tokens: number
  cost: number
}

// ─────────────────────────────────────────────
// API 函数
// ─────────────────────────────────────────────

/**
 * 获取仪表盘概览（今日指标 + 30天趋势）
 *
 * GET /api/admin/metrics/overview
 */
export async function fetchMetricsOverview(): Promise<MetricsOverview> {
  const { data } = await http.get<ApiResponse<MetricsOverview>>('/admin/metrics/overview')
  return unwrapApiResponse(data, '加载概览数据失败')
}

/**
 * 获取平台整体统计
 *
 * GET /api/admin/metrics/platform?period=
 */
export async function fetchPlatformStats(period: Period): Promise<UsageStats> {
  const { data } = await http.get<ApiResponse<UsageStats>>('/admin/metrics/platform', {
    params: { period },
  })
  return unwrapApiResponse(data, '加载平台统计失败')
}

/**
 * 获取用户级别统计
 *
 * GET /api/admin/metrics/user/{userId}?period=
 */
export async function fetchUserStats(userId: number, period: Period): Promise<UsageStats> {
  const { data } = await http.get<ApiResponse<UsageStats>>(`/admin/metrics/user/${userId}`, {
    params: { period },
  })
  return unwrapApiResponse(data, '加载用户统计失败')
}

/**
 * 获取群组级别统计
 *
 * GET /api/admin/metrics/group/{groupId}?period=
 */
export async function fetchGroupStats(groupId: number, period: Period): Promise<UsageStats> {
  const { data } = await http.get<ApiResponse<UsageStats>>(`/admin/metrics/group/${groupId}`, {
    params: { period },
  })
  return unwrapApiResponse(data, '加载群组统计失败')
}

/**
 * 获取趋势数据
 *
 * GET /api/admin/metrics/trend?period=&module=
 */
export async function fetchTrend(period: Period, module?: string): Promise<TrendItem[]> {
  const { data } = await http.get<ApiResponse<TrendItem[]>>('/admin/metrics/trend', {
    params: { period, module },
  })
  return unwrapApiResponse(data, '加载趋势数据失败')
}

/**
 * 获取用户排行
 *
 * GET /api/admin/metrics/rank/users?period=&limit=
 */
export async function fetchUserRank(period: Period, limit = 10): Promise<UsageRankItem[]> {
  const { data } = await http.get<ApiResponse<UsageRankItem[]>>('/admin/metrics/rank/users', {
    params: { period, limit },
  })
  return unwrapApiResponse(data, '加载用户排行失败')
}

/**
 * 获取群组排行
 *
 * GET /api/admin/metrics/rank/groups?period=&limit=
 */
export async function fetchGroupRank(period: Period, limit = 10): Promise<UsageRankItem[]> {
  const { data } = await http.get<ApiResponse<UsageRankItem[]>>('/admin/metrics/rank/groups', {
    params: { period, limit },
  })
  return unwrapApiResponse(data, '加载群组排行失败')
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
