package com.argus.rag.qa.model.vo;

import java.util.List;

/** QA 历史分页响应。 */
public record QaRecordPageVO(
        /** 当前页记录列表。 */
        List<QaRecordListItemVO> items,
        /** 符合条件的记录总数。 */
        long total,
        /** 当前页码，从 1 开始。 */
        int page,
        /** 每页数量。 */
        int pageSize
) {
}
