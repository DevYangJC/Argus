package com.argus.rag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.argus.rag.document.model.dto.DocumentQuery;
import com.argus.rag.document.model.entity.DocumentEntity;
import com.argus.rag.document.model.vo.DocumentListItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档元数据 Mapper 接口，继承 MyBatis-Plus BaseMapper 获得通用 CRUD 能力。
 * <p>
 * 提供对 documents 表的 CRUD 操作，包括按群组权限查询文档列表、
 * 软删除和批量状态回收等功能。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Mapper
public interface DocumentMapper extends BaseMapper<DocumentEntity> {

    /**
     * 查询当前用户可读的文档列表（根据群组成员关系过滤），
     * 并 JOIN users 表补充上传者信息。
     *
     * @param query 查询条件
     * @return 文档列表项
     */
    List<DocumentListItemVO> selectReadableDocuments(DocumentQuery query);

    /**
     * 按文档 ID 和群组 ID 查询文档详情，确保群组级别的数据隔离。
     *
     * @param documentId 文档 ID
     * @param groupId    群组 ID
     * @return 文档实体，未找到返回 null
     */
    DocumentEntity selectByIdAndGroupId(
            @Param("documentId") Long documentId,
            @Param("groupId") Long groupId
    );

    /**
     * 按群组 ID 和文件哈希值查询已存在的文档，用于秒传判断。
     *
     * @param groupId  群组 ID
     * @param fileHash 文件 SHA-256 哈希值
     * @return 已存在的文档实体，未找到返回 null
     */
    DocumentEntity selectByGroupIdAndFileHash(
            @Param("groupId") Long groupId,
            @Param("fileHash") String fileHash
    );

    /**
     * 软删除指定文档（设置 deleted = true）。
     *
     * @param documentId 文档 ID
     * @param groupId    群组 ID
     * @return 影响行数
     */
    int markDeleted(
            @Param("documentId") Long documentId,
            @Param("groupId") Long groupId
    );

    /**
     * 将超过指定时间仍未完成处理的长时卡住文档标记为失败。
     * 用于服务重启后清理僵尸状态的文档。
     *
     * @param fromStatus    原状态（如 PROCESSING）
     * @param toStatus      目标状态（如 FAILED）
     * @param failureReason 失败原因说明
     * @param staleBefore   在此时间之前的记录视为卡住
     * @param processedAt   处理完成时间
     * @return 影响行数
     */
    int markStaleProcessingDocumentsFailed(
            @Param("fromStatus") String fromStatus,
            @Param("toStatus") String toStatus,
            @Param("failureReason") String failureReason,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("processedAt") LocalDateTime processedAt
    );
}
