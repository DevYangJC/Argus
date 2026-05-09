package com.argus.rag.assistant.model.vo.conversation;

import com.argus.rag.assistant.model.vo.message.AssistantMessageVO;

import java.util.List;

public record AssistantConversationContextVO(
        /**
         * 会话摘要文本
         * <p>由 LLM 生成的会话历史摘要，用于在短时间内快速恢复上下文</p>
         */
        String summaryText,
        /**
         * 最近的消息列表
         */
        List<AssistantMessageVO> recentMessages
) {
}
