package com.argus.rag.qa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 查询规划结果，包含大模型分析出的检索策略和检索语句列表。
 * <p>
 * 由 {@code QueryPlanningService} 调用大模型生成，内容会经过校验和规范化处理。
 * 未知字段将被忽略（{@code @JsonIgnoreProperties(ignoreUnknown = true)}）。
 * </p>
 *
 * @param strategy 查询规划策略，取值见 {@link QueryPlanStrategy}
 * @param queries  经规划后的检索语句列表，最多 3 条
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QueryPlanResult(
        QueryPlanStrategy strategy,
        List<String> queries
) {

    /**
     * 创建回退结果：采用 DIRECT 策略，直接使用原始问题作为检索语句。
     * <p>当查询规划失败或结果无效时用作安全回退。</p>
     *
     * @param question 原始用户问题
     * @return 回退的查询规划结果
     */
    public static QueryPlanResult fallback(String question) {
        return new QueryPlanResult(QueryPlanStrategy.DIRECT, List.of(question));
    }
}
