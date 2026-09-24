# 模块 ops-02 · 部署：Compose 编排与 Nginx

> 读者：已读完 `ops/01-docker.md`
> 目标：**能解释 5 个容器如何按顺序起来；能管好环境变量与密钥；能读懂并修改 Nginx 配置；能自己从零部署一遍**
> 时长：约 1 天
> 上一篇：`ops/01-docker.md`

---

## 0. 这一篇解决什么问题

`ops/01` 讲的是「单个容器怎么造」。但本项目有 5 个服务，它们之间有依赖、要共享网络、要注入不同的配置。这一篇讲**怎么把它们编排成一个整体**。

| 问题 | 对应章节 |
|---|---|
| 5 个服务怎么一起起来？谁先谁后？ | 第 2–3 节 |
| 密码和密钥从哪来？怎么保证不会用默认弱密码上线？ | 第 4 节 |
| 一个 `http://localhost` 是怎么分流出三种请求的？ | 第 5 节 |
| 生产环境和本地开发差在哪？ | 第 6 节 |

---

## 1. 一次部署发生了什么

先看全景，后面逐块展开：

```text
docker compose up -d --build
        │
        ├─ ① 读 .env，做变量插值；必填项缺失 → 直接报错退出
        ├─ ② 构建 backend / frontend 镜像（有缓存则秒级）
        ├─ ③ 创建网络 diary_diary-net + 3 个具名卷
        ├─ ④ 启动 mysql / redis / minio（三者无依赖，并行）
        │       └─ 各自跑健康检查，直到 healthy
        ├─ ⑤ mysql/redis/minio 全部 healthy 后，启动 backend
        │       └─ 启动时 MinioBucketInitializer 建 bucket、设公开只读策略
        │       └─ 跑健康检查 /api/v1/ping，直到 healthy
        └─ ⑥ backend healthy 后，启动 nginx（对外发布 80 端口）

浏览器访问 http://localhost
```

**实测的启动时间线**（数据卷已存在的重新部署）：

```text
服务        容器创建    变为 healthy
mysql      +    1.1s   +    5.4s
minio      +    1.1s   +    6.6s
redis      +    1.1s   +   10.9s
backend    +    1.1s   +   16.5s      ← 等前面三个全部 healthy
nginx      +    1.1s   +   22.0s      ← 等 backend healthy

整体就绪：22.2 秒
```

**注意「容器创建」都是 +1.1s**：compose 会**先把 5 个容器都创建出来**，再按依赖顺序逐个启动。所以「创建」不等于「启动」。

**全新部署**需 5~10 分钟，多出来的是两件事：

| 额外耗时 | 说明 |
|---|---|
| 拉基础镜像 | `mysql:8.4.9` 1.12GB、`eclipse-temurin:25-jdk`、`node:22-alpine` |
| 容器内下载依赖 | Maven 400+ 个依赖、npm 依赖 |
| **MySQL 初始化** | 数据卷为空时会执行 `sql/init.sql` 建库建表，比上面的 5.4s 慢得多 |

> 上面那次 mysql 只用 5.4s，是因为**数据卷已存在**，跳过了初始化。

---

## 2. 逐段读 `docker-compose.yml`

### 2.1 项目名与总体结构

```yaml
name: diary                    # ← 项目名，决定容器名前缀与卷名前缀

services:
  mysql:    { ... }
  redis:    { ... }
  minio:    { ... }
  backend:  { ... }
  nginx:    { ... }

volumes:                       # 具名卷声明
  mysql-data:
  redis-data:
  minio-data:

networks:
  diary-net:
    driver: bridge
```

**`name: diary` 的影响**（这个要知道，否则你会找不到东西）：

| 产生的东西 | 名字 |
|---|---|
| 网络 | `diary_diary-net` |
| 卷 | `diary_mysql-data`、`diary_redis-data`、`diary_minio-data` |

> 卷名是 `<项目名>_<卷名>`。所以 `docker volume ls` 里看到的是 `diary_mysql-data` 而不是 `mysql-data`。

### 2.2 一个服务的完整样貌：以 mysql 为例

```yaml
mysql:
  image: mysql:8.4.9                              # ① 镜像写死到具体版本
  container_name: diary-mysql                     # ② 固定容器名（便于 docker exec）
  restart: unless-stopped                         # ③ 自动重启策略
  environment:
    MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:?请在 .env 中设置 DB_PASSWORD}
    MYSQL_DATABASE: ${DB_NAME:-diary}
    TZ: Asia/Shanghai
  command:                                        # ④ 追加给镜像入口的启动参数
    - --character-set-server=utf8mb4
    - --collation-server=utf8mb4_unicode_ci
    - --default-time-zone=+08:00
  ports:
    - "127.0.0.1:${DB_HOST_PORT:-3307}:3306"
  volumes:
    - mysql-data:/var/lib/mysql
    - ./sql/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
  healthcheck:
    test: [ "CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p\"$$MYSQL_ROOT_PASSWORD\" --silent" ]
    interval: 10s
    timeout: 5s
    retries: 12
    start_period: 30s
  networks:
    - diary-net
```

