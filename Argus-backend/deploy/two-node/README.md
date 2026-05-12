# 双机 Docker Compose 部署指南：服务器 A 完整流程

## 一、部署架构概述

本部署方案针对两台配置为 3.3G 级别的 Linux 服务器进行服务拆分，采用 Docker Compose 进行容器化编排。这套方案的设计理念是在保持开发链路简易性的同时，实现生产级别的服务分离与资源优化。服务器 A 承载所有数据密集型服务，包括 PostgreSQL 关系型数据库、Elasticsearch 全文搜索引擎、MinIO 对象存储服务以及可选的 elasticvue Web 管理界面。服务器 B 则负责运行 Ollama 本地大模型推理服务、Spring Boot 后端应用以及 Vite 开发服务器前端应用。

与传统的单体部署不同，本方案明确要求项目代码在两台服务器上保持同步，以便共享相同的 docker-compose 配置文件和环境变量模板。每个服务器的职责边界清晰划分，这种职责分离不仅有助于资源隔离和管理，更为后续的水平扩展和故障容错提供了坚实基础。需要特别强调的是，本方案不会对根目录现有的任何 docker-compose.yml 文件进行覆盖操作，所有部署文件均位于 `/opt/dd-rag/deploy/two-node/` 子目录中，从而确保不影响宿主机的其他 Docker 服务。

## 二、前置条件检查与环境准备

### 2.1 系统要求

在开始部署之前，必须确保两台服务器均满足以下基础环境要求。操作系统方面，建议使用 Ubuntu 20.04 LTS 或更高版本、Debian 11 或更高版本、CentOS 8 或 Rocky Linux 8 及以上版本，这些发行版对 Docker 的官方支持较好，且内核版本均满足 Docker 的运行需求。如果使用 Ubuntu 22.04 LTS 或更高版本，通常可以获得最佳的软件包兼容性和系统稳定性。

### 2.2 Docker 与 Docker Compose 版本检查

这是整个部署流程中最关键的前置步骤之一。执行以下命令检查当前安装的 Docker 版本：

```bash
docker --version
docker compose version
```

如果显示的 Docker 版本低于 20.10.0，或者 Docker Compose 版本低于 2.0.0，则必须进行升级操作。较旧版本的 Docker 可能存在已知的漏洞和不兼容性，特别是与某些服务（如 Elasticsearch）的内存限制配置存在冲突。Docker Compose V2 版本采用了 Go 语言重写，相比 V1 版本在性能和功能上有显著提升，特别是在处理大型 Compose 文件时表现更为稳定。

### 2.3 Docker 升级详细步骤

#### Ubuntu/Debian 系统升级 Docker

对于基于 Debian/Ubuntu 的系统，按照以下步骤进行 Docker 升级。首先，卸载可能存在的旧版本 Docker：

```bash
sudo apt-get remove docker docker-engine docker.io containerd runc
```

然后更新软件包索引并安装必要的依赖：

```bash
sudo apt-get update
sudo apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release
```

接下来，添加 Docker 的官方 GPG 密钥和软件源：

```bash
curl -fsSL https://download.docker.com/linux/$(lsb_release -is | tr '[:upper:]' '[:lower:]')/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/$(lsb_release -is | tr '[:upper:]' '[:lower:]') $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
```

最后，安装最新版本的 Docker Engine 和 Docker Compose 插件：

```bash
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

#### CentOS/Rocky Linux 系统升级 Docker

对于基于 RHEL 的系统，首先移除旧版本：

```bash
sudo yum remove docker docker-client docker-client-latest docker-common docker-latest docker-latest-logrotate docker-logrotate docker-engine
```

然后安装必要的依赖包：

```bash
sudo yum install -y yum-utils device-mapper-persistent-data lvm2
```

添加 Docker 官方仓库：

```bash
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
```

安装最新版本：

```bash
sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

#### Docker 服务启动与验证

