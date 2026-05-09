package com.argus.rag.qa.controller;

import com.argus.rag.qa.model.dto.AskQuestionRequest;
import com.argus.rag.qa.model.vo.AskQuestionResponse;
import com.argus.rag.qa.service.QaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识问答控制器。
 * <p>
 * 提供基于 RAG（检索增强生成）的知识库问答 API。
 * 用户在指定群组的知识库范围内提问，系统检索相关文档并由大模型生成回答。
 * </p>
 */
@RestController
@RequestMapping("/api/qa")
public class QaController {

    private final QaService qaService;

    /**
     * 构造函数。
     *
     * @param qaService 知识问答服务
     */
    public QaController(QaService qaService) {
        this.qaService = qaService;
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
            HttpServletRequest request
    ) {
        return qaService.ask(request, askQuestionRequest);
    }
}
