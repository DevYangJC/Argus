package com.argus.rag.document.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档元数据实体，对应 documents 表。
 * <p>
 * 记录每个上传到系统中的文档的基本信息、存储位置、处理状态及预览文本等。
 * 通过 groupId 与群组关联，实现文档的群组级别隔离。
 * </p>
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@Data
@TableName("documents")
public class DocumentEntity {

    /** 主键 ID，自增，唯一标识一个文档记录 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属群组 ID，外键引用 groups 表，用于群组级别的文档隔离 */
    private Long groupId;

    /** 上传者用户 ID，外键引用 users 表 */
    private Long uploaderUserId;

    /** 原始文件名（不含路径），最大长度 500 字符 */
    private String fileName;

    /** 文件扩展名（不含点号，如 "pdf"、"txt"），最大长度 50 字符，用于前端展示和类型识别 */
    private String fileExt;

    /** MIME 内容类型（如 "application/pdf"），最大长度 255 字符，用于下载时设置 Content-Type */
    private String contentType;

    /** 文件大小，单位：字节 */
    private Long fileSize;

    /** 文件 SHA-256 哈希值，用于秒传校验，长度为 64 字符的十六进制字符串 */
    private String fileHash;

    /** 对象存储桶名称，标识文件存储在哪个 Bucket */
    private String storageBucket;

    /** 对象存储键（Object Key），文件中在对象存储中的路径标识 */
    private String storageObjectKey;

    /**
     * 文档处理状态。
     * <ul>
     *   <li>PENDING -- 待处理（已上传到对象存储，等待异步解析）</li>
     *   <li>PROCESSING -- 处理中（正在解析提取文本）</li>
     *   <li>READY -- 处理完成，可供检索使用</li>
     *   <li>FAILED -- 处理失败</li>
     * </ul>
     * 最大长度 50 字符
     */
    private String status;

    /** 软删除标记，true 表示已删除，默认为 false */
    private Boolean deleted;

    /** 处理失败时的错误原因描述，仅在 status=FAILED 时有值 */
    private String failureReason;

    /** 文档预览文本（解析后的纯文本内容），用于前端预览和全文检索 */
    private String previewText;

    /** 上传时间（对象存储上传完成的时间） */
    private LocalDateTime uploadedAt;

    /** 处理完成时间（异步解析完成的时间） */
    private LocalDateTime processedAt;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    /** 记录最后更新时间 */
    private LocalDateTime updatedAt;

}
