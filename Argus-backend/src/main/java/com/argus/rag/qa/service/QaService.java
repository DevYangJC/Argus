package com.argus.rag.qa.service;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.group.service.GroupMembershipService;
import com.argus.rag.metrics.LlmEndpoint;
import com.argus.rag.metrics.LlmModule;
import com.argus.rag.metrics.collector.LlmUsageCollector;
import com.argus.rag.metrics.cost.LlmCostCalculator;
import com.argus.rag.metrics.model.dto.LlmUsageRecordDTO;
import com.argus.rag.qa.model.EvidenceLevel;
import com.argus.rag.qa.model.dto.AskQuestionRequest;
import com.argus.rag.qa.model.vo.AskQuestionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 知识问答入口服务。
 * <p>
 * 负责协调权限校验和问答流程：
 * 先校验用户对目标群组的读取权限，再委托 {@link QaChatService} 执行实际的检索和回答生成，
 * 并在调用完成后记录 LLM 用量。
 * </p>
 */
@Service
public class QaService {

    private static final Logger log = LoggerFactory.getLogger(QaService.class);
    private static final String MODEL_NAME = "qwen-plus";
    private static final String INSUFFICIENT_EVIDENCE_CODE = "INSUFFICIENT_EVIDENCE";
    private static final String SYSTEM_ERROR_CODE = "SYSTEM_ERROR";

    private final GroupMembershipService groupMembershipService;
    private final QaChatService qaChatService;
    private final CurrentUserService currentUserService;
    private final LlmUsageCollector llmUsageCollector;
    private final LlmCostCalculator llmCostCalculator;
    private final QaRecordPersistenceService qaRecordPersistenceService;

    /**
     * 构造函数。
     *
     * @param groupMembershipService 群组成员关系服务，用于校验用户权限
     * @param qaChatService          问答对话服务，执行实际的检索和大模型问答
     * @param currentUserService     当前用户服务，用于获取当前登录用户
     * @param llmUsageCollector      LLM 用量采集器
     * @param llmCostCalculator      LLM 费用计算器
     */
    public QaService(
            GroupMembershipService groupMembershipService,
            QaChatService qaChatService,
            CurrentUserService currentUserService,
            LlmUsageCollector llmUsageCollector,
            LlmCostCalculator llmCostCalculator,
            QaRecordPersistenceService qaRecordPersistenceService) {
        this.groupMembershipService = groupMembershipService;
        this.qaChatService = qaChatService;
        this.currentUserService = currentUserService;
        this.llmUsageCollector = llmUsageCollector;
        this.llmCostCalculator = llmCostCalculator;
        this.qaRecordPersistenceService = qaRecordPersistenceService;
    }

    /**
     * 处理用户提问请求。
     * <p>
     * 1. 校验当前用户对目标群组的读取权限（非成员将抛出异常）。<br>
     * 2. 委托 {@link QaChatService} 执行检索和回答生成。<br>
     * 3. 记录 LLM 用量。
     * </p>
     *
     * @param request            HTTP 请求对象，用于提取用户身份
     * @param askQuestionRequest 问答请求 DTO
     * @return 问答响应
     */
    public AskQuestionResponse ask(HttpServletRequest request, AskQuestionRequest askQuestionRequest) {
        Long groupId = askQuestionRequest.getGroupId();
        groupMembershipService.requireGroupReadable(groupId);
        Long userId = currentUserService.getRequiredCurrentUser().userId();

        QaChatService.AskResult result = qaChatService.askWithUsage(groupId, askQuestionRequest.getQuestion());

        Long recordId = saveQaRecord(
                userId,
                groupId,
                askQuestionRequest.getQuestion(),
                LlmEndpoint.QA_ASK,
                result.response(),
                result.evidenceLevel(),
                result.usage(),
                result.documents(),
                true,
                null
        );
        recordUsage(userId, groupId, LlmEndpoint.QA_ASK, result.usage(), true, null);

        return result.response().withRecordId(recordId);
    }

    /** 统一保存同步和流式 QA 记录，隐藏持久化服务的命令对象组装细节。 */
    private Long saveQaRecord(Long userId, Long groupId, String question, String endpoint,
                              AskQuestionResponse response,
                              com.argus.rag.qa.model.EvidenceLevel evidenceLevel,
                              QaChatService.UsageInfo usage,
                              List<Document> documents,
                              boolean success,
                              String errorMessage) {
        return qaRecordPersistenceService.saveCompleted(new QaRecordPersistenceService.SaveCommand(
                userId,
                groupId,
                question,
                endpoint,
                MODEL_NAME,
                response,
                evidenceLevel,
                usage,
                documents == null ? List.of() : documents,
                success,
                errorMessage
        ));
    }

