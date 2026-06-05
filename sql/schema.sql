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


-- ===================================================
-- 群组与成员管理
-- ===================================================

-- 群组表
CREATE TABLE IF NOT EXISTS groups (
    id              BIGSERIAL       PRIMARY KEY,
    group_code      VARCHAR(64)     NOT NULL,
    group_name      VARCHAR(128)    NOT NULL,
    description     TEXT            NOT NULL DEFAULT '',
    owner_user_id   BIGINT          NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT uq_groups_group_code UNIQUE (group_code),
    CONSTRAINT fk_groups_owner FOREIGN KEY (owner_user_id) REFERENCES users (id)
);

COMMENT ON TABLE  groups                IS '群组表（知识库）';
COMMENT ON COLUMN groups.id             IS '主键';
COMMENT ON COLUMN groups.group_code     IS '群组编码，唯一，前端展示用';
COMMENT ON COLUMN groups.group_name     IS '群组名称';
COMMENT ON COLUMN groups.description    IS '群组描述';
COMMENT ON COLUMN groups.owner_user_id  IS '群组创建者（所有者）用户 ID';
COMMENT ON COLUMN groups.status         IS '群组状态：ACTIVE | ARCHIVED';
COMMENT ON COLUMN groups.created_at     IS '创建时间';
COMMENT ON COLUMN groups.updated_at     IS '更新时间';

-- 索引：按所有者查询
CREATE INDEX IF NOT EXISTS idx_groups_owner ON groups (owner_user_id);


-- 群组成员表
CREATE TABLE IF NOT EXISTS group_memberships (
    id          BIGSERIAL       PRIMARY KEY,
    group_id    BIGINT          NOT NULL,
    user_id     BIGINT          NOT NULL,
    role        VARCHAR(16)     NOT NULL DEFAULT 'MEMBER',
    created_at  TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_membership_group FOREIGN KEY (group_id) REFERENCES groups (id),
    CONSTRAINT fk_membership_user  FOREIGN KEY (user_id)  REFERENCES users (id),
    CONSTRAINT uq_membership_group_user UNIQUE (group_id, user_id)
);

COMMENT ON TABLE  group_memberships             IS '群组成员关系表';
COMMENT ON COLUMN group_memberships.id          IS '主键';
COMMENT ON COLUMN group_memberships.group_id    IS '群组 ID';
COMMENT ON COLUMN group_memberships.user_id     IS '用户 ID';
COMMENT ON COLUMN group_memberships.role        IS '群组内角色：OWNER | MEMBER';
COMMENT ON COLUMN group_memberships.created_at  IS '加入时间';
COMMENT ON COLUMN group_memberships.updated_at  IS '更新时间';

-- 索引：按用户查询所属群组
CREATE INDEX IF NOT EXISTS idx_membership_user ON group_memberships (user_id);
-- 索引：按群组+角色查询（文档可读性判断用到）
CREATE INDEX IF NOT EXISTS idx_membership_group_role ON group_memberships (group_id, role);


-- 群组邀请表
CREATE TABLE IF NOT EXISTS group_invitations (
    id              BIGSERIAL       PRIMARY KEY,
    group_id        BIGINT          NOT NULL,
    inviter_user_id BIGINT          NOT NULL,
    invitee_user_id BIGINT          NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    decided_at      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_invitation_group   FOREIGN KEY (group_id)        REFERENCES groups (id),
    CONSTRAINT fk_invitation_inviter FOREIGN KEY (inviter_user_id) REFERENCES users (id),
    CONSTRAINT fk_invitation_invitee FOREIGN KEY (invitee_user_id) REFERENCES users (id)
);

COMMENT ON TABLE  group_invitations                 IS '群组邀请表';
COMMENT ON COLUMN group_invitations.id              IS '主键';
COMMENT ON COLUMN group_invitations.group_id        IS '群组 ID';
COMMENT ON COLUMN group_invitations.inviter_user_id IS '邀请人用户 ID';
COMMENT ON COLUMN group_invitations.invitee_user_id IS '被邀请人用户 ID';
COMMENT ON COLUMN group_invitations.status          IS '邀请状态：PENDING | ACCEPTED | REFUSED | CANCELLED';
COMMENT ON COLUMN group_invitations.decided_at      IS '决定时间（接受/拒绝时设置）';
COMMENT ON COLUMN group_invitations.created_at      IS '创建时间';
COMMENT ON COLUMN group_invitations.updated_at      IS '更新时间';

-- 索引：查询用户收到的待处理邀请
CREATE INDEX IF NOT EXISTS idx_invitation_invitee_status
    ON group_invitations (invitee_user_id, status);


