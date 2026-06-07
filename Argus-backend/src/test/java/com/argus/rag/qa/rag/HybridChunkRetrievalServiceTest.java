package com.argus.rag.qa.rag;

import com.argus.rag.engine.elasticsearch.ElasticsearchChunkIndexService;
import com.argus.rag.engine.pgvector.PgVectorRetrievalAdapter;
import com.argus.rag.ingestion.mapper.DocumentChunkMapper;
import com.argus.rag.ingestion.model.entity.DocumentChunkEntity;
import com.argus.rag.qa.model.QueryPlanResult;
import com.argus.rag.qa.model.QueryPlanStrategy;
import com.argus.rag.qa.service.QueryPlanningService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("混合切片检索服务")
class HybridChunkRetrievalServiceTest {

    @Mock
    private PgVectorRetrievalAdapter vectorRetrievalAdapter;

    @Mock
    private ElasticsearchChunkIndexService elasticsearchChunkIndexService;

    @Mock
    private DocumentChunkMapper documentChunkMapper;

    @Mock
    private QueryPlanningService queryPlanningService;

    @Test
    @DisplayName("按每个文档总结时应优先覆盖不同文档")
    void shouldPreferDifferentDocumentsWhenQuestionAsksEachDocumentSummary() {
        String question = "请根据团队所有文档，总结每个文档的知识要点";
        HybridChunkRetrievalService service = new HybridChunkRetrievalService(
                vectorRetrievalAdapter,
                elasticsearchChunkIndexService,
                documentChunkMapper,
                queryPlanningService,
                0
        );

        when(queryPlanningService.plan(question))
                .thenReturn(new QueryPlanResult(QueryPlanStrategy.DIRECT, List.of(question)));
        when(vectorRetrievalAdapter.search(eq(1L), eq(question), anyInt()))
                .thenReturn(List.of(
                        new PgVectorRetrievalAdapter.VectorHit(10L, 101L, 0, "doc1 chunk0", 0.99D),
                        new PgVectorRetrievalAdapter.VectorHit(10L, 102L, 1, "doc1 chunk1", 0.98D),
                        new PgVectorRetrievalAdapter.VectorHit(10L, 103L, 2, "doc1 chunk2", 0.97D),
                        new PgVectorRetrievalAdapter.VectorHit(20L, 201L, 0, "doc2 chunk0", 0.96D),
                        new PgVectorRetrievalAdapter.VectorHit(30L, 301L, 0, "doc3 chunk0", 0.95D)
                ));
        when(elasticsearchChunkIndexService.search(eq(1L), eq(question), anyInt()))
                .thenReturn(List.of());
        when(documentChunkMapper.selectQaReadyChunksByIds(eq(1L), anyList()))
                .thenAnswer(invocation -> invocation.<List<Long>>getArgument(1).stream()
                        .map(this::readyRow)
                        .toList());
        when(documentChunkMapper.selectReadyActiveChunksByDocumentId(eq(1L), eq(10L)))
                .thenReturn(List.of(
                        chunk(101L, 10L, 0, "doc1 chunk0"),
                        chunk(102L, 10L, 1, "doc1 chunk1"),
                        chunk(103L, 10L, 2, "doc1 chunk2")
                ));
        when(documentChunkMapper.selectReadyActiveChunksByDocumentId(eq(1L), eq(20L)))
                .thenReturn(List.of(chunk(201L, 20L, 0, "doc2 chunk0")));
        when(documentChunkMapper.selectReadyActiveChunksByDocumentId(eq(1L), eq(30L)))
                .thenReturn(List.of(chunk(301L, 30L, 0, "doc3 chunk0")));

        RetrievedEvidenceBundle bundle = service.retrieve(1L, question, 3);

        List<Object> documentIds = bundle.documents().stream()
                .map(Document::getMetadata)
                .map(metadata -> metadata.get("documentId"))
                .toList();
        assertThat(documentIds).containsExactly(10L, 20L, 30L);
    }

    private Map<String, Object> readyRow(Long chunkId, Long documentId, Integer chunkIndex, String fileName) {
        return Map.of(
                "groupId", 1L,
                "chunkId", chunkId,
                "documentId", documentId,
                "chunkIndex", chunkIndex,
                "chunkText", fileName + " text",
                "fileName", fileName
        );
    }

    private Map<String, Object> readyRow(Long chunkId) {
        return switch (chunkId.intValue()) {
            case 101 -> readyRow(101L, 10L, 0, "doc1.md");
            case 102 -> readyRow(102L, 10L, 1, "doc1.md");
            case 103 -> readyRow(103L, 10L, 2, "doc1.md");
            case 201 -> readyRow(201L, 20L, 0, "doc2.md");
            case 301 -> readyRow(301L, 30L, 0, "doc3.md");
            default -> throw new IllegalArgumentException("Unexpected chunkId: " + chunkId);
        };
    }

    private DocumentChunkEntity chunk(Long chunkId, Long documentId, Integer chunkIndex, String text) {
        DocumentChunkEntity entity = new DocumentChunkEntity();
        entity.setId(chunkId);
        entity.setGroupId(1L);
        entity.setDocumentId(documentId);
        entity.setChunkIndex(chunkIndex);
        entity.setChunkText(text);
        return entity;
    }
}
