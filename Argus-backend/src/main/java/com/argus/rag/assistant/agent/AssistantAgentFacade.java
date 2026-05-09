package com.argus.rag.assistant.agent;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.argus.rag.assistant.model.enums.AssistantToolMode;
import com.argus.rag.assistant.model.vo.chat.AssistantAgentResult;
import com.argus.rag.assistant.support.config.AssistantPromptContextBuilder;
import com.argus.rag.assistant.support.config.AssistantRunnableConfigFactory;
import com.argus.rag.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

/**
 * 助手 Agent 门面服务。
 * <p>封装 ReactAgent 的调用逻辑，提供同步聊天和流式聊天的统一入口。</p>
 * <p>负责构建运行时 instruction 和 RunnableConfig，处理 Agent 调用结果，
 * 并在调用失败时转换为业务异常抛出。</p>
 */
@Component
public class AssistantAgentFacade {

    private static final Logger log = LoggerFactory.getLogger(AssistantAgentFacade.class);

    private final AssistantReactAgentFactory assistantReactAgentFactory;
    private final AssistantRunnableConfigFactory assistantRunnableConfigFactory;
    private final AssistantPromptContextBuilder assistantPromptContextBuilder;

    public AssistantAgentFacade(
            AssistantReactAgentFactory assistantReactAgentFactory,
            AssistantRunnableConfigFactory assistantRunnableConfigFactory,
            AssistantPromptContextBuilder assistantPromptContextBuilder
    ) {
        this.assistantReactAgentFactory = assistantReactAgentFactory;
        this.assistantRunnableConfigFactory = assistantRunnableConfigFactory;
        this.assistantPromptContextBuilder = assistantPromptContextBuilder;
    }

    /**
     * 同步聊天。
     * <p>基于用户输入、会话上下文和工具模式构建 instruction，通过 ReactAgent
     * 同步执行对话，返回助手的文本回复和引用列表。</p>
     *
     * @param userId      当前用户 ID
     * @param sessionId   当前会话 ID
     * @param toolMode    工具模式（CHAT 仅对话 / KB_SEARCH 知识库检索）
     * @param groupId     知识库组 ID，CHAT 模式下可传 {@code null}
     * @param userMessage 用户输入的文本
     * @return 包含助手回复文本和引用列表的结果对象
     * @throws com.argus.rag.common.exception.BusinessException 当 Agent 调用失败或返回内容为空时
     */
    public AssistantAgentResult chat(
            Long userId,
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            String userMessage
    ) {
        // 这里把“仅对话模式”的运行时输入收口成两部分：
        // 1) instruction：系统提示词
        // 2) runnableConfig：session/user/toolMode 等 metadata，供 hooks 在 BEFORE_MODEL 阶段读取
        String instruction = assistantPromptContextBuilder.buildChatInstruction(
                userId,
                sessionId,
                toolMode,
                groupId
        );
        RunnableConfig runnableConfig = assistantRunnableConfigFactory.create(
                userId,
                sessionId,
                toolMode,
                groupId
        );
        // 当前虽然是“仅对话模式”，底层仍然使用 ReactAgent，只是 system prompt 已改成纯对话风格。
        AssistantKnowledgeBaseToolResultHolder resultHolder = new AssistantKnowledgeBaseToolResultHolder();
        ReactAgent agent = assistantReactAgentFactory.createAgent(instruction, toolMode, groupId, resultHolder);
        AssistantMessage assistantMessage;
        try {
            assistantMessage = agent.call(userMessage, runnableConfig);
        } catch (GraphRunnerException exception) {
            throw new BusinessException("助手调用失败", exception);
        }
        if (log.isDebugEnabled()) {
            log.debug(
                    "AssistantAgentFacade.chat result. userId={}, sessionId={}, toolMode={}, groupId={}, text={}, hasToolCalls={}, toolCallCount={}",
                    userId,
                    sessionId,
                    toolMode,
                    groupId,
                    assistantMessage == null ? null : abbreviate(assistantMessage.getText()),
                    assistantMessage != null && assistantMessage.hasToolCalls(),
                    assistantMessage == null || assistantMessage.getToolCalls() == null ? 0 : assistantMessage.getToolCalls().size()
            );
        }
        if (assistantMessage == null || assistantMessage.getText() == null || assistantMessage.getText().isBlank()) {
            throw new BusinessException("助手返回内容为空");
        }
        return new AssistantAgentResult(assistantMessage.getText(), resultHolder.currentCitations());
    }

