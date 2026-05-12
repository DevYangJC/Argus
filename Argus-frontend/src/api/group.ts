import http from './http'
import type { ApiResponse } from './http'

// ─────────────────────────────────────────────
// 类型定义
// ─────────────────────────────────────────────

/**
 * 群组简要信息
 * 对应后端 GroupItem / GroupMembershipService.GroupItem
 */
export interface GroupItem {
  /** 群组 ID */
  groupId: number
  /** 群组唯一编码（用于加入申请） */
  groupCode: string
  /** 群组名称 */
  groupName: string
  /** 群组描述 */
  description?: string
  /** 待处理申请数量 */
  pendingRequestCount?: number
  /** 创建时间 */
  createdAt?: string
}

/**
 * 待处理的邀请信息（被邀请者视角）
 * 对应后端 PendingInvitationItem
 */
export interface PendingInvitationItem {
  /** 邀请记录 ID */
  invitationId: number
  /** 邀请所属群组 ID */
  groupId: number
  /** 群组名称 */
  groupName: string
  /** 邀请人用户 ID */
  inviterUserId: number
  /** 邀请人显示名称 */
  inviterDisplayName: string
  /** 邀请状态（PENDING / ACCEPTED / REJECTED / CANCELLED） */
  status: string
}

/**
 * 当前用户可见的群组查询结果
 * 对应后端 GroupMembershipService.GroupQueryResult
 */
export interface GroupQueryResult {
  /** 当前用户作为管理员（OWNER）的群组列表 */
  ownedGroups: GroupItem[]
  /** 当前用户作为普通成员（MEMBER）的群组列表 */
  joinedGroups: GroupItem[]
  /** 发送给当前用户的待处理邀请列表 */
  pendingInvitations: PendingInvitationItem[]
}

/**
 * 创建群组请求参数
 * 对应后端 CreateGroupRequest
 */
export interface CreateGroupPayload {
  /** 群组名称 */
  name: string
  /** 群组描述（可选） */
  description?: string
}

/**
 * 群组成员信息
 * 对应后端 GroupMemberResponse
 */
export interface GroupMemberItem {
  /** 成员用户 ID */
  userId: number
  /** 成员用户编码 */
  userCode: string
  /** 成员显示名称 */
  displayName: string
  /** 成员在群组中的角色（OWNER / MEMBER） */
  role: string
}

/**
 * 申请者视角的加入申请记录
 * 对应后端 MyJoinRequestResponse
 */
export interface JoinRequestItem {
  /** 申请记录 ID */
  requestId: number
  /** 申请加入的群组 ID */
  groupId: number
  /** 申请加入的群组编码 */
  groupCode: string
  /** 申请加入的群组名称 */
  groupName: string
  /** 申请状态（PENDING / APPROVED / REJECTED） */
  status: string
  /** 申请创建时间（ISO 8601） */
  createdAt: string
  /** 审批决定时间（ISO 8601），未决定则为 null */
  decidedAt?: string | null
}

/**
 * 邀请方视角的发出邀请记录
 */
export interface MySentInvitationItem {
  /** 邀请记录 ID */
  invitationId: number
  /** 群组 ID */
  groupId: number
  /** 群组名称 */
  groupName: string
  /** 被邀请者用户 ID */
  inviteeUserId: number
  /** 被邀请者显示名称 */
  inviteeDisplayName: string
  /** 邀请状态（PENDING / ACCEPTED / REJECTED / CANCELED） */
  status: string
  /** 邀请创建时间 */
  createdAt: string
  /** 处理时间 */
  decidedAt?: string | null
}

/**
 * 管理员视角的加入申请记录
 * 对应后端 OwnerJoinRequestResponse
 */
export interface OwnerJoinRequestItem {
  /** 申请记录 ID */
  requestId: number
  /** 申请所属群组 ID */
  groupId: number
  /** 申请者用户 ID */
  applicantUserId: number
  /** 申请者用户编码 */
  applicantUserCode: string
  /** 申请者显示名称 */
  applicantDisplayName: string
  /** 申请状态 */
  status: string
  /** 申请创建时间（ISO 8601） */
  createdAt: string
}

