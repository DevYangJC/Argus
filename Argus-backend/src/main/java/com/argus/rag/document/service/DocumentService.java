package com.argus.rag.document.service;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.enums.DocumentStatus;
import com.argus.rag.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.argus.rag.document.mapper.DocumentMapper;
import com.argus.rag.document.model.dto.DocumentQuery;
import com.argus.rag.document.model.dto.UploadDocumentRequest;
import com.argus.rag.document.model.entity.DocumentEntity;
import com.argus.rag.document.model.vo.DocumentDownloadVO;
import com.argus.rag.document.model.vo.DocumentListItemVO;
import com.argus.rag.document.model.vo.DocumentPreviewVO;
import com.argus.rag.groupmembership.service.GroupMembershipService;
import com.argus.rag.ingestion.vector.VectorIngestionService;
import com.argus.rag.retrieval.elasticsearch.ElasticsearchChunkIndexService;
import com.argus.rag.storage.service.ObjectStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 文档核心服务，负责文档的上传、查询、删除、预览、下载以及异步 ETL 触发。
 *
 * <p>提供直接上传（小文件）和分片上传完成后的文档持久化。
 * 内部协调对象存储、向量数据库、Elasticsearch 等外部服务。
 * 上传失败时会自动补偿清理已上传的对象存储文件和外部索引。
 *
 * @author DD-RAG Team
 * @since 1.0.0
 */
@Service
@Slf4j
public class DocumentService {

