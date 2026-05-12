# Argus — 百眼巨人 · RAG 知识库平台

<p align="center">
  <strong>文档上传 · 智能解析 · 向量检索 · AI 对话</strong>
</p>

Argus 是一个基于 **RAG（检索增强生成）** 架构的知识库平台。用户可以创建协作群组、上传文档，经过自动解析与向量化后，通过 AI 对话方式与知识库内容进行交互。平台支持一次性问答和具备上下文记忆的多轮 Agent 对话两种模式，所有回答均附带可追溯的文档引用。

---

## 功能概览

| 模块 | 功能 |
|------|------|
| 用户认证 | 注册/登录、JWT 双令牌、角色权限（管理员/普通用户）、强制修改密码 |
| 群组协作 | 创建群组、邀请成员、加入申请、审批流程、成员管理、角色控制 |
| 文档管理 | 分片上传 / 直接上传、PDF/DOCX/MD/TXT 解析、MinIO 对象存储、软删除 |
| 文档索引 | ETL 异步处理流水线、PGvector 向量化（HNSW 索引）、Elasticsearch 关键词检索 |
| 知识库问答 | 混合检索 + 查询规划 + 证据评估 + 大模型生成回答 + 引用溯源 |
| AI 助手 | ReactAgent 多轮对话、CHAT/KB_SEARCH 双模式、SSE 流式输出、短期记忆管理 |
| 系统管理 | 用户 CRUD、启用/禁用、操作日志 AOP、Knife4j API 文档 |

---

## 技术栈

### 后端

| 层次 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 21 |
| 框架 | Spring Boot (MVC) | 3.5.0 |
| 构建 | Maven + mvnw | 3.9.14 |
| 数据库 | PostgreSQL + pgvector | 16+ |
| ORM | MyBatis-Plus | 3.5.15 |
| 认证 | JJWT + Spring Security Crypto (BCrypt) | 0.12.6 |
| API 文档 | Knife4j + SpringDoc | 4.5.0 |
| AI 模型 | Spring AI Alibaba (DashScope / 通义千问) | 1.1.2.0 |
| AI Agent | Spring AI Alibaba Agent Framework (ReactAgent) | 1.1.2.0 |
| 向量存储 | Spring AI PGvector | 1.1.2 |
| 对象存储 | MinIO | 8.5.17 |
| 搜索引擎 | Elasticsearch | 8.x |
| 文档解析 | Apache PDFBox / POI | 2.0.31 / 5.2.5 |

### 前端

| 层次 | 技术 | 版本 |
|------|------|------|
| 语言 | TypeScript | 6.0 |
| 框架 | Vue 3 (Composition API) | 3.5 |
| 构建 | Vite | 8.0 |
| 路由 | Vue Router | 5.0 |
| 状态管理 | Pinia | 3.0 |
| UI 组件 | Element Plus | 2.14 |
| HTTP | Axios | 1.16 |
| Markdown | marked | 18.0 |

---

## 项目结构