-- 群组加入申请表
CREATE TABLE IF NOT EXISTS group_join_requests (
    id                  BIGSERIAL       PRIMARY KEY,
    group_id            BIGINT          NOT NULL,
    applicant_user_id   BIGINT          NOT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    decided_by_user_id  BIGINT,
    decided_at          TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_join_request_group     FOREIGN KEY (group_id)           REFERENCES groups (id),
    CONSTRAINT fk_join_request_applicant FOREIGN KEY (applicant_user_id)   REFERENCES users (id),
    CONSTRAINT fk_join_request_decider   FOREIGN KEY (decided_by_user_id) REFERENCES users (id)
);

COMMENT ON TABLE  group_join_requests                    IS '群组加入申请表';
COMMENT ON COLUMN group_join_requests.id                 IS '主键';
COMMENT ON COLUMN group_join_requests.group_id           IS '群组 ID';
COMMENT ON COLUMN group_join_requests.applicant_user_id  IS '申请人用户 ID';
COMMENT ON COLUMN group_join_requests.status             IS '申请状态：PENDING | APPROVED | REJECTED | CANCELLED';
COMMENT ON COLUMN group_join_requests.decided_by_user_id IS '审批人用户 ID';
COMMENT ON COLUMN group_join_requests.decided_at         IS '审批时间';
COMMENT ON COLUMN group_join_requests.created_at         IS '创建时间';
COMMENT ON COLUMN group_join_requests.updated_at         IS '更新时间';

-- 索引：查询某群组的待处理申请
CREATE INDEX IF NOT EXISTS idx_join_request_group_status
    ON group_join_requests (group_id, status);
-- 索引：查询某用户发起的申请
CREATE INDEX IF NOT EXISTS idx_join_request_applicant
    ON group_join_requests (applicant_user_id);


-- ===================================================
-- 文档管理
-- ===================================================

-- 文档表
CREATE TABLE IF NOT EXISTS documents (
    id                  BIGSERIAL       PRIMARY KEY,
    group_id            BIGINT          NOT NULL,
    uploader_user_id    BIGINT          NOT NULL,
    file_name           VARCHAR(512)    NOT NULL,
    file_ext            VARCHAR(32)     NOT NULL DEFAULT '',
    content_type        VARCHAR(128)    NOT NULL DEFAULT '',
    file_size           BIGINT          NOT NULL DEFAULT 0,
    file_hash           VARCHAR(128)    NOT NULL,
    storage_bucket      VARCHAR(128)    NOT NULL DEFAULT '',
    storage_object_key  VARCHAR(512)    NOT NULL DEFAULT '',
    status              VARCHAR(16)     NOT NULL DEFAULT 'UPLOADED',
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    failure_reason      TEXT,
    preview_text        TEXT,
    uploaded_at         TIMESTAMP,
    processed_at        TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_document_group    FOREIGN KEY (group_id)         REFERENCES groups (id),
    CONSTRAINT fk_document_uploader FOREIGN KEY (uploader_user_id) REFERENCES users (id)
);

COMMENT ON TABLE  documents                    IS '文档表';
COMMENT ON COLUMN documents.id                 IS '主键';
COMMENT ON COLUMN documents.group_id           IS '所属群组 ID';
COMMENT ON COLUMN documents.uploader_user_id   IS '上传者用户 ID';
COMMENT ON COLUMN documents.file_name          IS '原始文件名';
COMMENT ON COLUMN documents.file_ext           IS '文件扩展名（不含点）';
COMMENT ON COLUMN documents.content_type       IS 'MIME 类型';
COMMENT ON COLUMN documents.file_size          IS '文件大小（字节）';
COMMENT ON COLUMN documents.file_hash          IS '文件 SHA-256 哈希';
COMMENT ON COLUMN documents.storage_bucket     IS '对象存储 Bucket 名称';
COMMENT ON COLUMN documents.storage_object_key IS '对象存储 Object Key';
COMMENT ON COLUMN documents.status             IS '处理状态：UPLOADED | PROCESSING | READY | FAILED';
COMMENT ON COLUMN documents.deleted            IS '逻辑删除标记';
COMMENT ON COLUMN documents.failure_reason     IS '处理失败原因';
COMMENT ON COLUMN documents.preview_text       IS '文档预览文本（前若干字符）';
COMMENT ON COLUMN documents.uploaded_at        IS '上传完成时间';
COMMENT ON COLUMN documents.processed_at       IS '处理完成时间';
COMMENT ON COLUMN documents.created_at         IS '创建时间';
COMMENT ON COLUMN documents.updated_at         IS '更新时间';

