package com.argus.rag.document.service;

import com.argus.rag.common.enums.DocumentStatus;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.document.mapper.DocumentMapper;
import com.argus.rag.document.model.entity.DocumentEntity;
import com.argus.rag.engine.elasticsearch.ElasticsearchChunkIndexService;
import com.argus.rag.ingestion.vector.VectorIngestionService;
import com.argus.rag.group.service.GroupMembershipService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档删除与重试服务。
 */
@Service
@Slf4j
public class DocumentDeleteService {

    private final DocumentMapper documentMapper;
    private final GroupMembershipService groupMembershipService;
    private final VectorIngestionService vectorIngestionService;
    private final ElasticsearchChunkIndexService elasticsearchChunkIndexService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public DocumentDeleteService(DocumentMapper documentMapper,
                                 GroupMembershipService groupMembershipService,
                                 VectorIngestionService vectorIngestionService,
                                 ElasticsearchChunkIndexService elasticsearchChunkIndexService,
                                 ApplicationEventPublisher applicationEventPublisher) {
        this.documentMapper = documentMapper;
        this.groupMembershipService = groupMembershipService;
        this.vectorIngestionService = vectorIngestionService;
        this.elasticsearchChunkIndexService = elasticsearchChunkIndexService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /** 软删除文档，需群组管理员权限 */
    public void softDeleteDocument(Long userId, Long groupId, Long documentId) {
        requireGroupId(groupId);
        groupMembershipService.requireGroupOwner(groupId);
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("文档ID非法");
        }
        log.info("开始软删除文档: groupId={}, documentId={}", groupId, documentId);
        if (documentMapper.markDeleted(documentId, groupId) == 0) {
            throw new BusinessException("文档不存在或已删除");
        }
        vectorIngestionService.deleteDocumentVectors(documentId);
        elasticsearchChunkIndexService.deleteDocumentChunks(documentId);
        log.info("文档软删除完成: groupId={}, documentId={}", groupId, documentId);
    }

    /** 重新处理失败的文档，需群组管理员权限 */
    @Transactional
    public void retryFailedDocumentIngestion(Long userId, Long groupId, Long documentId) {
        requireGroupId(groupId);
        groupMembershipService.requireGroupOwner(groupId);
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("文档ID非法");
        }
        DocumentEntity document = documentMapper.selectByIdAndGroupId(documentId, groupId);
        if (document == null) {
            throw new BusinessException("文档不存在或已删除");
        }
        if (!DocumentStatus.FAILED.name().equals(document.getStatus())) {
            throw new BusinessException("仅失败文档支持重新处理");
        }
        log.info("开始重试失败文档摄入: documentId={}, groupId={}", documentId, groupId);
        int updated = documentMapper.update(null, new LambdaUpdateWrapper<DocumentEntity>()
                .eq(DocumentEntity::getId, documentId)
                .eq(DocumentEntity::getGroupId, groupId)
                .set(DocumentEntity::getStatus, DocumentStatus.PROCESSING.name())
                .set(DocumentEntity::getFailureReason, null)
                .set(DocumentEntity::getProcessedAt, null)
        );
        if (updated == 0) {
            throw new BusinessException("重置文档状态失败");
        }
        applicationEventPublisher.publishEvent(new DocumentIngestionRequestedEvent(documentId, groupId));
        log.info("失败文档重试已触发: documentId={}, groupId={}, newStatus={}", documentId, groupId, DocumentStatus.PROCESSING.name());
    }

    private void requireGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
    }
}
