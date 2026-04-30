package com.argus.rag.user.model.dto;

import com.argus.rag.common.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 修改用户状态请求。
 */
public record UpdateUserStatusRequest(
        @NotNull(message = "用户状态不能为空")
        UserStatus status
) {
}
