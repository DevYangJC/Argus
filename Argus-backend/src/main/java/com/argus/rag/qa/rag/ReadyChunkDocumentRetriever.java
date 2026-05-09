package com.dong.ddrag.qa.rag;

import com.dong.ddrag.common.exception.BusinessException;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class ReadyChunkDocumentRetriever implements DocumentRetriever {

    private static final int DEFAULT_TOP_K = 5;
    private static final String GROUP_ID_CONTEXT_KEY = "groupId";
    public static final String PREFETCHED_DOCUMENTS_CONTEXT_KEY = "qaRetrievedDocuments";

    private final HybridChunkRetrievalService hybridChunkRetrievalService;
    private final int topK;

    @Autowired
    public ReadyChunkDocumentRetriever(
            HybridChunkRetrievalService hybridChunkRetrievalService
    ) {
        this(hybridChunkRetrievalService, DEFAULT_TOP_K);
    }

    public ReadyChunkDocumentRetriever(
            HybridChunkRetrievalService hybridChunkRetrievalService,
            int topK
    ) {
        this.hybridChunkRetrievalService = hybridChunkRetrievalService;
        this.topK = topK > 0 ? topK : DEFAULT_TOP_K;
    }

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

    public List<Document> retrieve(Long groupId, String question) {
        return retrieveEvidence(groupId, question).documents();
    }

    public RetrievedEvidenceBundle retrieveEvidence(Long groupId, String question) {
        return hybridChunkRetrievalService.retrieve(groupId, question, topK);
    }

    private Query requireQuery(Query query) {
        if (query == null) {
            throw new BusinessException("检索请求不能为空");
        }
        return query;
    }

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

    private Long requirePositiveGroupId(long groupId) {
        if (groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
        return groupId;
    }

}