**逐个解释**：

| 项 | 作用 | 注意 |
|---|---|---|
| `image: mysql:8.4.9` | **写死到补丁版本** | 写 `mysql:8` 会在某天拉到不兼容的新版本。这里的取舍是「今天能起、明年也能起」 |
| `container_name` | 固定容器名 | 便于 `docker exec diary-mysql ...`。默认名是 `diary-mysql-1` |
| `restart: unless-stopped` | 容器异常退出时自动重启 | **但手动 `docker stop` 不会重启**——这正是我们想要的 |
| `command` | 追加启动参数 | 注意它**替换**镜像的 CMD，不是追加。MySQL 镜像的 ENTRYPOINT 会把这些参数接住 |
| `ports` | 端口发布 | 见 `ops/01` 第 7.2 节 |
| `volumes` | 数据与配置挂载 | 见下 |
| `healthcheck` | 健康检查定义 | 见第 3 节 |
| `networks` | 加入哪个网络 | 同一网络的容器才能用名字互访 |

### 2.3 两个容易忽略的细节

**① `TZ: Asia/Shanghai` 出现在了每个服务上**

```yaml
environment:
  TZ: Asia/Shanghai
```

**为什么需要它**：容器默认时区是 UTC。如果不设，数据库里的 `created_at` 会比北京时间少 8 小时。本项目对时区做了三重保障：

| 位置 | 配置 |
|---|---|
| 容器 | `TZ=Asia/Shanghai` |
| MySQL 服务参数 | `--default-time-zone=+08:00` |
| JDBC 连接串 | `serverTimezone=Asia/Shanghai` |
| Jackson | `time-zone: Asia/Shanghai` |

> **这是个真实的坑**：只要有一处漏了，就可能出现「刚创建的日记显示成 8 小时前」。这也解释了为什么本项目严格要求「日记归属日期」用 `diary_date`（DATE 类型）而不是 `created_at`——**业务日期不该受时区换算影响**。

**② `ports` 里的 `127.0.0.1:` 前缀**

```yaml
ports:
  - "127.0.0.1:${DB_HOST_PORT:-3307}:3306"
```

- **不带前缀**：`8080:8080` → 绑定 `0.0.0.0`，**局域网内其他机器也能访问**
- **带 `127.0.0.1:`**：只绑回环地址，只有本机能连

**本项目只对 MySQL 破例**（为了让宿主机的 Database Client 能连），且**刻意只绑回环**。

### 2.4 变量插值的两种语法

你在 compose 里会看到这两种写法，语义完全不同：

```yaml
${DB_NAME:-diary}                          # 有默认值：没设就用 diary
${DB_PASSWORD:?请在 .env 中设置 DB_PASSWORD}   # 必填：没设就报错退出
```

| 语法 | 缺失时的行为 | 用在哪 |
|---|---|---|
| `${VAR:-default}` | 用 `default` | `DB_NAME`、`APP_PORT`、`JWT_ACCESS_EXPIRE` |
| `${VAR:?message}` | **`docker compose up` 直接失败**，打印 message | `DB_PASSWORD`、`JWT_SECRET`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY` |

**`:?` 是本项目的一个刻意设计**：

> **宁可起不来，也不要带着默认弱密码上线。**

对比一下两种失败方式：

| 方式 | 结果 |
|---|---|
| 给默认值 `changeme` | 「看起来部署成功了」，实际用着一个公开的弱密码运行——**故障被推迟到被攻击那一刻** |
| `:?` 报错退出 | 部署当场失败，你立刻知道要配什么 |

**同一个原则也用在 Java 侧**（`application-prod.yml`）：

```yaml
diary:
  jwt:
    secret: ${JWT_SECRET}      # ← 连 : 都没有，直接展开环境变量，缺失则 Spring 启动失败
```

---

## 3. 启动顺序：健康检查链（🔴 本节是核心）

### 3.1 问题的来源

后端启动时需要一个**已经能用的 MySQL**。如果两者同时启动，后端会连不上数据库。

`depends_on` 能表达「顺序」，但有**两种写法，语义完全不同**：

```yaml
# ❌ 短语法：只保证「容器已创建」，不保证「服务已就绪」
depends_on:
  - mysql

