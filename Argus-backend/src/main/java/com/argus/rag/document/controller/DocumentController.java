package com.argus.rag.document.controller;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.common.api.ApiResponse;
import com.argus.rag.document.model.dto.DocumentQuery;
import com.argus.rag.document.model.dto.UploadChunkRequest;
import com.argus.rag.document.model.dto.UploadDocumentRequest;
import com.argus.rag.document.model.dto.UploadInitRequest;
import com.argus.rag.document.model.vo.DocumentDownloadVO;
import com.argus.rag.document.model.vo.DocumentListItemVO;
import com.argus.rag.document.model.vo.DocumentPreviewVO;
import com.argus.rag.document.model.vo.UploadInitResponse;
import com.argus.rag.document.model.vo.UploadStatusResponse;
import com.argus.rag.document.service.DocumentDeleteService;
import com.argus.rag.document.service.DocumentDownloadService;
import com.argus.rag.document.service.DocumentPreviewService;
import com.argus.rag.document.service.DocumentQueryService;
import com.argus.rag.document.service.DocumentUploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文档管理 REST 控制器，提供文档上传、查询、删除、预览、下载等 API。
 *
 * <p>上传支持两种模式：
 * <ul>
 *   <li>分片上传（init / upload chunks / complete），适用于大文件（最大 256MB）</li>
 *   <li>直接上传（/upload），适用于小文件（最大 10MB）</li>
 * </ul>
 *
 * <p>所有接口需要用户认证，并对群组成员/管理员做权限校验。
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    /** 文档上传服务（直接上传 + 分片上传） */
    private final DocumentUploadService documentUploadService;
    /** 文档查询服务 */
    private final DocumentQueryService documentQueryService;
    /** 文档删除与重试服务 */
    private final DocumentDeleteService documentDeleteService;
    /** 文档预览服务 */
    private final DocumentPreviewService documentPreviewService;
    /** 文档下载服务 */
    private final DocumentDownloadService documentDownloadService;
    /** 当前用户服务 */
    private final CurrentUserService currentUserService;

    /**
     * 构造文档控制器，注入所有拆分后的文档服务和当前用户服务。
     */
    public DocumentController(DocumentUploadService documentUploadService,
                              DocumentQueryService documentQueryService,
                              DocumentDeleteService documentDeleteService,
                              DocumentPreviewService documentPreviewService,
                              DocumentDownloadService documentDownloadService,
                              CurrentUserService currentUserService) {
        this.documentUploadService = documentUploadService;
        this.documentQueryService = documentQueryService;
        this.documentDeleteService = documentDeleteService;
        this.documentPreviewService = documentPreviewService;
        this.documentDownloadService = documentDownloadService;
        this.currentUserService = currentUserService;
    }

    /**
     * 初始化分片上传会话。
     *
     * <p>根据文件哈希判断是否已有相同文件，若存在则秒传复用；否则创建新的上传会话。
     * 需要调用者是群组管理员。
     *
     * @param uploadRequest 上传初始化请求（包含文件名、大小、哈希、分片大小和分片数量等）
     * @param request       HTTP 请求（用于提取当前用户信息）
     * @return 上传初始化响应，包含 uploadId 及分片参数；若秒传成功则返回已完成文档 ID
     * @throws BusinessException 参数校验失败、群组不存在、无权限时抛出
     */
    @PostMapping(path = "/upload/init", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<UploadInitResponse> initUpload(
            @RequestBody UploadInitRequest uploadRequest,
            HttpServletRequest request) {
        return ApiResponse.success(documentUploadService.initUpload(request, uploadRequest));
    }

    /**
     * 上传单个分片。
     *
     * <p>接收 multipart/form-data 格式的分片数据，校验分片序号和大小，上传至对象存储。
     * 返回当前上传会话的最新状态。
     *
     * @param uploadRequest 分片上传请求（包含 uploadId、chunkIndex、分片数据和哈希）
     * @param request       HTTP 请求（用于提取当前用户信息）
     * @return 当前上传状态，包含已上传分片列表和进度信息
     * @throws BusinessException 会话不存在、分片序号越界、分片数据为空或超限时抛出
     */
    @PostMapping(path = "/upload/chunks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadStatusResponse> uploadChunk(
            @ModelAttribute UploadChunkRequest uploadRequest,
            HttpServletRequest request) {
        documentUploadService.uploadChunk(request, uploadRequest);
        return ApiResponse.success(documentUploadService.getUploadStatus(request, uploadRequest.uploadId()));
    }

    /**
     * 查询分片上传会话的当前状态。
     *
     * @param uploadId 上传会话 ID
     * @param request  HTTP 请求（用于提取当前用户信息）
     * @return 上传状态，包含已上传分片索引列表、已上传分片数量、总分片数
     * @throws BusinessException 会话不存在、不属于当前用户、已过期或已完成时抛出
     */
    @GetMapping("/upload/{uploadId}")
    public ApiResponse<UploadStatusResponse> getUploadStatus(
            @PathVariable String uploadId,
            HttpServletRequest request) {
        return ApiResponse.success(documentUploadService.getUploadStatus(request, uploadId));
    }

    /**
     * 完成分片上传，合并所有分片为最终文档。
     *
     * <p>校验所有分片已就位，调用对象存储合并分片，创建关联的文档记录并触发异步 ETL 处理。
     *
     * @param uploadId 上传会话 ID
     * @param request  HTTP 请求（用于提取当前用户信息）
     * @return 新创建的文档 ID
     * @throws BusinessException 会话不存在、分片缺失、无权限、合并存储失败时抛出
     */
    @PostMapping("/upload/{uploadId}/complete")
    public ApiResponse<Long> completeUpload(
            @PathVariable String uploadId,
            HttpServletRequest request) {
        return ApiResponse.success(documentUploadService.completeUpload(request, uploadId));
    }

    /**
     * 直接上传文档（无需分片，适用于小文件）。
     *
     * <p>接收 multipart/form-data 格式的文件，上传至对象存储并创建文档记录。
     * 文件大小限制为 10MB，支持 txt / md / pdf / docx 格式。需要调用者是群组管理员。
     * Controller 层负责提取用户身份后传入 Service 层。
     *
     * @param uploadRequest 上传请求（包含文件和 groupId）
     * @return 新创建的文档 ID，文档将以 PROCESSING 状态开始异步 ETL
     * @throws BusinessException 文件为空、类型不支持、超过大小限制、无权限时抛出
     */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Long> uploadDocument(
            @ModelAttribute UploadDocumentRequest uploadRequest) {
        CurrentUserService.CurrentUser user = currentUserService.requireBusinessUser();
        return ApiResponse.success(documentUploadService.uploadDocument(user.userId(), uploadRequest));
    }

    /**
     * 查询文档列表。
     *
     * <p>支持按群组、上传者、时间范围、文件名、状态等条件过滤。
     * 仅返回当前用户有权查看的文档。Controller 层负责提取用户身份后传入 Service 层。
     *
     * @param query 查询条件（groupId、上传时间、文件名、状态等，均为可选）
     * @return 符合条件的文档列表
     * @throws BusinessException 查询参数非法时抛出
     */
    @GetMapping
    public ApiResponse<List<DocumentListItemVO>> listDocuments(
            @ModelAttribute DocumentQuery query) {
        return ApiResponse.success(documentQueryService.listDocuments(query));
    }

    /**
     * 软删除文档。
     *
     * <p>将文档标记为已删除，同时清理向量数据和 Elasticsearch 索引。
     * 需要调用者是群组管理员。Controller 层负责提取用户身份后传入 Service 层。
     *
     * @param documentId 要删除的文档 ID
     * @param groupId    文档所属群组 ID
     * @return 空响应（HTTP 200 表示成功）
     * @throws BusinessException 文档不存在、无权限时抛出
     */
    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> deleteDocument(
            @PathVariable Long documentId,
            @RequestParam Long groupId) {
        CurrentUserService.CurrentUser user = currentUserService.requireBusinessUser();
        documentDeleteService.softDeleteDocument(user.userId(), groupId, documentId);
        return ApiResponse.success(null);
    }

    /**
     * 重新处理失败文档。
     *
     * <p>将状态为 FAILED 的文档重置为 PROCESSING 并重新发布异步 ETL 事件。
     * 需要调用者是群组管理员。Controller 层负责提取用户身份后传入 Service 层。
     *
     * @param documentId 文档 ID
     * @param groupId    文档所属群组 ID
     * @return 空响应（HTTP 200 表示成功）
     * @throws BusinessException 文档不存在、非失败状态、无权限时抛出
     */
    @PostMapping("/{documentId}/retry-ingestion")
    public ApiResponse<Void> retryDocumentIngestion(
            @PathVariable Long documentId,
            @RequestParam Long groupId) {
        CurrentUserService.CurrentUser user = currentUserService.requireBusinessUser();
        documentDeleteService.retryFailedDocumentIngestion(user.userId(), groupId, documentId);
        return ApiResponse.success(null);
    }

    /**
     * 预览文档完整文本内容。
     *
     * <p>从对象存储（MinIO）读取原始文件并解析全部文本后返回。
     * 不做任何截断，供前端全量渲染展示。
     * 需要调用者是群组成员。仅支持处于 READY 状态的文档。
     *
     * @param documentId 文档 ID
     * @param groupId    文档所属群组 ID
     * @return 文档预览信息，包含文档 ID、文件名和完整文本内容
     * @throws BusinessException 文档不存在、未就绪、无权限、解析失败时抛出
     */
    @GetMapping("/{documentId}/preview")
    public ApiResponse<DocumentPreviewVO> previewDocument(
            @PathVariable Long documentId,
            @RequestParam Long groupId) {
        CurrentUserService.CurrentUser user = currentUserService.requireBusinessUser();
        return ApiResponse.success(documentPreviewService.previewDocument(user.userId(), groupId, documentId));
    }

    /**
     * 下载文档原始文件。
     *
     * <p>从对象存储获取文档原始文件流并返回。
     * HTTP 响应为 200 OK，Content-Type 为文件原始 MIME 类型，
     * Content-Disposition 为 attachment（触发浏览器下载）。
     * 需要调用者是群组成员。Controller 层负责提取用户身份后传入 Service 层。
     *
     * @param documentId 文档 ID
     * @param groupId    文档所属群组 ID
     * @return ResponseEntity 包含文件输入流、类型和下载文件名
     * @throws BusinessException 文档不存在、未就绪、存储信息缺失、无权限时抛出
     */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(
            @PathVariable Long documentId,
            @RequestParam Long groupId) {
        CurrentUserService.CurrentUser user = currentUserService.requireBusinessUser();
        DocumentDownloadVO downloadInfo = documentDownloadService.downloadDocument(user.userId(), groupId, documentId);
        InputStreamResource resource = new InputStreamResource(downloadInfo.getInputStream());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(downloadInfo.getContentType()))
                .contentLength(downloadInfo.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + URLEncoder.encode(downloadInfo.getFileName(), StandardCharsets.UTF_8)
                                + "\"")
                .body(resource);
    }
}
