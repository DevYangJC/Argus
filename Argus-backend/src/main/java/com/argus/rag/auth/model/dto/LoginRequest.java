package com.argus.rag.auth.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。
 *
 * @param loginId  登录标识（用户名或邮箱）
 * @param password 密码
 */
public record LoginRequest(
        @NotBlank(message = "登录标识不能为空")
        String loginId,
        @NotBlank(message = "密码不能为空")
        String password
) {
}
