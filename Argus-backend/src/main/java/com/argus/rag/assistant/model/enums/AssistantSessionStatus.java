package com.argus.rag.assistant.model.enums;

/**
 * 助手会话状态枚举。
 * <p>定义会话的生命周期状态，用于控制会话的可用性和展示逻辑。</p>
 */
public enum AssistantSessionStatus {
    /**
     * 活跃状态，会话可用且可正常收发消息
     */
    ACTIVE,
    /**
     * 已归档状态，会话不再活跃但仍可查看历史记录
     */
    ARCHIVED,
    /**
     * 已删除状态，会话已被标记为删除状态
     */
    DELETED
}
