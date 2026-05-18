package com.argus.rag.metrics.cost;

import java.math.BigDecimal;

/**
 * LLM 调用费用计算器。
 * 根据模型名称和 token 数量计算费用。
 */
public interface LlmCostCalculator {
    /**
     * 计算一次 LLM 调用的费用。
     *
     * @param modelName        模型名称
     * @param promptTokens     输入 token 数
     * @param completionTokens 输出 token 数
     * @return 费用金额（单位：元）
     */
    BigDecimal calculate(String modelName, int promptTokens, int completionTokens);
}
