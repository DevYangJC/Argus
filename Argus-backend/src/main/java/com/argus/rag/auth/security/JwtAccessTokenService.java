package com.argus.rag.auth.security;

import com.argus.rag.auth.config.AuthProperties;
import com.argus.rag.common.enums.SystemRole;
import com.argus.rag.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT Access Token 签发与解析服务。
 * <p>
 * 使用 HMAC-SHA 签名，密钥从配置读取，最短要求 32 字节。
 */
@Service
public class JwtAccessTokenService {

    private static final String USER_ID_CLAIM = "uid";
    private static final String DISPLAY_NAME_CLAIM = "displayName";
    private static final String SYSTEM_ROLE_CLAIM = "systemRole";
    private static final String MUST_CHANGE_PASSWORD_CLAIM = "mustChangePassword";
    private static final int MIN_SECRET_LENGTH = 32;

    private final AuthProperties authProperties;
    private final Clock clock;
    private final SecretKey signingKey;

    public JwtAccessTokenService(AuthProperties authProperties, Clock clock) {
        this.authProperties = authProperties;
        this.clock = clock;
        this.signingKey = buildSigningKey(authProperties.getJwtSecret());
    }

    /** 签发 access token */
    public String issueToken(TokenSubject subject) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(authProperties.getAccessTokenExpireMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .issuer(authProperties.getIssuer())
                .subject(subject.userCode())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(USER_ID_CLAIM, subject.userId())
                .claim(DISPLAY_NAME_CLAIM, subject.displayName())
                .claim(SYSTEM_ROLE_CLAIM, subject.systemRole().name())
                .claim(MUST_CHANGE_PASSWORD_CLAIM, subject.mustChangePassword())
                .signWith(signingKey)
                .compact();
    }

    /** 解析并校验 access token */
    public AccessTokenClaims parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            validateClaims(claims);
            return new AccessTokenClaims(
                    claims.get(USER_ID_CLAIM, Long.class),
                    claims.getSubject(),
                    claims.get(DISPLAY_NAME_CLAIM, String.class),
                    SystemRole.valueOf(claims.get(SYSTEM_ROLE_CLAIM, String.class)),
                    claims.get(MUST_CHANGE_PASSWORD_CLAIM, Boolean.class),
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant()
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException("access token 非法或已过期", exception);
        } catch (RuntimeException exception) {
            throw new BusinessException("access token 非法或已过期", exception);
        }
    }

    /** 校验 claims 完整性 */
    private void validateClaims(Claims claims) {
        if (!authProperties.getIssuer().equals(claims.getIssuer())) {
            throw new BusinessException("access token 非法或已过期");
        }
        if (claims.getSubject() == null
                || claims.get(USER_ID_CLAIM, Long.class) == null
                || claims.get(DISPLAY_NAME_CLAIM, String.class) == null
                || claims.get(SYSTEM_ROLE_CLAIM, String.class) == null
                || claims.get(MUST_CHANGE_PASSWORD_CLAIM, Boolean.class) == null
                || claims.getIssuedAt() == null
                || claims.getExpiration() == null) {
            throw new BusinessException("access token 非法或已过期");
        }
    }

    /** 从配置的字符串构建 HMAC 密钥 */
    private SecretKey buildSigningKey(String jwtSecret) {
        byte[] secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT secret 至少需要 32 字节");
        }
        return Keys.hmacShaKeyFor(secretBytes);
    }

    /** 签发 token 时的主题信息 */
    public record TokenSubject(
            Long userId,
            String userCode,
            String displayName,
            SystemRole systemRole,
            boolean mustChangePassword
    ) {
    }

    /** 解析 token 后的声明信息 */
    public record AccessTokenClaims(
            Long userId,
            String userCode,
            String displayName,
            SystemRole systemRole,
            boolean mustChangePassword,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}
