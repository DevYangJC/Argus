package com.argus.rag.document.service;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.argus.rag.document.mapper.DocumentMapper;
import com.argus.rag.document.mapper.DocumentUploadChunkMapper;
import com.argus.rag.document.mapper.DocumentUploadSessionMapper;
import com.argus.rag.document.model.dto.UploadChunkRequest;
import com.argus.rag.document.model.dto.UploadInitRequest;
import com.argus.rag.document.model.entity.DocumentEntity;
import com.argus.rag.document.model.entity.DocumentUploadChunkEntity;
import com.argus.rag.document.model.entity.DocumentUploadSessionEntity;
import com.argus.rag.document.model.vo.UploadInitResponse;
import com.argus.rag.document.model.vo.UploadStatusResponse;
import com.argus.rag.groupmembership.service.GroupMembershipService;
import com.argus.rag.storage.service.ObjectStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 大文件分片上传服务。
 *
 * <p>支持将文件（最大 256MB）拆分为多个分片上传，每个分片最大 10MB。
 * 上传流程：初始化会话（init） -> 逐个上传分片（upload chunks） -> 合并完成（complete）。
 * 支持文件哈希去重（秒传复用）和可续传会话恢复。
 *
 * <p>上传会话在 24 小时后过期。
 *
 * @author DD-RAG Team
 * @since 1.0.0
 */
@Service
public class DocumentUploadService {

