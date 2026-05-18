package com.argus.rag.metrics.model.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UsageStatsVO {
    // 资源消耗
    private Long totalPromptTokens;
    private Long totalCompletionTokens;
    private Long totalTokens;
    private BigDecimal totalCost;

    // 使用统计
    private Long totalRequests;
    private Long successRequests;
    private Long failedRequests;
    private BigDecimal successRate;  // 百分比，如 98.5

    // 性能指标
    private BigDecimal avgLatencyMs;
    private BigDecimal avgRpm;       // 平均每分钟请求数
    private BigDecimal avgTpm;       // 平均每分钟 token 数
}
