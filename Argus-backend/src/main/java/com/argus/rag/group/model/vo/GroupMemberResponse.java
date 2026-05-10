package com.argus.rag.group.model.vo;

/**
 * 群组成员信息响应。
 */
public record GroupMemberResponse(
        /** 用户 ID */
        Long userId,
        /** 用户编码 */
        String userCode,
        /** 显示名称 */
        String displayName,
        /** 群组角色 */
        String role
) {
}
