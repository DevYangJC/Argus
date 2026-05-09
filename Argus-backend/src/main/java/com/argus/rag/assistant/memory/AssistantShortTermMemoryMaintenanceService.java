package com.argus.rag.assistant.memory;

import com.argus.rag.assistant.mapper.AssistantMessageMapper;
import com.argus.rag.assistant.mapper.AssistantSessionContextMapper;
import com.argus.rag.assistant.model.entity.AssistantMessageEntity;
import com.argus.rag.assistant.model.entity.AssistantSessionContextEntity;
import com.argus.rag.assistant.model.enums.AssistantToolMode;
import com.argus.rag.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 助手短期记忆维护服务。
 * <p>负责在消息持久化后触发短期记忆的增量更新和维护。</p>
 * <p>核心逻辑：</p>
 * <ul>
 *   <li>判断是否需要更新会话记忆（基于增量消息数和 token 阈值）</li>
 *   <li>调用 LLM 生成会话记忆摘要并持久化</li>
 *   <li>在需要时触发紧凑摘要的生成</li>
 *   <li>使用乐观锁控制并发更新</li>
 * </ul>
 */
@Service
public class AssistantShortTermMemoryMaintenanceService {

    private static final int TOKEN_ESTIMATE_DIVISOR = 4;

    /** 消息 Mapper，用于查询会话消息列表 */
    private final AssistantMessageMapper assistantMessageMapper;
    /** 会话上下文 Mapper，用于读写会话上下文（含版本号乐观锁） */
    private final AssistantSessionContextMapper assistantSessionContextMapper;
    /** 记忆摘要器，用于调用 LLM 生成摘要 */
    private final AssistantMemorySummarizer assistantMemorySummarizer;
    /** 触发会话压缩的总体 token 阈值 */
    private final int sessionTokenThreshold;
    /** 触发会话记忆更新的消息数阈值 */
    private final int sessionMemoryMessageTrigger;
    /** 触发会话记忆更新的 token 数阈值 */
    private final int sessionMemoryTokenTrigger;
    /** 触发紧凑摘要的消息数阈值 */
    private final int compactMessageTrigger;
    /** 触发紧凑摘要的 token 数阈值 */
    private final int compactTokenTrigger;

    public AssistantShortTermMemoryMaintenanceService(
            AssistantMessageMapper assistantMessageMapper,
            AssistantSessionContextMapper assistantSessionContextMapper,
            AssistantMemorySummarizer assistantMemorySummarizer,
            @Value("${assistant.short-term-memory.session-token-threshold:6500}") int sessionTokenThreshold,
            @Value("${assistant.short-term-memory.session-memory-message-trigger:4}") int sessionMemoryMessageTrigger,
            @Value("${assistant.short-term-memory.session-memory-token-trigger:1200}") int sessionMemoryTokenTrigger,
            @Value("${assistant.short-term-memory.compact-message-trigger:6}") int compactMessageTrigger,
            @Value("${assistant.short-term-memory.compact-token-trigger:1800}") int compactTokenTrigger
    ) {
        this.assistantMessageMapper = assistantMessageMapper;
        this.assistantSessionContextMapper = assistantSessionContextMapper;
        this.assistantMemorySummarizer = assistantMemorySummarizer;
        this.sessionTokenThreshold = sessionTokenThreshold;
        this.sessionMemoryMessageTrigger = sessionMemoryMessageTrigger;
        this.sessionMemoryTokenTrigger = sessionMemoryTokenTrigger;
        this.compactMessageTrigger = compactMessageTrigger;
        this.compactTokenTrigger = compactTokenTrigger;
    }

    /**
     * 在模型响应前执行短期记忆维护。
     * <p>与 {@link #maintainAfterResponse} 逻辑相同，用于在模型调用开始前确保上下文摘要是最新的。</p>
     *
     * @param sessionId        会话 ID
     * @param toolMode         当前工具模式
     * @param groupId          知识库组 ID
     * @param currentMessageId 当前消息 ID
     */
    public void maintainBeforeResponse(
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            Long currentMessageId
    ) {
        maintain(sessionId, toolMode, groupId, currentMessageId);
    }

    /**
     * 在模型响应后执行短期记忆维护。
     * <p>与 {@link #maintainBeforeResponse} 逻辑相同，用于在模型调用完成后将新消息纳入摘要。</p>
     *
     * @param sessionId        会话 ID
     * @param toolMode         当前工具模式
     * @param groupId          知识库组 ID
     * @param currentMessageId 当前消息 ID
     */
    public void maintainAfterResponse(
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            Long currentMessageId
    ) {
        maintain(sessionId, toolMode, groupId, currentMessageId);
    }

    /**
     * 判断是否需要更新会话记忆。
     * <p>当新增消息数达到 {@link #sessionMemoryMessageTrigger} 阈值或估算 token 数达到
     * {@link #sessionMemoryTokenTrigger} 阈值时，认为需要更新会话记忆。</p>
     *
     * @param newMessages           新增消息列表
     * @param lastRangeEndMessageId 上次摘要覆盖的最后一条消息 ID
     * @return 需要更新会话记忆时返回 {@code true}
     */
    public boolean shouldMaintainSessionMemory(List<AssistantMessageEntity> newMessages, long lastRangeEndMessageId) {
        if (newMessages == null || newMessages.isEmpty()) {
            return false;
        }
        int estimatedTokens = estimateTokens(newMessages);
        return newMessages.size() >= sessionMemoryMessageTrigger || estimatedTokens >= sessionMemoryTokenTrigger;
    }