安装或升级完成后，必须正确启动 Docker 服务并将其设置为开机自启动：

```bash
sudo systemctl start docker
sudo systemctl enable docker
sudo systemctl enable containerd
```

随后验证安装是否成功：

```bash
sudo docker run hello-world
```

如果看到 "Hello from Docker!" 的欢迎信息，说明 Docker 已正确安装并运行。同时，确认 Docker Compose 插件可用：

```bash
docker compose version
```

### 2.4 常见 Docker 安装问题与解决方案

#### 内核版本过低

如果系统内核版本低于 3.10，可能导致 Docker 无法正常运行或性能受限。使用 `uname -r` 命令检查内核版本，如需升级内核，请参考对应发行版的内核升级文档。

#### 存储驱动不兼容

Docker 需要合适的存储驱动支持。推荐使用 overlay2 存储驱动，它是目前最高效的存储方案。检查当前存储驱动配置：

```bash
docker info | grep "Storage Driver"
```

如需更改存储驱动，需要编辑 Docker 守护进程配置文件 `/etc/docker/daemon.json`：

```json
{
  "storage-driver": "overlay2"
}
```

修改后重启 Docker 服务使配置生效：

```bash
sudo systemctl restart docker
```

#### 内存或 Swap 不足

某些服务（特别是 Elasticsearch）对内存有较高要求。确保系统有足够的可用内存和 Swap 空间。检查当前内存状态：

```bash
free -h
swapon -s
```

对于 3.3G 级别的服务器，建议为 Elasticsearch 预留至少 2GB 内存，同时确保系统有足够的 Swap 空间以应对内存峰值。

### 2.5 Git 与项目代码准备

部署前需要在两台服务器上拉取项目代码。建议将项目代码统一放置在 `/opt/dd-rag` 目录下，以保持路径一致性：

```bash
sudo mkdir -p /opt/dd-rag
cd /opt/dd-rag
```

如果已有项目代码，可以直接复制或链接到该目录；如果需要从 Git 仓库拉取：

```bash
git clone <your-repo-url> .
```

确认项目结构包含 `deploy/two-node/` 子目录：

```bash
ls -la /opt/dd-rag/deploy/two-node/
```

预期应看到以下文件：

- `docker-compose.server-a.yml`
- `docker-compose.server-b.yml`
- `server-a.env.example`
- `server-b.env.example`

## 三、服务器 A 服务详解

### 3.1 PostgreSQL 数据库服务

PostgreSQL 是服务器 A 承载的核心数据存储服务，负责存储应用的结构化业务数据。在 3.3G 内存环境下，PostgreSQL 的默认配置通常能够良好运行，但建议根据实际负载情况进行适当调优。

PostgreSQL 服务默认监听端口 5432，该端口需要在防火墙中允许来自服务器 B 的访问。数据库的初始化和数据持久化通过 Docker 卷实现，确保容器重启或重建不会丢失数据。

### 3.2 Elasticsearch 全文搜索引擎

Elasticsearch 是本方案中资源消耗最大的服务，它负责提供高性能的全文搜索能力。默认情况下，Elasticsearch 的 JVM 堆内存配置可能会占用过多系统资源，在 3.3G 内存环境下需要特别关注。

Elasticsearch 默认监听端口 9200（HTTP）和 9300（传输层），在开发环境中通常只需暴露 9200 端口供外部访问。Elasticsearch 的数据目录和插件目录通过 Docker 卷进行持久化，确保索引数据不会因容器重建而丢失。

### 3.3 MinIO 对象存储服务

MinIO 提供了与 Amazon S3 兼容的对象存储能力，用于存储文档、文件和其他二进制数据。它的资源占用相对较低，非常适合在资源受限的环境中部署。

MinIO 控制台默认监听端口 9001，API 端点监听端口 9000。健康检查接口位于 `http://host:9000/minio/health/live`，可用于监控服务状态。

### 3.4 Elasticvue Web 管理界面（可选）