    /** 文件名最大长度 */
    private static final int MAX_FILE_NAME_LENGTH = 255;
    /** Content-Type 最大长度 */
    private static final int MAX_CONTENT_TYPE_LENGTH = 128;
    /** 文件哈希最大长度 */
    private static final int MAX_FILE_HASH_LENGTH = 128;
    /** 文件扩展名最大长度 */
    private static final int MAX_FILE_EXT_LENGTH = 16;
    /** 分片上传最大文件大小：256MB */
    private static final long MAX_FILE_SIZE = 256L * 1024 * 1024;
    /** 单个分片的最大大小：10MB */
    private static final long MAX_CHUNK_SIZE = 10L * 1024 * 1024;
    /** 上传会话过期时长：24 小时 */
    private static final long SESSION_EXPIRE_HOURS = 24L;
    /** 支持的上传文件格式 */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "pdf", "docx");
    /** 上传会话状态：已初始化 */
    private static final String UPLOAD_STATUS_INIT = "INIT";
    /** 上传会话状态：上传中 */
    private static final String UPLOAD_STATUS_UPLOADING = "UPLOADING";
    /** 上传会话状态：正在合并 */
    private static final String UPLOAD_STATUS_COMPLETING = "COMPLETING";
    /** 上传会话状态：已完成 */
    private static final String UPLOAD_STATUS_COMPLETED = "COMPLETED";
    /** 分片默认 MIME 类型 */
    private static final String OCTET_STREAM = "application/octet-stream";

    /** 文档数据访问 */
    private final DocumentMapper documentMapper;
    /** 上传会话数据访问 */
    private final DocumentUploadSessionMapper documentUploadSessionMapper;
    /** 上传分片数据访问 */
    private final DocumentUploadChunkMapper documentUploadChunkMapper;
    /** 群组成员权限服务 */
    private final GroupMembershipService groupMembershipService;
    /** 文档核心服务 */
    private final DocumentService documentService;
    /** 对象存储服务 */
    private final ObjectStorageService objectStorageService;

    /**
     * 构造分片上传服务，注入所有依赖。
     *
     * @param documentMapper                文档数据访问层
     * @param documentUploadSessionMapper   上传会话数据访问层
     * @param documentUploadChunkMapper     上传分片数据访问层
     * @param groupMembershipService        群组成员权限服务
     * @param documentService               文档核心服务
     * @param objectStorageService          对象存储服务
     */
    public DocumentUploadService(
            DocumentMapper documentMapper,
            DocumentUploadSessionMapper documentUploadSessionMapper,
            DocumentUploadChunkMapper documentUploadChunkMapper,
            GroupMembershipService groupMembershipService,
            DocumentService documentService,
            ObjectStorageService objectStorageService
    ) {
        this.documentMapper = documentMapper;
        this.documentUploadSessionMapper = documentUploadSessionMapper;
        this.documentUploadChunkMapper = documentUploadChunkMapper;
        this.groupMembershipService = groupMembershipService;
        this.documentService = documentService;
        this.objectStorageService = objectStorageService;
    }

    /**
     * 初始化分片上传会话。
     *
     * <p>按优先级依次尝试：
     * <ol>
     *   <li>秒传：存在相同哈希的 READY 文档则直接复用</li>
     *   <li>断点续传：存在相同哈希的未过期会话则恢复</li>
     *   <li>新建会话：创建全新的上传会话</li>
     * </ol>
     * 需要调用者是群组管理员。
     *
     * @param request       HTTP 请求（用于提取当前用户信息）
     * @param uploadRequest 上传初始化请求（文件名、大小、哈希、分片参数等）
     * @return 上传初始化响应（秒传返回 documentId，续传/新建返回 uploadId 和分片参数）
     * @throws BusinessException 参数校验失败、文件类型不支持、大小超限、无权限时抛出
     */
    @Transactional
    public UploadInitResponse initUpload(HttpServletRequest request, UploadInitRequest uploadRequest) {
        NormalizedInitRequest normalizedRequest = validateInitRequest(uploadRequest);
        Long groupId = normalizedRequest.groupId();
        CurrentUserService.CurrentUser currentUser = groupMembershipService.requireGroupOwner(groupId);
        DocumentEntity existingDocument = documentMapper.selectByGroupIdAndFileHash(groupId, normalizedRequest.fileHash());
        if (existingDocument != null && "READY".equals(existingDocument.getStatus())) {
            Long documentId = documentService.createInstantUploadedDocument(
                    groupId,
                    currentUser.userId(),
                    existingDocument,
                    normalizedRequest.fileName()
            );
            return UploadInitResponse.instant(documentId);
        }
        DocumentUploadSessionEntity existingSession = documentUploadSessionMapper.selectLatestReusableSession(
                groupId,
                currentUser.userId(),
                normalizedRequest.fileHash()
        );
        if (existingSession != null) {
            List<Integer> uploadedChunks = documentUploadChunkMapper.selectByUploadId(existingSession.getUploadId()).stream()
                    .map(DocumentUploadChunkEntity::getChunkIndex)
                    .toList();
            return UploadInitResponse.uploadSession(
                    existingSession.getUploadId(),
                    uploadedChunks,
                    existingSession.getChunkSize(),
                    existingSession.getChunkCount()
            );
        }
        DocumentUploadSessionEntity session = buildUploadSession(groupId, currentUser.userId(), normalizedRequest);
        documentUploadSessionMapper.insert(session);
        return UploadInitResponse.uploadSession(session.getUploadId(), session.getChunkSize(), session.getChunkCount());
    }

    /**
     * 上传单个分片。
     *
     * <p>校验分片序号和大小，将分片上传至对象存储，记录分片元数据到数据库。
     * 使用 upsert 支持同一分片重复上传（幂等）。
     * 需要上传会话属于当前用户且未过期、未完成。
     *
     * @param request       HTTP 请求（用于提取当前用户信息）
     * @param uploadRequest 分片上传请求（uploadId、chunkIndex、分片数据和哈希）
     * @return 当前已上传的分片索引列表
     * @throws BusinessException 会话无效、分片越界、分片数据为空或超限时抛出
     */
    @Transactional
    public List<Integer> uploadChunk(HttpServletRequest request, UploadChunkRequest uploadRequest) {
        DocumentUploadSessionEntity session = requireOwnedActiveSession(request, uploadRequest.uploadId());
        MultipartFile chunk = requireChunk(uploadRequest, session);
        String chunkHash = normalizeFileHash(uploadRequest.chunkHash());
        String objectKey = buildChunkObjectKey(session.getGroupId(), session.getUploadId(), uploadRequest.chunkIndex());
        LocalDateTime now = LocalDateTime.now();
        try {
            objectStorageService.putObject(
                    session.getStorageBucket(),
                    objectKey,
                    chunk.getInputStream(),
                    chunk.getSize(),
                    OCTET_STREAM
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("分片上传失败");
        }

        DocumentUploadChunkEntity uploadChunk = new DocumentUploadChunkEntity();
        uploadChunk.setUploadId(session.getUploadId());
        uploadChunk.setChunkIndex(uploadRequest.chunkIndex());
        uploadChunk.setChunkSize(chunk.getSize());
        uploadChunk.setChunkHash(chunkHash);
        uploadChunk.setStorageBucket(session.getStorageBucket());
        uploadChunk.setStorageObjectKey(objectKey);
        uploadChunk.setUploadedAt(now);
        uploadChunk.setCreatedAt(now);
        uploadChunk.setUpdatedAt(now);
        documentUploadChunkMapper.upsert(uploadChunk);
        documentUploadSessionMapper.update(null, new LambdaUpdateWrapper<DocumentUploadSessionEntity>()
                .eq(DocumentUploadSessionEntity::getUploadId, session.getUploadId())
                .set(DocumentUploadSessionEntity::getStatus, UPLOAD_STATUS_UPLOADING)
                .set(DocumentUploadSessionEntity::getMergedObjectKey, null)
                .set(DocumentUploadSessionEntity::getUpdatedAt, now)
        );
        return documentUploadChunkMapper.selectByUploadId(session.getUploadId()).stream()
                .map(DocumentUploadChunkEntity::getChunkIndex)
                .toList();
    }

    /**
     * 完成分片上传，合并所有分片为最终文档。
     *
     * <p>流程：
     * <ol>
     *   <li>校验所有分片已按序就位</li>
     *   <li>调用对象存储合并分片为单一对象</li>
     *   <li>委托 {@link DocumentService#finalizeUploadedDocument} 创建文档记录</li>
     *   <li>将上传会话标记为 COMPLETED</li>
     * </ol>
     * 合并失败时会尝试删除已合并的临时对象。
     * 需要上传会话属于当前用户且未过期、未完成。
     *
     * @param request  HTTP 请求（用于提取当前用户信息）
     * @param uploadId 上传会话 ID
     * @return 新创建的文档 ID
     * @throws BusinessException 会话无效、分片缺失、合并失败、文档持久化失败时抛出
     */
    @Transactional
    public Long completeUpload(HttpServletRequest request, String uploadId) {
        DocumentUploadSessionEntity session = requireOwnedActiveSession(request, uploadId);
        List<DocumentUploadChunkEntity> chunks = documentUploadChunkMapper.selectByUploadId(uploadId).stream()
                .sorted(Comparator.comparing(DocumentUploadChunkEntity::getChunkIndex))
                .toList();
        ensureAllChunksPresent(session, chunks);
        String objectKey = buildFinalObjectKey(session);
        LocalDateTime now = LocalDateTime.now();
        documentUploadSessionMapper.update(null, new LambdaUpdateWrapper<DocumentUploadSessionEntity>()
                .eq(DocumentUploadSessionEntity::getUploadId, uploadId)
                .set(DocumentUploadSessionEntity::getStatus, UPLOAD_STATUS_COMPLETING)
                .set(DocumentUploadSessionEntity::getMergedObjectKey, null)
                .set(DocumentUploadSessionEntity::getUpdatedAt, now)
        );
        try {
            objectStorageService.composeObject(
                    session.getStorageBucket(),
                    objectKey,
                    chunks.stream().map(DocumentUploadChunkEntity::getStorageObjectKey).toList(),
                    session.getContentType()
            );
            Long documentId = documentService.finalizeUploadedDocument(
                    session.getGroupId(),
                    session.getUploaderUserId(),
                    session.getFileName(),
                    session.getFileExt(),
                    session.getContentType(),
                    session.getFileSize(),
                    session.getFileHash(),
                    session.getStorageBucket(),
                    objectKey
            );
            documentUploadSessionMapper.update(null, new LambdaUpdateWrapper<DocumentUploadSessionEntity>()
                    .eq(DocumentUploadSessionEntity::getUploadId, uploadId)
                    .set(DocumentUploadSessionEntity::getStatus, UPLOAD_STATUS_COMPLETED)
                    .set(DocumentUploadSessionEntity::getMergedObjectKey, objectKey)
                    .set(DocumentUploadSessionEntity::getUpdatedAt, LocalDateTime.now())
            );
            return documentId;
        } catch (RuntimeException exception) {
            try {
                objectStorageService.deleteObject(session.getStorageBucket(), objectKey);
            } catch (RuntimeException ignored) {
            }
            throw exception;
        }
    }

    /**
     * 查询上传会话的当前状态。
     *
     * <p>返回会话状态、已上传分片索引列表、已上传分片数量、总分片数。
     * 需要上传会话属于当前用户且未过期、未完成。
     *
     * @param request  HTTP 请求（用于提取当前用户信息）
     * @param uploadId 上传会话 ID
     * @return 上传状态信息
     * @throws BusinessException 会话无效时抛出
     */
    public UploadStatusResponse getUploadStatus(HttpServletRequest request, String uploadId) {
        DocumentUploadSessionEntity session = requireOwnedActiveSession(request, uploadId);
        List<Integer> uploadedChunks = documentUploadChunkMapper.selectByUploadId(uploadId).stream()
                .map(DocumentUploadChunkEntity::getChunkIndex)
                .toList();
        return new UploadStatusResponse(
                session.getStatus(),
                uploadedChunks,
                uploadedChunks.size(),
                session.getChunkCount()
        );
    }

    /**
     * 校验并规范化上传初始化请求中的所有参数。
     *
     * @param uploadRequest 原始请求
     * @return 规范化后的请求参数
     * @throws BusinessException 请求为 null 或任一参数非法时抛出
     */
    private NormalizedInitRequest validateInitRequest(UploadInitRequest uploadRequest) {
        if (uploadRequest == null) {
            throw new BusinessException("上传初始化请求不能为空");
        }
        Long groupId = requireGroupId(uploadRequest.groupId());
        String fileName = sanitizeFileName(uploadRequest.fileName());
        String fileExt = extractFileExt(fileName);
        long fileSize = requirePositive(uploadRequest.fileSize(), "fileSize 非法");
        if (fileSize > MAX_FILE_SIZE) {
            throw new BusinessException("上传文件超过大小限制");
        }
        String contentType = normalizeContentType(uploadRequest.contentType());
        String fileHash = normalizeFileHash(uploadRequest.fileHash());
        long chunkSize = requirePositive(uploadRequest.chunkSize(), "chunkSize 非法");
        if (chunkSize > MAX_CHUNK_SIZE) {
            throw new BusinessException("chunkSize 超过限制");
        }
        int chunkCount = requirePositive(uploadRequest.chunkCount(), "chunkCount 非法");
        long expectedChunkCount = (fileSize + chunkSize - 1) / chunkSize;
        if (chunkCount != expectedChunkCount) {
            throw new BusinessException("chunkCount 与文件大小不匹配");
        }
        return new NormalizedInitRequest(groupId, fileName, fileExt, fileSize, contentType, fileHash, chunkSize, chunkCount);
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
     * 清洗文件名：去除路径、父目录引用，校验长度。
     *
     * @param fileName 原始文件名
     * @return 清洗后的文件名
     * @throws BusinessException 文件名为空或过长时抛出
     */
    private String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException("文件名非法");
        }
        String normalizedFileName = StringUtils.cleanPath(fileName.trim());
        String sanitizedFileName = normalizedFileName.substring(normalizedFileName.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(sanitizedFileName) || sanitizedFileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new BusinessException("文件名非法");
        }
        return sanitizedFileName;
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
        String normalizedContentType = contentType.trim();
        if (normalizedContentType.length() > MAX_CONTENT_TYPE_LENGTH) {
            throw new BusinessException("文件类型描述过长");
        }
        return normalizedContentType;
    }

    /**
     * 规范化文件哈希，校验非空且长度合法。
     *
     * @param fileHash 原始文件哈希
     * @return 规范化后的文件哈希
     * @throws BusinessException fileHash 为空或过长时抛出
     */
    private String normalizeFileHash(String fileHash) {
        if (!StringUtils.hasText(fileHash)) {
            throw new BusinessException("fileHash 非法");
        }
        String normalizedFileHash = fileHash.trim();
        if (normalizedFileHash.length() > MAX_FILE_HASH_LENGTH) {
            throw new BusinessException("fileHash 非法");
        }
        return normalizedFileHash;
    }

    /**
     * 校验 Long 值必须为正数。
     *
     * @param value   待校验的值
     * @param message 校验失败时的错误消息
     * @return 合法的值
     * @throws BusinessException value 为 null 或 <= 0 时抛出
     */
    private long requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new BusinessException(message);
        }
        return value;
    }

    /**
     * 校验 Integer 值必须为正数。
     *
     * @param value   待校验的值
     * @param message 校验失败时的错误消息
     * @return 合法的值
     * @throws BusinessException value 为 null 或 <= 0 时抛出
     */
    private int requirePositive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new BusinessException(message);
        }
        return value;
    }

    /**
     * 查询并校验上传会话：存在、属于当前用户、未过期、未完成。
     *
     * @param request  HTTP 请求（用于提取当前用户信息）
     * @param uploadId 上传会话 ID
     * @return 合法的上传会话实体
     * @throws BusinessException uploadId 为空、会话不存在、不属于当前用户、已过期、已完成时抛出
     */
    private DocumentUploadSessionEntity requireOwnedActiveSession(HttpServletRequest request, String uploadId) {
        if (!StringUtils.hasText(uploadId)) {
            throw new BusinessException("uploadId 非法");
        }
        DocumentUploadSessionEntity session = documentUploadSessionMapper.selectByUploadId(uploadId.trim());
        if (session == null) {
            throw new BusinessException("上传会话不存在");
        }
        CurrentUserService.CurrentUser currentUser = groupMembershipService.requireGroupOwner(session.getGroupId());
        if (!currentUser.userId().equals(session.getUploaderUserId())) {
            throw new BusinessException("上传会话不属于当前用户");
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("上传会话已过期");
        }
        if (UPLOAD_STATUS_COMPLETED.equals(session.getStatus())) {
            throw new BusinessException("上传会话已完成");
        }
        return session;
    }

    /**
     * 校验分片上传请求：非空、chunkIndex 在合法范围内、分片数据不为空且不超限。
     *
     * @param uploadRequest 分片上传请求
     * @param session       对应的上传会话
     * @return 校验通过的分片 MultipartFile
     * @throws BusinessException 请求为空、chunkIndex 越界、分片为空或超限时抛出
     */
    private MultipartFile requireChunk(UploadChunkRequest uploadRequest, DocumentUploadSessionEntity session) {
        if (uploadRequest == null) {
            throw new BusinessException("分片上传请求不能为空");
        }
        if (uploadRequest.chunkIndex() == null
                || uploadRequest.chunkIndex() < 0
                || uploadRequest.chunkIndex() >= session.getChunkCount()) {
            throw new BusinessException("chunkIndex 非法");
        }
        MultipartFile chunk = uploadRequest.chunk();
        if (chunk == null || chunk.isEmpty()) {
            throw new BusinessException("上传分片不能为空");
        }
        if (chunk.getSize() > session.getChunkSize()) {
            throw new BusinessException("上传分片超过大小限制");
        }
        return chunk;
    }

    /**
     * 确保所有分片已按序就位，数量和索引必须匹配。
     *
     * @param session 上传会话
     * @param chunks  已上传的分片列表
     * @throws BusinessException 分片数量不匹配或有缺失时抛出
     */
    private void ensureAllChunksPresent(DocumentUploadSessionEntity session, List<DocumentUploadChunkEntity> chunks) {
        if (chunks.size() != session.getChunkCount()) {
            throw new BusinessException("缺少分片，无法完成上传");
        }
        for (int index = 0; index < session.getChunkCount(); index++) {
            if (!Integer.valueOf(index).equals(chunks.get(index).getChunkIndex())) {
                throw new BusinessException("缺少分片，无法完成上传");
            }
        }
    }

    /**
     * 构造分片的对象存储 key。
     *
     * @param groupId    群组 ID
     * @param uploadId   上传会话 ID
     * @param chunkIndex 分片序号
     * @return 分片存储 key（格式: uploads/{groupId}/{uploadId}/chunks/{chunkIndex}）
     */
    private String buildChunkObjectKey(Long groupId, String uploadId, Integer chunkIndex) {
        return "uploads/%d/%s/chunks/%d".formatted(groupId, uploadId, chunkIndex);
    }

    /**
     * 构造合并后最终文档的对象存储 key。
     *
     * @param session 上传会话
     * @return 最终文档存储 key（格式: groups/{groupId}/users/{userId}/{uuid}.{ext}）
     */
    private String buildFinalObjectKey(DocumentUploadSessionEntity session) {
        String fileId = UUID.randomUUID().toString().replace("-", "");
        return "groups/%d/users/%d/%s.%s".formatted(
                session.getGroupId(),
                session.getUploaderUserId(),
                fileId,
                session.getFileExt()
        );
    }

    /**
     * 构建新的上传会话实体。
     *
     * @param groupId       群组 ID
     * @param userId        用户 ID
     * @param uploadRequest 规范化后的初始化请求
     * @return 初始化完成的上传会话实体（状态为 INIT，24 小时后过期）
     */
    private DocumentUploadSessionEntity buildUploadSession(Long groupId, Long userId, NormalizedInitRequest uploadRequest) {
        LocalDateTime now = LocalDateTime.now();
        DocumentUploadSessionEntity session = new DocumentUploadSessionEntity();
        session.setUploadId(UUID.randomUUID().toString().replace("-", ""));
        session.setGroupId(groupId);
        session.setUploaderUserId(userId);
        session.setFileName(uploadRequest.fileName());
        session.setFileExt(uploadRequest.fileExt());
        session.setContentType(uploadRequest.contentType());
        session.setFileSize(uploadRequest.fileSize());
        session.setFileHash(uploadRequest.fileHash());
        session.setChunkSize(uploadRequest.chunkSize());
        session.setChunkCount(uploadRequest.chunkCount());
        session.setStatus(UPLOAD_STATUS_INIT);
        session.setStorageBucket(objectStorageService.getDefaultBucket());
        session.setMergedObjectKey(null);
        session.setExpiresAt(now.plusHours(SESSION_EXPIRE_HOURS));
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return session;
    }

    /**
     * 规范化后的上传初始化请求参数，所有字段均已校验。
     *
     * @param groupId     群组 ID
     * @param fileName    文件名
     * @param fileExt     文件扩展名
     * @param fileSize    文件大小（字节）
     * @param contentType MIME 类型
     * @param fileHash    文件哈希
     * @param chunkSize   每个分片大小（字节）
     * @param chunkCount  总分片数
     */
    private record NormalizedInitRequest(
            Long groupId,
            String fileName,
            String fileExt,
            Long fileSize,
            String contentType,
            String fileHash,
            Long chunkSize,
            Integer chunkCount
    ) {
    }
}