# ✅ 长语法：等到 mysql 的健康检查通过才启动 backend
depends_on:
  mysql:
    condition: service_healthy
```

**短语法的问题**：MySQL 容器的「创建完成」和「能接受连接」之间差着十几秒（要初始化数据目录、启动服务）。短语法下后端会在这十几秒里反复报连接失败。

**本项目全部使用长语法**：

```yaml
backend:
  depends_on:
    mysql:
      condition: service_healthy
    redis:
      condition: service_healthy
    minio:
      condition: service_healthy

nginx:
  depends_on:
    backend:
      condition: service_healthy
```

于是形成一条链：

```text
mysql ─┐
redis ─┼─► 全部 healthy ─► backend 启动 ─► healthy ─► nginx 启动
minio ─┘
```

**实测印证**（第 1 节的时间线）：backend 在 +16.5s 才 healthy，**晚于三个依赖的 5.4/6.6/10.9s**，正是因为它必须等它们。

### 3.2 为什么 MinIO 的健康检查**不可省**（一个真实的设计连带后果）

`MinioBucketInitializer` 的实现是：

```java
@Override
public void run(ApplicationArguments args) {
    try {
        // 建 bucket、设公开只读策略
    } catch (Exception e) {
        log.warn("MinIO bucket 初始化失败，上传接口将不可用: {}", e.getMessage());
    }
}
```

**异常被吞掉了，只记 WARN，不阻断启动。**

| 这个设计的优点 | 带来的风险 |
|---|---|
| MinIO 挂了不影响其他功能（看日记、写日记都正常） | 如果后端比 MinIO 先起来，**bucket 建不出来，上传接口会一直不可用**——而且不会报错，只在启动日志里有一条 WARN |

**所以 compose 里必须保证 MinIO 先就绪**：

```yaml
minio:
  healthcheck:
    test: [ "CMD", "curl", "-fsS", "http://127.0.0.1:9000/minio/health/live" ]
    interval: 10s
    timeout: 5s
    retries: 12
    start_period: 15s
```

> **这是「一个模块的容错设计，变成了另一个模块的约束」的典型例子**。写代码时觉得「失败只告警」很优雅，但它把「必须保证顺序」的责任推给了部署层。
>
> 常见的替代方案是**给后端加重试**（初始化失败后定期重试），这样就不依赖启动顺序。本项目没做，代价是必须在 compose 里维持这条依赖。

### 3.3 健康检查的四个参数

```yaml
healthcheck:
  test: [ "CMD", "curl", "-fsS", "http://127.0.0.1:8080/api/v1/ping" ]
  interval: 15s        # 每 15 秒检查一次
  timeout: 5s          # 单次检查超过 5 秒算失败
  retries: 12          # 连续失败 12 次才标记 unhealthy（12 × 15s = 3 分钟）
  start_period: 60s    # 启动后的 60 秒内失败不计入 retries
```

**`start_period` 是最容易被忽略但很关键的一个**：应用启动要几十秒，这期间健康检查必然失败。没有 `start_period` 的话，可能刚启动就被判定为 unhealthy（进而让依赖它的服务放弃等待）。

| 服务 | `start_period` | 依据 |
|---|---|---|
| mysql | 30s | 首次启动要初始化数据目录 + 跑 `init.sql` |
| redis | 无 | 启动几乎瞬时 |
| minio | 15s | 启动较快 |
| backend | **60s** | JVM 启动 + Spring 容器初始化 + bucket 初始化 |
| nginx | 无（写 5s，在 Dockerfile 里） | 启动瞬时 |

### 3.4 两个服务健康检查的写法差异

```yaml
# redis：直接用容器内自带的客户端
test: [ "CMD", "redis-cli", "ping" ]

# minio：用镜像内的 curl 打官方健康端点
test: [ "CMD", "curl", "-fsS", "http://127.0.0.1:9000/minio/health/live" ]

# backend：打自己暴露的免认证接口
test: [ "CMD", "curl", "-fsS", "http://127.0.0.1:8080/api/v1/ping" ]

