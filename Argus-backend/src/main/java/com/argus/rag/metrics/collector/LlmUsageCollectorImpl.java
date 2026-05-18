package com.argus.rag.metrics.collector;

import com.argus.rag.metrics.model.dto.LlmUsageRecordDTO;
import com.argus.rag.metrics.model.entity.LlmUsageRecordEntity;
import com.argus.rag.metrics.mapper.LlmUsageRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class LlmUsageCollectorImpl implements LlmUsageCollector {

    private final LlmUsageRecordMapper llmUsageRecordMapper;

    public LlmUsageCollectorImpl(LlmUsageRecordMapper llmUsageRecordMapper) {
        this.llmUsageRecordMapper = llmUsageRecordMapper;
    }

    @Async
    @Override
    public void record(LlmUsageRecordDTO record) {
        try {
            LlmUsageRecordEntity entity = toEntity(record);
            llmUsageRecordMapper.insert(entity);
            log.debug("LLM 用量记录已保存: module={}, endpoint={}, totalTokens={}",
                    entity.getModule(), entity.getEndpoint(), entity.getTotalTokens());
        } catch (Exception e) {
            // 统计记录不应影响业务主流程，捕获异常后仅记录日志
            log.error("LLM 用量记录保存失败: {}", e.getMessage(), e);
        }
    }

    private LlmUsageRecordEntity toEntity(LlmUsageRecordDTO dto) {
        LlmUsageRecordEntity entity = new LlmUsageRecordEntity();
        entity.setUserId(dto.getUserId());
        entity.setGroupId(dto.getGroupId());
        entity.setModule(dto.getModule());
        entity.setEndpoint(dto.getEndpoint());
        entity.setSessionId(dto.getSessionId());
        entity.setPromptTokens(dto.getPromptTokens());
        entity.setCompletionTokens(dto.getCompletionTokens());
        entity.setTotalTokens(dto.getTotalTokens());
        entity.setIsEstimated(dto.getIsEstimated());
        entity.setCostAmount(dto.getCostAmount());
        entity.setCostCurrency("CNY");
        entity.setLatencyMs(dto.getLatencyMs());
        entity.setSuccess(dto.getSuccess());
        entity.setErrorMessage(dto.getErrorMessage());
        entity.setModelName(dto.getModelName());
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }
}
