package com.argus.rag.assistant.model.dto.message;

import com.argus.rag.assistant.model.enums.AssistantToolMode;

public record AssistantMessageCreateDTO(
        /**
         * 会话ID，标识消息所属的会话
         * <p>必填，必须为正整数</p>
         */
        Long sessionId,
        /**
         * 工具模式，标识消息产生时的对话工作模式
         * <p>必填，不允许为 null</p>
         */
        AssistantToolMode toolMode,
        /**
         * 知识库组ID，当 toolMode 为 KB_SEARCH 时必须提供
         * <p>选填，CHAT 模式下为 null</p>
         */
        Long groupId,
        /**
         * 消息文本内容
         * <p>必填，不能为空或空白字符串</p>
         */
        String content,
        /**
         * 结构化负载数据（JSON格式），用于存储额外的消息元数据
         * <p>选填，如果提供必须是合法的 JSON 字符串</p>
         */
        String structuredPayload
) {
}
