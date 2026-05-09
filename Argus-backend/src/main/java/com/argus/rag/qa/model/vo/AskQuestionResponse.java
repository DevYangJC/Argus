package com.argus.rag.qa.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 知识问答响应 VO。
 * <p>
 * 包含问答结果、拒答原因和引用来源列表。
 * 值为 {@code null} 的字段不会被序列化到 JSON 中。
 * </p>
 *
 * @param answered      是否已成功回答
 * @param answer        回答内容，仅在已回答时有值
 * @param reasonCode    拒答原因编码，仅在未回答时有值，如 "INSUFFICIENT_EVIDENCE"、"ANSWER_FORMAT_ERROR"
 * @param reasonMessage 拒答原因描述，仅在未回答时有值
 * @param citations     引用来源列表，已回答时包含支撑回答的文档引用
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AskQuestionResponse(
        boolean answered,
        String answer,
        String reasonCode,
        String reasonMessage,
        List<Citation> citations
) {

    /**
     * 创建已回答的响应。
     *
     * @param answer    回答内容
     * @param citations 引用来源列表
     * @return 已回答的响应对象
     */
    public static AskQuestionResponse answered(String answer, List<Citation> citations) {
        return new AskQuestionResponse(true, answer, null, null, citations);
    }

    /**
     * 创建未回答的响应。
     *
     * @param reasonCode    拒答原因编码
     * @param reasonMessage 拒答原因描述
     * @param citations     引用来源列表（可能为空）
     * @return 未回答的响应对象
     */
    public static AskQuestionResponse unanswered(
            String reasonCode,
            String reasonMessage,
            List<Citation> citations
    ) {
        return new AskQuestionResponse(false, null, reasonCode, reasonMessage, citations);
    }

    /**
     * 引用来源信息，记录回答所依据的文档切片。
     *
     * @param documentId 文档 ID
     * @param chunkId    文档切片 ID
     * @param chunkIndex 切片在文档中的序号（从 0 开始）
     * @param fileName   来源文件名
     * @param score      检索相关性评分
     * @param snippet    文本摘要片段（当前未使用，保留字段）
     */
    public record Citation(
            Long documentId,
            Long chunkId,
            Integer chunkIndex,
            String fileName,
            double score,
            String snippet
    ) {
    }
}
