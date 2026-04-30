package com.argus.rag.common.enums;

/** 文档处理状态 */
public enum DocumentStatus {
    /** 已上传，等待处理 */
    UPLOADED,
    /** 正在切片/向量化 */
    PROCESSING,
    /** 处理完成，可被检索 */
    READY,
    /** 处理失败 */
    FAILED
}
