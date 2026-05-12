package com.argus.rag.group.model.vo;

import java.time.LocalDateTime;

/**
 * 当前用户视角的发出邀请响应。
 */
public record MySentInvitationResponse(
        /** 邀请 ID */
        Long invitationId,
        /** 群组 ID */
        Long groupId,
        /** 群组名称 */
        String groupName,
        /** 被邀请用户 ID */
        Long inviteeUserId,
        /** 被邀请用户显示名称 */
        String inviteeDisplayName,
        /** 邀请状态 */
        String status,
        /** 邀请创建时间 */
        LocalDateTime createdAt,
        /** 被邀请人处理时间 */
        LocalDateTime decidedAt
) {
}
