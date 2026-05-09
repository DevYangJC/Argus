package com.argus.rag.assistant.model.vo.session;

import java.time.LocalDateTime;

public record AssistantSessionDetailVO(
        /**
         * 会话ID
         */
        Long sessionId,
        /**
         * 会话标题
         */
        String title,
        /**
         * 会话状态
         * <p>对应 {@link com.argus.rag.assistant.model.enums.AssistantSessionStatus} 的枚举值字符串</p>
         */
        String status,
        /**
         * 最后消息时间
         */
        LocalDateTime lastMessageAt,
        /**
         * 会话创建时间
         */
        LocalDateTime createdAt
) {
}