# mysql：必须用 CMD-SHELL，因为要在容器内展开 $MYSQL_ROOT_PASSWORD
test: [ "CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p\"$$MYSQL_ROOT_PASSWORD\" --silent" ]
```

**`CMD` vs `CMD-SHELL`**：

| 形式 | 行为 |
|---|---|
| `CMD` | 直接执行，**不做 shell 展开**（没有 `$VAR`、没有管道） |
| `CMD-SHELL` | 通过 `/bin/sh -c` 执行，**支持变量展开与管道** |

**注意 mysql 那行里的 `$$`**：Compose 会把 `$$` 转义成一个 `$` 传给容器，于是容器内的 shell 看到的是 `$MYSQL_ROOT_PASSWORD` 并展开它。

**为什么不能直接写 `$MYSQL_ROOT_PASSWORD`**：那样 Compose 会在**宿主机上**尝试展开（而宿主机没有这个变量），结果是空字符串，健康检查永远失败。

> 这也是为什么密码里如果有特殊字符也能正常工作——密码从不经过宿主机 shell。

### 3.5 后端健康检查为什么能免认证

```yaml
test: [ "CMD", "curl", "-fsS", "http://127.0.0.1:8080/api/v1/ping" ]
```

`/api/v1/ping` 在 `SecurityConfig.PUBLIC_PATHS` 白名单里：

```java
private static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/**",
        "/api/v1/public/**",
        "/api/v1/ping",          // ← 健康检查用它
        ...
};
```

**这是「部署需求反向影响接口设计」的例子**：为了让容器健康检查能打这个接口，必须有一个免认证、无副作用、开销极小的端点。`/api/v1/ping` 就承担了这个角色。

---

## 4. 环境变量与密钥管理

### 4.1 三层结构（这是重点）

配置不是「随便找个地方写」，而是有明确的三层：

```text
第 1 层  .env.example            模板，入库。只含占位值，不含真实密钥
         └─ 复制 → .env         真实值，被 .gitignore 忽略，绝不入库

第 2 层  docker-compose.yml      声明哪些变量要传给哪个容器
         └─ environment: DB_PASSWORD: ${DB_PASSWORD:?...}

第 3 层  application-prod.yml    Java 侧读取环境变量
         └─ password: ${DB_PASSWORD}
```

**为什么要分三层而不是直接写死**：

| 层 | 职责 | 为什么分开 |
|---|---|---|
| `.env` | 存**真实密钥** | 换环境只需改这一个文件，不动代码与 compose |
| `compose` | 决定**哪个容器需要哪些变量** | 显式声明，避免「这个变量从哪来的」的困惑 |
| `application-prod.yml` | 决定**变量映射到哪个配置项** | Java 侧的配置名与 compose 的变量名可以不同 |

**本项目刻意让两层名字保持一致**（`DB_PASSWORD` → `DB_PASSWORD`），减少心智负担；但**不是必须**——`MINIO_PUBLIC_ENDPOINT` → `public-endpoint` 就是个例子。

### 4.2 `.env` 与 `.env.example` 的配合

```powershell
Copy-Item .env.example .env       # 首次部署
# 然后编辑 .env，改掉 4 个必填项
```

**`.env.example` 只含占位值**：

```bash
DB_PASSWORD=change-me-strong-password
JWT_SECRET=change-me-to-a-random-string-of-at-least-32-bytes
MINIO_ACCESS_KEY=diary-minio-admin
MINIO_SECRET_KEY=change-me-strong-password
```

**生成随机密钥**（`.env.example` 里也写了这条提示）：

```powershell
# PowerShell
[Convert]::ToBase64String([byte[]](1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

```bash
# openssl
openssl rand -base64 48
```

**`.gitignore` 必须忽略 `.env`**：

```text
.env
deploy/backups/
```

> **`deploy/backups/` 也必须忽略**——那里面是含真实用户数据的 SQL dump。我在 P8 时踩过一次：原来只忽略了 `backup/`，而备份脚本实际写到 `deploy/backups/`，**如果不修，用户的日记数据会被提交进版本库**。

### 4.3 哪些变量必须配、配错了会怎样

| 变量 | 缺失时 | 影响面 |
|---|---|---|
| `DB_PASSWORD` | compose **直接报错退出** | 起不来 |
| `JWT_SECRET` | compose 报错退出；即便绕过，后端启动也会失败 | 起不来 |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | compose 报错退出 | 起不来 |
| `APP_PORT` | 用默认 `80` | 可能与其他程序冲突 |
| `JWT_ACCESS_EXPIRE` | 默认 `1800`（30 分钟） | 无 |
| `MINIO_PUBLIC_ENDPOINT` | 默认 `/files` | 改错会导致图片 URL 不对 |

**一条经验**：**缺失会导致「起不来」的变量，比「起得来但行为不对」的变量安全得多**。所以本项目把 4 个密钥都设为必填，宁可部署失败。

### 4.4 一个必须知道的边界

`.env` **只被 `docker compose` 读取**。本地开发（`.\mvnw.cmd spring-boot:run`）**不读它**——相关默认值都在 `application-dev.yml` 里。

**后果**：你在 `.env` 里改了 `DB_NAME`，本地开发不会受影响。两套配置是**独立**的。

---

## 5. Nginx 逐段读

`frontend/nginx.conf` 是整个系统的**唯一入口**，它做三件事：

```text
                            ┌─ /        → 静态文件（前端产物），SPA 回退
浏览器 ──► nginx:80 ────────┼─ /api/    → backend:8080（反向代理）
                            └─ /files/  → minio:9000（反向代理，图片）
```

### 5.1 全局段

```nginx
server {
    listen       80;
    server_name  _;

    root  /usr/share/nginx/html;      # 静态文件根目录（镜像里 dist 拷到这里）
    index index.html;

    resolver 127.0.0.11 valid=10s ipv6=off;   # ← 关键，见 5.3

    client_max_body_size 10m;                  # 上传大小上限
    gzip on;
    gzip_types text/plain text/css application/javascript application/json image/svg+xml;
```

| 配置 | 说明 |
|---|---|
| `server_name _` | 匹配任意 Host（单机部署够用） |
| `client_max_body_size 10m` | **默认只有 1m**，图片上传会被 413 拒绝。后端另有 5MB 业务校验，这里留余量 |
| `gzip` | 压缩文本类响应。注意 `application/javascript` 要显式写（不同 nginx 版本对 `text/javascript` 支持不同） |

### 5.2 三段 location

**① API 反代**

```nginx
location /api/ {
    set $backend_upstream "http://backend:8080";
    proxy_pass         $backend_upstream;
    proxy_set_header   Host              $http_host;
    proxy_set_header   X-Real-IP         $remote_addr;
    proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header   X-Forwarded-Proto $scheme;
    proxy_connect_timeout 10s;
    proxy_read_timeout    60s;
}
```

**`proxy_set_header` 这几个头的作用**：让后端知道「真实的客户端 IP 与协议」。没有它们，后端日志里看到的 IP 全是 nginx 容器的内网 IP，排查问题时会很困难。

**② 图片（MinIO）反代**

```nginx
location /files/ {
    set $minio_upstream "http://minio:9000";
    rewrite ^/files/(.*)$ /$1 break;     # 手动剥掉 /files 前缀
    proxy_pass         $minio_upstream;
    ...
    expires 7d;                          # 对象内容不变，交给浏览器缓存
}
```

**`rewrite` 这一行的必要性**：MinIO 期望的路径是 `/bucket/object`，但浏览器请求的是 `/files/bucket/object`。所以要把 `/files` 剥掉。

**为什么不能像 API 那样自动剥**：`proxy_pass http://minio:9000/`（带尾斜杠）能自动剥前缀，但**那只在字面量形式下有效**。这里用了变量形式（原因见 5.3），就只能手动 `rewrite`。

**③ SPA 回退（前端路由）**

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

**`try_files` 的语义**（按顺序尝试）：

1. `$uri` —— 有没有这个文件？比如 `/assets/index-abc.js` 存在 → 直接返回
2. `$uri/` —— 有没有这个目录？
3. `/index.html` —— **都没有就返回首页**，让前端路由接管

**没有这一行的后果**：用户在 `/stats` 页面刷新，浏览器请求 `GET /stats`，而 nginx 上并没有 `stats` 这个文件 → **404**。

> 这是 SPA 部署最经典的坑，也是本项目 DoD #2（「刷新任意前端路由不 404」）专门验收的一项。

### 5.3 一个真实缺陷：nginx 会缓存上游 IP

**这段配置里有本项目最有价值的一个修复**，值得完整讲一遍。

**问题**：原来的写法是

```nginx
location /api/ {
    proxy_pass http://backend:8080;     # ❌ 字面量形式
}
```

**nginx 对字面量 `proxy_pass` 只在「启动时」解析一次主机名，然后把 IP 缓存住**。而 `backend` 是 Docker DNS 的名字，**容器重建后 IP 会变**。

**触发场景**：README 里推荐的重新部署命令是 `docker compose up -d --build`——这会重建后端容器（换了 IP）。此后：

```text
nginx → 172.18.0.5:8080     （旧 IP，已不存在）
真实后端 → 172.18.0.7:8080
结果：持续 502，直到手动 docker compose restart nginx
```

**也就是说：我交付的部署方式本身会导致下次改代码后必然故障。**

**修复**：

```nginx
resolver 127.0.0.11 valid=10s ipv6=off;    # ① 显式指定 Docker 内嵌 DNS

location /api/ {
    set $backend_upstream "http://backend:8080";   # ② 先赋给变量
    proxy_pass $backend_upstream;                  # ③ 变量形式 → 按 TTL 重新解析
}
```

| 做法 | 效果 |
|---|---|
| `resolver 127.0.0.11` | `127.0.0.11` 是 Docker 在自定义网络里提供的内嵌 DNS 地址 |
| `valid=10s` | 解析结果缓存 10 秒，过期后重新解析 |
| **变量形式** | nginx 在**每次转发时**（按 TTL）重新解析，而不是启动时解析一次 |

**⚠️ 两个副作用，必须一起改**：

1. **`/files/` 无法自动剥前缀了** —— 变量形式透传完整原始 URI，所以要手动 `rewrite ^/files/(.*)$ /$1 break;`
2. **`set` 必须写在 `rewrite` 之前** —— `rewrite ... break` 会终止后续的 rewrite 阶段指令，如果 `set` 写在它后面就会被跳过，变量为空 → `proxy_pass ""` 报错

**验证过程**（这里我犯过一次方法错误，值得记录）：

```text
第一次尝试：只重建 backend，然后测 → 通过
问题：Docker 复用了刚释放的 IP（172.18.0.5），nginx 本来就指向它，测试不具说服力

第二次：用占位容器抢走旧 IP，强制后端换到 172.18.0.7
       nginx 全程未重启
       结果：/api/v1/ping 与 /files/ 均返回 200  ✅ 改造生效
```

> **教训**：验证「IP 变化」这类问题时，必须先确认 IP **真的变了**，否则测试通过也不代表什么。

### 5.4 缓存策略

```nginx
location /assets/ {
    expires 1y;                              # 带内容哈希的文件名，可长期缓存
}

location = /index.html {
    add_header Cache-Control "no-store";     # 首页绝不缓存
}

location /files/ {
    expires 7d;
}
```

**这套策略的设计逻辑**：

| 资源 | 缓存 | 为什么 |
|---|---|---|
| `/assets/*.js`、`*.css` | 1 年 | Vite 构建时文件名带内容哈希（如 `index-Ba58WQKn.js`），**内容变了文件名就变**，所以可以放心长期缓存 |
| `index.html` | **不缓存** | 它是「资源引用的入口」。如果它被缓存，发版后浏览器还会引用旧的文件名 → **用户看不到新版本** |
| `/files/*`（图片） | 7 天 | 对象名含 UUID，内容不可变 |

> **这是前端构建产物部署的标准做法**：`index.html` 不缓存 + 其余资源带哈希长期缓存 = **发版即时生效，且重复访问零请求**。

---

## 6. 生产配置与开发的差异

`application-prod.yml` **只写与 dev 的差异项**，其余沿用 `application.yml`。逐项说明：

### 6.1 数据源

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:mysql}:${DB_PORT:3306}/${DB_NAME:diary}?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: ${DB_USER:root}
    password: ${DB_PASSWORD}          # ← 没有默认值
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

| 项 | dev | prod | 为什么 |
|---|---|---|---|
| host | `127.0.0.1` | `mysql`（服务名） | 容器内通过 Docker DNS 互访 |
| `password` | 硬编码 `123456` | `${DB_PASSWORD}` **无默认值** | 缺失即启动失败 |
| `allowPublicKeyRetrieval=true` | — | ✅ | MySQL 8 的 `caching_sha2_password` 在非 SSL 连接下需要它 |
| 连接池 | 小 | `max=20, min=5` | 生产要能扛并发 |

### 6.2 关闭 SQL 日志（安全考虑，不只是性能）

```yaml
mybatis-plus:
  configuration:
    # 生产不打印 SQL，避免日志里出现用户内容
```

**dev 环境下 SQL 日志是这样的**：

```text
==>  Preparing: SELECT ... WHERE (title LIKE ? OR summary LIKE ?)
==>  Parameters: 1(Long), %爬山%(String)      ← 用户的搜索关键词明文出现在日志里
```

**生产开着它就是隐私问题**：用户的每一条搜索词、每一篇日记正文都会写进日志文件。

### 6.3 关闭接口文档

```yaml
springdoc:
  api-docs:
    enabled: ${SPRINGDOC_ENABLED:false}
  swagger-ui:
    enabled: ${SPRINGDOC_ENABLED:false}
```

**关掉的原因**：Swagger 会暴露**完整的 API 结构**（所有路径、参数、字段），对攻击者来说是免费的地图。

**需要临时排查时**：

```powershell
# 在 .env 或 shell 里设 SPRINGDOC_ENABLED=true，重启后端容器
$env:SPRINGDOC_ENABLED='true'; docker compose up -d backend
```

> ⚠️ 关掉之后访问 `/v3/api-docs` **应该返回 404**。如果返回 500，说明遇到了本项目的另一个已修缺陷（`GlobalExceptionHandler` 把未匹配路径兜成了 500），详见 `doc/backend/02-spring-boot.md`。

### 6.4 JWT 密钥

```yaml
diary:
  jwt:
    secret: ${JWT_SECRET}     # ← 没有 :默认值
```

**这是本项目最重要的一个安全设计**：密钥缺失时**启动失败**，而不是回退到某个默认值。

### 6.5 日志级别

```yaml
logging:
  level:
    root: info
    com.example.diary: info     # dev 下是 debug
```

---

## 7. 从零部署一遍（逐步）

### 步骤 0：前置检查

```powershell
docker version --format 'Client {{.Client.Version}} / Server {{.Server.Version}}'
docker compose version
```

**应该看到**：Client 与 Server 都有版本号。**如果只有 Client**，说明守护进程没起来——检查 Docker Desktop 是否在运行，以及 WSL2 是否可用（`wsl --status`）。

### 步骤 1：准备环境变量

```powershell
cd <仓库根目录>
Copy-Item .env.example .env
```

编辑 `.env`，**至少改掉这 4 项**：

```bash
DB_PASSWORD=<32 位随机串>
JWT_SECRET=<64 位随机串>
MINIO_ACCESS_KEY=<自定义>
MINIO_SECRET_KEY=<32 位随机串>
```

**验证插值是否正确**（不需要启动容器）：

```powershell
docker compose config | Select-String 'DB_PASSWORD|JWT_SECRET'
```

> 这一步能在构建前发现「变量名拼错」「`.env` 没被读到」这类问题，省下几分钟的构建时间。

### 步骤 2：构建并启动

```powershell
docker compose up -d --build
```

**首次约 5~10 分钟**（拉镜像 + 容器内下载依赖 + MySQL 初始化）。**改代码后重建约 20 秒**（层缓存）。

### 步骤 3：确认全部健康

```powershell
docker compose ps
```

**应该看到**：

```text
SERVICE   STATUS                    PORTS
backend   Up (healthy)              8080/tcp
minio     Up (healthy)              9000/tcp
mysql     Up (healthy)              127.0.0.1:3307->3306/tcp
nginx     Up (healthy)              0.0.0.0:80->80/tcp
redis     Up (healthy)              6379/tcp
```

**如果某个服务一直 unhealthy**，看健康检查的真实输出：

```powershell
docker inspect diary-backend --format '{{json .State.Health}}' | ConvertFrom-Json |
  Select-Object -ExpandProperty Log | Select-Object -Last 3
```

### 步骤 4：冒烟测试

```powershell
powershell -ExecutionPolicy Bypass -File deploy\smoke-test.ps1
```

**17 项断言**，覆盖：链路连通、SPA 路由回退、注册/登录/刷新/登出、日记 CRUD、图片上传与回取、统计接口、401 拦截。

**退出码 0 表示全通过。**

### 步骤 5：打开浏览器

```text
http://localhost          （APP_PORT 非 80 时用 http://localhost:<APP_PORT>）
登录：tester / 123456
```

---

## 8. 日常运维

### 8.1 改代码后重新部署

```powershell
# 改完后端 Java 代码
docker compose up -d --build backend

# 改了前端代码
docker compose up -d --build frontend
```

**注意**：前端镜像里**没有 Node**，所以改前端不能热更新，必须重建（见 `ops/01` 第 5 节）。日常改前端用本地开发模式。

### 8.2 备份

```powershell
powershell -ExecutionPolicy Bypass -File deploy\mysql-backup.ps1
```

**设为每日计划任务**：

```powershell
schtasks /Create /TN "diary-mysql-backup" /SC DAILY /ST 02:30 `
  /TR "powershell -NoProfile -ExecutionPolicy Bypass -File <仓库绝对路径>\deploy\mysql-backup.ps1" /F
```

完整的备份与**恢复**步骤见 `doc/backend/05-database.md` 第 8 节。

### 8.3 排查用的命令组合

```powershell
# 1. 谁不健康
docker compose ps

# 2. 看它的日志
docker compose logs --tail 100 backend

# 3. 看健康检查到底在跑什么、失败输出是什么
docker inspect diary-backend --format '{{json .State.Health}}'

# 4. 进容器手动复现健康检查
docker compose exec -T backend curl -v http://127.0.0.1:8080/api/v1/ping

# 5. 确认环境变量注入正确
docker compose exec -T backend env | Select-String 'DB_|REDIS_|MINIO_|JWT_'

# 6. 确认 .env 被正确读取
docker compose config | Select-String 'DB_PASSWORD'
```

---

## 9. 动手练习

1. **验证依赖链**：`docker compose down` 后 `up -d`，观察 `docker compose ps` 的输出变化——backend 会在依赖 healthy 之后才出现
2. **制造一次依赖失败**：把 mysql 健康检查的 `test` 临时改成一个必然失败的命令（`[ "CMD", "false" ]`）并重建，观察 `depends_on` 长语法的效果——**backend 根本不会被启动**（而不是启动了再连不上库）。验证后改回
   > 注意不要只改 `retries`：只要健康检查本身能成功，把重试次数改成 1 依然会通过，验证不到依赖链。
3. **验证必填校验**：把 `.env` 里的 `JWT_SECRET` 整行删掉（先备份），执行 `docker compose config`，确认报错信息。改回来
4. **验证 SPA 回退**：`curl.exe -s -o NUL -w '%{http_code}' http://localhost/stats` 应为 200；然后临时把 `try_files` 那行注释掉重建 nginx，应该变 404。**验证后务必改回**
5. **验证反向代理的头**：在后端日志里找一条请求，确认 `X-Real-IP` 头有没有被正确传递
6. **验证缓存策略**：`curl.exe -sI http://localhost/` 与 `curl.exe -sI http://localhost/assets/<某个js>`，对比 `Cache-Control` 头
7. **验证 nginx 的 DNS 重解析**：用 `ops/01` 第 8 节的方式看容器 IP，然后只重建 backend（**不动 nginx**），确认接口仍能访问

---

## 附录 A · 部署检查清单

```text
[ ] Docker 守护进程可用（docker version 有 Server 版本）
[ ] .env 已从 .env.example 复制
[ ] 4 个必填项都已改成随机强密钥
[ ] docker compose config 无报错（说明插值正常）
[ ] docker compose up -d --build 完成
[ ] docker compose ps 五个服务全部 (healthy)
[ ] deploy/smoke-test.ps1 退出码 0
[ ] 浏览器能打开 http://localhost 并登录
[ ] 备份脚本能产出 dump
[ ] 备份的恢复路径验证过一次（不要只备份不验证恢复）
[ ] .gitignore 已忽略 .env 与 deploy/backups/
```

---

## 附录 B · 故障对照表

| 症状 | 原因 | 处理 |
|---|---|---|
| `docker compose up` 报错退出，提示某个变量 | `.env` 缺必填项 | 按提示补全 |
| 服务一直 `starting` | 健康检查还没通过 | 看 `docker inspect <容器> --format '{{json .State.Health}}'` 的 Log |
| 服务一直 `unhealthy` | 健康检查命令本身失败 | 进容器手动执行那条命令复现 |
| backend 启动失败，日志含 `JWT_SECRET` | 密钥缺失 | 补 `.env` |
| backend 日志 `Communications link failure` | MySQL 未就绪 | 检查 mysql 是否 healthy；`depends_on` 是否用了长语法 |
| 上传图片报 413 | 超过 `client_max_body_size` | 调大 nginx 该值（后端另有 5MB 校验） |
| `/api/**` 返回 502 | nginx 指向了失效的后端 IP | 已用 `resolver` + 变量 `proxy_pass` 修复；若仍出现，`docker compose restart nginx` |
| 前端路由刷新 404 | `try_files` 未生效 | 确认 `location /` 里有 `try_files $uri $uri/ /index.html` |
| 图片不显示 | `MINIO_PUBLIC_ENDPOINT` 配错 | 应为 `/files`；并确认 nginx 的 `/files/` 段存在 |
| 上传接口一直不可用，但无报错 | MinIO 未就绪导致 bucket 没建出来 | 看后端启动日志有没有「bucket 初始化失败」的 WARN；确认 minio 健康检查存在 |
| 改完前端代码容器里没变化 | 前端镜像内没有 Node | 必须 `docker compose up -d --build frontend` |
| 数据库时间差 8 小时 | 时区没配 | 检查 `TZ`、`--default-time-zone`、`serverTimezone` 三处 |
| `/v3/api-docs` 返回 200 但内容像 HTML | nginx 的 SPA 回退兜住了 | 这是预期行为（生产关闭了文档）。要验证应进后端容器测 |
| 磁盘持续增长 | 构建缓存堆积 | `docker builder prune` |

---

## 下一篇

`doc/ops/03-troubleshooting.md`：排障方法论——按「网络 → 容器 → 应用 → 数据库」四层定位问题、日志的有效读法、健康检查与资源占用，以及**备份恢复演练**。
