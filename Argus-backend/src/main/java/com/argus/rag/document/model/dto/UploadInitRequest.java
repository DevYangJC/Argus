package com.argus.rag.document.model.dto;

/**
 * 上传初始化请求 DTO。
 * <p>
 * 大文件分片上传的第一步：客户端提交文件元数据和分片参数，
 * 服务端根据 fileHash 判断是否可以秒传（相同文件已存在）或断点续传（会话已存在但未完成），
 * 否则创建新的上传会话。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
public record UploadInitRequest(
        /** 目标群组 ID，文档将归属于该群组 */
        Long groupId,
        /** 原始文件名 */
        String fileName,
        /** 文件总大小，单位：字节 */
        Long fileSize,
        /** MIME 内容类型（如 "application/pdf"） */
        String contentType,
        /** 文件 SHA-256 哈希值，用于秒传校验 */
        String fileHash,
        /** 每个分片的大小，单位：字节 */
        Long chunkSize,
        /** 总分片数量 */
        Integer chunkCount
) {
}
