package com.argus.rag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.argus.rag.document.model.entity.DocumentUploadChunkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档上传分片 Mapper 接口，继承 MyBatis-Plus BaseMapper 获得通用 CRUD 能力。
 * <p>
 * 提供对 document_upload_chunks 表的操作，支持分片的 Upsert
 * 和按上传会话查询已上传分片列表。通用 insert 由 BaseMapper 提供。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Mapper
public interface DocumentUploadChunkMapper extends BaseMapper<DocumentUploadChunkEntity> {

    /**
     * 插入或更新分片记录（PostgreSQL ON CONFLICT Upsert）。
     * 如果相同 uploadId + chunkIndex 的记录已存在则更新，
     * 用于支持分片重传场景。
     *
     * @param chunk 分片实体
     * @return 影响行数
     */
    int upsert(DocumentUploadChunkEntity chunk);

    /**
     * 查询指定上传会话的所有已上传分片列表。
     *
     * @param uploadId 上传会话标识
     * @return 该会话下所有分片实体列表
     */
    List<DocumentUploadChunkEntity> selectByUploadId(@Param("uploadId") String uploadId);
}
