package com.argus.rag.assistant.model.entity;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class AssistantSessionContextEntity {

    /**
     * 会话ID，主键
     * <p>必填，关联 {@link AssistantSessionEntity} 的主键</p>
     */
    private Long sessionId;
    /**
     * 会话记忆内容，由 LLM 生成的长期记忆摘要
     * <p>选填，为空表示尚未生成会话记忆</p>
     */
    private String sessionMemory;
    /**
     * 紧凑摘要，由 LLM 生成的更精炼的会话摘要
     * <p>选填，为空表示尚未生成紧凑摘要</p>
     */
    private String compactSummary;
    /**
     * 会话记忆覆盖的起始消息ID（基础消息）
     * <p>选填，用于标记 sessionMemory 覆盖的消息范围起点</p>
     */
    private Long sessionMemoryBaseMessageId;
    /**
     * 会话记忆覆盖的结束消息ID（范围终点）
     * <p>选填，用于标记 sessionMemory 覆盖的消息范围终点</p>
     */
    private Long sessionMemoryRangeEndMessageId;
    /**
     * 紧凑摘要覆盖的起始消息ID（基础消息）
     * <p>选填，用于标记 compactSummary 覆盖的消息范围起点</p>
     */
    private Long compactSummaryBaseMessageId;
    /**
     * 紧凑摘要覆盖的结束消息ID（范围终点）
     * <p>选填，用于标记 compactSummary 覆盖的消息范围终点</p>
     */
    private Long compactSummaryRangeEndMessageId;
    /**
     * 会话摘要文本，由 {@link AssistantSessionSummaryService} 生成的简明历史摘要
     * <p>选填，为空表示尚未生成</p>
     */
    private String summaryText;
    /**
     * 摘要覆盖的结束消息ID，记录 summaryText 对应的最后一条消息
     * <p>选填，用于判断摘要是否需要更新</p>
     */
    private Long sourceMessageId;
    /**
     * 上下文版本号，用于乐观锁控制并发更新
     * <p>选填，初始为 0，每次更新递增</p>
     */
    private Long contextVersion;
    /**
     * 上下文最后更新时间
     * <p>必填，由系统自动设置</p>
     */
    private LocalDateTime updatedAt;

}
