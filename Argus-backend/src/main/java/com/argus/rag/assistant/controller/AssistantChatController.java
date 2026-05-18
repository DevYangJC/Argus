package com.argus.rag.assistant.controller;

import com.argus.rag.assistant.model.dto.chat.AssistantChatRequest;
import com.argus.rag.assistant.model.vo.chat.AssistantChatResponse;
import com.argus.rag.assistant.model.vo.chat.AssistantChatStreamEvent;
import com.argus.rag.assistant.service.AssistantService;
import com.argus.rag.common.api.ApiResponse;
import com.argus.rag.common.log.OperationLog;
import com.argus.rag.common.security.AuthenticatedUser;
import com.argus.rag.common.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 助手聊天控制器。
 * <p>提供同步聊天和流式聊天（SSE）的 RESTful 接口。</p>
 * <ul>
 *   <li>POST /api/assistant/chat - 同步聊天，一次请求完成对话</li>
 *   <li>POST /api/assistant/chat/stream - 流式聊天，通过 SSE 逐段推送回复内容</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantChatController {

    private static final long SSE_TIMEOUT_MILLIS = 0L;

    private final AssistantService assistantService;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public AssistantChatController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    /**
     * 同步聊天。
     * <p>接收用户消息，同步调用助手服务完成对话，一次性返回完整的回复和引用列表。</p>
     *
     * @param requestBody 聊天请求体，包含会话 ID、工具模式、知识库组 ID 和用户消息
     * @param request     HTTP 请求对象，用于身份认证
     * @return 包含助手回复内容和引用列表的响应
     */
    @PostMapping("/chat")
    @OperationLog
    public ApiResponse<AssistantChatResponse> chat(
            @Valid @RequestBody AssistantChatRequest requestBody,
            HttpServletRequest request
    ) {
        return ApiResponse.success(assistantService.chat(request, requestBody));
    }

    /**
     * 流式聊天（SSE）。
     * <p>建立 SSE 连接，通过助手服务的流式接口逐段推送模型生成的文本增量。
     * 客户端断开后自动停止推送，异常时发送错误事件并关闭连接。</p>
     *
     * @param requestBody 聊天请求体，包含会话 ID、工具模式、知识库组 ID 和用户消息
     * @param request     HTTP 请求对象，用于身份认证
     * @return SSE 发射器，用于向客户端发送流式事件
     */
    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @OperationLog
    public SseEmitter streamChat(
            @Valid @RequestBody AssistantChatRequest requestBody,
            HttpServletRequest request
    ) {
        // SSE 工作线程池与请求线程是不同线程，UserContext 作为 ThreadLocal
        // 不会自动传递。此处在请求线程里先抓取已认证用户，交给工作线程重建。
        AuthenticatedUser authenticatedUser = UserContext.get();
        // 流式对话入口只负责建立 SSE 通道，并把具体编排下沉到 AssistantService。
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));
        sseExecutor.execute(() -> {
            try {
                if (authenticatedUser != null) {
                    UserContext.set(authenticatedUser);
                }
                assistantService.streamChat(request, requestBody, event -> {
                    try {
                        sendEvent(emitter, event, closed);
                    } catch (IOException exception) {
                        throw new IllegalStateException("发送 SSE 事件失败", exception);
                    }
                });
                completeEmitter(emitter, closed);
            } catch (Exception exception) {
                try {
                    sendEvent(emitter, AssistantChatStreamEvent.error(
                            requestBody.sessionId(),
                            requestBody.toolMode(),
                            requestBody.groupId(),
                            exception.getMessage()
                    ), closed);
                    completeEmitter(emitter, closed);
                } catch (IOException ioException) {
                    completeEmitterWithError(emitter, closed, ioException);
                }
            } finally {
                UserContext.clear();
            }
        });
        return emitter;
    }

    private void sendEvent(
            SseEmitter emitter,
            AssistantChatStreamEvent event,
            AtomicBoolean closed
    ) throws IOException {
        // 客户端断开后不再继续写事件，避免把模型侧异常放大成二次 SSE 错误。
        if (closed.get()) {
            return;
        }
        emitter.send(SseEmitter.event()
                .name(event.event())
                .data(event));
    }

    private void completeEmitter(SseEmitter emitter, AtomicBoolean closed) {
        if (closed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    private void completeEmitterWithError(
            SseEmitter emitter,
            AtomicBoolean closed,
            Exception exception
    ) {
        if (closed.compareAndSet(false, true)) {
            emitter.completeWithError(exception);
        }
    }

    /**
     * 销毁回调。
     * <p>在 Bean 销毁前关闭 SSE 线程池，释放线程资源。</p>
     */
    @PreDestroy
    public void destroy() {
        sseExecutor.shutdown();
    }
}
