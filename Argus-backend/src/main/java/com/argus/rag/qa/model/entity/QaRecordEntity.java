package com.argus.rag.qa.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** QA 问答记录实体，对应 qa_records 表。 */
@Data
@TableName("qa_records")
public class QaRecordEntity {

    /** 问答记录主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 提问用户 ID。 */
    private Long userId;
    /** 问答发生的知识库群组 ID。 */
    private Long groupId;
    /** 用户原始问题。 */
    private String question;
    /** 模型生成的回答正文，拒答或失败时可为空。 */
    private String answer;
    /** 是否成功回答了问题。 */
    private Boolean answered;
    /** 拒答或失败原因编码。 */
    private String reasonCode;
    /** 拒答或失败原因说明。 */
    private String reasonMessage;
    /** 证据充分性等级。 */
    private String evidenceLevel;
    /** 保存的引用快照数量。 */
    private Integer citationCount;
    /** 输入 token 数。 */
    private Integer promptTokens;
    /** 输出 token 数。 */
    private Integer completionTokens;
    /** 总 token 数。 */
    private Integer totalTokens;
    /** token 用量是否为估算值。 */
    private Boolean isEstimated;
    /** 本次问答耗时，单位毫秒。 */
    private Long latencyMs;
    /** 调用的大模型名称。 */
    private String modelName;
    /** 触发记录的接口端点。 */
    private String endpoint;
    /** 持久化对应流程是否成功完成。 */
    private Boolean success;
    /** 系统异常信息，业务拒答时通常为空。 */
    private String errorMessage;
    /** 记录创建时间。 */
    private LocalDateTime createdAt;
}
