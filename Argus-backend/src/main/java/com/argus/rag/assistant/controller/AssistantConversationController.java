package com.argus.rag.assistant.controller;

import com.argus.rag.assistant.model.vo.conversation.AssistantConversationContextVO;
import com.argus.rag.assistant.service.AssistantConversationService;
import com.argus.rag.common.log.OperationLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 助手会话上下文控制器。
 * <p>提供会话上下文的查询接口，用于获取指定会话的摘要信息和最近消息列表。</p>
 * <ul>
 *   <li>GET /api/assistant/sessions/{sessionId}/context - 获取会话上下文</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/assistant/sessions")
@OperationLog
public class AssistantConversationController {

    private final AssistantConversationService assistantConversationService;

    public AssistantConversationController(AssistantConversationService assistantConversationService) {
        this.assistantConversationService = assistantConversationService;
    }

    /**
     * 获取指定会话的上下文信息。
     * <p>返回会话的摘要文本和最近的消息列表，用于前端恢复会话上下文或展示会话概览。</p>
     *
     * @param sessionId   会话ID，路径参数
     * @param recentLimit 最近消息条数上限，默认值为 12
     * @return 会话上下文 VO，包含摘要文本和最近消息列表
     */
    @GetMapping("/{sessionId}/context")
    public AssistantConversationContextVO getConversationContext(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "12") int recentLimit
    ) {
        return assistantConversationService.getConversationContext(sessionId, recentLimit);
    }
}
