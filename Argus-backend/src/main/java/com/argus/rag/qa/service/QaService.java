package com.argus.rag.qa.service;

import com.argus.rag.group.service.GroupMembershipService;
import com.argus.rag.qa.model.dto.AskQuestionRequest;
import com.argus.rag.qa.model.vo.AskQuestionResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 知识问答入口服务。
 * <p>
 * 负责协调权限校验和问答流程：
 * 先校验用户对目标群组的读取权限，再委托 {@link QaChatService} 执行实际的检索和回答生成。
 * </p>
 */
@Service
public class QaService {

    private final GroupMembershipService groupMembershipService;
    private final QaChatService qaChatService;

    /**
     * 构造函数。
     *
     * @param groupMembershipService 群组成员关系服务，用于校验用户权限
     * @param qaChatService          问答对话服务，执行实际的检索和大模型问答
     */
    public QaService(
            GroupMembershipService groupMembershipService,
            QaChatService qaChatService) {
        this.groupMembershipService = groupMembershipService;
        this.qaChatService = qaChatService;
    }

    /**
     * 处理用户提问请求。
     * <p>
     * 1. 校验当前用户对目标群组的读取权限（非成员将抛出异常）。<br>
     * 2. 委托 {@link QaChatService} 执行检索和回答生成。
     * </p>
     *
     * @param request            HTTP 请求对象，用于提取用户身份
     * @param askQuestionRequest 问答请求 DTO
     * @return 问答响应
     */
    public AskQuestionResponse ask(HttpServletRequest request, AskQuestionRequest askQuestionRequest) {
        Long groupId = askQuestionRequest.getGroupId();
        groupMembershipService.requireGroupReadable(groupId);
        return qaChatService.ask(groupId, askQuestionRequest.getQuestion());
    }

    /**
     * 处理流式用户提问请求。
     * <p>
     * 1. 校验当前用户对目标群组的读取权限（非成员将抛出异常）。<br>
     * 2. 委托 {@link QaChatService#askStream(Long, String)} 执行流式检索和回答生成。
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
        return qaChatService.askStream(groupId, askQuestionRequest.getQuestion());
    }
}
