package com.argus.rag.group.model.vo;

import java.time.LocalDateTime;

/**
 * 群主视角的加入申请响应。
 */
public record OwnerJoinRequestResponse(
        /** 申请 ID */
        Long requestId,
        /** 群组 ID */
        Long groupId,
        /** 申请人用户 ID */
        Long applicantUserId,
        /** 申请人用户编码 */
        String applicantUserCode,
        /** 申请人显示名称 */
        String applicantDisplayName,
        /** 申请状态 */
        String status,
        /** 申请创建时间 */
        LocalDateTime createdAt
) {
}
