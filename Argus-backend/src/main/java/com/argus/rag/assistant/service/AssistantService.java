package com.argus.rag.assistant.service;

import com.argus.rag.assistant.agent.AssistantAgentFacade;
import com.argus.rag.assistant.model.dto.chat.AssistantChatRequest;
import com.argus.rag.assistant.model.dto.message.AssistantMessageCreateDTO;
import com.argus.rag.assistant.model.enums.AssistantToolMode;
import com.argus.rag.assistant.model.vo.chat.AssistantAgentResult;
import com.argus.rag.assistant.model.vo.chat.AssistantChatResponse;
import com.argus.rag.assistant.model.vo.chat.AssistantChatStreamEvent;
import com.argus.rag.assistant.model.vo.message.AssistantMessageVO;
import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.groupmembership.service.GroupMembershipService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

/**
 * 助手核心服务。
 * <p>编排聊天流程，包括消息验证、用户消息持久化、Agent 调用、助手回复持久化等。</p>
 * <p>提供同步聊天和流式聊天两种模式，两种模式共享相同的业务流程编排。</p>
 */
@Service
public class AssistantService {

    private final AssistantConversationService assistantConversationService;
    private final AssistantAgentFacade assistantAgentFacade;
    private final GroupMembershipService groupMembershipService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public AssistantService(
            AssistantConversationService assistantConversationService,
            AssistantAgentFacade assistantAgentFacade,
            GroupMembershipService groupMembershipService,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper
    ) {
        this.assistantConversationService = assistantConversationService;
        this.assistantAgentFacade = assistantAgentFacade;
        this.groupMembershipService = groupMembershipService;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    /**
     * 同步聊天入口。
     * <p>按顺序执行：参数校验 → 保存用户消息 → 调用 Agent → 保存助手回复 → 返回完整响应。</p>
     *
     * @param request     HTTP 请求对象，用于身份认证和权限校验
     * @param chatRequest 聊天请求体，包含会话ID、消息内容、工具模式等
     * @return 聊天响应，包含助手回复内容和引用来源
     * @throws BusinessException 如果请求参数校验失败
     */
    @Transactional
    public AssistantChatResponse chat(HttpServletRequest request, AssistantChatRequest chatRequest) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        AssistantChatRequest safeRequest = requireChatRequest(chatRequest);

        // 仅对话模式先落用户消息，再调模型，这样后续 hook 可以基于最新会话状态重建上下文。
        saveUserMessage(currentUser.userId(), safeRequest);
        AssistantExecutionResult executionResult = executeAssistant(
                request,
                currentUser.userId(),
                safeRequest
        );
        AssistantMessageVO assistantMessage = saveAssistantMessage(
                currentUser.userId(),
                safeRequest,
                executionResult
        );
        return new AssistantChatResponse(
                safeRequest.sessionId(),
                assistantMessage.messageId(),
                assistantMessage.content(),
                safeRequest.toolMode(),
                safeRequest.groupId(),
                executionResult.citations()
        );
    }

    /**
     * 流式聊天入口（简化版本）。
     * <p>使用默认的 Agent 流式执行逻辑，将模型输出的 delta 逐段通过 eventEmitter 推送给前端。</p>
     *
     * @param request        HTTP 请求对象，用于身份认证和权限校验
     * @param chatRequest    聊天请求体
     * @param eventEmitter   流式事件发射器，用于推送 SSE 事件
     */
    @Transactional
    public void streamChat(
            HttpServletRequest request,
            AssistantChatRequest chatRequest,
            AssistantStreamEventEmitter eventEmitter
    ) {
        streamChat( chatRequest, eventEmitter, deltaEmitter ->
                assistantAgentFacade.streamChat(
                        currentUserService.requireBusinessUser().userId(),
                        chatRequest.sessionId(),
                        chatRequest.toolMode(),
                        chatRequest.groupId(),
                        chatRequest.message(),
                        deltaEmitter
                ));
    }

