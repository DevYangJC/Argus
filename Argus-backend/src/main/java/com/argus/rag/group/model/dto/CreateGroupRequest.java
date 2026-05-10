package com.argus.rag.group.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建群组请求。
 */
public record CreateGroupRequest(
        /** 群组名称 */
        @NotBlank(message = "组名称不能为空")
        @Size(max = 128, message = "组名称不能超过 128")
        String name,

        /** 群组描述 */
        @Size(max = 512, message = "组描述不能超过 512")
        String description
) {
}