-- 索引：按群组+删除状态查询文档列表
CREATE INDEX IF NOT EXISTS idx_documents_group_deleted
    ON documents (group_id, deleted);
-- 索引：按群组+文件哈希查重（防止重复上传）
CREATE INDEX IF NOT EXISTS idx_documents_group_hash
    ON documents (group_id, file_hash) WHERE status = 'READY' AND deleted = FALSE;
-- 索引：按状态查询（处理中、失败等）
CREATE INDEX IF NOT EXISTS idx_documents_status
    ON documents (status, deleted);
-- 索引：按上传者查询
CREATE INDEX IF NOT EXISTS idx_documents_uploader
    ON documents (uploader_user_id);


-- 文档上传会话表（分片上传）
CREATE TABLE IF NOT EXISTS document_upload_sessions (
    id                  BIGSERIAL       PRIMARY KEY,
    upload_id           VARCHAR(64)     NOT NULL,
    group_id            BIGINT          NOT NULL,
    uploader_user_id    BIGINT          NOT NULL,
    file_name           VARCHAR(512)    NOT NULL,
    file_ext            VARCHAR(32)     NOT NULL DEFAULT '',
    content_type        VARCHAR(128)    NOT NULL DEFAULT '',
    file_size           BIGINT          NOT NULL DEFAULT 0,
    file_hash           VARCHAR(128)    NOT NULL DEFAULT '',
    chunk_size          BIGINT          NOT NULL DEFAULT 0,
    chunk_count         INTEGER         NOT NULL DEFAULT 0,
    status              VARCHAR(16)     NOT NULL DEFAULT 'INIT',
    storage_bucket      VARCHAR(128)    NOT NULL DEFAULT '',
    merged_object_key   VARCHAR(512),
    expires_at          TIMESTAMP       NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_upload_session_group    FOREIGN KEY (group_id)         REFERENCES groups (id),
    CONSTRAINT fk_upload_session_uploader FOREIGN KEY (uploader_user_id) REFERENCES users (id),
    CONSTRAINT uq_upload_session_upload_id UNIQUE (upload_id)
);

COMMENT ON TABLE  document_upload_sessions                   IS '文档上传会话表（分片上传）';
COMMENT ON COLUMN document_upload_sessions.id                IS '主键';
COMMENT ON COLUMN document_upload_sessions.upload_id         IS '上传会话唯一标识（UUID）';
COMMENT ON COLUMN document_upload_sessions.group_id          IS '目标群组 ID';
COMMENT ON COLUMN document_upload_sessions.uploader_user_id  IS '上传者用户 ID';
COMMENT ON COLUMN document_upload_sessions.file_name         IS '原始文件名';
COMMENT ON COLUMN document_upload_sessions.file_ext          IS '文件扩展名';
COMMENT ON COLUMN document_upload_sessions.content_type      IS 'MIME 类型';
COMMENT ON COLUMN document_upload_sessions.file_size         IS '文件总大小（字节）';
COMMENT ON COLUMN document_upload_sessions.file_hash         IS '完整文件 SHA-256 哈希';
COMMENT ON COLUMN document_upload_sessions.chunk_size        IS '每个分片的标准大小（字节）';
COMMENT ON COLUMN document_upload_sessions.chunk_count       IS '总分片数';
COMMENT ON COLUMN document_upload_sessions.status            IS '会话状态：INIT | UPLOADING | COMPLETING | COMPLETED | EXPIRED';
COMMENT ON COLUMN document_upload_sessions.storage_bucket    IS '对象存储 Bucket 名称';
COMMENT ON COLUMN document_upload_sessions.merged_object_key IS '合并后完整文件的对象存储 Key';
COMMENT ON COLUMN document_upload_sessions.expires_at        IS '会话过期时间';
COMMENT ON COLUMN document_upload_sessions.created_at        IS '创建时间';
COMMENT ON COLUMN document_upload_sessions.updated_at        IS '更新时间';

-- 索引：查询可复用的上传会话
CREATE INDEX IF NOT EXISTS idx_upload_session_reusable
    ON document_upload_sessions (group_id, uploader_user_id, file_hash, status, expires_at);


