package com.argus.rag.qa.rag;

import com.argus.rag.common.exception.BusinessException;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 文档检索器，实现 Spring AI 的 {@link DocumentRetriever} 接口。
 * <p>
 * 作为 RAG 检索增强顾问的文档来源，委托 {@link HybridChunkRetrievalService} 执行实际检索。
 * 支持预取文档优化：若上下文中已包含预取的文档列表，则直接使用而不重复检索。
 * </p>
 */
@Component
public class ReadyChunkDocumentRetriever implements DocumentRetriever {

    /** 默认返回的检索结果数量 */
    private static final int DEFAULT_TOP_K = 5;
    /** 上下文中群组 ID 的键名 */
    private static final String GROUP_ID_CONTEXT_KEY = "groupId";
    /** 上下文中预取文档列表的键名，用于避免重复检索 */
    public static final String PREFETCHED_DOCUMENTS_CONTEXT_KEY = "qaRetrievedDocuments";

    private final HybridChunkRetrievalService hybridChunkRetrievalService;
    private final int topK;

    /**
     * 主构造函数（Spring 注入），使用默认 topK。
     *
     * @param hybridChunkRetrievalService 混合检索服务
     */
    @Autowired
    public ReadyChunkDocumentRetriever(
            HybridChunkRetrievalService hybridChunkRetrievalService
    ) {
        this(hybridChunkRetrievalService, DEFAULT_TOP_K);
    }

    /**
     * 构造函数，支持自定义 topK。
     *
     * @param hybridChunkRetrievalService 混合检索服务
     * @param topK                        每次检索返回的最大文档数
     */
    public ReadyChunkDocumentRetriever(
            HybridChunkRetrievalService hybridChunkRetrievalService,
            int topK
    ) {
        this.hybridChunkRetrievalService = hybridChunkRetrievalService;
        this.topK = topK > 0 ? topK : DEFAULT_TOP_K;
    }

    /**
     * 实现 {@link DocumentRetriever} 接口，供 RAG 顾问调用。
     * <p>优先使用上下文中的预取文档，否则执行实际检索。</p>
     *
     * @param query 检索请求，包含问题文本和上下文（groupId 等）
     * @return 检索到的文档列表
     */
    @Override
    public List<Document> retrieve(Query query) {
        Query validQuery = requireQuery(query);
        Long groupId = requireGroupId(validQuery);
        List<Document> prefetchedDocuments = readPrefetchedDocuments(validQuery);
        if (prefetchedDocuments != null) {
            return prefetchedDocuments;
        }
        return retrieve(groupId, query.text());
    }

    /**
     * 指定群组和问题执行检索，返回文档列表。
     *
     * @param groupId  群组 ID
     * @param question 用户问题
     * @return 检索到的文档列表
     */
    public List<Document> retrieve(Long groupId, String question) {
        return retrieveEvidence(groupId, question).documents();
    }

    /**
     * 指定群组和问题执行检索，返回包含证据等级的完整检索结果。
     *
     * @param groupId  群组 ID
     * @param question 用户问题
     * @return 检索证据束
     */
    public RetrievedEvidenceBundle retrieveEvidence(Long groupId, String question) {
        return hybridChunkRetrievalService.retrieve(groupId, question, topK);
    }

    /** 校验检索请求非空 */
    private Query requireQuery(Query query) {
        if (query == null) {
            throw new BusinessException("检索请求不能为空");
        }
        return query;
    }

    /** 从检索上下文中提取并校验 groupId */
    private Long requireGroupId(Query query) {
        Object groupId = query.context().get(GROUP_ID_CONTEXT_KEY);
        if (groupId instanceof Number) {
            return requirePositiveGroupId(((Number) groupId).longValue());
        }
        if (groupId instanceof String && StringUtils.hasText((String) groupId)) {
            try {
                return requirePositiveGroupId(Long.parseLong(((String) groupId).trim()));
            } catch (NumberFormatException exception) {
                throw new BusinessException("检索上下文中的 groupId 非法", exception);
            }
        }
        throw new BusinessException("检索上下文缺少 groupId");
    }

    /** 尝试从上下文中读取预取的文档列表，若不存在则返回 {@code null} */
    private List<Document> readPrefetchedDocuments(Query query) {
        Object documents = query.context().get(PREFETCHED_DOCUMENTS_CONTEXT_KEY);
        if (documents == null) {
            return null;
        }
        if (!(documents instanceof List<?> documentList)) {
            throw new BusinessException("检索上下文中的预取证据格式非法");
        }
        for (Object document : documentList) {
            if (!(document instanceof Document)) {
                throw new BusinessException("检索上下文中的预取证据格式非法");
            }
        }
        @SuppressWarnings("unchecked")
        List<Document> castedDocuments = (List<Document>) documentList;
        return List.copyOf(castedDocuments);
    }

    /** 校验 groupId 为正数 */
    private Long requirePositiveGroupId(long groupId) {
        if (groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
        return groupId;
    }

}
