package com.argus.rag.qa.support;

import com.argus.rag.qa.model.vo.AskQuestionResponse;
import com.argus.rag.qa.model.vo.QaRecordDetailVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("证据覆盖概览组装器")
class EvidenceOverviewAssemblerTest {

    private final EvidenceOverviewAssembler assembler = new EvidenceOverviewAssembler();

    @Test
    @DisplayName("应按文档分组统计证据覆盖情况")
    void shouldGroupEvidenceByDocument() {
        List<Document> documents = List.of(
                evidence("E1", 10L, 101L, 0, "产品需求.md", 0.91D, "BOTH", "DOCUMENT_COVERAGE", "需求背景和用户目标"),
                evidence("E2", 10L, 102L, 1, "产品需求.md", 0.76D, "VECTOR", "DOCUMENT_COVERAGE", "核心流程和边界条件"),
                evidence("E3", 20L, 201L, 0, "部署说明.md", 0.84D, "KEYWORD", "DOCUMENT_COVERAGE", "部署步骤和回滚方案")
        );

        AskQuestionResponse.EvidenceOverview overview = assembler.assemble(documents);

        assertThat(overview.documentCount()).isEqualTo(2);
        assertThat(overview.evidenceCount()).isEqualTo(3);
        assertThat(overview.coverageMode()).isEqualTo("DOCUMENT_COVERAGE");
        assertThat(overview.groups()).hasSize(2);
        AskQuestionResponse.DocumentEvidenceGroup firstGroup = overview.groups().getFirst();
        assertThat(firstGroup.documentId()).isEqualTo(10L);
        assertThat(firstGroup.fileName()).isEqualTo("产品需求.md");
        assertThat(firstGroup.evidenceCount()).isEqualTo(2);
        assertThat(firstGroup.topScore()).isEqualTo(0.91D);
        assertThat(firstGroup.retrievalSources()).containsExactly("BOTH", "VECTOR");
        assertThat(firstGroup.snippets())
                .extracting(AskQuestionResponse.EvidenceSnippet::chunkId)
                .containsExactly(101L, 102L);
        assertThat(overview.warnings()).isEmpty();
    }

    @Test
    @DisplayName("跨文档覆盖模式仅命中一个文档时应给出提示")
    void shouldWarnWhenCoverageModeOnlyHitsOneDocument() {
        AskQuestionResponse.EvidenceOverview overview = assembler.assemble(List.of(
                evidence("E1", 10L, 101L, 0, "产品需求.md", 0.91D, "BOTH", "DOCUMENT_COVERAGE", "需求背景")
        ));

        assertThat(overview.warnings())
                .containsExactly("当前问题启用了跨文档覆盖检索，但本次证据仅覆盖 1 个文档。");
    }

    @Test
    @DisplayName("应从历史引用快照组装证据覆盖概览")
    void shouldAssembleOverviewFromHistoryCitations() {
        List<QaRecordDetailVO.Citation> citations = List.of(
                new QaRecordDetailVO.Citation(
                        10L, null, 101L, 0, 0, 1, "产品需求.md", 0.91D, "BOTH", 0.9D, 0.8D, 1.7D,
                        "需求背景和用户目标"),
                new QaRecordDetailVO.Citation(
                        10L, null, 102L, 2, 2, 2, "产品需求.md", 0.76D, "VECTOR", 0.76D, null, 0.76D,
                        "核心流程和边界条件"),
                new QaRecordDetailVO.Citation(
                        20L, null, 201L, 0, 0, 0, "部署说明.md", 0.84D, "KEYWORD", null, 0.84D, 0.84D,
                        "部署步骤和回滚方案")
        );

        AskQuestionResponse.EvidenceOverview overview = assembler.assembleHistoryCitations(citations);

        assertThat(overview.documentCount()).isEqualTo(2);
        assertThat(overview.evidenceCount()).isEqualTo(3);
        assertThat(overview.coverageMode()).isEqualTo("HISTORY_SNAPSHOT");
        assertThat(overview.groups()).extracting(AskQuestionResponse.DocumentEvidenceGroup::fileName)
                .containsExactly("产品需求.md", "部署说明.md");
        assertThat(overview.groups().getFirst().snippets())
                .extracting(AskQuestionResponse.EvidenceSnippet::chunkId)
                .containsExactly(101L, 102L);
    }

    private Document evidence(
            String evidenceId,
            Long documentId,
            Long chunkId,
            Integer chunkIndex,
            String fileName,
            double score,
            String retrievalSource,
            String coverageMode,
            String text) {
        return Document.builder()
                .id(evidenceId)
                .text("文件名：" + fileName + "\n" + text)
                .metadata(Map.ofEntries(
                        entry("evidenceId", evidenceId),
                        entry("documentId", documentId),
                        entry("chunkId", chunkId),
                        entry("chunkIndex", chunkIndex),
                        entry("startChunkIndex", chunkIndex),
                        entry("endChunkIndex", chunkIndex),
                        entry("fileName", fileName),
                        entry("score", score),
                        entry("retrievalSource", retrievalSource),
                        entry("coverageMode", coverageMode)
                ))
                .build();
    }
}