Elasticvue 是一个轻量级的 Elasticsearch Web 管理工具，可以在浏览器中查看索引、管理搜索请求等。虽然它是可选服务，但在开发和调试阶段非常有用。

## 四、服务器 A 详细部署流程

### 4.1 环境变量文件准备

首先进入部署目录并复制环境变量示例文件：

```bash
cd /opt/dd-rag/deploy/two-node
cp server-a.env.example .env.server-a
```

接下来需要编辑 `.env.server-a` 文件，配置适合生产环境的关键参数。以下是必须检查和修改的配置项：

```bash
nano .env.server-a  # 或使用 vi、vim 等编辑器
```

#### PostgreSQL 配置

确保以下环境变量符合实际需求：

- `POSTGRES_DB`：应用使用的数据库名称
- `POSTGRES_USER`：数据库用户名
- `POSTGRES_PASSWORD`：数据库密码（生产环境务必使用强密码）

#### Elasticsearch 配置

Elasticsearch 的内存配置至关重要。在 `.env.server-a` 中添加或确认以下 JVM 选项：

```bash
ES_JAVA_OPTS=-Xms1g -Xmx1g
```

上述配置将 Elasticsearch 的 JVM 堆内存限制在 1GB，在 3.3G 总内存环境下是合理的选择。如果服务器资源允许，可以适当调高至 1.5GB，但建议不要超过系统可用内存的 50%，以避免 OOM 问题。

Elasticsearch 的安全配置也需要考虑。在开发环境中，可以暂时禁用安全特性以简化配置：

```bash
xpack.security.enabled=false
```

#### MinIO 配置

MinIO 的访问凭证需要妥善配置：

- `MINIO_ROOT_USER`：访问密钥（建议使用较长的随机字符串）
- `MINIO_ROOT_PASSWORD`：密钥密码

默认的 `minioadmin/minioadmin` 凭证仅适用于开发测试环境，生产部署务必修改。

### 4.2 创建必要的数据目录

Docker 卷虽然可以自动创建，但为数据目录设置明确的挂载点可以提供更好的可管理性和备份能力。在宿主机上创建数据目录：

```bash
sudo mkdir -p /opt/dd-rag/data/postgres
sudo mkdir -p /opt/dd-rag/data/elasticsearch
sudo mkdir -p /opt/dd-rag/data/minio

# 设置适当的权限
sudo chown -R 1000:1000 /opt/dd-rag/data/postgres
sudo chown -R 1000:1000 /opt/dd-rag/data/elasticsearch
sudo chown -R 1000:1000 /opt/dd-rag/data/minio
```

Elasticsearch 容器默认以 1000 UID 的用户运行，因此需要确保数据目录对该用户有读写权限。

### 4.3 拉取或更新 Docker 镜像

在正式启动服务之前，建议预先拉取所有需要的 Docker 镜像，以避免启动过程中因网络问题导致的服务中断：

```bash
cd /opt/dd-rag/deploy/two-node
docker compose --env-file .env.server-a -f docker-compose.server-a.yml pull
```

这条命令会从 Docker Hub 或配置的镜像仓库拉取所有服务的基础镜像。如果使用私有镜像仓库，需要确保已登录：

```bash
docker login <registry-url>
```

### 4.4 首次启动服务

使用以下命令启动服务器 A 上的所有服务：

```bash
docker compose --env-file .env.server-a -f docker-compose.server-a.yml up -d --build
```

参数说明：

- `--env-file .env.server-a`：指定环境变量文件
- `-f docker-compose.server-a.yml`：指定使用的 Compose 文件
- `-d`：以守护进程模式运行，所有容器在后台执行
- `--build`：在启动前构建镜像（如果 Dockerfile 有更新）

首次启动时，Docker 需要下载基础镜像、构建自定义镜像，并初始化各个服务的初始状态，可能需要较长时间。通过添加 `--pull always` 参数可以确保每次都使用最新的基础镜像：

