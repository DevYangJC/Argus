package com.argus.rag.group.model.vo;

import java.time.LocalDateTime;

/**
 * 当前用户视角的加入申请响应。
 */
public record MyJoinRequestResponse(
        /** 申请 ID */
        Long requestId,
        /** 群组 ID */
        Long groupId,
        /** 群组编码 */
        String groupCode,
        /** 群组名称 */
        String groupName,
        /** 申请状态 */
        String status,
        /** 申请创建时间 */
        LocalDateTime createdAt,
        /** 审批时间 */
        LocalDateTime decidedAt
) {
}
