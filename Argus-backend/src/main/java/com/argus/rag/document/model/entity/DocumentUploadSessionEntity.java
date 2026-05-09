package com.argus.rag.document.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档上传会话实体，对应 document_upload_sessions 表。
 * <p>
 * 记录大文件分片上传的会话信息。当文件大小超过单次上传阈值时，前端发起分片上传会话，
 * 每个分片通过 {@link DocumentUploadChunkEntity} 记录。所有分片上传完毕后，
 * 系统会合并分片生成最终的合并对象。
 * </p>
 * <p>
 * 支持秒传功能：如果同一群组内已有相同 fileHash 的文档，且会话状态未过期，
 * 则可以直接复用已有会话，无需重新上传。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Data
@TableName("document_upload_sessions")
public class DocumentUploadSessionEntity {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上传会话唯一标识，UUID 字符串，长度 36 字符，用于客户端跟踪分片上传进度 */
    private String uploadId;

    /** 所属群组 ID，外键引用 groups 表 */
    private Long groupId;

    /** 上传者用户 ID，外键引用 users 表 */
    private Long uploaderUserId;

    /** 原始文件名，最大长度 500 字符 */
    private String fileName;

    /** 文件扩展名（不含点号），最大长度 50 字符 */
    private String fileExt;

    /** MIME 内容类型，最大长度 255 字符 */
    private String contentType;

    /** 文件总大小，单位：字节 */
    private Long fileSize;

    /** 文件 SHA-256 哈希值，64 字符十六进制字符串，用于秒传校验 */
    private String fileHash;

    /** 每个分片的大小，单位：字节 */
    private Long chunkSize;

    /** 总分片数量 */
    private Integer chunkCount;

    /**
     * 上传会话状态。
     * <ul>
     *   <li>UPLOADING -- 上传中（分片未全部上传完）</li>
     *   <li>MERGING -- 合并中（所有分片已上传，正在合并文件）</li>
     *   <li>UPLOADED -- 合并完成，等待异步解析处理</li>
     *   <li>FAILED -- 上传失败</li>
     * </ul>
     * 最大长度 50 字符
     */
    private String status;

    /** 对象存储桶名称，合并完成后存储的目标 Bucket */
    private String storageBucket;

    /** 合并后的对象存储键（Object Key），所有分片合并后的最终文件路径 */
    private String mergedObjectKey;

    /** 会话过期时间，超过此时间未完成上传的会话将被清理 */
    private LocalDateTime expiresAt;

    /** 会话创建时间 */
    private LocalDateTime createdAt;

    /** 会话最后更新时间 */
    private LocalDateTime updatedAt;
}
