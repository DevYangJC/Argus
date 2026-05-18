package com.argus.rag.metrics;

/**
 * LLM 接口端点标识常量。
 */
public final class LlmEndpoint {
    private LlmEndpoint() {}

    public static final String QA_ASK = "qa/ask";
    public static final String QA_STREAM_ASK = "qa/stream-ask";
    public static final String ASSISTANT_CHAT = "assistant/chat";
    public static final String ASSISTANT_CHAT_STREAM = "assistant/chat/stream";
}
