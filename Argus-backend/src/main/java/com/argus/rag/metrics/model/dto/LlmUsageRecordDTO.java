package com.argus.rag.metrics.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * LLM 用量记录数据传输对象。
 * <p>用于各模块调用 LLM 后上报用量信息，最终转换为实体类持久化。</p>
 */
@Data
@Builder
public class LlmUsageRecordDTO {

    /** 调用用户 ID */
    private Long userId;

    /** 知识库组 ID */
    private Long groupId;

    /** 所属模块：QA / ASSISTANT */
    private String module;

    /** 调用端点：qa/ask, qa/stream-ask, assistant/chat, assistant/chat/stream */
    private String endpoint;

    /** 会话 ID */
    private String sessionId;

    /** 输入 token 数量，默认 0 */
    @Builder.Default
    private Integer promptTokens = 0;

    /** 输出 token 数量，默认 0 */
    @Builder.Default
    private Integer completionTokens = 0;

    /** 总 token 数量，默认 0 */
    @Builder.Default
    private Integer totalTokens = 0;

    /** 是否为估算值，默认 false（精确值） */
    @Builder.Default
    private Boolean isEstimated = false;

    /** 成本金额（元） */
    private BigDecimal costAmount;

    /** 调用耗时（毫秒） */
    private Long latencyMs;

    /** 调用是否成功，默认 true */
    @Builder.Default
    private Boolean success = true;

    /** 错误信息，调用失败时记录 */
    private String errorMessage;

    /** 模型名称，如 qwen-plus */
    private String modelName;
}
