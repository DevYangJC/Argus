package com.argus.rag.metrics.model.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 用量排行项视图对象。
 * <p>用于展示用户或群组在指定时间段内的用量排行数据。</p>
 */
@Data
public class UsageRankItemVO {

    /** 用户 ID 或群组 ID */
    private Long id;

    /** 用户名或群组名称 */
    private String name;

    /** 总请求次数 */
    private Long totalRequests;

    /** 总 token 消耗数 */
    private Long totalTokens;

    /** 总成本金额（元） */
    private BigDecimal totalCost;
}
