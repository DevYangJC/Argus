package com.argus.rag.document.service;

import com.argus.rag.common.enums.DocumentStatus;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.document.mapper.DocumentMapper;
import com.argus.rag.document.model.entity.DocumentEntity;
import com.argus.rag.document.model.vo.DocumentPreviewVO;
import com.argus.rag.engine.storage.ObjectStorageService;
import com.argus.rag.group.service.GroupMembershipService;
import com.argus.rag.ingestion.service.pipeline.parser.DocumentParserFactory;
import com.argus.rag.ingestion.service.pipeline.reader.StoredObjectDocumentReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档预览服务。
 */
@Service
public class DocumentPreviewService {

    private static final Logger log = LoggerFactory.getLogger(DocumentPreviewService.class);

    private final DocumentMapper documentMapper;
    private final GroupMembershipService groupMembershipService;
    private final ObjectStorageService storageService;
    private final DocumentParserFactory parserFactory;

    public DocumentPreviewService(DocumentMapper documentMapper,
                                  GroupMembershipService groupMembershipService,
                                  ObjectStorageService storageService,
                                  DocumentParserFactory parserFactory) {
        this.documentMapper = documentMapper;
        this.groupMembershipService = groupMembershipService;
        this.storageService = storageService;
        this.parserFactory = parserFactory;
    }

    /**
     * 从 MinIO 读取文档原始文件并解析为完整文本内容。
     *
     * <p>与数据库中的 previewText（仅 200 字符）不同，此方法通过
     * {@link StoredObjectDocumentReader} 直接从对象存储下载文件、
     * 调用对应解析器提取全部文本后返回，不做任何截断。
     * 需群组成员权限，文档必须处于 READY 状态。
     */
    public DocumentPreviewVO previewDocument(Long userId, Long groupId, Long documentId) {
        requireGroupId(groupId);
        groupMembershipService.requireGroupReadable(groupId);
        DocumentEntity document = loadDocument(documentId, groupId);

        log.info("从 MinIO 读取完整文档内容: documentId={}, fileName={}", documentId, document.getFileName());
        StoredObjectDocumentReader reader = new StoredObjectDocumentReader(storageService, parserFactory, document);
        List<Document> parsedDocuments = reader.get();
        String fullText = parsedDocuments.stream()
                .map(Document::getText)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("\n"));

        if (fullText.isBlank()) {
            throw new BusinessException("文档解析后无文本内容");
        }

        DocumentPreviewVO preview = new DocumentPreviewVO();
        preview.setDocumentId(document.getId());
        preview.setFileName(document.getFileName());
        preview.setPreviewText(fullText);
        return preview;
    }

    private DocumentEntity loadDocument(Long documentId, Long groupId) {
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("文档ID非法");
        }
        DocumentEntity document = documentMapper.selectByIdAndGroupId(documentId, groupId);
        if (document == null) {
            throw new BusinessException("文档不存在或已删除");
        }
        if (!DocumentStatus.READY.name().equals(document.getStatus())) {
            throw new BusinessException("文档尚未就绪，暂不可预览");
        }
        return document;
    }

    private void requireGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
    }
}
