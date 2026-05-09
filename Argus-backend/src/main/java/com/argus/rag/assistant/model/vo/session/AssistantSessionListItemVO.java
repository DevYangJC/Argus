package com.argus.rag.assistant.model.vo.session;

import java.time.LocalDateTime;

public record AssistantSessionListItemVO(
        /**
         * 会话ID
         */
        Long sessionId,
        /**
         * 会话标题
         */
        String title,
        /**
         * 最后消息时间
         */
        LocalDateTime lastMessageAt
) {
}
