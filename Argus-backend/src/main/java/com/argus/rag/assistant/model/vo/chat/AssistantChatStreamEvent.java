package com.argus.rag.assistant.model.vo.chat;

import com.argus.rag.assistant.model.enums.AssistantToolMode;
import com.argus.rag.qa.model.vo.AskQuestionResponse;

import java.util.List;

public record AssistantChatStreamEvent(
        /**
         * 事件类型名称
         * <p>可选值：start（开始）、delta（流式增量）、done（完成）、error（错误）</p>
         */
        String event,
        /**
         * 会话ID
         */
        Long sessionId,
        /**
         * 当前对话的工具模式
         */
        AssistantToolMode toolMode,
        /**
         * 知识库组ID（仅 KB_SEARCH 模式有值）
         */
        Long groupId,
        /**
         * 流式增量文本内容，仅在 delta 事件中有值
         */
        String delta,
        /**
         * 完成后的消息ID，仅在 done 事件中有值
         */
        Long messageId,
        /**
         * 完成后的完整回复文本，仅在 done 事件中有值
         */
        String reply,
        /**
         * 知识库引用来源列表，仅在 done 事件中有值
         */
        List<AskQuestionResponse.Citation> citations,
        /**
         * 错误信息，仅在 error 事件中有值
         */
        String error
) {

    public static AssistantChatStreamEvent start(
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId
    ) {
        return new AssistantChatStreamEvent(
                "start",
                sessionId,
                toolMode,
                groupId,
                null,
                null,
                null,
                List.of(),
                null
        );
    }

    public static AssistantChatStreamEvent delta(
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            String delta
    ) {
        return new AssistantChatStreamEvent(
                "delta",
                sessionId,
                toolMode,
                groupId,
                delta,
                null,
                null,
                List.of(),
                null
        );
    }

    public static AssistantChatStreamEvent done(
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            Long messageId,
            String reply,
            List<AskQuestionResponse.Citation> citations
    ) {
        return new AssistantChatStreamEvent(
                "done",
                sessionId,
                toolMode,
                groupId,
                null,
                messageId,
                reply,
                citations == null ? List.of() : citations,
                null
        );
    }

    public static AssistantChatStreamEvent error(
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            String error
    ) {
        return new AssistantChatStreamEvent(
                "error",
                sessionId,
                toolMode,
                groupId,
                null,
                null,
                null,
                List.of(),
                error
        );
    }
}
