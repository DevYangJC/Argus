package com.argus.rag.qa.controller;

import com.argus.rag.common.log.OperationLog;
import com.argus.rag.qa.model.dto.AskQuestionRequest;
import com.argus.rag.qa.model.vo.AskQuestionResponse;
import com.argus.rag.qa.service.QaChatService;
import com.argus.rag.qa.service.QaService;
import com.argus.rag.qa.support.CitationAssembler;
import com.argus.rag.qa.support.EvidenceOverviewAssembler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 知识问答控制器。
 * <p>
 * 提供基于 RAG（检索增强生成）的知识库问答 API。
 * 用户在指定群组的知识库范围内提问，系统检索相关文档并由大模型生成回答。
 * </p>
 */
@RestController
@RequestMapping("/api/qa")
@OperationLog
public class QaController {

    private static final Logger log = LoggerFactory.getLogger(QaController.class);

    /** SSE 连接超时时间（毫秒），默认 5 分钟 */
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final QaService qaService;
    private final CitationAssembler citationAssembler;
    private final EvidenceOverviewAssembler evidenceOverviewAssembler;

    /**
     * 构造函数。
     *
     * @param qaService         知识问答服务
     * @param citationAssembler 引用组装器，用于流式回答完成后组装引用来源
     */
    public QaController(
            QaService qaService,
            CitationAssembler citationAssembler,
            EvidenceOverviewAssembler evidenceOverviewAssembler) {
        this.qaService = qaService;
        this.citationAssembler = citationAssembler;
        this.evidenceOverviewAssembler = evidenceOverviewAssembler;
    }

    /**
     * 提问接口：在指定群组的知识库中检索并回答用户问题。
     * <p>
     * 流程：权限校验 → 查询规划 → 混合检索 → 证据评估 → 大模型生成回答 → 引用组装。
     * </p>
     *
     * @param askQuestionRequest 问答请求，包含群组 ID 和问题文本
     * @param request            HTTP 请求对象，用于提取当前用户身份信息
     * @return 问答响应，包含回答内容或拒答原因及引用来源
     */
    @PostMapping("/ask")
    public AskQuestionResponse askQuestion(
            @Valid @RequestBody AskQuestionRequest askQuestionRequest,
            HttpServletRequest request) {
        return qaService.ask(request, askQuestionRequest);
    }

    /**
     * 流式提问接口：使用 SSE（Server-Sent Events）逐 token 推送大模型回答。
     * <p>
     * 与 {@link #askQuestion(AskQuestionRequest, HttpServletRequest)} 流程一致：<br>
     * 权限校验 → 查询规划 → 混合检索 → 证据评估 → 大模型流式生成回答 → 引用组装。
     * 区别在于大模型生成的回答通过 SSE 以 token 为单位实时推送给客户端。
     * </p>
     *
     * <h3>SSE 事件类型</h3>
     * <ul>
     * <li><b>token</b> — 大模型生成的文本片段，客户端应拼接所有 token 得到完整回答</li>
     * <li><b>citations</b> — 引用来源列表，在流式回答结束后发送，数据为
     * {@link AskQuestionResponse.Citation} 数组</li>
     * <li><b>error</b> — 错误事件，包含 {@code message} 字段描述错误原因</li>
     * </ul>
     *
     * <h3>连接生命周期</h3>
     * <ul>
     * <li>超时时间：5 分钟，超时后自动关闭连接</li>
     * <li>客户端断开连接时，后台流自动取消</li>
     * <li>异常发生时发送 {@code error} 事件后正常关闭连接</li>
     * </ul>
     *
     * @param askQuestionRequest 问答请求，包含群组 ID 和问题文本
     * @param request            HTTP 请求对象，用于提取当前用户身份信息
     * @return SseEmitter 实例，用于推送 SSE 事件
     */
    @PostMapping("/stream-ask")
    public SseEmitter streamAsk(
            @Valid @RequestBody AskQuestionRequest askQuestionRequest,
            HttpServletRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 注册连接回调：客户端断开时取消 Flux 订阅
        emitter.onCompletion(() -> log.debug("SSE 连接正常完成"));
        emitter.onTimeout(() -> log.warn("SSE 连接超时"));
        emitter.onError(error -> log.error("SSE 连接异常", error));

        try {
            QaChatService.StreamContext streamContext = qaService.askStream(request, askQuestionRequest);

            streamContext.tokenStream()
                    .doOnNext(token -> {
                        try {
                            emitter.send(SseEmitter.event().name("token").data(token));
                        } catch (IOException e) {
                            throw new RuntimeException("SSE 发送 token 失败", e);
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            List<AskQuestionResponse.Citation> citations = citationAssembler
                                    .assembleDocuments(streamContext.documents());
                            if (!citations.isEmpty()) {
                                emitter.send(SseEmitter.event().name("citations").data(citations));
                            }
                            AskQuestionResponse.EvidenceOverview evidenceOverview = evidenceOverviewAssembler
                                    .assemble(streamContext.documents());
                            if (evidenceOverview != null) {
                                emitter.send(SseEmitter.event().name("evidence-overview").data(evidenceOverview));
                            }
                            Long recordId = streamContext.recordId();
                            if (recordId != null) {
                                emitter.send(SseEmitter.event().name("record").data(Map.of("recordId", recordId)));
                            }
                            emitter.complete();
                        } catch (IOException e) {
                            log.error("SSE 发送 citations 失败", e);
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(error -> {
                        log.error("流式问答 token 流异常", error);
                        try {
                            String message = error.getMessage() != null
                                    ? error.getMessage()
                                    : "流式问答服务内部错误";
                            emitter.send(SseEmitter.event().name("error")
                                    .data(Map.of("message", message)));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .subscribe();
        } catch (Exception e) {
            // 捕获同步阶段的异常（如权限校验失败）
            log.error("流式问答初始化失败", e);
            try {
                String message = e.getMessage() != null ? e.getMessage() : "请求处理失败";
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("message", message)));
                emitter.complete();
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        }

        return emitter;
    }
}