// ─────────────────────────────────────────────
// API 函数 —— 群组查询
// ─────────────────────────────────────────────

/**
 * 获取当前用户可见的群组列表
 *
 * GET /api/groups/my
 *
 * 后端返回 ApiResponse 包裹的 GroupQueryResult 对象。
 * 包含当前用户拥有的群组、已加入的群组和待处理邀请。
 *
 * @returns 群组查询结果
 */
export async function fetchGroups(): Promise<GroupQueryResult> {
  const { data } = await http.get<ApiResponse<GroupQueryResult>>('/groups/my')
  if (!data.success || !data.data) {
    throw new Error(data.message ?? '获取群组列表失败')
  }
  return data.data
}

// ─────────────────────────────────────────────
// API 函数 —— 协作小组
// ─────────────────────────────────────────────

/**
 * 创建新群组
 *
 * POST /api/groups
 *
 * 创建者自动成为群组管理员（OWNER）。
 *
 * @param payload 创建参数（name + 可选 description）
 * @returns 新创建的群组 ID
 */
export async function createGroup(payload: CreateGroupPayload): Promise<number> {
  const { data } = await http.post<ApiResponse<number>>('/groups', payload)
  if (!data.success || typeof data.data !== 'number') {
    throw new Error(data.message ?? '创建组失败')
  }
  return data.data
}

/**
 * 邀请用户加入群组
 *
 * POST /api/groups/{groupId}/invitations
 *
 * 仅群组管理员可操作。被邀请者会在 /groups/my 的 pendingInvitations 中看到此邀请。
 *
 * @param groupId 目标群组 ID
 * @param inviteeUserId 被邀请的用户 ID
 * @returns 新创建的邀请记录 ID
 */
export async function createInvitation(groupId: number, inviteeUserId: number): Promise<number> {
  const { data } = await http.post<ApiResponse<number>>(`/groups/${groupId}/invitations`, {
    inviteeUserId,
  })
  if (!data.success || typeof data.data !== 'number') {
    throw new Error(data.message ?? '创建邀请失败')
  }
  return data.data
}

/**
 * 获取群组成员列表
 *
 * GET /api/groups/{groupId}/members
 *
 * 后端返回 ApiResponse<List>，此处已解包为数组。
 *
 * @param groupId 群组 ID
 * @returns 成员列表
 */
export async function fetchGroupMembers(groupId: number): Promise<GroupMemberItem[]> {
  const { data } = await http.get<ApiResponse<GroupMemberItem[]>>(`/groups/${groupId}/members`)
  if (!data.success) {
    throw new Error(data.message ?? '获取群组成员失败')
  }
  return data.data
}

/**
 * 移除群组成员
 *
 * DELETE /api/groups/{groupId}/members/{userId}
 *
 * 仅群组管理员可操作，管理员不能移除自己。
 *
 * @param groupId 群组 ID
 * @param userId 要移除的成员用户 ID
 */
export async function removeGroupMember(groupId: number, userId: number): Promise<void> {
  const { data } = await http.delete<ApiResponse<null>>(`/groups/${groupId}/members/${userId}`)
  if (!data.success) {
    throw new Error(data.message ?? '移除成员失败')
  }
}

/**
 * 退出群组
 *
 * POST /api/groups/{groupId}/leave
 *
 * 群组管理员不能退出（需先转让或解散群组）。
 *
 * @param groupId 要退出的群组 ID
 */
export async function leaveGroup(groupId: number): Promise<void> {
  await postVoid(`/groups/${groupId}/leave`, '退出群组失败')
}

// ─────────────────────────────────────────────
// API 函数 —— 邀请决策
// ─────────────────────────────────────────────

/**
 * 接受群组邀请
 *
 * POST /api/invitations/{invitationId}/accept
 *
 * 接受后当前用户成为对应群组的普通成员。
 *
 * @param invitationId 邀请记录 ID
 */
export async function acceptInvitation(invitationId: number): Promise<void> {
  await postVoid(`/invitations/${invitationId}/accept`, '接受邀请失败')
}

/**
 * 拒绝群组邀请
 *
 * POST /api/invitations/{invitationId}/reject
 *
 * @param invitationId 邀请记录 ID
 */
