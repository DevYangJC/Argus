package com.argus.rag.common.enums;

/** 加入知识库/分组申请的状态 */
public enum GroupJoinRequestStatus {
    /** 待审批 */
    PENDING,
    /** 已通过 */
    APPROVED,
    /** 已拒绝 */
    REJECTED,
    /** 已取消 */
    CANCELED
}
