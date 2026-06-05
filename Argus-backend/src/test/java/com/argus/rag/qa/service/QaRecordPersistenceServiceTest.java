package com.argus.rag.qa.service;

import com.argus.rag.qa.mapper.QaRecordCitationMapper;
import com.argus.rag.qa.mapper.QaRecordMapper;
import com.argus.rag.qa.model.EvidenceLevel;
import com.argus.rag.qa.model.entity.QaRecordCitationEntity;
import com.argus.rag.qa.model.entity.QaRecordEntity;
import com.argus.rag.qa.model.vo.AskQuestionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static java.util.Map.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QA 问答记录持久化服务")
class QaRecordPersistenceServiceTest {

    @Mock
    private QaRecordMapper qaRecordMapper;

    @Mock
    private QaRecordCitationMapper qaRecordCitationMapper;

    @Test
    @DisplayName("应保存成功回答和原始证据引用快照")
    void shouldPersistAnsweredRecordWithRawEvidenceCitations() {
        when(qaRecordMapper.insert(any(QaRecordEntity.class))).thenAnswer(invocation -> {
            QaRecordEntity entity = invocation.getArgument(0);
            entity.setId(77L);
            return 1;
        });

        QaRecordPersistenceService service = new QaRecordPersistenceService(qaRecordMapper, qaRecordCitationMapper);
        AskQuestionResponse response = AskQuestionResponse.answered(
                "上传流程分为初始化、上传分片和完成合并。",
                List.of(new AskQuestionResponse.Citation(10L, 100L, 2, "guide.pdf", 0.88D, null))
        );
        Document evidence = Document.builder()
                .id("E1")
                .text("文件名：guide.pdf\n上传流程分为初始化、上传分片和完成合并。")
                .metadata(Map.ofEntries(
                        entry("documentId", 10L),
                        entry("chunkId", 100L),
                        entry("chunkIndex", 2),
                        entry("fileName", "guide.pdf"),
                        entry("score", 0.88D),
                        entry("retrievalSource", "BOTH"),
                        entry("vectorScore", 0.61D),
                        entry("keywordScore", 0.73D),
                        entry("hybridScore", 1.25D),
                        entry("startChunkIndex", 1),
                        entry("endChunkIndex", 3)
                ))
                .build();

        Long recordId = service.saveCompleted(new QaRecordPersistenceService.SaveCommand(
                5L,
                1L,
                "文档上传流程是什么？",
                "qa/ask",
                "qwen-plus",
                response,
                EvidenceLevel.SUFFICIENT,
                new QaChatService.UsageInfo(100, 40, 140, false, 1234L),
                List.of(evidence),
                true,
                null
        ));

        assertThat(recordId).isEqualTo(77L);

        ArgumentCaptor<QaRecordEntity> recordCaptor = ArgumentCaptor.forClass(QaRecordEntity.class);
        verify(qaRecordMapper).insert(recordCaptor.capture());
        QaRecordEntity record = recordCaptor.getValue();
        assertThat(record.getUserId()).isEqualTo(5L);
        assertThat(record.getGroupId()).isEqualTo(1L);
        assertThat(record.getQuestion()).isEqualTo("文档上传流程是什么？");
        assertThat(record.getAnswer()).isEqualTo("上传流程分为初始化、上传分片和完成合并。");
        assertThat(record.getAnswered()).isTrue();
        assertThat(record.getEvidenceLevel()).isEqualTo(EvidenceLevel.SUFFICIENT.name());
        assertThat(record.getCitationCount()).isEqualTo(1);
        assertThat(record.getPromptTokens()).isEqualTo(100);
        assertThat(record.getCompletionTokens()).isEqualTo(40);
        assertThat(record.getTotalTokens()).isEqualTo(140);
        assertThat(record.getLatencyMs()).isEqualTo(1234L);

        ArgumentCaptor<QaRecordCitationEntity> citationCaptor = ArgumentCaptor.forClass(QaRecordCitationEntity.class);
        verify(qaRecordCitationMapper).insert(citationCaptor.capture());
        QaRecordCitationEntity citation = citationCaptor.getValue();
        assertThat(citation.getQaRecordId()).isEqualTo(77L);
        assertThat(citation.getDocumentId()).isEqualTo(10L);
        assertThat(citation.getChunkId()).isEqualTo(100L);
        assertThat(citation.getChunkIndex()).isEqualTo(2);
        assertThat(citation.getFileName()).isEqualTo("guide.pdf");
        assertThat(citation.getScore()).isEqualTo(0.88D);
        assertThat(citation.getRetrievalSource()).isEqualTo("BOTH");
        assertThat(citation.getVectorScore()).isEqualTo(0.61D);
        assertThat(citation.getKeywordScore()).isEqualTo(0.73D);
        assertThat(citation.getHybridScore()).isEqualTo(1.25D);
        assertThat(citation.getStartChunkIndex()).isEqualTo(1);
        assertThat(citation.getEndChunkIndex()).isEqualTo(3);
        assertThat(citation.getSnippet()).contains("上传流程");
    }
}