    /**
     * 处理流式用户提问请求。
     * <p>
     * 1. 校验当前用户对目标群组的读取权限（非成员将抛出异常）。<br>
     * 2. 委托 {@link QaChatService#askStream(Long, String, java.util.function.Consumer)} 执行流式检索和回答生成。<br>
     * 3. 在流完成后记录 LLM 用量。
     * </p>
     * <p>
     * 返回的 {@link QaChatService.StreamContext} 包含：<br>
     * {@code tokenStream} — 大模型逐 token 输出的文本流；<br>
     * {@code documents} — 检索到的证据文档，供 SSE 调用方在流结束后组装引用来源。
     * </p>
     *
     * @param request            HTTP 请求对象，用于提取用户身份
     * @param askQuestionRequest 问答请求 DTO
     * @return 流式问答上下文
     */
    public QaChatService.StreamContext askStream(HttpServletRequest request, AskQuestionRequest askQuestionRequest) {
        Long groupId = askQuestionRequest.getGroupId();
        groupMembershipService.requireGroupReadable(groupId);
        Long userId = currentUserService.getRequiredCurrentUser().userId();

        AtomicReference<QaChatService.UsageInfo> usageRef = new AtomicReference<>(
                new QaChatService.UsageInfo(0, 0, 0, false, 0L));
        QaChatService.StreamContext rawContext = qaChatService.askStream(groupId, askQuestionRequest.getQuestion(), usage -> {
            usageRef.set(usage);
            recordUsage(userId, groupId, LlmEndpoint.QA_STREAM_ASK, usage, true, null);
        });
        StringBuilder answerBuilder = new StringBuilder();
        AtomicReference<Long> recordIdRef = new AtomicReference<>();
        // 包装原始 token 流：边推送边收集完整回答，结束后再落 QA 记录。
        Flux<String> persistedTokenStream = rawContext.tokenStream()
                .doOnNext(answerBuilder::append)
                .doOnComplete(() -> {
                    AskQuestionResponse response = AskQuestionResponse.answered(
                            answerBuilder.toString(),
                            List.of());
                    Long recordId = saveQaRecord(
                            userId,
                            groupId,
                            askQuestionRequest.getQuestion(),
                            LlmEndpoint.QA_STREAM_ASK,
                            response,
                            rawContext.evidenceLevel(),
                            usageRef.get(),
                            rawContext.documents(),
                            true,
                            null
                    );
                    recordIdRef.set(recordId);
                })
                .doOnError(error -> {
                    // 无证据属于业务拒答，其它异常记录为系统失败。
                    boolean insufficientEvidence = error instanceof BusinessException
                            && error.getMessage() != null
                            && error.getMessage().startsWith(INSUFFICIENT_EVIDENCE_CODE);
                    String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
                    AskQuestionResponse response = insufficientEvidence
                            ? AskQuestionResponse.unanswered(INSUFFICIENT_EVIDENCE_CODE, message, List.of())
                            : AskQuestionResponse.unanswered(SYSTEM_ERROR_CODE, message, List.of());
                    Long recordId = saveQaRecord(
                            userId,
                            groupId,
                            askQuestionRequest.getQuestion(),
                            LlmEndpoint.QA_STREAM_ASK,
                            response,
                            insufficientEvidence ? EvidenceLevel.NONE : rawContext.evidenceLevel(),
                            usageRef.get(),
                            rawContext.documents(),
                            insufficientEvidence,
                            insufficientEvidence ? null : message
                    );
                    recordIdRef.set(recordId);
                });
        return new QaChatService.StreamContext(
                persistedTokenStream,
                rawContext.documents(),
                rawContext.evidenceLevel(),
                recordIdRef);
    }

    private void recordUsage(Long userId, Long groupId, String endpoint,
                             QaChatService.UsageInfo usage, boolean success, String errorMessage) {
        try {
            BigDecimal cost = llmCostCalculator.calculate(MODEL_NAME, usage.promptTokens(), usage.completionTokens());
            LlmUsageRecordDTO dto = LlmUsageRecordDTO.builder()
                    .userId(userId)
                    .groupId(groupId)
                    .module(LlmModule.QA)
                    .endpoint(endpoint)
                    .promptTokens(usage.promptTokens())
                    .completionTokens(usage.completionTokens())
                    .totalTokens(usage.totalTokens())
                    .isEstimated(usage.estimated())
                    .costAmount(cost)
                    .latencyMs(usage.latencyMs())
                    .success(success)
                    .errorMessage(errorMessage)
                    .modelName(MODEL_NAME)
                    .build();
            llmUsageCollector.record(dto);
        } catch (Exception e) {
            log.warn("QA用量记录失败: userId={}, groupId={}, endpoint={}", userId, groupId, endpoint, e);
        }
    }
}