-- 文档上传分片表
CREATE TABLE IF NOT EXISTS document_upload_chunks (
    id                  BIGSERIAL       PRIMARY KEY,
    upload_id           VARCHAR(64)     NOT NULL,
    chunk_index         INTEGER         NOT NULL,
    chunk_size          BIGINT          NOT NULL DEFAULT 0,
    chunk_hash          VARCHAR(128)    NOT NULL DEFAULT '',
    storage_bucket      VARCHAR(128)    NOT NULL DEFAULT '',
    storage_object_key  VARCHAR(512)    NOT NULL DEFAULT '',
    uploaded_at         TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_upload_chunk_session FOREIGN KEY (upload_id) REFERENCES document_upload_sessions (upload_id),
    CONSTRAINT uq_upload_chunk UNIQUE (upload_id, chunk_index)
);

COMMENT ON TABLE  document_upload_chunks                    IS '文档上传分片表';
COMMENT ON COLUMN document_upload_chunks.id                 IS '主键';
COMMENT ON COLUMN document_upload_chunks.upload_id          IS '所属上传会话 ID';
COMMENT ON COLUMN document_upload_chunks.chunk_index        IS '分片序号（从 0 开始）';
COMMENT ON COLUMN document_upload_chunks.chunk_size         IS '分片实际大小（字节）';
COMMENT ON COLUMN document_upload_chunks.chunk_hash         IS '分片 SHA-256 哈希';
COMMENT ON COLUMN document_upload_chunks.storage_bucket     IS '对象存储 Bucket 名称';
COMMENT ON COLUMN document_upload_chunks.storage_object_key IS '分片对象存储 Key';
COMMENT ON COLUMN document_upload_chunks.uploaded_at        IS '分片上传完成时间';
COMMENT ON COLUMN document_upload_chunks.created_at         IS '创建时间';
COMMENT ON COLUMN document_upload_chunks.updated_at         IS '更新时间';

-- 索引：按上传会话查询所有分片
CREATE INDEX IF NOT EXISTS idx_upload_chunk_upload_id
    ON document_upload_chunks (upload_id, chunk_index);


-- ===================================================
-- 文档摄入（ETL 流水线）
-- ===================================================

-- 文档切片表
CREATE TABLE IF NOT EXISTS document_chunks (
    id              BIGSERIAL       PRIMARY KEY,
    document_id     BIGINT          NOT NULL,
    group_id        BIGINT          NOT NULL,
    chunk_index     INTEGER         NOT NULL,
    chunk_text      TEXT            NOT NULL,
    chunk_summary   TEXT,
    char_start      INTEGER         NOT NULL DEFAULT 0,
    char_end        INTEGER         NOT NULL DEFAULT 0,
    metadata_json   JSONB,
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_chunk_document FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT fk_chunk_group    FOREIGN KEY (group_id)    REFERENCES groups (id)
);

COMMENT ON TABLE  document_chunks               IS '文档切片表（向量检索的最小单元）';
COMMENT ON COLUMN document_chunks.id            IS '主键';
COMMENT ON COLUMN document_chunks.document_id   IS '所属文档 ID';
COMMENT ON COLUMN document_chunks.group_id      IS '所属群组 ID（冗余，加速查询）';
COMMENT ON COLUMN document_chunks.chunk_index   IS '切片在文档中的序号';
COMMENT ON COLUMN document_chunks.chunk_text    IS '切片文本内容';
COMMENT ON COLUMN document_chunks.chunk_summary IS '切片摘要（可选）';
COMMENT ON COLUMN document_chunks.char_start    IS '切片在原文档中的起始字符位置';
COMMENT ON COLUMN document_chunks.char_end      IS '切片在原文档中的结束字符位置';
COMMENT ON COLUMN document_chunks.metadata_json IS '切片元数据（JSON 格式）';
COMMENT ON COLUMN document_chunks.created_at    IS '创建时间';
COMMENT ON COLUMN document_chunks.updated_at    IS '更新时间';

-- 索引：按文档 ID 查询所有切片
CREATE INDEX IF NOT EXISTS idx_chunk_document ON document_chunks (document_id);
-- 索引：按群组+状态查询可向量化的切片
CREATE INDEX IF NOT EXISTS idx_chunk_group ON document_chunks (group_id);


-- 摄入任务表（异步 Worker 调度）
CREATE TABLE IF NOT EXISTS ingestion_jobs (
    id              BIGSERIAL       PRIMARY KEY,
    document_id     BIGINT          NOT NULL,
    group_id        BIGINT          NOT NULL,
    job_type        VARCHAR(32)     NOT NULL DEFAULT 'INGEST_DOCUMENT',
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    max_retries     INTEGER         NOT NULL DEFAULT 3,
    worker_id       VARCHAR(128),
    started_at      TIMESTAMP,
    finished_at     TIMESTAMP,
    next_retry_at   TIMESTAMP,
    last_error      TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_job_document FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT fk_job_group    FOREIGN KEY (group_id)    REFERENCES groups (id)
);

