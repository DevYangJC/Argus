package com.argus.rag.metrics.collector;

import com.argus.rag.metrics.model.dto.LlmUsageRecordDTO;

/**
 * LLM 用量统一采集接口。
 * 各 AI 模块（QA / Assistant / 未来扩展）在 LLM 调用完成后调用此接口记录用量。
 */
public interface LlmUsageCollector {
    /**
     * 异步记录一次 LLM 调用的用量信息。
     * 此方法不阻塞调用方。
     */
    void record(LlmUsageRecordDTO record);
}
