package com.argus.rag.metrics.model.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class UsageRankItemVO {
    private Long id;            // userId 或 groupId
    private String name;        // 用户名或群组名
    private Long totalRequests;
    private Long totalTokens;
    private BigDecimal totalCost;
}
