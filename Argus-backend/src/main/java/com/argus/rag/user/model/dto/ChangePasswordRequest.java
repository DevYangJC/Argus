package com.argus.rag.user.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 修改密码请求。
 */
public record ChangePasswordRequest(
        @NotBlank(message = "当前密码不能为空")
        String currentPassword,
        @NotBlank(message = "新密码不能为空")
        String newPassword
) {
}