    /**
     * 流式聊天。
     * <p>与 {@link #chat} 类似，但通过 ReactAgent 的流式接口执行对话，
     * 模型生成的文本增量会通过 {@code deltaConsumer} 逐段推送给调用方。</p>
     * <p>内部处理流式节点的输出拼接与去重，确保最终返回完整的助手回复。</p>
     *
     * @param userId        当前用户 ID
     * @param sessionId     当前会话 ID
     * @param toolMode      工具模式（CHAT 仅对话 / KB_SEARCH 知识库检索）
     * @param groupId       知识库组 ID，CHAT 模式下可传 {@code null}
     * @param userMessage   用户输入的文本
     * @param deltaConsumer 流式文本增量回调，每个增量片段都会调用此回调
     * @return 包含助手完整回复文本和引用列表的结果对象
     * @throws com.argus.rag.common.exception.BusinessException 当 Agent 调用失败或返回内容为空时
     */
    public AssistantAgentResult streamChat(
            Long userId,
            Long sessionId,
            AssistantToolMode toolMode,
            Long groupId,
            String userMessage,
            Consumer<String> deltaConsumer
    ) {
        String instruction = assistantPromptContextBuilder.buildChatInstruction(
                userId,
                sessionId,
                toolMode,
                groupId
        );
        RunnableConfig runnableConfig = assistantRunnableConfigFactory.create(
                userId,
                sessionId,
                toolMode,
                groupId
        );
        AssistantKnowledgeBaseToolResultHolder resultHolder = new AssistantKnowledgeBaseToolResultHolder();
        ReactAgent agent = assistantReactAgentFactory.createAgent(instruction, toolMode, groupId, resultHolder);
        StringBuilder finalReply = new StringBuilder();
        try {
            // stream() 返回的是图执行过程中的节点输出，这里只抽取模型实际产出的文本 delta。
            Flux<NodeOutput> stream = agent.stream(userMessage, runnableConfig);
            stream.doOnNext(output -> handleStreamingOutput(output, deltaConsumer, finalReply))
                    .blockLast();
        } catch (GraphRunnerException exception) {
            throw new BusinessException("助手调用失败", exception);
        }
        String reply = finalReply.toString().trim();
        if (log.isDebugEnabled()) {
            log.debug(
                    "AssistantAgentFacade.streamChat result. userId={}, sessionId={}, toolMode={}, groupId={}, reply={}, citationCount={}",
                    userId,
                    sessionId,
                    toolMode,
                    groupId,
                    abbreviate(reply),
                    resultHolder.currentCitations().size()
            );
        }
        if (reply.isBlank()) {
            throw new BusinessException("助手返回内容为空");
        }
        return new AssistantAgentResult(reply, resultHolder.currentCitations());
    }

    private void handleStreamingOutput(
            NodeOutput output,
            Consumer<String> deltaConsumer,
            StringBuilder finalReply
    ) {
        if (!(output instanceof StreamingOutput streamingOutput)) {
            return;
        }
        OutputType type = streamingOutput.getOutputType();
        Message message = streamingOutput.message();
        if (!(message instanceof AssistantMessage assistantMessage)) {
            return;
        }
        if (type == OutputType.AGENT_MODEL_STREAMING) {
            // 正常流式路径下，模型边生成边向前端透传文本。
            String delta = normalizeStreamingDelta(assistantMessage.getText(), finalReply.toString());
            if (delta.isBlank()) {
                return;
            }
            finalReply.append(delta);
            deltaConsumer.accept(delta);
            return;
        }
        if (type == OutputType.AGENT_MODEL_FINISHED
                && !assistantMessage.hasToolCalls()
                && finalReply.isEmpty()) {
            // 某些情况下模型不会走 streaming delta，而是一次性在 finished 节点返回完整文本，这里兜底补发。
            String fullText = assistantMessage.getText();
            if (fullText == null || fullText.isBlank()) {
                return;
            }
            finalReply.append(fullText);
            deltaConsumer.accept(fullText);
        }
    }

    private String normalizeStreamingDelta(String candidateText, String accumulatedReply) {
        if (candidateText == null || candidateText.isBlank()) {
            return "";
        }
        if (accumulatedReply.isEmpty()) {
            return candidateText;
        }
        // Spring AI Alibaba 的流式节点在某些模型下会反复返回“截至当前的全文”，
        // 这里需要把累计前缀裁掉，转换成真正的增量 delta，再交给前端做 append。
        if (candidateText.startsWith(accumulatedReply)) {
            return candidateText.substring(accumulatedReply.length());
        }
        return candidateText;
    }

    private String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replace("\r\n", "\n").replace('\n', ' ').trim();
        if (normalized.length() > 200) {
            return normalized.substring(0, 200) + "...";
        }
        return normalized;
    }
}
