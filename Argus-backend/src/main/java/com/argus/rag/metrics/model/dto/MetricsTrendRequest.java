package com.argus.rag.metrics.model.dto;

import com.argus.rag.metrics.model.enums.StatsPeriod;
import lombok.Data;

@Data
public class MetricsTrendRequest {
    private StatsPeriod period;
    private String module;    // 可选，筛选模块
}
