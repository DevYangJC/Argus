package com.argus.rag.group.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 创建群组邀请请求。
 */
public record CreateInvitationRequest(
        /** 被邀请用户 ID */
        @NotNull(message = "被邀请用户不能为空")
        @Positive(message = "被邀请用户非法")
        Long inviteeUserId
) {
}
