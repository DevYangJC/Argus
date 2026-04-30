package com.argus.rag.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 认证相关配置属性，前缀 {@code ddrag.auth}。
 */
@Validated
@ConfigurationProperties(prefix = "ddrag.auth")
public class AuthProperties {

    /** JWT 签发者 */
    @NotBlank
    private String issuer = "dd-rag";

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
    private String refreshCookieName = "DD_RAG_REFRESH_TOKEN";

    /** Refresh token Cookie 是否仅 HTTPS 发送（生产环境应设为 true） */
    private boolean refreshCookieSecure = true;

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public int getAccessTokenExpireMinutes() { return accessTokenExpireMinutes; }
    public void setAccessTokenExpireMinutes(int accessTokenExpireMinutes) { this.accessTokenExpireMinutes = accessTokenExpireMinutes; }

    public int getRefreshTokenExpireDays() { return refreshTokenExpireDays; }
    public void setRefreshTokenExpireDays(int refreshTokenExpireDays) { this.refreshTokenExpireDays = refreshTokenExpireDays; }

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }

    public String getRefreshCookieName() { return refreshCookieName; }
    public void setRefreshCookieName(String refreshCookieName) { this.refreshCookieName = refreshCookieName; }

    public boolean isRefreshCookieSecure() { return refreshCookieSecure; }
    public void setRefreshCookieSecure(boolean refreshCookieSecure) { this.refreshCookieSecure = refreshCookieSecure; }
}
