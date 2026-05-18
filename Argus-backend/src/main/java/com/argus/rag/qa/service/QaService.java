package com.argus.rag.qa.service;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.group.service.GroupMembershipService;
import com.argus.rag.metrics.LlmEndpoint;
import com.argus.rag.metrics.LlmModule;
import com.argus.rag.metrics.collector.LlmUsageCollector;
import com.argus.rag.metrics.cost.LlmCostCalculator;
import com.argus.rag.metrics.model.dto.LlmUsageRecordDTO;
import com.argus.rag.qa.model.dto.AskQuestionRequest;
import com.argus.rag.qa.model.vo.AskQuestionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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

    private final GroupMembershipService groupMembershipService;
    private final QaChatService qaChatService;
    private final CurrentUserService currentUserService;
    private final LlmUsageCollector llmUsageCollector;
    private final LlmCostCalculator llmCostCalculator;

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
            LlmCostCalculator llmCostCalculator) {
        this.groupMembershipService = groupMembershipService;
        this.qaChatService = qaChatService;
        this.currentUserService = currentUserService;
        this.llmUsageCollector = llmUsageCollector;
        this.llmCostCalculator = llmCostCalculator;
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

        recordUsage(userId, groupId, LlmEndpoint.QA_ASK, result.usage(), true, null);

        return result.response();
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

        return qaChatService.askStream(groupId, askQuestionRequest.getQuestion(), usage -> {
            recordUsage(userId, groupId, LlmEndpoint.QA_STREAM_ASK, usage, true, null);
        });
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
