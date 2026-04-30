package com.argus.rag.common.enums;

/** 文档摄入任务执行状态 */
public enum IngestionJobStatus {
    /** 等待执行 */
    PENDING,
    /** 执行中 */
    RUNNING,
    /** 执行成功 */
    SUCCEEDED,
    /** 执行失败 */
    FAILED,
    /** 已取消 */
    CANCELLED
}
