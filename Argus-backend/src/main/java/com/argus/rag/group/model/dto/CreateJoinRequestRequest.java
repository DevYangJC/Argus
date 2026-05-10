package com.argus.rag.group.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建加入群组申请请求。
 */
public record CreateJoinRequestRequest(
        /** 群组编码 */
        @NotBlank(message = "组织 ID 不能为空")
        @Size(max = 80, message = "组织 ID 不能超过 80")
        String groupCode
) {
}
