package com.argus.rag.metrics.model.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class MetricsOverviewVO {
    // 今日核心指标
    private Long todayRequests;
    private Long todayTokens;
    private BigDecimal todayCost;
    private BigDecimal todaySuccessRate;

    // 30天趋势（用于图表）
    private List<DailyStats> dailyTrend;

    @Data
    public static class DailyStats {
        private String date;          // yyyy-MM-dd
        private Long requests;
        private Long tokens;
        private BigDecimal cost;
    }
}