```bash
docker compose --env-file .env.server-a -f docker-compose.server-a.yml up -d --build --pull always
```

### 4.5 监控启动过程

启动命令执行后，可以使用以下方式监控服务状态：

#### 查看所有容器状态

```bash
docker compose --env-file .env.server-a -f docker-compose.server-a.yml ps
```

预期输出应显示所有服务的状态为 "running"，并且健康检查（如果配置了）应显示 "healthy"。

#### 查看实时日志

对于首次启动，建议实时查看日志以捕获任何错误：

```bash
docker compose --env-file .env.server-a -f docker-compose.server-a.yml logs -f
```

如果只想查看特定服务的日志：

```bash
docker compose --env-file .env.server-a -f docker-compose.server-a.yml logs -f elasticsearch
docker compose --env-file .env.server-a -f docker-compose.server-b.yml logs -f postgres
```

#### 等待服务就绪

某些服务（如 Elasticsearch）在容器启动后需要一段时间才能完全就绪。可以使用以下脚本等待服务就绪：

```bash
# 等待 Elasticsearch 就绪
until curl -s http://127.0.0.1:9200 > /dev/null; do
    echo "Waiting for Elasticsearch..."
    sleep 5
done
echo "Elasticsearch is ready!"
```

### 4.6 服务健康验证

服务启动后，必须进行全面的健康检查以确保所有组件正常工作。

#### PostgreSQL 健康检查

```bash
# 检查 PostgreSQL 是否响应
docker exec -it $(docker compose --env-file .env.server-a -f docker-compose.server-a.yml ps -q postgres) pg_isready -U postgres

# 或者直接测试连接
docker exec -it $(docker compose --env-file .env.server-a -f docker-compose.server-a.yml ps -q postgres) psql -U postgres -c "SELECT 1;"
```

#### Elasticsearch 健康检查

```bash
curl http://127.0.0.1:9200
```

预期返回包含集群名称、版本号等信息的 JSON 响应。

#### MinIO 健康检查

```bash
curl http://127.0.0.1:9000/minio/health/live
```

健康端点应返回 "OK" 或 `"status": "ok"`。

### 4.7 防火墙配置

服务器 A 需要开放特定端口以允许服务器 B 访问。假设服务器 B 的 IP 地址为 `192.168.1.100`，执行以下防火墙配置（以 UFW 为例）：

```bash
# 允许来自服务器 B 的 PostgreSQL 访问
sudo ufw allow from 192.168.1.100 to any port 5432

# 允许来自服务器 B 的 Elasticsearch 访问
sudo ufw allow from 192.168.1.100 to any port 9200

# 允许来自服务器 B 的 MinIO API 访问
sudo ufw allow from 192.168.1.100 to any port 9000

# 如果需要从外部浏览器访问 MinIO 控制台
sudo ufw allow from any to any port 9001

# 重启防火墙使配置生效
sudo ufw reload

# 查看防火墙规则
sudo ufw status
```

对于使用 iptables 的系统：

```bash
sudo iptables -A INPUT -s 192.168.1.100 -p tcp --dport 5432 -j ACCEPT
sudo iptables -A INPUT -s 192.168.1.100 -p tcp --dport 9200 -j ACCEPT
sudo iptables -A INPUT -s 192.168.1.100 -p tcp --dport 9000 -j ACCEPT
```

## 五、常见问题与故障排除

### 5.1 Docker 权限问题

如果当前用户不是 root 用户组，可能需要使用 sudo 执行 Docker 命令。解决方法是将近用户添加到 docker 用户组：

```bash
sudo usermod -aG docker $USER
newgrp docker
```

执行后需要重新登录 shell 以使组更改生效。

### 5.2 端口冲突

如果启动时报错提示端口已被占用，需要检查并释放端口：

```bash
# 检查端口占用情况
sudo lsof -i :9200
sudo lsof -i :5432
sudo lsof -i :9000

# 如果是其他进程占用，可以停止该进程或修改 docker-compose 文件中的端口映射
```

