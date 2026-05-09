package com.argus.rag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.argus.rag.document.model.entity.DocumentUploadSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文档上传会话 Mapper 接口，继承 MyBatis-Plus BaseMapper 获得通用 CRUD 能力。
 * <p>
 * 提供对 document_upload_sessions 表的查询操作，支持上传会话的创建、
 * 查询和复用判断等功能。通用 insert 由 BaseMapper 提供。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Mapper
public interface DocumentUploadSessionMapper extends BaseMapper<DocumentUploadSessionEntity> {

    /**
     * 按上传会话标识查询会话详情。
     *
     * @param uploadId 上传会话标识（UUID）
     * @return 会话实体，未找到返回 null
     */
    DocumentUploadSessionEntity selectByUploadId(@Param("uploadId") String uploadId);

    /**
     * 查询同一个群组内同一用户上传的、相同文件哈希的、最近的可复用上传会话。
     * 用于断点续传判断：如果存在未过期的进行中会话，客户端可以继续上传剩余分片。
     *
     * @param groupId        群组 ID
     * @param uploaderUserId 上传者用户 ID
     * @param fileHash       文件 SHA-256 哈希值
     * @return 可复用的会话实体，未找到返回 null
     */
    DocumentUploadSessionEntity selectLatestReusableSession(
            @Param("groupId") Long groupId,
            @Param("uploaderUserId") Long uploaderUserId,
            @Param("fileHash") String fileHash
    );
}
