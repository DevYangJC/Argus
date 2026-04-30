package com.argus.rag.auth.security;

import com.argus.rag.auth.config.AuthProperties;
import com.argus.rag.auth.mapper.UserRefreshTokenMapper;
import com.argus.rag.auth.model.entity.UserRefreshToken;
import com.argus.rag.auth.service.PasswordHasher;
import com.argus.rag.auth.service.RefreshTokenRecord;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh Token 管理服务。
 * <p>
 * Refresh token 格式为 {@code tokenId.secret}，tokenId 明文字段索引查找记录，
 * secret 部分 bcrypt 哈希存储。签发和吊销均操作 user_refresh_tokens 表。
 */
@Service
public class RefreshTokenService {

    private static final String TOKEN_SEPARATOR = ".";
    /** 随机 secret 字节数，Base64URL 编码后约 32 字符 */
    private static final int SECRET_BYTES = 24;

    private final UserRefreshTokenMapper tokenMapper;
    private final PasswordHasher passwordHasher;
    private final AuthProperties authProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            UserRefreshTokenMapper tokenMapper,
            PasswordHasher passwordHasher,
            AuthProperties authProperties,
            Clock clock
    ) {
        this.tokenMapper = tokenMapper;
        this.passwordHasher = passwordHasher;
        this.authProperties = authProperties;
        this.clock = clock;
    }

    /** 签发新的 refresh token */
    public IssuedRefreshToken issueToken(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String refreshToken = tokenId + TOKEN_SEPARATOR + newTokenSecret();
        LocalDateTime expiresAt = now.plusDays(authProperties.getRefreshTokenExpireDays());

        UserRefreshToken entity = new UserRefreshToken();
        entity.setUserId(userId);
        entity.setTokenId(tokenId);
        entity.setTokenHash(passwordHasher.hash(refreshToken));
        entity.setExpiresAt(expiresAt);
        entity.setCreatedAt(now);
        tokenMapper.insert(entity);

        return new IssuedRefreshToken(refreshToken, toRecord(entity));
    }

    /** 查找有效（未吊销且未过期）的 token 记录 */
    public Optional<RefreshTokenRecord> findActiveToken(String refreshToken) {
        Optional<ParsedRefreshToken> parsedToken = parseToken(refreshToken);
        if (parsedToken.isEmpty()) {
            return Optional.empty();
        }
        UserRefreshToken entity = tokenMapper.selectOne(
                new LambdaQueryWrapper<UserRefreshToken>()
                        .eq(UserRefreshToken::getTokenId, parsedToken.get().tokenId())
        );
        if (entity == null) {
            return Optional.empty();
        }
        RefreshTokenRecord record = toRecord(entity);
        if (!passwordHasher.matches(refreshToken, record.tokenHash())) {
            return Optional.empty();
        }
        if (!record.isActive(LocalDateTime.now(clock))) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    /** 吊销指定用户所有有效 token（登录时调用，实现单设备登录） */
    public void revokeActiveTokens(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        UserRefreshToken update = new UserRefreshToken();
        update.setRevokedAt(now);
        tokenMapper.update(update,
                new LambdaQueryWrapper<UserRefreshToken>()
                        .eq(UserRefreshToken::getUserId, userId)
                        .isNull(UserRefreshToken::getRevokedAt)
                        .gt(UserRefreshToken::getExpiresAt, now)
        );
    }

    /** 原子吊销单条 token（登出时调用），返回 true 表示成功吊销 */
    public boolean revokeToken(String refreshToken) {
        Optional<RefreshTokenRecord> activeToken = findActiveToken(refreshToken);
        if (activeToken.isEmpty()) {
            return false;
        }
        return revokeTokenById(activeToken.get().id());
    }

    /**
     * 原子吊销指定 ID 的 token：仅当 revoked_at 仍为 null 时才更新。
     * 返回 true 表示成功吊销，false 表示已被其他请求抢先吊销（疑似 token 窃取）。
     */
    public boolean revokeTokenById(Long id) {
        LocalDateTime now = LocalDateTime.now(clock);
        return tokenMapper.revokeByIdIfActive(id, now) > 0;
    }

    /** 解析 token 格式：tokenId.secret */
    private Optional<ParsedRefreshToken> parseToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        String[] segments = refreshToken.trim().split("\\Q" + TOKEN_SEPARATOR + "\\E", 2);
        if (segments.length != 2 || segments[0].isBlank() || segments[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedRefreshToken(segments[0], segments[1]));
    }

    /** 生成安全随机 secret */
    private String newTokenSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static RefreshTokenRecord toRecord(UserRefreshToken entity) {
        return new RefreshTokenRecord(
                entity.getId(),
                entity.getUserId(),
                entity.getTokenId(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getRevokedAt()
        );
    }

    private record ParsedRefreshToken(String tokenId, String secret) {
    }

    /** 签发后返回给调用方的 token 信息 */
    public record IssuedRefreshToken(String refreshToken, RefreshTokenRecord record) {
    }
}
