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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
     * <p>
     * 对于 Markdown（.md）文件，直接返回原始 Markdown 源码（不经过解析器剥离语法），
     * 以便前端使用 marked.js 等库进行富文本渲染；对于其他格式（PDF、DOCX、TXT），
     * 通过 {@link StoredObjectDocumentReader} 解析器提取纯文本，不做任何截断。
     * 需群组成员权限，文档必须处于 READY 状态。
     */
    public DocumentPreviewVO previewDocument(Long userId, Long groupId, Long documentId) {
        requireGroupId(groupId);
        groupMembershipService.requireGroupReadable(groupId);
        DocumentEntity document = loadDocument(documentId, groupId);

        log.info("从 MinIO 读取完整文档内容: documentId={}, fileName={}", documentId, document.getFileName());

        String fullText;
        if ("md".equalsIgnoreCase(document.getFileExt())) {
            // Markdown 文件：返回原始 Markdown 源码，交由前端 marked.js 渲染，保留标题、列表、粗体等格式
            fullText = readRawContent(document);
        } else {
            // 其他格式（PDF / DOCX / TXT）：通过解析器提取纯文本
            StoredObjectDocumentReader reader = new StoredObjectDocumentReader(storageService, parserFactory, document);
            List<Document> parsedDocuments = reader.get();
            fullText = parsedDocuments.stream()
                    .map(Document::getText)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining("\n"));
        }

        if (fullText.isBlank()) {
            throw new BusinessException("文档解析后无文本内容");
        }

        DocumentPreviewVO preview = new DocumentPreviewVO();
        preview.setDocumentId(document.getId());
        preview.setFileName(document.getFileName());
        preview.setPreviewText(fullText);
        return preview;
    }

    /**
     * 直接从对象存储读取 Markdown 文件的原始内容（不做任何语法剥离）。
     *
     * <p>
     * 读取的文件内容以 UTF-8 解码后原样返回，保留所有 Markdown 语法标记
     * （标题 #、粗体 **、列表 -、代码块 ``` 等），供前端 marked.js 渲染。
     * </p>
     *
     * @param document 文档实体（需包含 storageBucket / storageObjectKey）
     * @return 原始 Markdown 文本内容
     * @throws BusinessException 当读取存储失败时抛出
     */
    private String readRawContent(DocumentEntity document) {
        String bucket = resolveBucket(document);
        String objectKey = document.getStorageObjectKey();
        try (InputStream inputStream = storageService.getObject(bucket, objectKey)) {
            byte[] bytes = inputStream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("读取 Markdown 原始文件失败", e);
        }
    }

    /**
     * 解析存储桶名称（优先使用文档实体上指定的桶，否则回退到默认桶）。
     */
    private String resolveBucket(DocumentEntity document) {
        if (document.getStorageBucket() != null && !document.getStorageBucket().isBlank()) {
            return document.getStorageBucket();
        }
        return storageService.getDefaultBucket();
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
