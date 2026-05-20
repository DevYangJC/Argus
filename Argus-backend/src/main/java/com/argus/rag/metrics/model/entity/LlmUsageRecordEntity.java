package com.argus.rag.metrics.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LLM 用量记录实体类。
 * <p>存储每次 LLM 调用的用量信息，用于成本统计和用量监控。</p>
 */
@Data
@TableName("llm_usage_records")
public class LlmUsageRecordEntity {

    /** 记录主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调用用户 ID */
    private Long userId;

    /** 知识库组 ID，用于区分不同租户/组 */
    private Long groupId;

    /** 所属模块：QA(问答) / ASSISTANT(智能体) */
    private String module;

    /** 调用端点：qa/ask, qa/stream-ask, assistant/chat, assistant/chat/stream */
    private String endpoint;

    /** 会话 ID，用于追踪对话上下文 */
    private String sessionId;

    /** 输入 token 数量（提示词） */
    private Integer promptTokens;

    /** 输出 token 数量（模型生成） */
    private Integer completionTokens;

    /** 总 token 数量（输入 + 输出） */
    private Integer totalTokens;

    /** 是否为估算值：true-估算，false-精确值 */
    private Boolean isEstimated;

    /** 成本金额（人民币） */
    private BigDecimal costAmount;

    /** 成本货币类型，默认 CNY */
    private String costCurrency;

    /** 调用耗时（毫秒） */
    private Long latencyMs;

    /** 调用是否成功 */
    private Boolean success;

    /** 错误信息，调用失败时记录 */
    private String errorMessage;

    /** 模型名称，如 qwen-plus */
    private String modelName;

    /** 记录创建时间 */
    private LocalDateTime createdAt;
}