    /** 预览文本最大长度 */
    private static final int PREVIEW_MAX_LENGTH = 200;
    /** 文件名最大长度 */
    private static final int MAX_FILE_NAME_LENGTH = 255;
    /** Content-Type 最大长度 */
    private static final int MAX_CONTENT_TYPE_LENGTH = 128;
    /** 文件扩展名最大长度 */
    private static final int MAX_FILE_EXT_LENGTH = 16;
    /** 直接上传最大文件大小：10MB */
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    /** 支持的上传文件格式 */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "pdf", "docx");

    /** 文档数据访问 */
    private final DocumentMapper documentMapper;
    /** 群组成员权限服务 */
    private final GroupMembershipService groupMembershipService;
    /** 当前用户服务 */
    private final CurrentUserService currentUserService;
    /** 对象存储服务 */
    private final ObjectStorageService objectStorageService;
    /** 向量导入服务 */
    private final VectorIngestionService vectorIngestionService;
    /** Elasticsearch chunk 索引服务 */
    private final ElasticsearchChunkIndexService elasticsearchChunkIndexService;
    /** Spring 事件发布器，用于发布异步 ETL 事件 */
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 构造文档服务，注入所有依赖。
     *
     * @param documentMapper                 文档数据访问层
     * @param groupMembershipService         群组成员权限服务
     * @param currentUserService             当前用户服务
     * @param objectStorageService           对象存储服务
     * @param vectorIngestionService         向量导入服务
     * @param elasticsearchChunkIndexService ES chunk 索引服务
     * @param applicationEventPublisher      事件发布器
     */
    public DocumentService(
            DocumentMapper documentMapper,
            GroupMembershipService groupMembershipService,
            CurrentUserService currentUserService,
            ObjectStorageService objectStorageService,
            VectorIngestionService vectorIngestionService,
            ElasticsearchChunkIndexService elasticsearchChunkIndexService,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.documentMapper = documentMapper;
        this.groupMembershipService = groupMembershipService;
        this.currentUserService = currentUserService;
        this.objectStorageService = objectStorageService;
        this.vectorIngestionService = vectorIngestionService;
        this.elasticsearchChunkIndexService = elasticsearchChunkIndexService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 直接上传文档（小文件模式）。
     *
     * <p>流程：校验文件、上传对象存储、写入文档元数据、发布异步 ETL 事件。
     * 上传失败时会自动补偿清理对象存储文件和外部索引。
     * 需要调用者是群组管理员。
     *
     * @param request       HTTP 请求（用于提取当前用户信息）
     * @param uploadRequest 上传请求（包含文件和 groupId）
     * @return 新创建的文档 ID
     * @throws BusinessException 文件为空、类型不支持、超过大小限制、无权限时抛出
     */
    @Transactional
    public Long uploadDocument(HttpServletRequest request, UploadDocumentRequest uploadRequest) {
        Long groupId = requireGroupId(uploadRequest.getGroupId());
        CurrentUserService.CurrentUser currentUser = requireGroupOwner(groupId);
        MultipartFile file = requireValidFile(uploadRequest.getFile());
        String fileName = extractFileName(file);
        String fileExt = extractFileExt(fileName);
        String bucket = objectStorageService.getDefaultBucket();
        String objectKey = buildObjectKey(groupId, currentUser.userId(), fileExt);
        DocumentEntity document = null;
        log.info("开始上传文档: groupId={}, userId={}, fileName={}, size={}, objectKey={}",
                groupId, currentUser.userId(), fileName, file.getSize(), objectKey);
        uploadFile(bucket, objectKey, file);
        log.info("对象存储上传完成: groupId={}, objectKey={}", groupId, objectKey);
        try {
            document = persistAndFinalizeUploadedDocument(new FinalizedUploadCommand(
                    groupId,
                    currentUser.userId(),
                    fileName,
                    fileExt,
                    normalizeContentType(file.getContentType()),
                    file.getSize(),
                    null,
                    bucket,
                    objectKey
            ));
            return document.getId();
        } catch (RuntimeException exception) {
            log.error("文档上传链路失败: groupId={}, objectKey={}, reason={}",
                    groupId, objectKey, exception.getMessage(), exception);
            compensateExternalIndexes(document);
            compensateUploadedObject(bucket, objectKey, exception);
            throw exception;
        }
    }

    /**
     * 通过复用已有文档创建新文档记录（秒传）。
     *
     * <p>当上传初始化发现相同哈希的 READY 文档时调用，复用对象存储文件创建新文档记录。
     *
     * @param groupId           目的群组 ID
     * @param userId            上传者用户 ID
     * @param existingDocument  已有的 READY 状态文档实体
     * @param fileName          新文档文件名
     * @return 新创建的文档 ID
     * @throws BusinessException 已有文档为 null 或参数校验失败时抛出
     */
    @Transactional
    public Long createInstantUploadedDocument(
            Long groupId,
            Long userId,
            DocumentEntity existingDocument,
            String fileName
    ) {
        if (existingDocument == null) {
            throw new BusinessException("复用文档不存在");
        }
        DocumentEntity document = persistAndFinalizeUploadedDocument(new FinalizedUploadCommand(
                requireGroupId(groupId),
                requirePositiveUserId(userId),
                validateReusableFileName(fileName),
                requireText(existingDocument.getFileExt(), "文件扩展名非法"),
                normalizeContentType(existingDocument.getContentType()),
                requirePositiveFileSize(existingDocument.getFileSize()),
                existingDocument.getFileHash(),
                requireText(existingDocument.getStorageBucket(), "对象存储桶非法"),
                requireText(existingDocument.getStorageObjectKey(), "对象存储路径非法")
        ));
        return document.getId();
    }

    /**
     * 完成分片上传后的文档持久化。
     *
     * <p>由分片上传服务合并分片后调用，参数已经过校验。
     * 写入文档元数据并发布异步 ETL 事件。
     *
     * @param groupId     群组 ID
     * @param userId      上传者用户 ID
     * @param fileName    文件名
     * @param fileExt     文件扩展名
     * @param contentType MIME 类型
     * @param fileSize    文件大小（字节）
     * @param fileHash    文件哈希
     * @param bucket      对象存储桶名称
     * @param objectKey   对象存储 key
     * @return 新创建的文档 ID
     * @throws BusinessException 参数校验失败时抛出
     */
    @Transactional
    public Long finalizeUploadedDocument(
            Long groupId,
            Long userId,
            String fileName,
            String fileExt,
            String contentType,
            Long fileSize,
            String fileHash,
            String bucket,
            String objectKey
    ) {
        DocumentEntity document = persistAndFinalizeUploadedDocument(new FinalizedUploadCommand(
                requireGroupId(groupId),
                requirePositiveUserId(userId),
                normalizeFileName(fileName),
                requireText(fileExt, "文件扩展名非法"),
                normalizeContentType(contentType),
                requirePositiveFileSize(fileSize),
                fileHash,
                requireText(bucket, "对象存储桶非法"),
                requireText(objectKey, "对象存储路径非法")
        ));
        return document.getId();
    }

    /**
     * 查询文档列表。
     *
     * <p>根据查询条件筛选当前用户有权查看的文档，支持按群组、上传者、文件状态等维度过滤。
     *
     * @param request HTTP 请求（用于提取当前用户信息）
     * @param query   查询条件（groupId、上传者、时间范围、文件名、状态等，均为可选）
     * @return 符合条件的文档列表
     * @throws BusinessException 查询参数非法时抛出
     */
    public List<DocumentListItemVO> listDocuments(HttpServletRequest request, DocumentQuery query) {
        DocumentQuery validatedQuery = normalizeQuery(request, query);
        return documentMapper.selectReadableDocuments(validatedQuery);
    }

    /**
     * 软删除文档。
     *
     * <p>将文档标记为已删除，同时清理向量数据和 Elasticsearch 索引。
     * 需要调用者是群组管理员。
     *
     * @param request    HTTP 请求（用于提取当前用户信息）
     * @param groupId    文档所属群组 ID
     * @param documentId 要删除的文档 ID
     * @throws BusinessException 文档不存在、已删除、无权限时抛出
     */
    public void softDeleteDocument(HttpServletRequest request, Long groupId, Long documentId) {
        requireGroupOwner(requireGroupId(groupId));
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("文档ID非法");
        }
        if (documentMapper.markDeleted(documentId, groupId) == 0) {
            throw new BusinessException("文档不存在或已删除");
        }
        vectorIngestionService.deleteDocumentVectors(documentId);
        elasticsearchChunkIndexService.deleteDocumentChunks(documentId);
    }

    /**
     * 重新处理失败的文档。
     *
     * <p>仅允许对状态为 FAILED 的文档执行。将其状态重置为 PROCESSING，
     * 并重新发布异步 ETL 事件触发重新处理。
     * 需要调用者是群组管理员。
     *
     * @param request    HTTP 请求（用于提取当前用户信息）
     * @param groupId    文档所属群组 ID
     * @param documentId 文档 ID
     * @throws BusinessException 文档不存在、非失败状态、状态重置失败、无权限时抛出
     */
    @Transactional
    public void retryFailedDocumentIngestion(HttpServletRequest request, Long groupId, Long documentId) {
        Long requiredGroupId = requireGroupId(groupId);
        requireGroupOwner(requiredGroupId);
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("文档ID非法");
        }
        DocumentEntity document = documentMapper.selectByIdAndGroupId(documentId, requiredGroupId);
        if (document == null) {
            throw new BusinessException("文档不存在或已删除");
        }
        if (!DocumentStatus.FAILED.name().equals(document.getStatus())) {
            throw new BusinessException("仅失败文档支持重新处理");
        }
        int updated = documentMapper.update(null, new LambdaUpdateWrapper<DocumentEntity>()
                .eq(DocumentEntity::getId, documentId)
                .eq(DocumentEntity::getGroupId, requiredGroupId)
                .set(DocumentEntity::getStatus, DocumentStatus.PROCESSING.name())
                .set(DocumentEntity::getFailureReason, null)
                .set(DocumentEntity::getProcessedAt, null)
        );
        if (updated == 0) {
            throw new BusinessException("重置文档状态失败");
        }
        publishIngestionRequestedEvent(documentId, requiredGroupId);
    }

    /**
     * 预览文档文本内容。
     *
     * <p>返回文档前 200 字符的预览文本。仅允许预览状态为 READY 的文档。
     * 需要调用者是群组成员。
     *
     * @param request    HTTP 请求（用于提取当前用户信息）
     * @param groupId    文档所属群组 ID
     * @param documentId 文档 ID
     * @return 文档预览信息（文档 ID、文件名、截断的预览文本）
     * @throws BusinessException 文档不存在、未就绪、暂无可预览内容、无权限时抛出
     */
    public DocumentPreviewVO previewDocument(HttpServletRequest request, Long groupId, Long documentId) {
        Long requiredGroupId = requireGroupId(groupId);
        groupMembershipService.requireGroupReadable(requiredGroupId);
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("文档ID非法");
        }
        DocumentEntity document = documentMapper.selectByIdAndGroupId(documentId, requiredGroupId);
        if (document == null) {
            throw new BusinessException("文档不存在或已删除");
        }
        if (!DocumentStatus.READY.name().equals(document.getStatus())) {
            throw new BusinessException("文档尚未就绪，暂不可预览");
        }
        if (!StringUtils.hasText(document.getPreviewText())) {
            throw new BusinessException("文档暂无可预览内容");
        }
        DocumentPreviewVO preview = new DocumentPreviewVO();
        preview.setDocumentId(document.getId());
        preview.setFileName(document.getFileName());
        preview.setPreviewText(trimPreviewText(document.getPreviewText()));
        return preview;
    }

    /**
     * 获取文档下载信息。
     *
     * <p>从对象存储获取文档原始文件的输入流并封装返回。
     * 仅允许下载状态为 READY 的文档，且必须存在有效的对象存储路径。
     * 需要调用者是群组成员。
     *
     * @param request    HTTP 请求（用于提取当前用户信息）
     * @param groupId    文档所属群组 ID
     * @param documentId 文档 ID
     * @return 下载封装（文件输入流、文件名、Content-Type、文件大小）
     * @throws BusinessException 文档不存在、未就绪、存储信息缺失、无权限时抛出
     */
    public DocumentDownloadVO downloadDocument(HttpServletRequest request, Long groupId, Long documentId) {
        Long requiredGroupId = requireGroupId(groupId);
        groupMembershipService.requireGroupReadable(requiredGroupId);
        if (documentId == null || documentId <= 0) {
            throw new BusinessException("文档ID非法");
        }
        DocumentEntity document = documentMapper.selectByIdAndGroupId(documentId, requiredGroupId);
        if (document == null) {
            throw new BusinessException("文档不存在或已删除");
        }
        if (!DocumentStatus.READY.name().equals(document.getStatus())) {
            throw new BusinessException("文档尚未就绪，暂不可下载");
        }
        if (!StringUtils.hasText(document.getStorageBucket())
                || !StringUtils.hasText(document.getStorageObjectKey())) {
            throw new BusinessException("文档存储信息缺失");
        }
        InputStream inputStream = objectStorageService.getObject(
                document.getStorageBucket(), document.getStorageObjectKey());
        DocumentDownloadVO downloadInfo = new DocumentDownloadVO();
        downloadInfo.setInputStream(inputStream);
        downloadInfo.setFileName(document.getFileName());
        downloadInfo.setContentType(document.getContentType());
        downloadInfo.setFileSize(document.getFileSize());
        return downloadInfo;
    }

    /**
     * 校验 groupId 必须为正数。
     *
     * @param groupId 群组 ID
     * @return 合法的 groupId
     * @throws BusinessException groupId 为 null 或 <= 0 时抛出
     */
    private Long requireGroupId(Long groupId) {
        if (groupId == null || groupId <= 0) {
            throw new BusinessException("groupId 非法");
        }
        return groupId;
    }

    /**
     * 校验当前用户是群组管理员，同时返回用户信息。
     *
     * @param request HTTP 请求
     * @param groupId 群组 ID
     * @return 当前用户信息
     * @throws BusinessException 用户不在群组内或不是管理员时抛出（透传下层异常）
     */
    private CurrentUserService.CurrentUser requireGroupOwner(Long groupId) {
        CurrentUserService.CurrentUser currentUser = groupMembershipService.requireGroupReadable(groupId);
        groupMembershipService.requireGroupOwner(groupId);
        return currentUser;
    }

    /**
     * 校验上传文件不为空且不超过大小限制。
     *
     * @param file 上传的文件
     * @return 校验通过的文件
     * @throws BusinessException 文件为空或超过大小限制时抛出
     */
    private MultipartFile requireValidFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("上传文件超过大小限制");
        }
        return file;
    }

    /**
     * 从 MultipartFile 中提取原始文件名并规范化。
     *
     * @param file 上传的文件
     * @return 规范化后的文件名
     */
    private String extractFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        return normalizeFileName(originalFileName);
    }

    /**
     * 从文件名中提取扩展名并校验是否受支持。
     *
     * @param fileName 文件名
     * @return 小写的文件扩展名
     * @throws BusinessException 扩展名非法或类型不支持时抛出
     */
    private String extractFileExt(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            throw new BusinessException("文件扩展名非法");
        }
        String fileExt = fileName.substring(dotIndex + 1).toLowerCase();
        if (fileExt.length() > MAX_FILE_EXT_LENGTH || !SUPPORTED_EXTENSIONS.contains(fileExt)) {
            throw new BusinessException("文件类型不支持");
        }
        return fileExt;
    }

    /**
     * 构造对象存储的 key 路径。
     *
     * @param groupId  群组 ID
     * @param userId   用户 ID
     * @param fileExt  文件扩展名
     * @return 对象存储 key（格式: groups/{groupId}/users/{userId}/{uuid}.{ext}）
     */
    private String buildObjectKey(Long groupId, Long userId, String fileExt) {
        String fileId = UUID.randomUUID().toString().replace("-", "");
        return "groups/%d/users/%d/%s.%s".formatted(groupId, userId, fileId, fileExt);
    }

    /**
     * 将文件上传至对象存储。
     *
     * @param bucket    存储桶
     * @param objectKey 对象 key
     * @param file      上传的文件
     * @throws BusinessException 读取文件失败或对象存储上传异常时抛出
     */
    private void uploadFile(String bucket, String objectKey, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            objectStorageService.putObject(
                    bucket,
                    objectKey,
                    inputStream,
                    file.getSize(),
                    normalizeContentType(file.getContentType())
            );
        } catch (IOException exception) {
            throw new BusinessException("读取上传文件失败");
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException("文档上传失败");
        }
    }

    /**
     * 补偿清理对象存储中已上传的文件。
     *
     * <p>在文档元数据持久化失败后调用。若补偿清理也失败，将异常附加到原始异常上。
     *
     * @param bucket             存储桶
     * @param objectKey          对象 key
     * @param originalException  原始异常（用于附加补偿失败异常）
     */
    private void compensateUploadedObject(String bucket, String objectKey, RuntimeException originalException) {
        try {
            objectStorageService.deleteObject(bucket, objectKey);
        } catch (RuntimeException compensationException) {
            originalException.addSuppressed(compensationException);
            log.warn(
                    "Failed to compensate uploaded object after metadata persistence failure, bucket={}, objectKey={}, reason={}",
                    bucket,
                    objectKey,
                    compensationException.getMessage()
            );
        }
    }

    /**
     * 补偿清理文档关联的向量数据和 ES 索引。
     *
     * <p>文档上传失败时尝试清理已写入的外部索引，清理失败仅记录日志不中断流程。
     *
     * @param document 已部分创建的文档实体（可能为 null，为 null 则跳过）
     */
    private void compensateExternalIndexes(DocumentEntity document) {
        if (document == null || document.getId() == null) {
            return;
        }
        try {
            vectorIngestionService.deleteDocumentVectors(document.getId());
        } catch (RuntimeException exception) {
            log.warn("文档失败补偿时删除向量失败: documentId={}, reason={}", document.getId(), exception.getMessage());
        }
        try {
            elasticsearchChunkIndexService.deleteDocumentChunks(document.getId());
        } catch (RuntimeException exception) {
            log.warn("文档失败补偿时删除 ES 索引失败: documentId={}, reason={}", document.getId(), exception.getMessage());
        }
    }

    /**
     * 持久化文档元数据并发布异步 ETL 事件。
     *
     * @param command 上传完成命令（包含所有校验过的参数）
     * @return 已持久化的文档实体（含自增 ID）
     */
    private DocumentEntity persistAndFinalizeUploadedDocument(FinalizedUploadCommand command) {
        DocumentEntity document = buildDocument(command);
        documentMapper.insert(document);
        log.info("文档元数据入库完成: documentId={}, groupId={}, status={}",
                document.getId(), command.groupId(), document.getStatus());
        publishIngestionRequestedEvent(document.getId(), command.groupId());
        log.info("已发布文档异步ETL事件: documentId={}, groupId={}", document.getId(), command.groupId());
        return document;
    }

    /**
     * 发布文档摄入请求事件。
     *
     * @param documentId 文档 ID
     * @param groupId    群组 ID
     */
    private void publishIngestionRequestedEvent(Long documentId, Long groupId) {
        applicationEventPublisher.publishEvent(new DocumentIngestionRequestedEvent(documentId, groupId));
    }

    /**
     * 规范化 Content-Type，空值默认为 application/octet-stream。
     *
     * @param contentType 原始 Content-Type
     * @return 规范化后的 Content-Type
     * @throws BusinessException Content-Type 过长时抛出
     */
    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "application/octet-stream";
        }
        if (contentType.length() > MAX_CONTENT_TYPE_LENGTH) {
            throw new BusinessException("文件类型描述过长");
        }
        return contentType;
    }

    /**
     * 规范化查询参数：设置当前用户、校验权限、规范化过滤条件。
     *
     * @param request HTTP 请求
     * @param query   原始查询参数
     * @return 规范化后的查询参数
     * @throws BusinessException 查询参数非法时抛出
     */
    private DocumentQuery normalizeQuery(HttpServletRequest request, DocumentQuery query) {
        DocumentQuery safeQuery = query == null ? new DocumentQuery() : query;
        CurrentUserService.CurrentUser currentUser = currentUserService.requireBusinessUser();
        safeQuery.setCurrentUserId(currentUser.userId());
        if (safeQuery.getGroupId() != null) {
            groupMembershipService.requireGroupReadable(requireGroupId(safeQuery.getGroupId()));
        }
        if (safeQuery.getUploaderUserId() != null && safeQuery.getUploaderUserId() <= 0) {
            throw new BusinessException("uploaderUserId 非法");
        }
        if (safeQuery.getUploadedFrom() != null
                && safeQuery.getUploadedTo() != null
                && safeQuery.getUploadedFrom().isAfter(safeQuery.getUploadedTo())) {
            throw new BusinessException("uploadedFrom 不能晚于 uploadedTo");
        }
        if (StringUtils.hasText(safeQuery.getGroupRelation())) {
            safeQuery.setGroupRelation(normalizeGroupRelation(safeQuery.getGroupRelation()));
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            safeQuery.setStatus(normalizeStatus(safeQuery.getStatus()));
        }
        if (StringUtils.hasText(safeQuery.getFileName())) {
            safeQuery.setFileName(safeQuery.getFileName().trim());
        }
        return safeQuery;
    }

    /**
     * 规范化群组关系查询参数。
     *
     * <p>OWNER/OWNED 统一归为 OWNED，MEMBER/JOINED 统一归为 JOINED。
     *
     * @param groupRelation 原始群组关系
     * @return 规范化后的群组关系（OWNED 或 JOINED）
     * @throws BusinessException 参数不合法时抛出
     */
    private String normalizeGroupRelation(String groupRelation) {
        String normalized = groupRelation.trim().toUpperCase();
        return switch (normalized) {
            case "OWNER", "OWNED" -> "OWNED";
            case "MEMBER", "JOINED" -> "JOINED";
            default -> throw new BusinessException("groupRelation 非法");
        };
    }

    /**
     * 规范化文档状态查询参数，校验是否为合法的 DocumentStatus 枚举值。
     *
     * @param status 原始状态字符串
     * @return 规范化后的状态（DocumentStatus.name()）
     * @throws BusinessException 状态值不合法时抛出
     */
    private String normalizeStatus(String status) {
        try {
            return DocumentStatus.valueOf(status.trim().toUpperCase()).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("status 非法");
        }
    }

    /**
     * 根据上传命令构建 DocumentEntity 对象。
     *
     * @param command 上传完成命令
     * @return 初始化完成的文档实体（状态为 PROCESSING）
     */
    private DocumentEntity buildDocument(FinalizedUploadCommand command) {
        LocalDateTime now = LocalDateTime.now();
        DocumentEntity document = new DocumentEntity();
        document.setGroupId(command.groupId());
        document.setUploaderUserId(command.userId());
        document.setFileName(command.fileName());
        document.setFileExt(command.fileExt());
        document.setContentType(command.contentType());
        document.setFileSize(command.fileSize());
        document.setFileHash(command.fileHash());
        document.setStorageBucket(command.bucket());
        document.setStorageObjectKey(command.objectKey());
        document.setStatus(DocumentStatus.PROCESSING.name());
        document.setDeleted(false);
        document.setUploadedAt(now);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        return document;
    }

    /**
     * 校验 userId 必须为正数。
     *
     * @param userId 用户 ID
     * @return 合法的 userId
     * @throws BusinessException userId 为 null 或 <= 0 时抛出
     */
    private Long requirePositiveUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("userId 非法");
        }
        return userId;
    }

    /**
     * 校验 fileSize 必须为正数。
     *
     * @param fileSize 文件大小
     * @return 合法的 fileSize
     * @throws BusinessException fileSize 为 null 或 <= 0 时抛出
     */
    private long requirePositiveFileSize(Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            throw new BusinessException("fileSize 非法");
        }
        return fileSize;
    }

    /**
     * 截断预览文本至最大长度。
     *
     * @param previewText 原始预览文本
     * @return 截断后的预览文本
     */
    private String trimPreviewText(String previewText) {
        if (!StringUtils.hasText(previewText) || previewText.length() <= PREVIEW_MAX_LENGTH) {
            return previewText;
        }
        return previewText.substring(0, PREVIEW_MAX_LENGTH);
    }

    /**
     * 校验可复用文件名（委托 normalizeFileName 实现）。
     *
     * @param fileName 文件名
     * @return 规范化后的文件名
     */
    private String validateReusableFileName(String fileName) {
        return normalizeFileName(fileName);
    }

    /**
     * 校验字符串必须有文本内容。
     *
     * @param value   待校验的字符串
     * @param message 校验失败时的错误消息
     * @return trim 后的字符串
     * @throws BusinessException value 为空或仅含空白时抛出
     */
    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    /**
     * 规范化文件名：清洗路径、去除父目录引用、校验长度。
     *
     * @param rawFileName 原始文件名
     * @return 规范化后的文件名
     * @throws BusinessException 文件名为空或过长时抛出
     */
    private String normalizeFileName(String rawFileName) {
        if (!StringUtils.hasText(rawFileName)) {
            throw new BusinessException("文件名非法");
        }
        String normalizedFileName = StringUtils.cleanPath(rawFileName.trim());
        String fileName = normalizedFileName.substring(normalizedFileName.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(fileName) || fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new BusinessException("文件名非法");
        }
        return fileName;
    }

    /**
     * 上传完成命令，封装所有经过校验的文档参数。
     *
     * @param groupId     群组 ID
     * @param userId      上传者用户 ID
     * @param fileName    文件名
     * @param fileExt     文件扩展名
     * @param contentType MIME 类型
     * @param fileSize    文件大小
     * @param fileHash    文件哈希
     * @param bucket      对象存储桶
     * @param objectKey   对象存储 key
     */
    record FinalizedUploadCommand(
            Long groupId,
            Long userId,
            String fileName,
            String fileExt,
            String contentType,
            Long fileSize,
            String fileHash,
            String bucket,
            String objectKey
    ) {
    }
}
