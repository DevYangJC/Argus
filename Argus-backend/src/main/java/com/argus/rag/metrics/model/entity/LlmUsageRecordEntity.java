package com.argus.rag.metrics.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("llm_usage_records")
public class LlmUsageRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long groupId;
    private String module; // QA / ASSISTANT
    private String endpoint; // qa/ask, qa/stream-ask, assistant/chat, assistant/chat/stream
    private String sessionId;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Boolean isEstimated;
    private BigDecimal costAmount;
    private String costCurrency;
    private Long latencyMs;
    private Boolean success;
    private String errorMessage;
    private String modelName;
    private LocalDateTime createdAt;
}