COMMENT ON TABLE  ingestion_jobs               IS '文档摄入任务表（异步 Worker 调度）';
COMMENT ON COLUMN ingestion_jobs.id            IS '主键';
COMMENT ON COLUMN ingestion_jobs.document_id   IS '关联的文档 ID';
COMMENT ON COLUMN ingestion_jobs.group_id      IS '关联的群组 ID';
COMMENT ON COLUMN ingestion_jobs.job_type      IS '任务类型：INGEST_DOCUMENT';
COMMENT ON COLUMN ingestion_jobs.status        IS '任务状态：PENDING | RUNNING | SUCCEEDED | FAILED | CANCELLED';
COMMENT ON COLUMN ingestion_jobs.retry_count   IS '当前重试次数';
COMMENT ON COLUMN ingestion_jobs.max_retries   IS '最大重试次数';
COMMENT ON COLUMN ingestion_jobs.worker_id     IS '执行该任务的 Worker 标识';
COMMENT ON COLUMN ingestion_jobs.started_at    IS '任务开始执行时间';
COMMENT ON COLUMN ingestion_jobs.finished_at   IS '任务完成时间';
COMMENT ON COLUMN ingestion_jobs.next_retry_at IS '下次重试时间（null 表示无需重试）';
COMMENT ON COLUMN ingestion_jobs.last_error    IS '最近一次失败的错误信息';
COMMENT ON COLUMN ingestion_jobs.created_at    IS '创建时间';
COMMENT ON COLUMN ingestion_jobs.updated_at    IS '更新时间';

-- 索引：Worker 轮询可执行任务（按状态+计划时间排序）
CREATE INDEX IF NOT EXISTS idx_job_runnable
    ON ingestion_jobs (status, next_retry_at, created_at);
-- 索引：按文档 ID 查询关联任务
CREATE INDEX IF NOT EXISTS idx_job_document ON ingestion_jobs (document_id);


-- ===================================================
-- 向量存储（PGvector）
-- ===================================================

-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 向量存储表（由 Spring AI PGvector 自动管理，此处 DDL 供手动建库参考）
CREATE TABLE IF NOT EXISTS vector_store (
    id          VARCHAR(36)     PRIMARY KEY,
    content     TEXT,
    metadata    JSONB,
    embedding   VECTOR(512)
);

COMMENT ON TABLE  vector_store            IS '向量存储表（PGvector）';
COMMENT ON COLUMN vector_store.id         IS '向量条目唯一标识（UUID）';
COMMENT ON COLUMN vector_store.content    IS '原始文本内容';
COMMENT ON COLUMN vector_store.metadata   IS '元数据（JSONB 格式，含 document_id、chunk_index、source 等）';
COMMENT ON COLUMN vector_store.embedding  IS '文本嵌入向量（512 维）';

-- HNSW 索引（加速近似最近邻搜索）
CREATE INDEX IF NOT EXISTS idx_vector_embedding_hnsw
    ON vector_store
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);


-- ===================================================
-- AI 助手模块（V4.0）
-- ===================================================

-- 助手会话表
CREATE TABLE IF NOT EXISTS assistant_sessions (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    title           VARCHAR(255)    NOT NULL DEFAULT '新会话',
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    last_message_at TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_assistant_session_user FOREIGN KEY (user_id) REFERENCES users (id)
);

COMMENT ON TABLE  assistant_sessions                IS 'AI 助手会话表';
COMMENT ON COLUMN assistant_sessions.id             IS '会话主键';
COMMENT ON COLUMN assistant_sessions.user_id        IS '所属用户 ID';
COMMENT ON COLUMN assistant_sessions.title          IS '会话标题，默认"新会话"，首条消息自动重命名';
COMMENT ON COLUMN assistant_sessions.status         IS '会话状态：ACTIVE | ARCHIVED | DELETED';
COMMENT ON COLUMN assistant_sessions.last_message_at IS '最后消息时间，用于排序展示';
COMMENT ON COLUMN assistant_sessions.created_at     IS '创建时间';
COMMENT ON COLUMN assistant_sessions.updated_at     IS '更新时间';

-- 索引：按用户+状态查询会话列表
CREATE INDEX IF NOT EXISTS idx_sessions_user_status
    ON assistant_sessions (user_id, status);


