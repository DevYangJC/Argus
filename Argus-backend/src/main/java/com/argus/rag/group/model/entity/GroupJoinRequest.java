package com.argus.rag.group.model.entity;

import com.argus.rag.common.enums.GroupJoinRequestStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群组加入申请实体，映射 group_join_requests 表。
 */
@TableName("group_join_requests")
@Data
public class GroupJoinRequest {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 群组 ID */
    private Long groupId;

    /** 申请人用户 ID */
    private Long applicantUserId;

    /** 审批人用户 ID */
    private Long decidedByUserId;

    /** 申请状态 */
    private GroupJoinRequestStatus status;

    /** 申请时间 */
    private LocalDateTime createdAt;

    /** 审批时间 */
    private LocalDateTime decidedAt;
}
