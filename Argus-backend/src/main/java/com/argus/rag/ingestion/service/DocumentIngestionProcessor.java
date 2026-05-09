package com.argus.rag.ingestion.service;

/**
 * 文档摄取处理器，定义将文档内容切分并写入向量库的核心契约。
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
public interface DocumentIngestionProcessor {

    /**
     * 对指定文档执行完整的摄取流程（切分 + 向量写入）。
     *
     * @param documentId 待摄取文档的 ID
     * @param groupId    文档所属群组的 ID，用于向量库元数据标注
     */
    void process(Long documentId, Long groupId);
}
