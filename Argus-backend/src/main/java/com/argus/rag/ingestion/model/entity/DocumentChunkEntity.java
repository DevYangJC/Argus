package com.argus.rag.ingestion.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档切片实体，映射 {@code document_chunks} 表。
 *
 * @author Argus-RAG Team
 * @since 1.0.0
 */
@TableName("document_chunks")
@Data
public class DocumentChunkEntity {

    /** 切片主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属文档 ID */
    private Long documentId;
    /** 所属知识库 ID */
    private Long groupId;
    /** 切片在文档中的序号（从 0 开始） */
    private Integer chunkIndex;
    /** 切片文本内容 */
    private String chunkText;
    /** 切片文本摘要，截取自前若干字符 */
    private String chunkSummary;
    /** 切片在原文档中的起始字符位置 */
    private Integer charStart;
    /** 切片在原文档中的结束字符位置 */
    private Integer charEnd;
    /** 切片元数据 JSON */
    private String metadataJson;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
