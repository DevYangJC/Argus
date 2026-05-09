package com.argus.rag.assistant.model.vo.tool;

import java.util.List;

public record KnowledgeBaseSearchToolResponse(
        /**
         * 是否检索到有效证据
         */
        boolean found,
        /**
         * 结果原因码，当未找到时提供具体原因标识
         */
        String reasonCode,
        /**
         * 结果原因描述，当未找到时提供具体原因说明
         */
        String reasonMessage,
        /**
         * 证据级别标识
         */
        String evidenceLevel,
        /**
         * 证据指导说明，指引 LLM 如何使用检索到的证据
         */
        String evidenceGuidance,
        /**
         * 检索到的证据片段列表
         */
        List<Evidence> evidences,
        /**
         * 引用来源列表，用于前端展示引用信息
         */
        List<com.argus.rag.qa.model.vo.AskQuestionResponse.Citation> citations
) {

    public KnowledgeBaseSearchToolResponse {
        evidences = evidences == null ? List.of() : List.copyOf(evidences);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public record Evidence(
            /**
             * 文档ID，标识证据来源文档
             */
            Long documentId,
            /**
             * 文档块ID，标识证据来源的具体文档片段
             */
            Long chunkId,
            /**
             * 文档块在原始文档中的索引位置
             */
            Integer chunkIndex,
            /**
             * 文件名，证据来源的文件名称
             */
            String fileName,
            /**
             * 检索相似度分数
             */
            double score,
            /**
             * 证据文本片段内容
             */
            String snippet
    ) {
    }
}
