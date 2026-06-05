package com.argus.rag.qa.model.vo;

import java.time.LocalDateTime;

/**
 * QA 历史列表项。
 * <p>只返回列表展示所需字段，回答正文使用预览文本避免列表过重。</p>
 */
public record QaRecordListItemVO(
        /** 问答记录 ID。 */
        Long id,
        /** 提问用户 ID。 */
        Long userId,
        /** 所属群组 ID。 */
        Long groupId,
        /** 用户原始问题。 */
        String question,
        /** 是否成功回答。 */
        Boolean answered,
        /** 回答或拒答原因的简短预览。 */
        String answerPreview,
        /** 拒答或失败原因编码。 */
        String reasonCode,
        /** 证据充分性等级。 */
        String evidenceLevel,
        /** 引用快照数量。 */
        Integer citationCount,
        /** 问答耗时，单位毫秒。 */
        Long latencyMs,
        /** 记录创建时间。 */
        LocalDateTime createdAt
) {
}
