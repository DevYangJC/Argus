package com.argus.rag.qa.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** QA 引用快照实体，对应 qa_record_citations 表。 */
@Data
@TableName("qa_record_citations")
public class QaRecordCitationEntity {

    /** 引用快照主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属 QA 问答记录 ID。 */
    private Long qaRecordId;
    /** 来源文档 ID。 */
    private Long documentId;
    /** 来源文档版本 ID，当前系统未启用版本时可为空。 */
    private Long documentVersionId;
    /** 来源切片 ID。 */
    private Long chunkId;
    /** 切片在文档中的序号。 */
    private Integer chunkIndex;
    /** 合并证据片段的起始切片序号。 */
    private Integer startChunkIndex;
    /** 合并证据片段的结束切片序号。 */
    private Integer endChunkIndex;
    /** 来源文件名。 */
    private String fileName;
    /** 最终相关性得分。 */
    private Double score;
    /** 检索来源类型，如 VECTOR、KEYWORD、BOTH。 */
    private String retrievalSource;
    /** 向量检索得分。 */
    private Double vectorScore;
    /** 关键词检索得分。 */
    private Double keywordScore;
    /** 混合检索融合得分。 */
    private Double hybridScore;
    /** 回答时使用的证据文本快照。 */
    private String snippet;
    /** 快照创建时间。 */
    private LocalDateTime createdAt;
}