-- 助手消息表
CREATE TABLE IF NOT EXISTS assistant_messages (
    id                  BIGSERIAL       PRIMARY KEY,
    session_id          BIGINT          NOT NULL,
    role                VARCHAR(16)     NOT NULL,
    tool_mode           VARCHAR(32)     NOT NULL,
    group_id            BIGINT,
    content             TEXT            NOT NULL,
    structured_payload  JSONB,
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_assistant_message_session FOREIGN KEY (session_id) REFERENCES assistant_sessions (id)
);

COMMENT ON TABLE  assistant_messages                   IS 'AI 助手消息表';
COMMENT ON COLUMN assistant_messages.id                IS '消息主键';
COMMENT ON COLUMN assistant_messages.session_id        IS '所属会话 ID';
COMMENT ON COLUMN assistant_messages.role              IS '消息角色：USER | ASSISTANT | TOOL';
COMMENT ON COLUMN assistant_messages.tool_mode         IS '消息产生时的工具模式：CHAT | KB_SEARCH';
COMMENT ON COLUMN assistant_messages.group_id          IS '知识库组 ID，CHAT 模式下为 NULL';
COMMENT ON COLUMN assistant_messages.content           IS '消息文本内容';
COMMENT ON COLUMN assistant_messages.structured_payload IS '结构化负载数据（JSONB 格式），如工具调用结果';
COMMENT ON COLUMN assistant_messages.created_at        IS '消息创建时间';

-- 索引：按会话+时间查询消息（上下文加载 + 分页）
CREATE INDEX IF NOT EXISTS idx_messages_session_created
    ON assistant_messages (session_id, created_at);


-- 助手会话上下文表（短期记忆存储）
CREATE TABLE IF NOT EXISTS assistant_session_contexts (
    session_id                          BIGINT      PRIMARY KEY,
    session_memory                      TEXT,
    compact_summary                     TEXT,
    session_memory_base_message_id      BIGINT,
    session_memory_range_end_message_id BIGINT,
    compact_summary_base_message_id     BIGINT,
    compact_summary_range_end_message_id BIGINT,
    summary_text                        TEXT,
    source_message_id                   BIGINT,
    context_version                     BIGINT      NOT NULL DEFAULT 0,
    updated_at                          TIMESTAMP   NOT NULL DEFAULT now(),

    CONSTRAINT fk_session_context_session FOREIGN KEY (session_id) REFERENCES assistant_sessions (id)
);

COMMENT ON TABLE  assistant_session_contexts                             IS 'AI 助手会话上下文表（短期记忆存储）';
COMMENT ON COLUMN assistant_session_contexts.session_id                  IS '会话 ID，一对一关联 assistant_sessions.id';
COMMENT ON COLUMN assistant_session_contexts.session_memory              IS 'LLM 生成的增量会话记忆摘要';
COMMENT ON COLUMN assistant_session_contexts.compact_summary             IS 'LLM 生成的紧凑摘要（更精炼的压缩）';
COMMENT ON COLUMN assistant_session_contexts.session_memory_base_message_id      IS '会话记忆覆盖的起始消息 ID';
COMMENT ON COLUMN assistant_session_contexts.session_memory_range_end_message_id IS '会话记忆覆盖的结束消息 ID';
COMMENT ON COLUMN assistant_session_contexts.compact_summary_base_message_id     IS '紧凑摘要覆盖的起始消息 ID';
COMMENT ON COLUMN assistant_session_contexts.compact_summary_range_end_message_id IS '紧凑摘要覆盖的结束消息 ID';
COMMENT ON COLUMN assistant_session_contexts.summary_text                IS '会话摘要文本（早期消息的简明摘要）';
COMMENT ON COLUMN assistant_session_contexts.source_message_id           IS '摘要覆盖的结束消息 ID';
COMMENT ON COLUMN assistant_session_contexts.context_version             IS '乐观锁版本号，初始为 0，每次更新递增';
COMMENT ON COLUMN assistant_session_contexts.updated_at                  IS '上下文最后更新时间';


-- ============================================================
-- LLM 调用统计记录表
-- 记录所有 AI 模块（QA / Assistant / 未来扩展）的 LLM 调用明细
-- ============================================================

CREATE TABLE IF NOT EXISTS llm_usage_records (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             BIGINT          NOT NULL,
    group_id            BIGINT,
    module              VARCHAR(32)     NOT NULL,
    endpoint            VARCHAR(64)     NOT NULL,
    session_id          VARCHAR(64),
    prompt_tokens       INT             NOT NULL DEFAULT 0,
    completion_tokens   INT             NOT NULL DEFAULT 0,
    total_tokens        INT             NOT NULL DEFAULT 0,
    is_estimated        BOOLEAN         NOT NULL DEFAULT FALSE,
    cost_amount         DECIMAL(12,6)   DEFAULT 0,
    cost_currency       VARCHAR(8)      DEFAULT 'CNY',
    latency_ms          BIGINT          NOT NULL DEFAULT 0,
    success             BOOLEAN         NOT NULL DEFAULT TRUE,
    error_message       TEXT,
    model_name          VARCHAR(64),
    created_at          TIMESTAMP       NOT NULL DEFAULT now()
);

