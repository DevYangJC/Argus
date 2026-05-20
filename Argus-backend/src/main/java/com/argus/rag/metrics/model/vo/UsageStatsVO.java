package com.argus.rag.metrics.model.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 用量统计视图对象。
 * <p>聚合展示指定时间段内的完整用量统计信息，包括资源消耗、使用统计和性能指标。</p>
 */
@Data
public class UsageStatsVO {

    // ========== 资源消耗 ==========

    /** 输入 token 总数（提示词） */
    private Long totalPromptTokens;

    /** 输出 token 总数（模型生成） */
    private Long totalCompletionTokens;

    /** token 总消耗数（输入 + 输出） */
    private Long totalTokens;

    /** 总成本金额（元） */
    private BigDecimal totalCost;

    // ========== 使用统计 ==========

    /** 总请求次数 */
    private Long totalRequests;

    /** 成功请求次数 */
    private Long successRequests;

    /** 失败请求次数 */
    private Long failedRequests;

    /** 请求成功率（百分比，如 98.5） */
    private BigDecimal successRate;

    // ========== 性能指标 ==========

    /** 平均响应延迟（毫秒） */
    private BigDecimal avgLatencyMs;

    /** 平均每分钟请求数（Requests Per Minute） */
    private BigDecimal avgRpm;

    /** 平均每分钟 token 数（Tokens Per Minute） */
    private BigDecimal avgTpm;
}
