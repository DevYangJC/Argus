package com.argus.rag.auth.service;

import java.time.LocalDateTime;

/**
 * Refresh Token 数据库记录。
 */
public record RefreshTokenRecord(
        /** 主键 */
        Long id,
        /** 关联用户 ID */
        Long userId,
        /** token 唯一标识 */
        String tokenId,
        /** BCrypt 哈希后的 token */
        String tokenHash,
        /** 过期时间 */
        LocalDateTime expiresAt,
        /** 吊销时间，null 表示未吊销 */
        LocalDateTime revokedAt
) {

    /** 判断 token 是否当前有效 */
    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
