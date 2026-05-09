package com.argus.rag.document.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分片上传块实体，对应 document_upload_chunks 表。
 * <p>
 * 记录大文件分片上传中每个分片的元数据。一个上传会话（uploadId）包含多个分片，
 * 每个分片有独立的序号（chunkIndex）和存储位置。当所有分片上传完毕后，
 * 系统将合并所有分片生成最终文件。
 * </p>
 * <p>
 * 支持断点续传：已上传的分片会被记录在此表中，客户端可以通过查询已上传的分片列表
 * 来跳过已成功上传的分片，只上传尚未完成的部分。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Data
@TableName("document_upload_chunks")
public class DocumentUploadChunkEntity {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属上传会话标识，UUID 字符串，长度 36 字符，对应 document_upload_sessions 表的 uploadId */
    private String uploadId;

    /** 分片序号，从 0 开始递增 */
    private Integer chunkIndex;

    /** 当前分片大小，单位：字节（最后一片可能小于标准片大小） */
    private Long chunkSize;

    /** 当前分片的 SHA-256 哈希值，用于校验分片传输完整性 */
    private String chunkHash;

    /** 对象存储桶名称，分片文件临时存储的 Bucket */
    private String storageBucket;

    /** 对象存储键（Object Key），该分片在对象存储中的路径标识 */
    private String storageObjectKey;

    /** 该分片上传完成的时间 */
    private LocalDateTime uploadedAt;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    /** 记录最后更新时间 */
    private LocalDateTime updatedAt;
}
