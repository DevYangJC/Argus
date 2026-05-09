package com.argus.rag.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 认证相关配置属性，前缀 {@code rag.auth}。
 */
@Validated
@ConfigurationProperties(prefix = "rag.auth")
@Data
public class AuthProperties {

    /** JWT 签发者 */
    @NotBlank
    private String issuer = "argus-rag";

    /** Access token 有效期（分钟） */
    @Min(1)
    private int accessTokenExpireMinutes = 30;

    /** Refresh token 有效期（天） */
    @Min(1)
    private int refreshTokenExpireDays = 14;

    /** JWT HMAC 签名密钥，至少 32 字节 */
    @NotBlank
    private String jwtSecret;

    /** Refresh token Cookie 名称 */
    @NotBlank
    private String refreshCookieName = "ARGUS_DD_RAG_REFRESH_TOKEN";

    /** Refresh token Cookie 是否仅 HTTPS 发送（生产环境应设为 true） */
    private boolean refreshCookieSecure = true;

}
