package com.argus.rag.assistant.memory;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.argus.rag.assistant.model.enums.AssistantToolMode;
import com.argus.rag.assistant.model.vo.message.AssistantMessageVO;
import com.argus.rag.assistant.service.AssistantConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 助手短期记忆 Hook。
 * <p>作为 ReactAgent 的 BEFORE_MODEL Hook，在每次模型调用前组装上下文消息。</p>
 * <p>通过从数据库中加载会话的紧凑摘要、会话记忆和最近消息，构建发送给 LLM 的消息列表，
 * 实现短期记忆的自动注入。</p>
 */
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
public class AssistantShortTermMemoryHook extends MessagesModelHook {

    /** 日志记录器 */
    private static final Logger log = LoggerFactory.getLogger(AssistantShortTermMemoryHook.class);
    /** 加载最近消息的最大条数 */
    private static final int RECENT_MESSAGE_LIMIT = 10;
    /** 触发运行时压缩的 token 阈值 */
    private static final int RUNTIME_TOKEN_THRESHOLD = 50000;

    private final AssistantConversationService assistantConversationService;

    public AssistantShortTermMemoryHook(AssistantConversationService assistantConversationService) {
        this.assistantConversationService = assistantConversationService;
    }

    @Override
    public String getName() {
        return "assistant_short_term_memory_hook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        if (previousMessages == null || previousMessages.isEmpty() || config == null) {
            return new AgentCommand(previousMessages, UpdatePolicy.REPLACE);
        }
        Long userId = metadataAsLong(config, "userId").orElse(null);
        Long sessionId = metadataAsLong(config, "sessionId").orElse(null);
        AssistantToolMode toolMode = metadataAsToolMode(config, "toolMode").orElse(null);
        if (userId == null || sessionId == null || toolMode == null) {
            return new AgentCommand(previousMessages, UpdatePolicy.REPLACE);
        }
        String currentQuestion = extractCurrentQuestion(previousMessages);
        if (currentQuestion == null) {
            return new AgentCommand(previousMessages, UpdatePolicy.REPLACE);
        }
        Long groupId = metadataAsLong(config, "groupId").orElse(null);
        if (log.isDebugEnabled()) {
            log.debug(
                    "ShortTermMemoryHook.beforeModel input. userId={}, sessionId={}, toolMode={}, groupId={}, previousMessages={}",
                    userId,
                    sessionId,
                    toolMode,
                    groupId,
                    summarizeMessages(previousMessages)
            );
        }
        // 这里不是在原 messages 上做增量补丁，而是直接重建一份真正送进模型的上下文：
        // compact summary -> session memory -> recent messages -> current question。
        List<Message> assembledMessages = assembleBeforeModelMessages(
                userId,
                sessionId,
                toolMode,
                groupId,
                currentQuestion,
                previousMessages
        );
        if (log.isDebugEnabled()) {
            log.debug(
                    "ShortTermMemoryHook.beforeModel output. userId={}, sessionId={}, toolMode={}, groupId={}, currentQuestion={}, assembledMessages={}",
                    userId,
                    sessionId,
                    toolMode,
                    groupId,
                    currentQuestion,
                    summarizeMessages(assembledMessages)
            );
        }
        return new AgentCommand(
                assembledMessages,
                UpdatePolicy.REPLACE
        );
    }

    /**
     * 兼容旧版 Agent 框架的 beforeModel 重载。
     * <p>该方法仅透传输入，不做任何处理，确保与旧版 API 兼容。</p>
     *
     * @param ignored 输入对象，直接返回
     * @return 与输入相同的对象
     */
    public Object beforeModel(Object ignored) {
        return ignored;
    }

    /**
     * 组装模型调用前的消息列表（无运行时消息的便捷方法）。
     * <p>等价于 {@code assembleBeforeModelMessages(userId, sessionId, toolMode, groupId, currentQuestion, List.of())}。</p>
     *
     * @param userId          用户 ID
     * @param sessionId       会话 ID
     * @param toolMode        当前工具模式
     * @param groupId         知识库组 ID
     * @param currentQuestion 当前用户问题
     * @return 组装后的消息列表
     */
    public List<Message> assembleBeforeModelMessages(
            Long userId,
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            String currentQuestion
    ) {
        return assembleBeforeModelMessages(
                userId,
                sessionId,
                toolMode,
                groupId,
                currentQuestion,
                List.of()
        );
    }

    /**
     * 组装模型调用前的消息列表（完整参数版本）。
     * <p>按以下顺序构建发送给 LLM 的消息列表：</p>
     * <ol>
     *   <li>紧凑摘要（作为系统消息注入）</li>
     *   <li>会话记忆（作为系统消息注入）</li>
     *   <li>最近的对话历史</li>
     *   <li>运行时工具消息</li>
     *   <li>当前用户问题</li>
     * </ol>
     *
     * @param userId           用户 ID
     * @param sessionId        会话 ID
     * @param toolMode         当前工具模式
     * @param groupId          知识库组 ID
     * @param currentQuestion  当前用户问题
     * @param runtimeMessages  运行时工具返回的消息列表
     * @return 组装后的消息列表
     */
    public List<Message> assembleBeforeModelMessages(
            Long userId,
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            String currentQuestion,
            List<Message> runtimeMessages
    ) {
        List<Message> messages = new ArrayList<>();
        AssistantConversationService.AssistantConversationContext conversationContext =
                assistantConversationService.loadConversationContext(userId, sessionId, RECENT_MESSAGE_LIMIT);
        // compact summary 和 session memory 被降级成系统消息注入，
        // 目的是让模型先看到压缩后的会话状态，再看最近几轮原始消息。
        // 1. 紧凑摘要（作为系统消息）
        addSystemMemory(messages, "compact summary", conversationContext.compactSummary());
        // 2. 会话记忆（作为系统消息）
        addSystemMemory(messages, "session memory", conversationContext.sessionMemory());
        // 3. 最近的对话历史
        appendRecentMessages(messages, conversationContext.recentMessages(), currentQuestion);
        // 4. 运行时工具消息（Agent 图执行过程中产生的 ToolResponseMessage）
        appendRuntimeToolMessages(messages, runtimeMessages);
        // 5. 当前用户问题
        messages.add(new UserMessage(currentQuestion));
        return messages;
    }

    /**
     * 判断是否需要运行时压缩。
     * <p>当估算 token 数超过阈值 {@value #RUNTIME_TOKEN_THRESHOLD} 时，认为需要触发运行时压缩。</p>
     *
     * @param estimatedTokens 估算的 token 数
     * @return 需要运行时压缩时返回 {@code true}
     */
    public boolean shouldRuntimeCompact(int estimatedTokens) {
        return estimatedTokens > RUNTIME_TOKEN_THRESHOLD;
    }

    /**
     * 执行运行时压缩。
     * <p>保留消息列表末尾最多 3 条消息，丢弃更早的消息以控制 token 数量。</p>
     *
     * @param messages 待压缩的消息列表
     * @return 压缩后的消息列表，若输入为空则返回空列表
     */
    public List<Message> runtimeCompact(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int keepCount = Math.min(3, messages.size());
        return new ArrayList<>(messages.subList(messages.size() - keepCount, messages.size()));
    }

    private void addSystemMemory(List<Message> messages, String label, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        messages.add(new SystemMessage((label + System.lineSeparator() + content).trim()));
    }

    private void appendRecentMessages(
            List<Message> messages,
            List<AssistantMessageVO> recentMessages,
            String currentQuestion
    ) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return;
        }
        int lastIndex = recentMessages.size() - 1;
        for (int index = 0; index < recentMessages.size(); index++) {
            AssistantMessageVO recentMessage = recentMessages.get(index);
            if (recentMessage == null || recentMessage.content() == null || recentMessage.content().isBlank()) {
                continue;
            }
            boolean isCurrentQuestionEcho = index == lastIndex
                    && recentMessage.role() == com.argus.rag.assistant.model.enums.AssistantMessageRole.USER
                    && currentQuestion.equals(recentMessage.content().trim());
            if (isCurrentQuestionEcho) {
                // 当前问题会在 assembleBeforeModelMessages 的最后重新追加，这里跳过数据库里的回显，避免重复喂给模型。
                continue;
            }
            switch (recentMessage.role()) {
                case USER -> messages.add(new UserMessage(formatRecentMessage(recentMessage)));
                case ASSISTANT -> messages.add(new AssistantMessage(formatRecentMessage(recentMessage)));
                case TOOL -> messages.add(new AssistantMessage(formatToolMessage(recentMessage.content())));
            }
        }
    }

    private void appendRuntimeToolMessages(List<Message> messages, List<Message> runtimeMessages) {
        if (runtimeMessages == null || runtimeMessages.isEmpty()) {
            return;
        }
        for (Message runtimeMessage : runtimeMessages) {
            if (!(runtimeMessage instanceof ToolResponseMessage toolResponseMessage)) {
                continue;
            }
            if (toolResponseMessage.getResponses() == null || toolResponseMessage.getResponses().isEmpty()) {
                continue;
            }
            for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                if (response == null || response.responseData() == null || response.responseData().isBlank()) {
                    continue;
                }
                messages.add(new AssistantMessage(formatToolMessage(response.responseData())));
            }
        }
    }

    private String formatRecentMessage(AssistantMessageVO message) {
        String mode = message.toolMode() == null ? "UNKNOWN" : message.toolMode().name();
        return ("[历史消息 | 模式：" + mode + "]" + System.lineSeparator() + message.content()).trim();
    }

    private String formatToolMessage(String content) {
        return ("[工具观察]" + System.lineSeparator() + content).trim();
    }

    private Optional<Long> metadataAsLong(RunnableConfig config, String key) {
        try {
            return config.metadata(key)
                    .map(value -> {
                    if (value instanceof Number number) {
                        return number.longValue();
                    }
                    if (value instanceof String stringValue && !stringValue.isBlank()) {
                        return Long.parseLong(stringValue);
                    }
                    return null;
                });
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<AssistantToolMode> metadataAsToolMode(RunnableConfig config, String key) {
        try {
            return config.metadata(key)
                    .map(Object::toString)
                    .filter(value -> !value.isBlank())
                    .map(AssistantToolMode::valueOf);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String extractCurrentQuestion(List<Message> previousMessages) {
        for (int index = previousMessages.size() - 1; index >= 0; index--) {
            Message message = previousMessages.get(index);
            if (message instanceof UserMessage) {
                UserMessage userMessage = (UserMessage) message;
                String text = userMessage.getText();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private String summarizeMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "[]";
        }
        List<String> summaries = new ArrayList<>();
        for (int index = 0; index < messages.size(); index++) {
            Message message = messages.get(index);
            if (message == null) {
                summaries.add(index + ":null");
                continue;
            }
            String type = message.getClass().getSimpleName();
            String text = message.getText();
            String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\n', ' ').trim();
            if (normalized.length() > 200) {
                normalized = normalized.substring(0, 200) + "...";
            }
            summaries.add(index + ":" + type + "[" + normalized + "]");
        }
        return summaries.toString();
    }
}
