package com.dong.ddrag.qa.rag;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.ingestion.mapper.DocumentChunkMapper;
import com.dong.ddrag.ingestion.model.entity.DocumentChunkEntity;
import com.dong.ddrag.qa.model.EvidenceLevel;
import com.dong.ddrag.qa.model.QueryPlanResult;
import com.dong.ddrag.qa.service.QueryPlanningService;
import com.dong.ddrag.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import com.dong.ddrag.retrieval.vectorstore.PgVectorRetrievalAdapter;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HybridChunkRetrievalService {

    private static final int DEFAULT_NEIGHBOR_WINDOW = 1;
    private static final int CHANNEL_TOP_K = 50;
    private static final int RRF_K = 60;

    private final PgVectorRetrievalAdapter vectorRetrievalAdapter;
    private final ElasticsearchChunkIndexService elasticsearchChunkIndexService;
    private final DocumentChunkMapper documentChunkMapper;
    private final QueryPlanningService queryPlanningService;
    private final int neighborWindow;

    @Autowired
    public HybridChunkRetrievalService(
            PgVectorRetrievalAdapter vectorRetrievalAdapter,
            ElasticsearchChunkIndexService elasticsearchChunkIndexService,
            DocumentChunkMapper documentChunkMapper,
            QueryPlanningService queryPlanningService
    ) {
        this(
                vectorRetrievalAdapter,
                elasticsearchChunkIndexService,
                documentChunkMapper,
                queryPlanningService,
                DEFAULT_NEIGHBOR_WINDOW
        );
    }

    public HybridChunkRetrievalService(
            PgVectorRetrievalAdapter vectorRetrievalAdapter,
            ElasticsearchChunkIndexService elasticsearchChunkIndexService,
            DocumentChunkMapper documentChunkMapper,
            QueryPlanningService queryPlanningService,
            int neighborWindow
    ) {
        this.vectorRetrievalAdapter = vectorRetrievalAdapter;
        this.elasticsearchChunkIndexService = elasticsearchChunkIndexService;
        this.documentChunkMapper = documentChunkMapper;
        this.queryPlanningService = queryPlanningService;
        this.neighborWindow = Math.max(0, neighborWindow);
    }

    public RetrievedEvidenceBundle retrieve(Long groupId, String question, int topK) {
        Long validGroupId = requirePositiveGroupId(groupId);
        String normalizedQuestion = requireQuestion(question);
        int validTopK = topK > 0 ? topK : 5;
        QueryPlanResult queryPlan = queryPlanningService.plan(normalizedQuestion);
        Map<Long, RetrievalCandidate> candidates = new LinkedHashMap<>();

        for (String plannedQuery : queryPlan.queries()) {
            mergeVectorHits(candidates, validGroupId, plannedQuery);
            mergeKeywordHits(candidates, validGroupId, plannedQuery);
        }

        if (candidates.isEmpty()) {
            return RetrievedEvidenceBundle.empty();
        }

        List<RetrievalCandidate> rankedCandidates = candidates.values().stream()
                .sorted(Comparator
                        .comparingDouble(RetrievalCandidate::rankingScore).reversed()
                        .thenComparing(RetrievalCandidate::chunkId))
                .limit(validTopK)
                .toList();
        List<RetrievalCluster> rankedClusters = buildClusters(rankedCandidates);

        List<Long> chunkIds = rankedCandidates.stream().map(RetrievalCandidate::chunkId).toList();
        Map<Long, Map<String, Object>> rowByChunkId = indexRows(
                documentChunkMapper.selectQaReadyChunksByIds(validGroupId, chunkIds)
        );
        Map<Long, List<DocumentChunkEntity>> chunkWindowCache = new LinkedHashMap<>();
        List<Document> documents = new ArrayList<>();
        int evidenceIndex = 1;
        for (RetrievalCluster cluster : rankedClusters) {
            Map<String, Object> row = rowByChunkId.get(cluster.primaryChunkId());
            if (row == null) {
                continue;
            }
            Document document = toDocument("E" + evidenceIndex, row, cluster, chunkWindowCache);
            if (document == null) {
                continue;
            }
            documents.add(document);
            evidenceIndex++;
        }
        if (documents.isEmpty()) {
            return RetrievedEvidenceBundle.empty();
        }
        EvidenceLevel evidenceLevel = evaluateEvidenceLevel(documents);
        return new RetrievedEvidenceBundle(documents, evidenceLevel, buildEvidenceGuidance(evidenceLevel));
    }

    private void mergeVectorHits(
            Map<Long, RetrievalCandidate> candidates,
            Long groupId,
            String query
    ) {
        List<PgVectorRetrievalAdapter.VectorHit> vectorHits = vectorRetrievalAdapter.search(groupId, query, CHANNEL_TOP_K);
        for (int index = 0; index < vectorHits.size(); index++) {
            PgVectorRetrievalAdapter.VectorHit hit = vectorHits.get(index);
            RetrievalCandidate candidate = candidates.computeIfAbsent(
                    hit.chunkId(),
                    ignored -> RetrievalCandidate.fromVectorHit(hit)
            );
            candidate.mergeVectorHit(hit, index + 1);
        }
    }

    private void mergeKeywordHits(
            Map<Long, RetrievalCandidate> candidates,
            Long groupId,
            String query
    ) {
        List<ElasticsearchChunkIndexService.KeywordHit> keywordHits =
                elasticsearchChunkIndexService.search(groupId, query, CHANNEL_TOP_K);
        for (int index = 0; index < keywordHits.size(); index++) {
            ElasticsearchChunkIndexService.KeywordHit hit = keywordHits.get(index);
            RetrievalCandidate candidate = candidates.computeIfAbsent(
                    hit.chunkId(),
                    ignored -> RetrievalCandidate.fromKeywordHit(hit)
            );
            candidate.mergeKeywordHit(hit, index + 1);
        }
    }

    private Map<Long, Map<String, Object>> indexRows(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> rowByChunkId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            rowByChunkId.put(requireLong(getValue(row, "chunkId"), "chunkId"), row);
        }
        return rowByChunkId;
    }

    private Document toDocument(
            String evidenceId,
            Map<String, Object> row,
            RetrievalCluster cluster,
            Map<Long, List<DocumentChunkEntity>> chunkWindowCache
    ) {
        Long documentId = requireLong(getValue(row, "documentId"), "documentId");
        Integer chunkIndex = requireInteger(getValue(row, "chunkIndex"), "chunkIndex");
        if (!documentId.equals(cluster.documentId()) || !chunkIndex.equals(cluster.primaryChunkIndex())) {
            throw new BusinessException("检索结果与文档切片不一致");
        }
        Long chunkId = requireLong(getValue(row, "chunkId"), "chunkId");
        String fileName = requireText(getValue(row, "fileName"), "fileName");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("evidenceId", evidenceId);
        metadata.put("groupId", requireLong(getValue(row, "groupId"), "groupId"));
        metadata.put("documentId", documentId);
        metadata.put("chunkId", chunkId);
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("primaryChunkId", cluster.primaryChunkId());
        metadata.put("primaryChunkIndex", cluster.primaryChunkIndex());
        metadata.put("startChunkIndex", cluster.expandedStartChunkIndex(neighborWindow));
        metadata.put("endChunkIndex", cluster.expandedEndChunkIndex(neighborWindow));
        metadata.put("fileName", fileName);
        metadata.put("score", cluster.rankingScore());
        metadata.put("retrievalSource", cluster.source());
        metadata.put("vectorScore", cluster.vectorScore());
        metadata.put("keywordScore", cluster.keywordScore());
        metadata.put("hybridScore", cluster.rankingScore());
        String evidenceText = buildEvidenceWindow(row, cluster, chunkWindowCache);
        if (!StringUtils.hasText(evidenceText)) {
            return null;
        }
        return Document.builder()
                .id(evidenceId)
                .text(evidenceText)
                .metadata(metadata)
                .build();
    }

    private String buildEvidenceWindow(
            Map<String, Object> row,
            RetrievalCluster cluster,
            Map<Long, List<DocumentChunkEntity>> chunkWindowCache
    ) {
        Long groupId = requireLong(getValue(row, "groupId"), "groupId");
        Long documentId = requireLong(getValue(row, "documentId"), "documentId");
        String fileName = requireText(getValue(row, "fileName"), "fileName");
        List<DocumentChunkEntity> chunks = chunkWindowCache.computeIfAbsent(
                documentId,
                ignored -> documentChunkMapper.selectReadyActiveChunksByDocumentId(groupId, documentId)
        );
        if (chunks.isEmpty()) {
            return null;
        }
        int startIndex = cluster.expandedStartChunkIndex(neighborWindow);
        int endIndex = cluster.expandedEndChunkIndex(neighborWindow);
        StringBuilder builder = new StringBuilder();
        for (DocumentChunkEntity chunk : chunks) {
            if (chunk.getChunkIndex() != null
                    && chunk.getChunkIndex() >= startIndex
                    && chunk.getChunkIndex() <= endIndex
                    && StringUtils.hasText(chunk.getChunkText())) {
                if (!builder.isEmpty()) {
                    builder.append("\n");
                }
                builder.append(chunk.getChunkText().trim());
            }
        }
        if (builder.isEmpty()) {
            return null;
        }
        return "文件名：" + fileName + "\n" + builder;
    }

    private List<RetrievalCluster> buildClusters(List<RetrievalCandidate> rankedCandidates) {
        Map<Long, List<RetrievalCandidate>> candidatesByDocumentId = new LinkedHashMap<>();
        for (RetrievalCandidate candidate : rankedCandidates) {
            candidatesByDocumentId.computeIfAbsent(candidate.documentId(), ignored -> new ArrayList<>()).add(candidate);
        }
        List<RetrievalCluster> clusters = new ArrayList<>();
        for (List<RetrievalCandidate> sameDocumentCandidates : candidatesByDocumentId.values()) {
            List<RetrievalCandidate> sortedByChunkIndex = sameDocumentCandidates.stream()
                    .sorted(Comparator.comparing(RetrievalCandidate::chunkIndex))
                    .toList();
            RetrievalCluster currentCluster = null;
            for (RetrievalCandidate candidate : sortedByChunkIndex) {
                if (currentCluster == null || !currentCluster.isContinuousWith(candidate)) {
                    currentCluster = new RetrievalCluster(candidate);
                    clusters.add(currentCluster);
                    continue;
                }
                currentCluster.add(candidate);
            }
        }
        return clusters.stream()
                .sorted(Comparator
                        .comparingDouble(RetrievalCluster::rankingScore).reversed()
                        .thenComparing(RetrievalCluster::primaryChunkId))
                .toList();
    }

    private EvidenceLevel evaluateEvidenceLevel(List<Document> documents) {
        if (documents.isEmpty()) {
            return EvidenceLevel.NONE;
        }
        boolean hasBothSource = documents.stream()
                .map(document -> document.getMetadata().get("retrievalSource"))
                .anyMatch("BOTH"::equals);
        boolean hasVectorEvidence = documents.stream()
                .map(document -> document.getMetadata().get("retrievalSource"))
                .anyMatch(source -> "VECTOR".equals(source) || "BOTH".equals(source));
        double topScore = documents.stream()
                .map(document -> document.getMetadata().get("hybridScore"))
                .filter(Double.class::isInstance)
                .map(Double.class::cast)
                .max(Double::compareTo)
                .orElse(0D);
        if (documents.size() >= 2 && (hasBothSource || (hasVectorEvidence && topScore >= 0.95D))) {
            return EvidenceLevel.SUFFICIENT;
        }
        if (hasBothSource || documents.size() >= 2) {
            return EvidenceLevel.PARTIAL;
        }
        return EvidenceLevel.WEAK;
    }

    private String buildEvidenceGuidance(EvidenceLevel evidenceLevel) {
        return switch (evidenceLevel) {
            case NONE -> "当前没有可用证据，必须直接拒答。";
            case WEAK -> "当前证据相关性有限，只能谨慎回答，必须明确说明依据有限，不能给出确定性结论。";
            case PARTIAL -> "当前证据只覆盖部分问题，只能回答证据明确支持的部分，未覆盖部分必须明确说明不足。";
            case SUFFICIENT -> "当前证据较充分，可以正常回答，但仍然不得超出证据进行臆测。";
        };
    }

    private Object getValue(Map<String, Object> row, String field) {
        Object value = row.get(field);
        if (value != null) {
            return value;
        }
        return row.get(field.toLowerCase());
    }

    private Long requirePositiveGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
        return groupId;
    }

    private String requireQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException("问题不能为空");
        }
        return question.trim();
    }

    private Long requireLong(Object value, String field) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new BusinessException("检索结果缺少字段: " + field);
    }

    private Integer requireInteger(Object value, String field) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new BusinessException("检索结果缺少字段: " + field);
    }

    private String requireText(Object value, String field) {
        if (value instanceof String text && StringUtils.hasText(text)) {
            return text.trim();
        }
        throw new BusinessException("检索结果缺少字段: " + field);
    }

    static final class RetrievalCandidate {

        private final Long documentId;
        private final Long chunkId;
        private final Integer chunkIndex;
        private double vectorScore;
        private double keywordScore;
        private double rankingScore;
        private boolean vectorMatched;
        private boolean keywordMatched;

        private RetrievalCandidate(Long documentId, Long chunkId, Integer chunkIndex) {
            this.documentId = documentId;
            this.chunkId = chunkId;
            this.chunkIndex = chunkIndex;
        }

        static RetrievalCandidate fromVectorHit(PgVectorRetrievalAdapter.VectorHit hit) {
            return new RetrievalCandidate(hit.documentId(), hit.chunkId(), hit.chunkIndex());
        }

        static RetrievalCandidate fromKeywordHit(ElasticsearchChunkIndexService.KeywordHit hit) {
            return new RetrievalCandidate(hit.documentId(), hit.chunkId(), hit.chunkIndex());
        }

        void mergeVectorHit(PgVectorRetrievalAdapter.VectorHit hit, int rank) {
            this.vectorMatched = true;
            this.vectorScore = Math.max(this.vectorScore, hit.score());
            this.rankingScore += reciprocalRank(rank);
        }

        void mergeKeywordHit(ElasticsearchChunkIndexService.KeywordHit hit, int rank) {
            this.keywordMatched = true;
            this.keywordScore = Math.max(this.keywordScore, hit.normalizedScore());
            this.rankingScore += reciprocalRank(rank);
        }

        Long documentId() {
            return documentId;
        }

        Long chunkId() {
            return chunkId;
        }

        Integer chunkIndex() {
            return chunkIndex;
        }

        double vectorScore() {
            return vectorScore;
        }

        double keywordScore() {
            return keywordScore;
        }

        double rankingScore() {
            return rankingScore;
        }

        String source() {
            if (vectorMatched && keywordMatched) {
                return "BOTH";
            }
            return vectorMatched ? "VECTOR" : "KEYWORD";
        }

        private double reciprocalRank(int rank) {
            return 1D / (RRF_K + Math.max(rank, 1));
        }
    }

    static final class RetrievalCluster {

        private final Long documentId;
        private final List<RetrievalCandidate> members = new ArrayList<>();
        private RetrievalCandidate primaryCandidate;
        private int startChunkIndex;
        private int endChunkIndex;
        private double rankingScore;
        private double vectorScore;
        private double keywordScore;
        private boolean hasVectorSource;
        private boolean hasKeywordSource;

        private RetrievalCluster(RetrievalCandidate seed) {
            this.documentId = seed.documentId();
            this.startChunkIndex = seed.chunkIndex();
            this.endChunkIndex = seed.chunkIndex();
            add(seed);
        }

        boolean isContinuousWith(RetrievalCandidate candidate) {
            return documentId.equals(candidate.documentId()) && candidate.chunkIndex() == endChunkIndex + 1;
        }

        void add(RetrievalCandidate candidate) {
            members.add(candidate);
            endChunkIndex = Math.max(endChunkIndex, candidate.chunkIndex());
            startChunkIndex = Math.min(startChunkIndex, candidate.chunkIndex());
            rankingScore = Math.max(rankingScore, candidate.rankingScore());
            vectorScore = Math.max(vectorScore, candidate.vectorScore());
            keywordScore = Math.max(keywordScore, candidate.keywordScore());
            hasVectorSource = hasVectorSource || "VECTOR".equals(candidate.source()) || "BOTH".equals(candidate.source());
            hasKeywordSource = hasKeywordSource || "KEYWORD".equals(candidate.source()) || "BOTH".equals(candidate.source());
            if (primaryCandidate == null
                    || candidate.rankingScore() > primaryCandidate.rankingScore()
                    || (candidate.rankingScore() == primaryCandidate.rankingScore()
                    && candidate.chunkIndex() < primaryCandidate.chunkIndex())) {
                primaryCandidate = candidate;
            }
        }

        Long documentId() {
            return documentId;
        }

        Long primaryChunkId() {
            return primaryCandidate.chunkId();
        }

        Integer primaryChunkIndex() {
            return primaryCandidate.chunkIndex();
        }

        double rankingScore() {
            return rankingScore;
        }

        double vectorScore() {
            return vectorScore;
        }

        double keywordScore() {
            return keywordScore;
        }

        int expandedStartChunkIndex(int neighborWindow) {
            return Math.max(0, startChunkIndex - Math.max(0, neighborWindow));
        }

        int expandedEndChunkIndex(int neighborWindow) {
            return endChunkIndex + Math.max(0, neighborWindow);
        }

        String source() {
            if (hasVectorSource && hasKeywordSource) {
                return "BOTH";
            }
            return hasVectorSource ? "VECTOR" : "KEYWORD";
        }
    }
}
