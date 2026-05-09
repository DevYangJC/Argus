package com.argus.rag.assistant.model.dto.session;

public class CreateAssistantSessionRequest {

    /**
     * 创建会话时的初始消息内容
     * <p>选填，如果提供则会在创建会话后立即发送该消息；可以为 null 或空字符串</p>
     */
    private String initialMessage;

    public String getInitialMessage() {
        return initialMessage;
    }

    public void setInitialMessage(String initialMessage) {
        this.initialMessage = initialMessage;
    }
}