COMMENT ON TABLE  llm_usage_records                   IS 'LLM调用统计记录表';

COMMENT ON COLUMN llm_usage_records.id                IS '主键';
COMMENT ON COLUMN llm_usage_records.user_id           IS '调用用户ID';
COMMENT ON COLUMN llm_usage_records.group_id          IS '关联群组ID（可为空）';
COMMENT ON COLUMN llm_usage_records.module            IS '模块标识: QA / ASSISTANT';
COMMENT ON COLUMN llm_usage_records.endpoint          IS '接口标识: qa/ask, qa/stream-ask, assistant/chat, assistant/chat/stream';
COMMENT ON COLUMN llm_usage_records.session_id        IS '助手会话ID（QA模块可为空）';
COMMENT ON COLUMN llm_usage_records.prompt_tokens     IS '输入token数';
COMMENT ON COLUMN llm_usage_records.completion_tokens IS '输出token数';
COMMENT ON COLUMN llm_usage_records.total_tokens      IS '总token数';
COMMENT ON COLUMN llm_usage_records.is_estimated      IS '是否为估算值（流式调用可能无法获取精确token数）';
COMMENT ON COLUMN llm_usage_records.cost_amount       IS '本次调用费用（元）';
COMMENT ON COLUMN llm_usage_records.cost_currency     IS '货币单位';
COMMENT ON COLUMN llm_usage_records.latency_ms        IS '响应耗时(毫秒)';
COMMENT ON COLUMN llm_usage_records.success           IS '是否成功';
COMMENT ON COLUMN llm_usage_records.error_message     IS '失败原因';
COMMENT ON COLUMN llm_usage_records.model_name        IS '使用的模型名称';
COMMENT ON COLUMN llm_usage_records.created_at        IS '记录时间';

-- 索引：按用户+时间查询调用记录
CREATE INDEX IF NOT EXISTS idx_llm_usage_user_created
    ON llm_usage_records (user_id, created_at);
-- 索引：按群组+时间查询调用记录
CREATE INDEX IF NOT EXISTS idx_llm_usage_group_created
    ON llm_usage_records (group_id, created_at);
-- 索引：按模块+时间查询调用记录
CREATE INDEX IF NOT EXISTS idx_llm_usage_module_created
    ON llm_usage_records (module, created_at);
-- 索引：按时间查询调用记录
CREATE INDEX IF NOT EXISTS idx_llm_usage_created_at
    ON llm_usage_records (created_at);


-- ============================================================
-- QA records persistence (V5.0)
-- ============================================================

CREATE TABLE IF NOT EXISTS qa_records (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             BIGINT          NOT NULL,
    group_id            BIGINT          NOT NULL,
    question            TEXT            NOT NULL,
    answer              TEXT,
    answered            BOOLEAN         NOT NULL DEFAULT FALSE,
    reason_code         VARCHAR(64),
    reason_message      TEXT,
    evidence_level      VARCHAR(32),
    citation_count      INTEGER         NOT NULL DEFAULT 0,
    prompt_tokens       INTEGER         NOT NULL DEFAULT 0,
    completion_tokens   INTEGER         NOT NULL DEFAULT 0,
    total_tokens        INTEGER         NOT NULL DEFAULT 0,
    is_estimated        BOOLEAN         NOT NULL DEFAULT FALSE,
    latency_ms          BIGINT          NOT NULL DEFAULT 0,
    model_name          VARCHAR(64),
    endpoint            VARCHAR(64)     NOT NULL,
    success             BOOLEAN         NOT NULL DEFAULT TRUE,
    error_message       TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_qa_record_user  FOREIGN KEY (user_id)  REFERENCES users (id),
    CONSTRAINT fk_qa_record_group FOREIGN KEY (group_id) REFERENCES groups (id)
);

