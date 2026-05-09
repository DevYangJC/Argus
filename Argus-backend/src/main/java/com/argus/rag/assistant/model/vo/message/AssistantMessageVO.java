package com.argus.rag.assistant.model.vo.message;

import com.argus.rag.assistant.model.enums.AssistantMessageRole;
import com.argus.rag.assistant.model.enums.AssistantToolMode;

import java.time.LocalDateTime;

public record AssistantMessageVO(
        /**
         * 消息ID
         */
        Long messageId,
        /**
         * 所属会话ID
         */
        Long sessionId,
        /**
         * 消息角色（USER/ASSISTANT/TOOL）
         */
        AssistantMessageRole role,
        /**
         * 消息产生时的工具模式
         */
        AssistantToolMode toolMode,
        /**
         * 知识库组ID
         */
        Long groupId,
        /**
         * 消息文本内容
         */
        String content,
        /**
         * 结构化负载数据（JSON格式）
         */
        String structuredPayload,
        /**
         * 消息创建时间
         */
        LocalDateTime createdAt
) {
}
