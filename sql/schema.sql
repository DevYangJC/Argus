-- ===================================================
-- Argus 数据库初始化 DDL（PostgreSQL）
-- ===================================================

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id                  BIGSERIAL       PRIMARY KEY,
    user_code           VARCHAR(64)     NOT NULL,
    username            VARCHAR(64)     NOT NULL,
    email               VARCHAR(128)    NOT NULL,
    display_name        VARCHAR(128)    NOT NULL,
    password_hash       VARCHAR(256)    NOT NULL,
    system_role         VARCHAR(16)     NOT NULL DEFAULT 'USER',
    status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN        NOT NULL DEFAULT FALSE,
    last_login_at       TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email)
);

COMMENT ON TABLE  users                    IS '用户表';
COMMENT ON COLUMN users.id                 IS '主键';
COMMENT ON COLUMN users.user_code          IS '用户编码，前端展示用，不可修改';
COMMENT ON COLUMN users.username           IS '登录用户名，唯一';
COMMENT ON COLUMN users.email              IS '邮箱，唯一';
COMMENT ON COLUMN users.display_name       IS '显示名称';
COMMENT ON COLUMN users.password_hash      IS 'BCrypt 密码哈希';
COMMENT ON COLUMN users.system_role        IS '系统角色：ADMIN | USER';
COMMENT ON COLUMN users.status             IS '账号状态：ACTIVE | DISABLED';
COMMENT ON COLUMN users.must_change_password IS '是否强制修改密码';
COMMENT ON COLUMN users.last_login_at      IS '最后登录时间';
COMMENT ON COLUMN users.created_at         IS '创建时间';
COMMENT ON COLUMN users.updated_at         IS '更新时间';


-- Refresh Token 表
CREATE TABLE IF NOT EXISTS user_refresh_tokens (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    token_id        VARCHAR(64)     NOT NULL,
    token_hash      VARCHAR(256)    NOT NULL,
    expires_at      TIMESTAMP       NOT NULL,
    revoked_at      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users (id)
);

COMMENT ON TABLE  user_refresh_tokens               IS '用户 Refresh Token 表';
COMMENT ON COLUMN user_refresh_tokens.id             IS '主键';
COMMENT ON COLUMN user_refresh_tokens.user_id        IS '关联的用户 ID';
COMMENT ON COLUMN user_refresh_tokens.token_id       IS 'token 唯一标识（UUID 去横线）';
COMMENT ON COLUMN user_refresh_tokens.token_hash     IS 'BCrypt 哈希后的 token';
COMMENT ON COLUMN user_refresh_tokens.expires_at     IS '过期时间';
COMMENT ON COLUMN user_refresh_tokens.revoked_at     IS '吊销时间，null 表示未吊销';
COMMENT ON COLUMN user_refresh_tokens.created_at     IS '创建时间';

-- 索引：按 token_id 查找 token
CREATE INDEX IF NOT EXISTS idx_refresh_token_token_id
    ON user_refresh_tokens (token_id);

-- 索引：查询用户的有效 token（吊销未过期）
CREATE INDEX IF NOT EXISTS idx_refresh_token_user_active
    ON user_refresh_tokens (user_id, revoked_at, expires_at);
