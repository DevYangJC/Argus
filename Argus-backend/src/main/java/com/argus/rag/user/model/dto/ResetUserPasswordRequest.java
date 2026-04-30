package com.argus.rag.user.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员重置用户密码请求。
 */
public record ResetUserPasswordRequest(
        @NotBlank(message = "新密码不能为空")
        String newPassword
) {
}