    /**
     * 流式聊天入口（完整版本）。
     * <p>按顺序执行：参数校验 → 保存用户消息 → 发送 start 事件 → 执行 Agent 流式调用 →
     * 流式过程中逐段发送 delta 事件 → 保存助手回复 → 发送 done 事件。</p>
     *
     * @param chatRequest    聊天请求体
     * @param eventEmitter   流式事件发射器
     * @param streamExecutor 流式执行器，定义具体的 Agent 流式调用逻辑
     */
    @Transactional
    public void streamChat(
            AssistantChatRequest chatRequest,
            AssistantStreamEventEmitter eventEmitter,
            ChatStreamExecutor streamExecutor
    ) {
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        AssistantChatRequest safeRequest = requireChatRequest(chatRequest);
        // 流式场景和同步场景共享同一条主链，只是把模型回复拆成 delta 逐步回传给前端。
        // 用户消息是先落库，再调模型。
        saveUserMessage(currentUser.userId(), safeRequest);

        // 告诉前端：流式回答开始了
        eventEmitter.emit(AssistantChatStreamEvent.start(
                safeRequest.sessionId(),
                safeRequest.toolMode(),
                safeRequest.groupId()
        ));


        AssistantExecutionResult executionResult = executeAssistantStreaming(
                currentUser.userId(),
                safeRequest,
                // 每当模型吐出一小段文本 delta, 包装为AssistantChatStreamEvent.delta()，再通过 eventEmitter.emit(...) 发给前端
                delta -> eventEmitter.emit(AssistantChatStreamEvent.delta(
                        safeRequest.sessionId(),
                        safeRequest.toolMode(),
                        safeRequest.groupId(),
                        delta
                )),
                streamExecutor
        );
        // 保存助手回复
        AssistantMessageVO assistantMessage = saveAssistantMessage(
                currentUser.userId(),
                safeRequest,
                executionResult
        );

        // 发送done事件，表示流式回答结束
        eventEmitter.emit(AssistantChatStreamEvent.done(
                safeRequest.sessionId(),
                safeRequest.toolMode(),
                safeRequest.groupId(),
                assistantMessage.messageId(),
                executionResult.reply(),
                executionResult.citations()
        ));
    }

    /**
     * 校验聊天请求参数的合法性。
     * <p>检查请求是否为 {@code null}，并根据工具模式校验 {@code groupId} 的传递规则：
     * {@code CHAT} 模式不允许传 {@code groupId}，{@code KB_SEARCH} 模式必须传 {@code groupId}。</p>
     *
     * @param chatRequest 聊天请求体
     * @return 校验通过后的聊天请求体
     * @throws BusinessException 如果参数校验失败
     */
    private AssistantChatRequest requireChatRequest(AssistantChatRequest chatRequest) {
        if (chatRequest == null) {
            throw new BusinessException("聊天请求不能为空");
        }
        if (chatRequest.toolMode() == AssistantToolMode.CHAT && chatRequest.groupId() != null) {
            throw new BusinessException("CHAT 模式不允许传 groupId");
        }
        if (chatRequest.toolMode() == AssistantToolMode.KB_SEARCH && chatRequest.groupId() == null) {
            throw new BusinessException("KB_SEARCH 模式必须传 groupId");
        }
        return chatRequest;
    }

    /**
     * 执行同步 Agent 调用。
     * <p>根据工具模式判断是否需要校验知识库可读权限，然后调用 {@link AssistantAgentFacade#chat}
     * 获取 Agent 回复结果，封装为 {@link AssistantExecutionResult}。</p>
     *
     * @param request    HTTP 请求对象，用于权限校验
     * @param userId     当前用户ID
     * @param safeRequest 已校验的聊天请求体
     * @return Agent 执行结果，包含回复文本、结构化数据和引用来源
     */
    private AssistantExecutionResult executeAssistant(
            HttpServletRequest request,
            Long userId,
            AssistantChatRequest safeRequest
    ) {
        requireKnowledgeBaseReadableIfNeeded( safeRequest);
        // CHAT 和 KB_SEARCH 都统一走 Agent。KB_SEARCH 模式下，Agent 会通过知识库 Tool 获取证据。
        AssistantAgentResult agentResult = assistantAgentFacade.chat(
                userId,
                safeRequest.sessionId(),
                safeRequest.toolMode(),
                safeRequest.groupId(),
                safeRequest.message()
        );
        return new AssistantExecutionResult(agentResult.reply(), null, agentResult.citations());
    }

