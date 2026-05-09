package com.argus.rag.qa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 大模型知识问答的结构化输出结果。
 * <p>
 * 用于接收大模型返回的 JSON 结构化回答，包含是否已回答、回答内容和拒答原因。
 * 未知字段将被忽略（{@code @JsonIgnoreProperties(ignoreUnknown = true)}）。
 * </p>
 *
 * @param answered      是否已成功回答，{@code true} 表示模型给出了有效回答
 * @param answer        回答内容，仅在 {@code answered = true} 时有值
 * @param reasonCode    拒答原因编码，仅在 {@code answered = false} 时有值，如 "INSUFFICIENT_EVIDENCE"
 * @param reasonMessage 拒答原因描述，仅在 {@code answered = false} 时有值，人可读的拒答说明
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeAnswerOutput(
        boolean answered,
        String answer,
        String reasonCode,
        String reasonMessage
) {
}
