package com.argus.rag.document.model.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 小文件（一次性）上传请求 DTO。
 * <p>
 * 当文件大小低于分片阈值时，前端通过此请求一次性上传整个文件，
 * 无需先初始化上传会话。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Data
public class UploadDocumentRequest {

    /** 目标群组 ID，文档将归属于该群组 */
    private Long groupId;

    /** 要上传的文件内容 */
    private MultipartFile file;

}
