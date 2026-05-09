package com.argus.rag.document.model.vo;

import lombok.Data;

/**
 * 文档预览 VO。
 * <p>
 * 返回指定文档的可预览文本内容，用于前端文档详情页或预览弹窗展示。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Data
public class DocumentPreviewVO {

    /** 文档 ID */
    private Long documentId;

    /** 文件名 */
    private String fileName;

    /** 文档解析后的纯文本预览内容（已去除非可读字符） */
    private String previewText;

}