    /**
     * 执行流式 Agent 调用。
     * <p>根据工具模式判断是否需要校验知识库可读权限，然后通过 {@link ChatStreamExecutor}
     * 执行 Agent 流式调用，模型输出的每一段 delta 回调给 {@code deltaConsumer}。</p>
     *
     * @param userId        当前用户ID
     * @param safeRequest   已校验的聊天请求体
     * @param deltaConsumer delta 文本片段消费回调
     * @param streamExecutor 流式执行器，封装 Agent 流式调用逻辑
     * @return Agent 执行结果，包含完整回复文本、结构化数据和引用来源
     */
    private AssistantExecutionResult executeAssistantStreaming(
            Long userId,
            AssistantChatRequest safeRequest,
            Consumer<String> deltaConsumer,
            ChatStreamExecutor streamExecutor
    ) {
        if (safeRequest.toolMode() == AssistantToolMode.CHAT) {
            // 仅对话流式模式下，delta 直接来自 AgentFacade.streamChat 的模型流输出。
            AssistantAgentResult agentResult = streamExecutor.execute(deltaConsumer);
            return new AssistantExecutionResult(agentResult.reply(), null, agentResult.citations());
        }
        requireKnowledgeBaseReadableIfNeeded(safeRequest);
        AssistantAgentResult agentResult = streamExecutor.execute(deltaConsumer);
        return new AssistantExecutionResult(agentResult.reply(), null, agentResult.citations());
    }

    /**
     * 保存用户消息到数据库。
     * <p>将聊天请求中的消息内容持久化为 {@code USER} 角色的消息记录。</p>
     *
     * @param userId      当前用户ID
     * @param safeRequest 已校验的聊天请求体
     */
    private void saveUserMessage(Long userId, AssistantChatRequest safeRequest) {
        assistantConversationService.saveUserMessage(
                userId,
                new AssistantMessageCreateDTO(
                        safeRequest.sessionId(),
                        safeRequest.toolMode(),
                        safeRequest.groupId(),
                        safeRequest.message(),
                        null
                )
        );
    }

    /**
     * 保存助手回复消息到数据库。
     * <p>将 Agent 执行结果中的回复文本和结构化数据持久化为 {@code ASSISTANT} 角色的消息记录。</p>
     *
     * @param userId          当前用户ID
     * @param safeRequest     已校验的聊天请求体
     * @param executionResult Agent 执行结果
     * @return 保存后的助手消息视图对象
     */
    private AssistantMessageVO saveAssistantMessage(
            Long userId,
            AssistantChatRequest safeRequest,
            AssistantExecutionResult executionResult
    ) {
        return assistantConversationService.saveAssistantMessage(
                userId,
                new AssistantMessageCreateDTO(
                        safeRequest.sessionId(),
                        safeRequest.toolMode(),
                        safeRequest.groupId(),
                        executionResult.reply(),
                        executionResult.structuredPayload()
                )
        );
    }

    /**
     * 按需校验知识库可读权限。
     * <p>当工具模式为 {@code KB_SEARCH} 时，校验当前用户对目标知识库组的可读权限。</p>
     *
     * @param safeRequest 已校验的聊天请求体
     */
    private void requireKnowledgeBaseReadableIfNeeded( AssistantChatRequest safeRequest) {
        if (safeRequest.toolMode() == AssistantToolMode.KB_SEARCH) {
            groupMembershipService.requireGroupReadable( safeRequest.groupId());
        }
    }

    /**
     * Agent 执行结果内部记录。
     * <p>封装 Agent 调用返回的完整回复文本、结构化负载和引用来源列表。</p>
     *
     * @param reply             Agent 生成的完整回复文本
     * @param structuredPayload 结构化负载（如 JSON），可能为 {@code null}
     * @param citations         引用来源列表，可能为 {@code null}
     */
    private record AssistantExecutionResult(
            String reply,
            String structuredPayload,
            List<com.argus.rag.qa.model.vo.AskQuestionResponse.Citation> citations
    ) {
    }

    /**
     * 聊天流式执行器函数式接口。
     * <p>封装 Agent 流式调用的具体逻辑，将模型输出的 delta 文本通过 {@code deltaConsumer} 逐段回调。</p>
     */
    @FunctionalInterface
    public interface ChatStreamExecutor {

        /**
         * 执行流式 Agent 调用。
         *
         * @param deltaConsumer 用于接收模型输出的每一段 delta 文本的消费回调
         * @return Agent 调用结果，包含完整回复文本和引用来源
         */
        AssistantAgentResult execute(Consumer<String> deltaConsumer);
    }
}