CREATE TABLE IF NOT EXISTS qa_record_citations (
    id                   BIGSERIAL       PRIMARY KEY,
    qa_record_id         BIGINT          NOT NULL,
    document_id          BIGINT,
    document_version_id  BIGINT,
    chunk_id             BIGINT,
    chunk_index          INTEGER,
    start_chunk_index    INTEGER,
    end_chunk_index      INTEGER,
    file_name            VARCHAR(512)    NOT NULL,
    score                DOUBLE PRECISION,
    retrieval_source     VARCHAR(32),
    vector_score         DOUBLE PRECISION,
    keyword_score        DOUBLE PRECISION,
    hybrid_score         DOUBLE PRECISION,
    snippet              TEXT,
    created_at           TIMESTAMP       NOT NULL DEFAULT now(),

    CONSTRAINT fk_qa_record_citation_record
        FOREIGN KEY (qa_record_id) REFERENCES qa_records (id) ON DELETE CASCADE
);

COMMENT ON TABLE qa_records IS 'QA 问答记录表，保存每次知识库问答的完整结果和用量信息';
COMMENT ON COLUMN qa_records.id IS '问答记录主键';
COMMENT ON COLUMN qa_records.user_id IS '提问用户 ID';
COMMENT ON COLUMN qa_records.group_id IS '问答发生的知识库群组 ID';
COMMENT ON COLUMN qa_records.question IS '用户原始问题';
COMMENT ON COLUMN qa_records.answer IS '模型回答正文，拒答或失败时可为空';
COMMENT ON COLUMN qa_records.answered IS '是否成功回答问题';
COMMENT ON COLUMN qa_records.reason_code IS '拒答或失败原因编码';
COMMENT ON COLUMN qa_records.reason_message IS '拒答或失败原因说明';
COMMENT ON COLUMN qa_records.evidence_level IS '检索证据充分性等级';
COMMENT ON COLUMN qa_records.citation_count IS '保存的引用快照数量';
COMMENT ON COLUMN qa_records.prompt_tokens IS '输入 token 数';
COMMENT ON COLUMN qa_records.completion_tokens IS '输出 token 数';
COMMENT ON COLUMN qa_records.total_tokens IS '总 token 数';
COMMENT ON COLUMN qa_records.is_estimated IS 'token 用量是否为估算值';
COMMENT ON COLUMN qa_records.latency_ms IS '问答耗时，单位毫秒';
COMMENT ON COLUMN qa_records.model_name IS '调用的大模型名称';
COMMENT ON COLUMN qa_records.endpoint IS '触发记录的接口端点';
COMMENT ON COLUMN qa_records.success IS '本次问答流程是否成功完成';
COMMENT ON COLUMN qa_records.error_message IS '系统异常信息，业务拒答时通常为空';
COMMENT ON COLUMN qa_records.created_at IS '记录创建时间';

COMMENT ON TABLE qa_record_citations IS 'QA 引用快照表，保存回答时实际使用的检索证据';
COMMENT ON COLUMN qa_record_citations.id IS '引用快照主键';
COMMENT ON COLUMN qa_record_citations.qa_record_id IS '所属 QA 问答记录 ID';
COMMENT ON COLUMN qa_record_citations.document_id IS '来源文档 ID';
COMMENT ON COLUMN qa_record_citations.document_version_id IS '来源文档版本 ID，当前系统未启用版本时可为空';
COMMENT ON COLUMN qa_record_citations.chunk_id IS '来源切片 ID';
COMMENT ON COLUMN qa_record_citations.chunk_index IS '切片在文档中的序号';
COMMENT ON COLUMN qa_record_citations.start_chunk_index IS '合并证据片段的起始切片序号';
COMMENT ON COLUMN qa_record_citations.end_chunk_index IS '合并证据片段的结束切片序号';
COMMENT ON COLUMN qa_record_citations.file_name IS '来源文件名';
COMMENT ON COLUMN qa_record_citations.score IS '最终相关性得分';
COMMENT ON COLUMN qa_record_citations.retrieval_source IS '检索来源类型，如 VECTOR、KEYWORD、BOTH';
COMMENT ON COLUMN qa_record_citations.vector_score IS '向量检索得分';
COMMENT ON COLUMN qa_record_citations.keyword_score IS '关键词检索得分';
COMMENT ON COLUMN qa_record_citations.hybrid_score IS '混合检索融合得分';
COMMENT ON COLUMN qa_record_citations.snippet IS '回答时使用的证据文本快照';
COMMENT ON COLUMN qa_record_citations.created_at IS '快照创建时间';

CREATE INDEX IF NOT EXISTS idx_qa_records_user_created
    ON qa_records (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_qa_records_group_created
    ON qa_records (group_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_qa_records_group_user_created
    ON qa_records (group_id, user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_qa_records_answered_created
    ON qa_records (answered, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_qa_record_citations_record
    ON qa_record_citations (qa_record_id, id);
