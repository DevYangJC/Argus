package com.argus.rag.assistant.model.dto.chat;

import com.argus.rag.assistant.model.enums.AssistantToolMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AssistantChatRequest(
        /**
         * 会话ID，标识本次聊天所属的会话
         * <p>必填，必须为正整数</p>
         */
        @NotNull(message = "sessionId 不能为空")
        @Positive(message = "sessionId 非法")
        Long sessionId,
        /**
         * 用户输入的聊天消息内容
         * <p>必填，不能为空字符串，最大长度4000个字符</p>
         */
        @NotBlank(message = "message 不能为空")
        @Size(max = 4000, message = "message 长度不能超过 4000")
        String message,
        /**
         * 工具模式，指定当前对话的工作模式
         * <p>必填，可选值：{@link AssistantToolMode#CHAT 纯对话模式}、{@link AssistantToolMode#KB_SEARCH 知识库检索模式}</p>
         */
        @NotNull(message = "toolMode 不能为空")
        AssistantToolMode toolMode,
        /**
         * 知识库组ID，仅当 toolMode 为 {@link AssistantToolMode#KB_SEARCH} 时必须填写
         * <p>选填，如果填写则必须为正整数；CHAT 模式下不应传递此字段</p>
         */
        @Positive(message = "groupId 非法")
        Long groupId
) {
}
