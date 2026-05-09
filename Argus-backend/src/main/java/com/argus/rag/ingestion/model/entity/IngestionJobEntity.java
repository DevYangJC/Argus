package com.argus.rag.ingestion.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 索引任务实体，映射 {@code ingestion_jobs} 表。
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Data
@TableName("ingestion_jobs")
public class IngestionJobEntity {

    /** 任务主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联的文档 ID */
    private Long documentId;
    /** 关联的知识库 ID */
    private Long groupId;
    /** 任务类型 */
    private String jobType;
    /** 任务状态 */
    private String status;
    /** 当前重试次数 */
    private Integer retryCount;
    /** 最大重试次数 */
    private Integer maxRetries;
    /** 执行该任务的 Worker 标识 */
    private String workerId;
    /** 任务开始时间 */
    private LocalDateTime startedAt;
    /** 任务完成时间 */
    private LocalDateTime finishedAt;
    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;
    /** 最近一次失败的错误信息 */
    private String lastError;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;

}
