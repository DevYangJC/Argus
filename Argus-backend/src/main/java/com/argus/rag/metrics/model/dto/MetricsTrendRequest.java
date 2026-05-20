package com.argus.rag.metrics.model.dto;

import com.argus.rag.metrics.model.enums.StatsPeriod;
import lombok.Data;

/**
 * 指标趋势查询请求 DTO。
 * <p>用于查询指定时间段内的用量趋势数据，可按模块筛选。</p>
 */
@Data
public class MetricsTrendRequest {

    /** 统计时间段：TODAY / LAST_7_DAYS / LAST_14_DAYS / LAST_30_DAYS */
    private StatsPeriod period;

    /** 模块筛选，可选。值：QA / ASSISTANT */
    private String module;
}