```
Argus/
├── Argus-backend/                 # Spring Boot 后端
│   ├── src/main/java/com/argus/rag/
│   │   ├── ArgusBackendApplication.java
│   │   ├── common/                # 公共基础设施
│   │   │   ├── api/ApiResponse    #   统一响应体 { success, data, message }
│   │   │   ├── enums/             #   枚举（角色、状态、群组角色等）
│   │   │   ├── exception/         #   全局异常处理 + 业务异常类
│   │   │   └── log/               #   AOP 操作日志
│   │   ├── auth/                  # 认证授权模块
│   │   │   ├── controller/        #   /api/auth/*
│   │   │   ├── service/           #   登录/注册/令牌刷新
│   │   │   ├── security/          #   JWT 过滤器 + 双令牌机制
│   │   │   ├── config/            #   认证配置 + 开发环境管理员初始化
│   │   │   └── model/             #   实体、DTO、VO
│   │   ├── user/                  # 用户管理模块
│   │   │   ├── controller/        #   /api/account/*, /api/admin/users/*
│   │   │   ├── service/           #   账户管理 + 管理员用户管理
│   │   │   └── model/             #   实体、DTO、VO
│   │   ├── group/                 # 群组协作模块
│   │   │   ├── controller/        #   /api/groups/* 群组 CRUD + 成员管理
│   │   │   ├── service/           #   群组管理 + 成员服务 + 邀请/申请
│   │   │   └── model/             #   实体（群组、成员、邀请、申请）
│   │   ├── document/              # 文档管理模块
│   │   │   ├── controller/        #   /api/documents/* 上传/查询/删除/预览/下载
│   │   │   ├── service/           #   分片上传 + 查询 + 预览 + 下载 + 异步 ETL 触发
│   │   │   └── model/             #   实体、DTO、VO
│   │   ├── ingestion/             # 文档索引（ETL）模块
│   │   │   ├── service/           #   异步处理器 + ETL 流水线
│   │   │   └── model/             #   IngestionJob + DocumentChunk 实体
│   │   ├── qa/                    # 知识库问答模块
│   │   │   ├── controller/        #   /api/qa/ask
│   │   │   ├── service/           #   查询规划 + 混合检索 + 回答生成
│   │   │   ├── rag/               #   RAG 检索（QueryRewrite + EvidenceBundle）
│   │   │   └── model/             #   请求/响应模型
│   │   └── assistant/             # AI 助手模块
│   │       ├── controller/        #   /api/assistant/chat, /chat/stream, /sessions
│   │       ├── service/           #   对话编排 + 会话管理 + 流式事件发射
│   │       ├── agent/             #   ReactAgent 工厂 + Agent 门面
│   │       ├── memory/            #   短期记忆管理（三级压缩策略）
│   │       └── model/             #   实体、DTO、VO、枚举
│   └── src/main/resources/
│       ├── application.yml        # 公共配置（MyBatis-Plus 等）
│       ├── application-local.yml  # 本地环境配置
│       ├── mappers/               # MyBatis XML 映射文件
│       └── prompts/               # AI Prompt 模板（StringTemplate4）
│
├── Argus-frontend/                # Vue 3 前端
│   └── src/
│       ├── api/                   # 后端 API 封装（auth, document, group, qa, assistant）
│       ├── stores/                # Pinia 状态管理（auth, app）
│       ├── router/                # Vue Router 路由 + 导航守卫
│       ├── layouts/               # DefaultLayout（侧边栏 + 顶栏布局）
│       ├── views/
│       │   ├── HomeView.vue       #   公开首页
│       │   ├── LoginView.vue      #   登录页
│       │   ├── documents/         #   文档管理页
│       │   ├── qa/                #   知识库问答页
│       │   ├── assistant/         #   AI 助手页
│       │   ├── groups/            #   协作小组页
│       │   ├── admin/             #   用户管理页
│       │   └── settings/          #   系统设置页
│       ├── components/            # 公共组件
│       └── types/                 # TypeScript 类型定义
│
└── docs/                          # 项目文档（版本迭代文档）
```

---

## 快速开始

### 环境要求

- **JDK 21**（Record 语法、虚拟线程）
- **Node.js** >= 20.19.0 或 >= 22.12.0
- **PostgreSQL 16+**（需安装 pgvector 扩展）
- **Elasticsearch 8.x**（需安装 IK 中文分词器）
- **MinIO**（对象存储，可按需启用）

### 后端启动

```bash
# 1. 设置 JDK 21
export JAVA_HOME="/path/to/jdk-21"

# 2. 配置数据库连接
#    编辑 Argus-backend/src/main/resources/application-local.yml
#    填写 PostgreSQL、Elasticsearch、DashScope API Key 等配置

# 3. 编译
cd Argus-backend
./mvnw clean compile

# 4. 运行（默认 local 环境）
./mvnw spring-boot:run

# API 文档：http://localhost:8080/doc.html
```

### 前端启动

```bash
cd Argus-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 访问：http://localhost:5173
```

### 默认账户

开发环境（`--spring.profiles.active=dev`）会自动创建管理员账户：
- 用户名：`admin`
- 密码：`admin123`

---

## API 端点总览

### 认证 - `/api/auth`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/auth/refresh` | 刷新令牌 |
| POST | `/api/auth/logout` | 登出 |
| GET | `/api/auth/me` | 获取当前用户信息 |

### 用户管理 - `/api/account`, `/api/admin/users`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/account/change-password` | 修改密码 |
| GET | `/api/admin/users` | 用户列表（管理员） |
| GET | `/api/admin/users/{id}` | 用户详情（管理员） |
| PUT | `/api/admin/users/{id}/status` | 启用/禁用用户 |
| PUT | `/api/admin/users/{id}/password` | 重置密码 |