### 5.3 磁盘空间不足

Docker 镜像和容器数据会占用大量磁盘空间。定期清理未使用的资源：

```bash
# 清理未使用的 Docker 资源
docker system prune -a

# 清理悬空卷
docker volume prune

# 查看 Docker 磁盘使用
docker system df
```

### 5.4 Elasticsearch 启动失败

Elasticsearch 是最容易出现启动问题的服务，常见原因包括：

#### 内存不足

Elasticsearch 需要足够的可用内存。如果系统内存不足，会导致 JVM 初始化失败。检查系统内存：

```bash
free -h
```

如有必要，减少 Elasticsearch 的 JVM 堆大小（见 4.1 节环境变量配置）。

#### 文件描述符限制

Elasticsearch 需要大量文件描述符。检查当前限制：

```bash
ulimit -n
```

如果值低于 65536，需要提高限制。编辑 `/etc/security/limits.conf`：

```
* soft nofile 65536
* hard nofile 65536
```

#### 虚拟内存不足

Elasticsearch 需要配置 `vm.max_map_count` 参数：

```bash
# 检查当前值
cat /proc/sys/vm/max_map_count

# 临时修改
sudo sysctl -w vm.max_map_count=262144

# 永久修改
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf
```

### 5.5 PostgreSQL 连接问题

如果服务器 B 无法连接到 PostgreSQL，检查以下几点：

1. PostgreSQL 是否监听所有接口（检查 `listen_addresses` 配置）
2. 防火墙是否允许 5432 端口访问
3. `pg_hba.conf` 是否允许远程连接

## 六、日常运维命令

### 6.1 服务重启

重启所有服务：

```bash
docker compose --env-file .env.server-a -f docker-compose.server-a.yml restart
```

重启单个服务：

```bash
docker compose --env-file .env.server-a -f docker-compose.server-a.yml restart elasticsearch
```

### 6.2 服务停止

停止所有服务（保留数据卷）：

```bash
docker compose --env-file .env.server-a -f docker-compose.server-a.yml down
```

停止并删除数据卷（慎用，会丢失数据）：

```bash
docker compose --env-file .env.server-a -f docker-compose.server-a.yml down -v
```

### 6.3 服务更新

当需要更新服务镜像时：

```bash
cd /opt/dd-rag/deploy/two-node

# 拉取最新镜像
docker compose --env-file .env.server-a -f docker-compose.server-a.yml pull

# 重建并重启服务
docker compose --env-file .env.server-a -f docker-compose.server-a.yml up -d --build --pull always
```

### 6.4 日志管理

查看特定服务的最近日志：

```bash
docker compose --env-file .env.server-a -f docker-compose.server-a.yml logs --tail=100 elasticsearch
```

日志轮转配置，编辑 `/etc/docker/daemon.json`：

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
```

修改后重启 Docker：

```bash
sudo systemctl restart docker
```

## 七、安全建议

### 7.1 生产环境注意事项

- 所有默认密码必须修改为强密码
- 生产环境应启用 Elasticsearch 安全特性
- 限制 PostgreSQL 的远程访问，仅允许必要的 IP 地址
- 定期更新 Docker 镜像以获取安全补丁
- 启用 Docker 守护进程的 TLS 远程访问（如果需要远程管理）

### 7.2 数据备份策略

建议建立定期备份机制：

- PostgreSQL：使用 `pg_dump` 定期备份数据库
- Elasticsearch：配置快照仓库进行索引备份
- MinIO：使用 `mc mirror` 命令同步数据到备份位置

## 八、下一步：服务器 B 部署

服务器 A 部署并验证成功后，下一步是部署服务器 B。请参考配套文档《双机 Docker Compose 部署指南：服务器 B 完整流程》。

---

*本文档为服务器 A 部署的完整指南。如有问题，请检查日志输出或参考各服务的官方文档。*
