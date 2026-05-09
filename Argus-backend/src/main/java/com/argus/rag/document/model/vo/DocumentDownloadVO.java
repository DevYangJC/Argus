package com.argus.rag.document.model.vo;

import lombok.Data;

import java.io.InputStream;

/**
 * 文档下载 VO。
 * <p>
 * 封装从对象存储获取的文档文件流及元数据，用于通过 HTTP 响应流式下载文件。
 * 调用方需要在使用完毕后关闭 inputStream。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Data
public class DocumentDownloadVO {

    /** 文件内容输入流，由调用方负责关闭 */
    private InputStream inputStream;

    /** 文件名，用于设置 Content-Disposition 响应头 */
    private String fileName;

    /** MIME 内容类型，用于设置 Content-Type 响应头 */
    private String contentType;

    /** 文件大小（字节），用于设置 Content-Length 响应头 */
    private long fileSize;
}
