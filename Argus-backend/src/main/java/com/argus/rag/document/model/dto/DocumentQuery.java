package com.argus.rag.document.model.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 文档列表查询参数 DTO。
 * <p>
 * 封装文档列表的所有查询条件，包括群组筛选、文件名模糊匹配、上传者筛选、
 * 状态筛选和上传时间范围等。支持按用户群组角色进行权限过滤。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Data
public class DocumentQuery {

    /** 当前请求用户的 ID，用于权限过滤和群组关系的上下文判断 */
    private Long currentUserId;

    /** 按群组 ID 精确筛选文档 */
    private Long groupId;

    /** 群组关系类型，用于按用户在群组中的角色（OWNER/MEMBER）过滤可读文档 */
    private String groupRelation;

    /** 按文件名模糊搜索 */
    private String fileName;

    /** 按上传者用户 ID 精确筛选 */
    private Long uploaderUserId;

    /** 按文档状态精确筛选（PENDING/PROCESSING/READY/FAILED） */
    private String status;

    /** 上传时间范围起始（含），ISO 8601 格式 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime uploadedFrom;

    /** 上传时间范围结束（含），ISO 8601 格式 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime uploadedTo;
}
