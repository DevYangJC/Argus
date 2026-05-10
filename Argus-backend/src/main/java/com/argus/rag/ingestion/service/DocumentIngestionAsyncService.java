package com.argus.rag.ingestion.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.argus.rag.common.enums.DocumentStatus;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.document.mapper.DocumentMapper;
import com.argus.rag.document.model.entity.DocumentEntity;
import com.argus.rag.ingestion.mapper.DocumentChunkMapper;
import com.argus.rag.ingestion.model.entity.DocumentChunkEntity;
import com.argus.rag.ingestion.service.DocumentIngestionProcessor;
import com.argus.rag.ingestion.vector.VectorIngestionService;
import com.argus.rag.engine.elasticsearch.ElasticsearchChunkIndexService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步文档 ETL（提取-转换-加载）服务。
 *
 * <p>负责在文档上传完成后异步执行以下流程：
 * <ol>
 *   <li>清理上一次处理的中间产物（chunk、向量、ES 索引）</li>
 *   <li>调用 {@link DocumentIngestionProcessor} 进行文档解析和分块</li>
 *   <li>将分块索引同步至 Elasticsearch</li>
 *   <li>将文档状态更新为 READY</li>
 * </ol>
 *
 * <p>通过 {@link Retryable} 注解支持失败自动重试（最多 3 次，退避策略：2s / 4s / 8s）。
 * 全部重试失败后由 {@link #recover} 方法将文档标记为 FAILED。
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class DocumentIngestionAsyncService {

    /** 失败原因字段最大长度 */
    private static final int FAILURE_REASON_MAX_LENGTH = 512;

    /** 文档数据访问 */
    private final DocumentMapper documentMapper;
    /** 文档分块处理引擎 */
    private final DocumentIngestionProcessor documentIngestionProcessor;
    /** 文档分块数据访问 */
    private final DocumentChunkMapper documentChunkMapper;
    /** 向量导入服务 */
    private final VectorIngestionService vectorIngestionService;
    /** Elasticsearch chunk 索引服务 */
    private final ElasticsearchChunkIndexService elasticsearchChunkIndexService;

    /**
     * 构造异步 ETL 服务，注入所有依赖。
     *
     * @param documentMapper                 文档数据访问层
     * @param documentIngestionProcessor     文档分块处理引擎
     * @param documentChunkMapper            文档分块数据访问层
     * @param vectorIngestionService         向量导入服务
     * @param elasticsearchChunkIndexService ES chunk 索引服务
     */
    public DocumentIngestionAsyncService(
            DocumentMapper documentMapper,
            DocumentIngestionProcessor documentIngestionProcessor,
            DocumentChunkMapper documentChunkMapper,
            VectorIngestionService vectorIngestionService,
            ElasticsearchChunkIndexService elasticsearchChunkIndexService
    ) {
        this.documentMapper = documentMapper;
        this.documentIngestionProcessor = documentIngestionProcessor;
        this.documentChunkMapper = documentChunkMapper;
        this.vectorIngestionService = vectorIngestionService;
        this.elasticsearchChunkIndexService = elasticsearchChunkIndexService;
    }

    /**
     * 异步执行文档 ETL 流程。
     *
     * <p>带重试机制：遇到 RuntimeException 时最多重试 3 次，
     * 退避策略为首次 2s，后续每次乘以 2（2s / 4s / 8s）。
     * 所有重试耗尽后由 {@link #recover} 兜底。
     *
     * @param documentId 文档 ID
     * @param groupId    文档所属群组 ID
     * @throws BusinessException 文档不存在或状态更新失败时抛出
     */
    @Retryable(
            retryFor = RuntimeException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2.0)
    )
    @Transactional
    public void ingestDocument(Long documentId, Long groupId) {
        DocumentEntity document = requireDocument(documentId, groupId);
        log.info("开始异步执行文档ETL: documentId={}, groupId={}", documentId, groupId);
        cleanupProcessingArtifacts(documentId);
        documentIngestionProcessor.process(documentId, groupId);
        syncSearchIndex(document);
        markDocumentStatus(documentId, groupId, DocumentStatus.READY.name(), null, LocalDateTime.now());
        log.info("异步文档ETL完成: documentId={}, groupId={}, status={}", documentId, groupId, DocumentStatus.READY.name());
    }

    /**
     * 重试全部失败后的兜底恢复方法。
     *
     * <p>清理中间产物，将文档状态标记为 FAILED 并记录截断后的失败原因。
     * 此方法由 Spring Retry 的 {@link Recover} 机制自动调用。
     *
     * @param exception  导致最终失败的异常
     * @param documentId 文档 ID
     * @param groupId    文档所属群组 ID
     */
    @Recover
    @Transactional
    public void recover(RuntimeException exception, Long documentId, Long groupId) {
        log.error("异步文档ETL最终失败: documentId={}, groupId={}, reason={}", documentId, groupId, exception.getMessage(), exception);
        cleanupProcessingArtifacts(documentId);
        markDocumentStatus(
                documentId,
                groupId,
                DocumentStatus.FAILED.name(),
                truncateFailureReason(exception.getMessage()),
                LocalDateTime.now()
        );
    }

    /**
     * 查询文档实体，不存在时抛出异常。
     *
     * @param documentId 文档 ID
     * @param groupId    文档所属群组 ID
     * @return 文档实体
     * @throws BusinessException 文档不存在时抛出
     */
    private DocumentEntity requireDocument(Long documentId, Long groupId) {
        DocumentEntity document = documentMapper.selectByIdAndGroupId(documentId, groupId);
        if (document == null) {
            throw new BusinessException("待处理文档不存在");
        }
        return document;
    }

    /**
     * 清理上一次处理遗留的中间产物。
     *
     * <p>依次删除 chunk 记录、向量数据和 ES 索引。每项清理失败时仅记录日志，不中断后续清理。
     *
     * @param documentId 文档 ID
     */
    private void cleanupProcessingArtifacts(Long documentId) {
        log.info("开始清理上次处理中间产物: documentId={}", documentId);
        try {
            documentChunkMapper.deleteByDocumentId(documentId);
        } catch (RuntimeException exception) {
            log.warn("清理旧 chunk 失败: documentId={}, reason={}", documentId, exception.getMessage());
        }
        try {
            vectorIngestionService.deleteDocumentVectors(documentId);
        } catch (RuntimeException exception) {
            log.warn("清理旧向量失败: documentId={}, reason={}", documentId, exception.getMessage());
        }
        try {
            elasticsearchChunkIndexService.deleteDocumentChunks(documentId);
        } catch (RuntimeException exception) {
            log.warn("清理旧 ES 索引失败: documentId={}, reason={}", documentId, exception.getMessage());
        }
        log.info("中间产物清理完成: documentId={}", documentId);
    }

    /**
     * 将文档的分块数据同步到 Elasticsearch 搜索索引。
     *
     * @param document 文档实体
     */
    private void syncSearchIndex(DocumentEntity document) {
        log.info("开始同步ES搜索索引: documentId={}, fileName={}", document.getId(), document.getFileName());
        List<DocumentChunkEntity> chunks = documentChunkMapper.selectByDocumentId(document.getId());
        elasticsearchChunkIndexService.indexReadyChunks(document.getFileName(), chunks);
        log.info("ES搜索索引同步完成: documentId={}, indexedChunks={}", document.getId(), chunks.size());
    }

    /**
     * 更新文档状态、失败原因和处理时间。
     *
     * @param documentId    文档 ID
     * @param groupId       文档所属群组 ID
     * @param status        目标状态（READY 或 FAILED）
     * @param failureReason 失败原因（成功时为 null）
     * @param processedAt   处理完成时间
     * @throws BusinessException 更新影响行数为 0 时抛出
     */
    private void markDocumentStatus(
            Long documentId,
            Long groupId,
            String status,
            String failureReason,
            LocalDateTime processedAt
    ) {
        int updated = documentMapper.update(null, new LambdaUpdateWrapper<DocumentEntity>()
                .eq(DocumentEntity::getId, documentId)
                .eq(DocumentEntity::getGroupId, groupId)
                .set(DocumentEntity::getStatus, status)
                .set(DocumentEntity::getFailureReason, failureReason)
                .set(DocumentEntity::getProcessedAt, processedAt)
        );
        if (updated == 0) {
            throw new BusinessException("文档状态更新失败");
        }
    }

    /**
     * 截断失败原因字符串至最大长度，空值返回默认消息。
     *
     * @param failureReason 原始失败原因
     * @return 截断后的失败原因
     */
    private String truncateFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "文档处理失败";
        }
        return failureReason.length() <= FAILURE_REASON_MAX_LENGTH
                ? failureReason
                : failureReason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
