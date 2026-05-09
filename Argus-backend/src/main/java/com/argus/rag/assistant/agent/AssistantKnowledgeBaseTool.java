package com.argus.rag.assistant.agent;

import com.argus.rag.assistant.model.vo.tool.KnowledgeBaseSearchToolResponse;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.qa.model.vo.AskQuestionResponse;
import com.argus.rag.qa.rag.ReadyChunkDocumentRetriever;
import com.argus.rag.qa.rag.RetrievedEvidenceBundle;
import com.argus.rag.qa.support.CitationAssembler;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 知识库检索工具。
 * <p>作为 Spring AI Tool 暴露给 Agent，用于在 KB_SEARCH 模式下执行知识库检索。</p>
 * <p>工具名称：{@value #TOOL_NAME}，功能：根据用户查询检索知识库组中的相关文档片段。</p>
 */
@Component
public class AssistantKnowledgeBaseTool {

    public static final String TOOL_NAME = "knowledgeBaseSearch";
    public static final String GROUP_ID_CONTEXT_KEY = "groupId";
    public static final String RESULT_HOLDER_CONTEXT_KEY = "knowledgeBaseToolResultHolder";

    private static final String INSUFFICIENT_CODE = "INSUFFICIENT_EVIDENCE";
    private static final String INSUFFICIENT_MESSAGE = "检索到的有效证据不足，暂不回答。";

    private final ReadyChunkDocumentRetriever readyChunkDocumentRetriever;
    private final CitationAssembler citationAssembler;

    public AssistantKnowledgeBaseTool(
            ReadyChunkDocumentRetriever readyChunkDocumentRetriever,
            CitationAssembler citationAssembler
    ) {
        this.readyChunkDocumentRetriever = readyChunkDocumentRetriever;
        this.citationAssembler = citationAssembler;
    }

    /**
     * 执行知识库检索。
     * <p>作为 Spring AI Tool 暴露给 ReactAgent，Agent 会在需要检索外部知识时自动调用。
     * 每次会话仅允许一次有效检索，重复调用会返回提示信息要求 Agent 基于已有证据作答。</p>
     * <p>检索结果通过 {@link AssistantKnowledgeBaseToolResultHolder} 记录引用列表，
     * 供 {@link AssistantAgentFacade} 在构建最终响应时使用。</p>
     *
     * @param query       用于检索知识库的查询文本，通常为用户的当前问题
     * @param toolContext Spring AI 工具上下文，包含 groupId 和结果持有器
     * @return 知识库检索结果，包含成功标识、证据列表和引用列表
     */
    @Tool(
            name = TOOL_NAME,
            description = "在当前已选择的知识库组内检索相关证据片段。只返回证据，不生成最终答案。"
    )
    public KnowledgeBaseSearchToolResponse search(
            @ToolParam(description = "用于检索知识库的查询文本，通常使用用户当前问题。") String query,
            ToolContext toolContext
    ) {
        Long groupId = readGroupId(toolContext);
        AssistantKnowledgeBaseToolResultHolder resultHolder = readResultHolder(toolContext);
        if (resultHolder.hasCompletedSearch()) {
            return new KnowledgeBaseSearchToolResponse(
                    false,
                    "DUPLICATE_TOOL_CALL",
                    "本轮已经完成过一次知识库检索，请基于上一条工具返回的 evidences 直接给出最终回答，不要再次调用工具。",
                    null,
                    "本轮知识库检索结果已经返回，必须停止继续调用工具并生成最终回答。",
                    List.of(),
                    resultHolder.currentCitations()
            );
        }
        String safeQuery = requireQuery(query);
        RetrievedEvidenceBundle evidenceBundle = readyChunkDocumentRetriever.retrieveEvidence(groupId, safeQuery);
        List<Document> documents = evidenceBundle.documents();
        if (documents == null || documents.isEmpty()) {
            resultHolder.recordCitations(List.of());
            return new KnowledgeBaseSearchToolResponse(
                    false,
                    INSUFFICIENT_CODE,
                    INSUFFICIENT_MESSAGE,
                    evidenceBundle.evidenceLevel() == null ? null : evidenceBundle.evidenceLevel().name(),
                    evidenceBundle.evidenceGuidance(),
                    List.of(),
                    List.of()
            );
        }
        List<AskQuestionResponse.Citation> citations = citationAssembler.assembleDocuments(documents);
        resultHolder.recordCitations(citations);
        return new KnowledgeBaseSearchToolResponse(
                true,
                null,
                null,
                evidenceBundle.evidenceLevel() == null ? null : evidenceBundle.evidenceLevel().name(),
                evidenceBundle.evidenceGuidance(),
                documents.stream().map(this::toEvidence).toList(),
                citations
        );
    }

    private String requireQuery(String query) {
        if (!StringUtils.hasText(query)) {
            throw new BusinessException("知识库检索 query 不能为空");
        }
        return query.trim();
    }

    private Long readGroupId(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            throw new BusinessException("知识库检索缺少工具上下文");
        }
        Object value = toolContext.getContext().get(GROUP_ID_CONTEXT_KEY);
        if (value instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        if (value instanceof String stringValue && StringUtils.hasText(stringValue)) {
            try {
                long parsed = Long.parseLong(stringValue);
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new BusinessException("知识库检索缺少 groupId");
    }

    private AssistantKnowledgeBaseToolResultHolder readResultHolder(ToolContext toolContext) {
        Object value = toolContext.getContext().get(RESULT_HOLDER_CONTEXT_KEY);
        if (value instanceof AssistantKnowledgeBaseToolResultHolder holder) {
            return holder;
        }
        throw new BusinessException("知识库检索缺少结果上下文");
    }

    private KnowledgeBaseSearchToolResponse.Evidence toEvidence(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new KnowledgeBaseSearchToolResponse.Evidence(
                readLong(metadata, "documentId"),
                readLong(metadata, "chunkId"),
                readInteger(metadata, "chunkIndex"),
                readText(metadata, "fileName"),
                readScore(metadata),
                document.getText()
        );
    }

    private Long readLong(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer readInteger(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private double readScore(Map<String, Object> metadata) {
        Object value = metadata.get("score");
        return value instanceof Number number ? number.doubleValue() : 0D;
    }

    private String readText(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value instanceof String text ? text : null;
    }
}
