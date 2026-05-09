package com.argus.rag.assistant.model.enums;

/**
 * 助手工具模式枚举。
 * <p>定义助手对话的工作模式，影响 Agent 的 system prompt 和可用工具。</p>
 */
public enum AssistantToolMode {
    /**
     * 纯对话模式，不使用知识库工具，仅基于模型自身知识进行对话
     */
    CHAT,
    /**
     * 知识库检索模式，允许 Agent 调用知识库检索工具获取相关证据后生成回答
     */
    KB_SEARCH
}
