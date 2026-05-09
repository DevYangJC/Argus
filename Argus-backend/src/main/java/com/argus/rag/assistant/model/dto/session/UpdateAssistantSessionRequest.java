package com.argus.rag.assistant.model.dto.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAssistantSessionRequest(
        /**
         * 会话的新标题
         * <p>必填，不能为空或空白字符串，最大长度255个字符</p>
         */
        @NotBlank(message = "title 不能为空")
        @Size(max = 255, message = "title 长度不能超过 255")
        String title
) {
}
