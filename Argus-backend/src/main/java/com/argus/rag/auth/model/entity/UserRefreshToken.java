package com.argus.rag.auth.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户 Refresh Token 实体，映射 user_refresh_tokens 表。
 */
@TableName("user_refresh_tokens")
@Data
public class UserRefreshToken {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的用户 ID */
    private Long userId;

    /** token 唯一标识（UUID 去横线） */
    private String tokenId;

    /** token 哈希值，用于安全比对 */
    private String tokenHash;

    /** 过期时间 */
    private LocalDateTime expiresAt;

    /** 吊销时间，null 表示未吊销 */
    private LocalDateTime revokedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
