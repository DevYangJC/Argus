package com.argus.rag.ingestion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.argus.rag.ingestion.model.entity.DocumentChunkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 文档切片 Mapper 接口，继承 MyBatis-Plus BaseMapper 获得通用 CRUD 能力。
 * <p>
 * 提供对 document_chunks 表的 CRUD 操作，包括按文档 ID 查询、
 * 删除旧切片和批量插入等功能。切片数据用于向量检索和全文搜索。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunkEntity> {


    /**
     * 批量插入文档切片数据。
     *
     * @param chunks
     * @return
     */
    int insertBatch(@Param("chunks") List<DocumentChunkEntity> chunks);

    /**
     * 删除指定文档的所有切片记录。
     *
     * @param documentId 文档 ID
     * @return 影响行数
     */
    int deleteByDocumentId(@Param("documentId") Long documentId);

    /**
     * 查询指定文档的所有切片列表。
     *
     * @param documentId 文档 ID
     * @return 切片实体列表
     */
    List<DocumentChunkEntity> selectByDocumentId(@Param("documentId") Long documentId);

    /**
     * 查询指定文档中状态为 READY 且未软删除的活跃切片列表。
     *
     * @param groupId    群组 ID
     * @param documentId 文档 ID
     * @return 切片实体列表
     */
    List<DocumentChunkEntity> selectReadyActiveChunksByDocumentId(
            @Param("groupId") Long groupId,
            @Param("documentId") Long documentId
    );

    /**
     * 按切片 ID 批量查询 READY 状态的活跃切片。
     *
     * @param groupId  群组 ID
     * @param chunkIds 切片 ID 列表
     * @return 切片数据列表（Map 格式）
     */
    List<Map<String, Object>> selectReadyActiveChunksByIds(
            @Param("groupId") Long groupId,
            @Param("chunkIds") List<Long> chunkIds
    );

    /**
     * 按切片 ID 批量查询 QA 就绪状态的切片。
     *
     * @param groupId  群组 ID
     * @param chunkIds 切片 ID 列表
     * @return 切片数据列表（Map 格式）
     */
    List<Map<String, Object>> selectQaReadyChunksByIds(
            @Param("groupId") Long groupId,
            @Param("chunkIds") List<Long> chunkIds
    );
}
