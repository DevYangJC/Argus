package com.argus.rag.assistant.model.enums;

/**
 * 助手模块消息角色枚举。
 * <p>定义消息发送者的角色类型，用于标识消息来源和区分消息展示样式。</p>
 */
public enum AssistantMessageRole {
    /**
     * 用户消息，由登录用户发送的消息
     */
    USER,
    /**
     * 助手消息，由 AI 助手生成的回复
     */
    ASSISTANT,
    /**
     * 工具消息，由工具调用产生的结果消息
     */
    TOOL
}