export async function rejectInvitation(invitationId: number): Promise<void> {
  await postVoid(`/invitations/${invitationId}/reject`, '拒绝邀请失败')
}

/**
 * 取消群组邀请（由邀请方操作）
 *
 * POST /api/invitations/{invitationId}/cancel
 *
 * 仅发出邀请的群组管理员可取消。
 *
 * @param invitationId 邀请记录 ID
 */
export async function cancelInvitation(invitationId: number): Promise<void> {
  await postVoid(`/invitations/${invitationId}/cancel`, '取消邀请失败')
}

// ─────────────────────────────────────────────
// API 函数 —— 加入申请
// ─────────────────────────────────────────────

/**
 * 提交加入群组申请
 *
 * POST /api/groups/join-requests
 *
 * 通过群组编码（groupCode）申请加入，等待管理员审批。
 *
 * @param groupCode 目标群组的唯一编码
 * @returns 新创建的申请记录 ID
 */
export async function submitJoinRequest(groupCode: string): Promise<number> {
  const { data } = await http.post<ApiResponse<number>>('/groups/join-requests', { groupCode })
  if (!data.success || typeof data.data !== 'number') {
    throw new Error(data.message ?? '提交加入申请失败')
  }
  return data.data
}

/**
 * 查询当前用户的所有加入申请记录
 *
 * GET /api/groups/join-requests/my
 *
 * @returns 申请记录列表（申请者视角）
 */
export async function fetchMyJoinRequests(): Promise<JoinRequestItem[]> {
  const { data } = await http.get<ApiResponse<JoinRequestItem[]>>('/groups/join-requests/my')
  if (!data.success) {
    throw new Error(data.message ?? '加载我的申请失败')
  }
  return data.data
}

/**
 * 查询当前用户发出的所有群组邀请
 *
 * GET /api/groups/invitations/my-sent
 */
export async function fetchMySentInvitations(): Promise<MySentInvitationItem[]> {
  const { data } = await http.get<ApiResponse<MySentInvitationItem[]>>('/groups/invitations/my-sent')
  if (!data.success) {
    throw new Error(data.message ?? '加载发出的邀请失败')
  }
  return data.data
}

/**
 * 查询指定群组的待处理加入申请（管理员视角）
 *
 * GET /api/groups/{groupId}/join-requests
 *
 * 仅群组管理员可调用。
 *
 * @param groupId 群组 ID
 * @returns 待审批的申请列表（管理员视角）
 */
export async function fetchOwnerJoinRequests(groupId: number): Promise<OwnerJoinRequestItem[]> {
  const { data } = await http.get<ApiResponse<OwnerJoinRequestItem[]>>(`/groups/${groupId}/join-requests`)
  if (!data.success) {
    throw new Error(data.message ?? '加载待审批申请失败')
  }
  return data.data
}

/**
 * 审批通过加入申请
 *
 * POST /api/groups/{groupId}/join-requests/{requestId}/approve
 *
 * 申请者将被添加为群组普通成员。
 *
 * @param groupId 群组 ID
 * @param requestId 申请记录 ID
 */
export async function approveJoinRequest(groupId: number, requestId: number): Promise<void> {
  await postVoid(`/groups/${groupId}/join-requests/${requestId}/approve`, '通过申请失败')
}

/**
 * 拒绝加入申请
 *
 * POST /api/groups/{groupId}/join-requests/{requestId}/reject
 *
 * @param groupId 群组 ID
 * @param requestId 申请记录 ID
 */
export async function rejectJoinRequest(groupId: number, requestId: number): Promise<void> {
  await postVoid(`/groups/${groupId}/join-requests/${requestId}/reject`, '拒绝申请失败')
}

// ─────────────────────────────────────────────
// 内部工具函数
// ─────────────────────────────────────────────

/**
 * 发送 POST 请求并校验 ApiResponse.success，失败时抛出错误
 * @param url 请求路径（相对于 baseURL）
 * @param fallbackMessage 无响应消息时的兜底错误文本
 */
async function postVoid(url: string, fallbackMessage: string) {
  const { data } = await http.post<ApiResponse<null>>(url)
  if (!data.success) {
    throw new Error(data.message ?? fallbackMessage)
  }
}
