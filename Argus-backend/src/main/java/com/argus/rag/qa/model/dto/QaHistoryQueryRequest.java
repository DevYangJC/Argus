package com.argus.rag.qa.model.dto;

import lombok.Data;

/** QA 历史列表查询条件。 */
@Data
public class QaHistoryQueryRequest {
    /** 按群组过滤，为空时查询当前用户可见的全部记录。 */
    private Long groupId;
    /** 按是否成功回答过滤，为空时不过滤。 */
    private Boolean answered;
    /** 页码，从 1 开始。 */
    private Integer page = 1;
    /** 每页数量。 */
    private Integer pageSize = 20;
}
