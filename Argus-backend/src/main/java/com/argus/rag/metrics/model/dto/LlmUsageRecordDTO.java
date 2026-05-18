package com.argus.rag.metrics.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class LlmUsageRecordDTO {
    private Long userId;
    private Long groupId;
    private String module;
    private String endpoint;
    private String sessionId;
    @Builder.Default
    private Integer promptTokens = 0;
    @Builder.Default
    private Integer completionTokens = 0;
    @Builder.Default
    private Integer totalTokens = 0;
    @Builder.Default
    private Boolean isEstimated = false;
    private BigDecimal costAmount;
    private Long latencyMs;
    @Builder.Default
    private Boolean success = true;
    private String errorMessage;
    private String modelName;
}
