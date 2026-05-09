package com.argus.rag.document.model.dto;

import org.springframework.web.multipart.MultipartFile;

/**
 * 分片上传请求 DTO。
 * <p>
 * 封装大文件分片上传中单个分片的信息，包含会话标识、分片序号、分片哈希
 * 以及分片文件内容本身。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
public record UploadChunkRequest(
        /** 上传会话标识（UUID），用于关联到具体的上传会话 */
        String uploadId,
        /** 分片序号，从 0 开始，标识当前分片在文件中的位置 */
        Integer chunkIndex,
        /** 当前分片的 SHA-256 哈希值，用于校验分片完整性 */
        String chunkHash,
        /** 分片文件的二进制内容 */
        MultipartFile chunk
) {
}
