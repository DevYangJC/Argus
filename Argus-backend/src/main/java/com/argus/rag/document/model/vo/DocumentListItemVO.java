package com.argus.rag.document.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档列表项 VO。
 * <p>
 * 用于文档列表接口返回的每一项文档信息，包含文档元数据、上传者信息和预览文本摘要。
 * 由 MyBatis 多表联查（documents + users）直接映射填充。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Data
public class DocumentListItemVO {

    /** 文档 ID */
    private Long documentId;

    /** 所属群组 ID */
    private Long groupId;

    /** 文件名 */
    private String fileName;

    /** 文件扩展名 */
    private String fileExt;

    /** MIME 内容类型 */
    private String contentType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文档处理状态（PENDING/PROCESSING/READY/FAILED） */
    private String status;

    /** 处理失败原因，仅 status=FAILED 时有值 */
    private String failureReason;

    /** 上传时间 */
    private LocalDateTime uploadedAt;

    /** 上传者用户 ID */
    private Long uploaderUserId;

    /** 上传者用户登录名（user_code） */
    private String uploaderUserCode;

    /** 上传者显示名称 */
    private String uploaderDisplayName;

    /** 文档预览文本摘要（前端列表页展示用） */
    private String previewText;
}