    /**
     * 判断是否需要触发紧凑摘要。
     * <p>当会话总 token 数超过 {@link #sessionTokenThreshold} 阈值，且新增消息数或新增 token 数
     * 达到对应的触发器阈值时，需要生成紧凑摘要。</p>
     *
     * @param estimatedTokens 会话总 token 估算值
     * @param newMessageCount 新增消息数
     * @param newTokenCount   新增 token 估算值
     * @return 需要紧凑摘要时返回 {@code true}
     */
    public boolean shouldCompactSession(int estimatedTokens, long newMessageCount, long newTokenCount) {
        return estimatedTokens > sessionTokenThreshold
                && (newMessageCount >= compactMessageTrigger || newTokenCount >= compactTokenTrigger);
    }

    /**
     * 估算消息列表的 token 数。
     * <p>基于消息内容总字符数除以固定除数（{@value #TOKEN_ESTIMATE_DIVISOR}）进行粗略估算。</p>
     *
     * @param messages 消息实体列表
     * @return 估算的 token 数量，至少为 0
     */
    public int estimateTokens(List<AssistantMessageEntity> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int totalChars = messages.stream()
                .map(AssistantMessageEntity::getContent)
                .filter(content -> content != null && !content.isBlank())
                .mapToInt(String::length)
                .sum();
        return Math.max(1, totalChars / TOKEN_ESTIMATE_DIVISOR);
    }

    private void maintain(Long sessionId, AssistantToolMode toolMode, Long groupId, Long currentMessageId) {
        List<AssistantMessageEntity> allMessages = assistantMessageMapper.selectBySessionIdOrderByCreatedAt(sessionId);
        AssistantSessionContextEntity existingContext = assistantSessionContextMapper.selectBySessionId(sessionId);
        long lastRangeEndMessageId = existingContext == null || existingContext.getSessionMemoryRangeEndMessageId() == null
                ? 0L
                : existingContext.getSessionMemoryRangeEndMessageId();
        List<AssistantMessageEntity> newMessages = allMessages.stream()
                .filter(message -> message.getId() != null && message.getId() > lastRangeEndMessageId)
                .toList();
        if (!shouldMaintainSessionMemory(newMessages, lastRangeEndMessageId)) {
            return;
        }
        AssistantSessionContextEntity contextToWrite = existingContext == null
                ? new AssistantSessionContextEntity()
                : existingContext;
        contextToWrite.setSessionId(sessionId);
        contextToWrite.setSessionMemory(assistantMemorySummarizer.summarizeSessionMemory(
                existingContext == null ? null : existingContext.getSessionMemory(),
                newMessages,
                toolMode,
                groupId
        ));
        contextToWrite.setSessionMemoryBaseMessageId(newMessages.getFirst().getId());
        contextToWrite.setSessionMemoryRangeEndMessageId(newMessages.getLast().getId());
        contextToWrite.setUpdatedAt(LocalDateTime.now());
        long expectedVersion = existingContext == null || existingContext.getContextVersion() == null
                ? 0L
                : existingContext.getContextVersion();
        contextToWrite.setContextVersion(expectedVersion + 1);

        int estimatedTokens = estimateTokens(allMessages);
        int newTokenCount = estimateTokens(newMessages);
        if (shouldCompactSession(estimatedTokens, newMessages.size(), newTokenCount)) {
            contextToWrite.setCompactSummary(assistantMemorySummarizer.summarizeCompactSummary(
                    existingContext == null ? null : existingContext.getCompactSummary(),
                    contextToWrite.getSessionMemory(),
                    collectMessagesToCompact(allMessages, currentMessageId)
            ));
            contextToWrite.setCompactSummaryBaseMessageId(allMessages.getFirst().getId());
            contextToWrite.setCompactSummaryRangeEndMessageId(newMessages.getLast().getId());
        }

        int updatedRows;
        if (existingContext == null) {
            updatedRows = assistantSessionContextMapper.upsert(contextToWrite);
        } else {
            updatedRows = assistantSessionContextMapper.updateShortTermMemoryWithVersion(contextToWrite, expectedVersion);
        }
        if (updatedRows != 1) {
            throw new BusinessException("短期记忆写回失败");
        }
    }

    private List<AssistantMessageEntity> collectMessagesToCompact(
            List<AssistantMessageEntity> allMessages,
            Long currentMessageId
    ) {
        List<AssistantMessageEntity> messagesToCompact = new java.util.ArrayList<>();
        for (AssistantMessageEntity message : allMessages) {
            if (message.getId() != null && currentMessageId != null && message.getId() >= currentMessageId) {
                break;
            }
            messagesToCompact.add(message);
        }
        return messagesToCompact;
    }
}