### 群组 - `/api/groups`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/groups` | 创建群组 |
| GET | `/api/groups` | 查询可见群组列表 |
| GET | `/api/groups/{id}/members` | 成员列表 |
| POST | `/api/groups/{id}/invitations` | 创建邀请 |
| GET | `/api/groups/invitations/my-sent` | 我发出的邀请 |
| POST | `/api/groups/invitations/{id}/accept` | 接受邀请 |
| POST | `/api/groups/invitations/{id}/reject` | 拒绝邀请 |
| POST | `/api/groups/{id}/join-request` | 申请加入 |
| POST | `/api/groups/join-requests/{id}/approve` | 审批通过 |
| POST | `/api/groups/join-requests/{id}/reject` | 审批拒绝 |
| DELETE | `/api/groups/{id}/members/{userId}` | 移除成员 |
| POST | `/api/groups/{id}/leave` | 退出群组 |

### 文档 - `/api/documents`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/documents` | 文档列表 |
| POST | `/api/documents/upload` | 直接上传（≤10MB） |
| POST | `/api/documents/upload/init` | 初始化分片上传 |
| POST | `/api/documents/upload/chunks` | 上传分片 |
| GET | `/api/documents/upload/{uploadId}` | 查询上传状态 |
| POST | `/api/documents/upload/{uploadId}/complete` | 完成分片上传 |
| GET | `/api/documents/{id}/preview` | 预览文档 |
| GET | `/api/documents/{id}/download` | 下载文档 |
| DELETE | `/api/documents/{id}` | 软删除文档 |
| POST | `/api/documents/{id}/retry-ingestion` | 重试失败文档 |

### 知识库问答 - `/api/qa`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/qa/ask` | 提交问题，获取 AI 回答 + 引用 |

### AI 助手 - `/api/assistant`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/assistant/chat` | 同步聊天 |
| POST | `/api/assistant/chat/stream` | 流式聊天（SSE） |
| GET | `/api/assistant/sessions` | 会话列表 |
| POST | `/api/assistant/sessions` | 创建会话 |
| GET | `/api/assistant/sessions/{id}` | 会话详情 |
| PATCH | `/api/assistant/sessions/{id}` | 重命名会话 |
| DELETE | `/api/assistant/sessions/{id}` | 删除会话 |
| GET | `/api/assistant/sessions/{id}/context` | 获取会话上下文 |
| GET | `/api/assistant/sessions/{id}/messages` | 获取会话消息 |

---

## 架构设计

### 认证流程

```
客户端请求 → JwtAuthenticationFilter（提取 Bearer Token）
           → JwtAccessTokenService（解析验证 JWT）
           → 设置 AuthenticatedUser 到 Request Attribute
           → CurrentUserService（提取用户信息）
           → Controller 处理业务逻辑
```

采用 **Access Token + Refresh Token** 双令牌机制：
- Access Token：短期 JWT（15 分钟），每次请求通过 Header 携带
- Refresh Token：长期令牌，存储于 httpOnly Cookie + 数据库，支持 Rotation + 防重放攻击

### 文档索引流水线

```
文档上传 → MinIO 存储 → 异步 ETL 事件
                       → IngestionJob（状态追踪）
                       → PDF/DOCX/MD 文本解析
                       → 文本分块（DocumentChunk）
                       → 向量嵌入（DashScope Embedding → PGvector）
                       → 关键词索引（Elasticsearch + IK 分词）
                       → 文档状态 → READY
```

### AI 助手 Agent 架构

```
用户消息 → AssistantService
         → ReactAgent（图执行引擎）
            ├── BEFORE_MODEL Hook：注入上下文（会话记忆 + 紧凑摘要）
            ├── CHAT 模式：直接生成回复
            ├── KB_SEARCH 模式：Agent 自主决定调用知识库检索工具
            └── AFTER_AGENT Hook：摘要判断 + 保存
         → SSE 流式推送给前端
```

短期记忆采用 **三级压缩策略**：
1. **会话记忆**（Session Memory）：增量 LLM 摘要
2. **紧凑摘要**（Compact Summary）：精炼的历史压缩
3. **运行时压缩**（Runtime Compact）：Token 超阈值时自动截断

---

## 开发指南

详细技术文档请参阅 `docs/` 目录：
- `V1.0-项目文档.md` — 项目骨架 + 用户认证 + 群组管理
- `V2.0-项目文档.md` — 文档上传 + ETL 索引流水线
- `V3.0-项目文档.md` — 知识库问答（RAG）
- `V4.0-项目文档.md` — AI 助手 Agent + 流式对话 + 短期记忆

---

## License

MIT
