package com.argus.rag.document.service;

/**
 * 文档摄入请求事件。
 *
 * <p>在文档上传完成并持久化到数据库后发布，由 {@link DocumentIngestionAsyncListener}
 * 异步监听，触发文档的 ETL（提取-转换-加载）流水线。
 *
 * @param documentId 新创建的文档 ID
 * @param groupId    文档所属群组 ID
 * @author Argus-RAG Team
 * @since 1.0.0
 */
public record DocumentIngestionRequestedEvent(
        Long documentId,
        Long groupId
) {
}
