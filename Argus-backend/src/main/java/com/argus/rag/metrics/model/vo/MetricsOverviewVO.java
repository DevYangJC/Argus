package com.argus.rag.metrics.model.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 指标概览视图对象。
 * <p>用于管理后台仪表盘展示今日核心指标和30天趋势数据。</p>
 */
@Data
public class MetricsOverviewVO {

    /** 今日请求总数 */
    private Long todayRequests;

    /** 今日 token 消耗总数 */
    private Long todayTokens;

    /** 今日总成本金额（元） */
    private BigDecimal todayCost;

    /** 今日请求成功率（百分比，如 98.5） */
    private BigDecimal todaySuccessRate;

    /** 30天每日统计数据，用于趋势图表展示 */
    private List<DailyStats> dailyTrend;

    /**
     * 每日统计明细。
     */
    @Data
    public static class DailyStats {

        /** 日期，格式 yyyy-MM-dd */
        private String date;

        /** 当日请求数 */
        private Long requests;

        /** 当日 token 消耗数 */
        private Long tokens;

        /** 当日成本金额（元） */
        private BigDecimal cost;
    }
}
