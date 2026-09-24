# 日记全栈平台（diary-fullstack-platform）

前后端分离的个人日记平台：支持注册登录、日记 CRUD、Markdown 编辑、标签心情筛选、图片上传与统计。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | React 19 + TypeScript 6 + Vite 8 + Ant Design 5 |
| 前端状态 | Zustand 5（客户端状态） + TanStack Query 5（服务端状态） |
| 路由 | React Router 7 |
| 后端 | Spring Boot 3.5.16 + Java 25 |
| 安全 | Spring Security（无状态 JWT） + JJWT 0.12.7 |
| 持久层 | MyBatis-Plus 3.5.17（含 mybatis-plus-jsqlparser） + MySQL 8.4.9 |
| 缓存 | Redis 5.0.14.1（Windows 移植版，存 Refresh Token） |
| 对象存储 | MinIO（P5 阶段引入） |
| 接口文档 | SpringDoc OpenAPI 2.9 + Swagger UI |
| 构建 | Maven Wrapper（随仓库自带，无需本机安装 Maven） |
| 代码风格 | 不使用 Lombok，访问器显式书写（JDK 25 下 Lombok 会触发 Unsafe 告警） |

## 目录结构

```text
diary-fullstack-platform/
├── backend/
│   ├── mvnw / mvnw.cmd         # Maven Wrapper
│   ├── pom.xml
│   ├── Dockerfile              # 多阶段构建：JDK 编译 → JRE 运行（非 root）
│   ├── .dockerignore
│   └── src/
│       ├── main/java/com/example/diary/
│       │   ├── common/
│       │   │   ├── result/     # Result / PageResult / ResultCode
│       │   │   ├── exception/  # BizException / GlobalExceptionHandler
│       │   │   └── util/       # JwtUtil / SecurityUtil
│       │   ├── config/         # Security / MyBatis-Plus / 填充 / Redis / MinIO / OpenAPI
│       │   ├── security/       # JWT 过滤器 / EntryPoint / AccessDeniedHandler / LoginUser
│       │   ├── controller/     # Auth / User / Diary / Tag / File / Stats / Ping
│       │   ├── service/        # 接口与 impl/
│       │   ├── entity/         # User / Diary / Tag / DiaryTag / Attachment
│       │   ├── mapper/         # UserMapper / DiaryMapper / TagMapper / DiaryTagMapper / AttachmentMapper
│       │   ├── converter/      # 实体 → 出参转换（UserConverter / DiaryConverter）
│       │   ├── dto/            # 入参对象
│       │   └── vo/             # 出参对象
│       ├── main/resources/     # application.yml / -dev.yml / -prod.yml
│       └── test/java/          # 单元测试
├── frontend/
│   ├── Dockerfile              # 多阶段构建：Node 构建 → Nginx 托管
│   ├── nginx.conf              # 静态托管 + /api 反代后端 + /files 反代 MinIO
│   ├── .dockerignore
│   └── src/
│       ├── api/                # request.ts（拦截器）/ auth.ts / diary.ts / tag.ts / file.ts / stats.ts / ping.ts
│       ├── types/              # api.ts（分页）/ diary.ts（含心情枚举）
│       ├── hooks/              # useDiaryList / useTags / useStats / useDebounce
│       ├── components/         # DiaryCard / MoodPicker / TagSelect / MarkdownEditor / ErrorBoundary / PageLoading
│       ├── utils/              # date.ts（相对时间 / 友好日期）
│       ├── store/              # useUserStore / useThemeStore（Zustand + persist）
│       ├── router/             # index.tsx（路由懒加载）/ AuthGuard.tsx
│       ├── layouts/            # MainLayout.tsx（主题切换 / 响应式折叠）
│       ├── pages/              # Login / Register / DiaryList / DiaryDetail / DiaryEdit / TagManage / Stats / Profile / NotFound
│       └── main.tsx
├── doc/
│   ├── deepseek_markdown_20260923_c1afe0.md   # 架构设计（最初的方案）
│   ├── development-plan.md                    # 分步开发方案（阶段计划 + 完成记录）
│   ├── development-log.md                     # 开发实录（决策依据、踩坑与修复过程）
│   ├── usage-guide.md                         # 使用手册（部署 / 使用 / 备份恢复 / 故障排查）
│   ├── learning-path.md                       # 学习路径·前端（Vue 背景开发者分模块上手）
│   ├── fullstack-roadmap.md                   # 学习路径·全栈总索引（模块清单与顺序）
│   ├── backend/                               # 学习路径·后端（01 Java / 02 Spring / 03 持久层 / 04 安全 / 05 数据库）
│   └── ops/                                   # 学习路径·运维（01 Docker / 02 部署 / 03 排障）
├── sql/
│   └── init.sql                # 建库建表 + 测试账号
├── deploy/
│   ├── mysql-backup.ps1        # MySQL 备份 + 保留 7 天（Windows 计划任务）
│   ├── mysql-backup.sh         # MySQL 备份 + 保留 7 天（Linux crontab）
│   └── smoke-test.ps1          # 部署后冒烟测试（登录/CRUD/上传/统计）
├── docker-compose.yml          # 单机一键部署编排
├── .env.example                # Compose 部署环境变量模板
└── .gitignore
```

## 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 25 | 本地开发后端 |
| Node.js | 22 | 本地开发前端 |
| MySQL | 8.4.9 | 免安装版，位于 `D:\business\tools\mysql-8.4.9-winx64` |
| Redis | 5.0.14.1 | 绿色版，位于 `D:\business\tools\redis-5.0.14.1` |
| MinIO | 归档版 `RELEASE.2025-07-23` | 单文件免安装，位于 `D:\business\tools\minio`（详见下方说明） |
| Docker Desktop | 4.91.0 | **仅容器化部署需要**（见「部署」章节），本地开发不需要 |

> 上表是**本地开发**所需的中间件。若改用 Docker Compose 部署，JDK / Node / MySQL / Redis / MinIO 全部由容器提供，宿主机只需要 Docker。
> Maven 无需单独安装，使用仓库自带的 `mvnw` / `mvnw.cmd`。

### 中间件启停

```powershell
# MySQL
powershell -ExecutionPolicy Bypass -File D:\business\tools\mysql-start.ps1
powershell -ExecutionPolicy Bypass -File D:\business\tools\mysql-stop.ps1

# Redis
powershell -ExecutionPolicy Bypass -File D:\business\tools\redis-start.ps1
powershell -ExecutionPolicy Bypass -File D:\business\tools\redis-stop.ps1

# MinIO
powershell -ExecutionPolicy Bypass -File D:\business\tools\minio-start.ps1
powershell -ExecutionPolicy Bypass -File D:\business\tools\minio-stop.ps1
```

脚本幂等，重复执行会直接返回；均未注册为 Windows 服务，重启机器后需重新启动。

| 组件 | 连接信息 |
|---|---|
| MySQL | `127.0.0.1:3306`，`root` / `123456`，库名 `diary` |
| Redis | `127.0.0.1:6379`，无密码 |
| MinIO | S3 API `127.0.0.1:9000`，Web 控制台 `127.0.0.1:9001`，`minioadmin` / `minioadmin` |

> **关于 MinIO**：MinIO 开源版已被官方归档，`dl.min.io` 返回 `410 Gone`，不再提供下载与安全更新。本项目使用的是从国内镜像获取的归档版 `RELEASE.2025-07-23T15-54-02Z`，**仅适合本地 / 内网学习用途**。若要部署到公网，建议改用 SeaweedFS 等活跃维护的 S3 兼容方案或云厂商 OSS——两者都可通过 AWS S3 SDK 平滑替换。

> 脚本文件必须保持 **UTF-8 with BOM**：Windows PowerShell 5.1 会把无 BOM 的 UTF-8 按 GBK 解码，导致中文提示乱码并引发脚本解析失败。

### 初始化数据库

首次使用或需要重建表结构时执行：

```powershell
D:\business\tools\mysql-8.4.9-winx64\bin\mysql.exe -u root -p123456 -h 127.0.0.1 --default-character-set=utf8mb4 -e "source d:/business/ai-fullstack-platform/diary-fullstack-platform/sql/init.sql"
```

脚本可重复执行，并内置测试账号：**`tester` / `123456`**（密码为 BCrypt 密文，可直接用于登录联调）。

### IDE 扩展（可选，便于查看数据）

CodeBuddy CN 的 CLI 入口为 `C:\Users\DELL\AppData\Local\Programs\CodeBuddy CN\bin\buddycn.cmd`：

```powershell
$cli = "C:\Users\DELL\AppData\Local\Programs\CodeBuddy CN\bin\buddycn.cmd"

# 图形化查看 MySQL
& $cli --install-extension cweijan.vscode-database-client2
& $cli --install-extension cweijan.dbclient-jdbc
```

安装后 `Ctrl+Shift+P` → `Reload Window` 生效。左侧会出现 Database 图标，用下面的参数建连接：

| 字段 | 值 |
|---|---|
| Host / Port | `127.0.0.1` / `3306` |
| Username / Password | `root` / `123456` |
| Database | `diary` |

要连**容器里的库**，用下面这组（容器 MySQL 映射在 3307，见「部署」章节）：

| 字段 | 值 |
|---|---|
| Host / Port | `127.0.0.1` / `3307` |
| Username / Password | `root` / `.env` 中的 `DB_PASSWORD` |
| Database | `diary` |

```powershell
# 查看 .env 里随机生成的容器库密码
Get-Content .env -Encoding UTF8 | Select-String '^DB_PASSWORD='
```

> 两套库并存，**注意别连错**：`3306` 是本地开发库（`root` / `123456`，写过 P3~P7 的联调数据），
> `3307` 是容器库（密码随机生成，初始只有 `init.sql` 建的表和 `tester` 账号）。
> 3307 只监听回环地址，局域网内其他机器连不上；不需要图形客户端时，把 `docker-compose.yml` 中 mysql 的 `ports` 删掉即可。
>
> **连不上时先读报错**：`Access denied for user 'root'@'172.18.0.1'` 里的 `172.18.0.1` 是 Docker 网桥网关，
> 说明端口通路正常、失败在认证环节——**最常见的原因是顺手填了本地库的 `123456`**。
> 可以用下面这条把容器库密码直接放进剪贴板，粘贴进客户端，省得手抄出错：
>
> ```powershell
> (Get-Content .env -Encoding UTF8 | Where-Object { $_ -like 'DB_PASSWORD=*' }) -replace '^DB_PASSWORD=', '' | Set-Clipboard
> ```
>
> 想先自证服务端没问题，用 CLI 连一次（成功时会打印 `root@%` 与 `root@172.18.0.1`）：
>
> ```powershell
> $env:MYSQL_PWD = (Get-Content .env -Encoding UTF8 | Where-Object { $_ -like 'DB_PASSWORD=*' }) -replace '^DB_PASSWORD=', ''
> & "D:\business\tools\mysql-8.4.9-winx64\bin\mysql.exe" -u root -h 127.0.0.1 -P 3307 --default-character-set=utf8mb4 -e "select current_user(), user();"
> Remove-Item Env:MYSQL_PWD
> ```

Redis 使用 `redis.redis-for-vscode` 扩展，连接地址 `redis://127.0.0.1:6379`（无密码，指本地开发库）。

> 注意：用原生 SQL 查 `t_diary` 会连 `deleted = 1` 的记录一起查出，逻辑删除条件由 MyBatis-Plus 在应用层拼接。模拟应用行为需自行加 `WHERE deleted = 0`。

## 快速开始

### 1. 启动中间件

```powershell
powershell -ExecutionPolicy Bypass -File D:\business\tools\mysql-start.ps1
powershell -ExecutionPolicy Bypass -File D:\business\tools\redis-start.ps1
powershell -ExecutionPolicy Bypass -File D:\business\tools\minio-start.ps1
```

### 2. 配置环境变量

复制 `.env.example` 为 `.env` 并填写真实值（`.env` 已被 git 忽略）。
本机开发可直接沿用默认值：`root` / `123456` / 库名 `diary`。

### 3. 启动后端

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

- 服务地址：http://localhost:8080
- 健康检查：http://localhost:8080/api/v1/ping
- 接口文档：http://localhost:8080/swagger-ui.html

### 4. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

- 访问 http://localhost:5173
- 开发环境下 `/api` 由 Vite 代理转发到 `http://localhost:8080`，前后端同源，无需处理跨域

### 5. 构建

```powershell
# 后端
cd backend; .\mvnw.cmd -DskipTests package

# 前端
cd frontend; npm run build
```

## 部署（Docker Compose）

单机部署只需 Docker，宿主机不必安装 JDK / Node / MySQL / Redis / MinIO。

```powershell
# 1. 准备环境变量。必填项缺失时 compose 会直接报错退出，不会用默认弱密码启动
Copy-Item .env.example .env
# 编辑 .env，至少改掉 DB_PASSWORD / JWT_SECRET / MINIO_ACCESS_KEY / MINIO_SECRET_KEY

# 2. 构建并启动（首次需拉镜像 + 编译前后端，约 5~10 分钟）
docker compose up -d --build

# 3. 冒烟测试：17 项断言，覆盖链路 / 注册登录 / CRUD / 上传 / 统计 / 401 拦截
powershell -ExecutionPolicy Bypass -File deploy\smoke-test.ps1
```

访问 `http://localhost`（`APP_PORT` 非 80 时为 `http://localhost:<APP_PORT>`）。

常用命令：

```powershell
docker compose ps                  # 查看容器状态与健康检查结果
docker compose logs -f backend     # 跟踪后端日志
docker compose restart backend     # 改完配置重启单个服务
docker compose down                # 停止但保留数据卷
docker compose down -v             # 停止并删除数据卷（清空所有数据）
```

请求走向：

```text
浏览器 ──> nginx:80 ──┬─ /        静态资源（前端产物，SPA 回退到 index.html）
                      ├─ /api/    反向代理 → backend:8080
                      └─ /files/  反向代理 → minio:9000（图片）

容器内网（应用间通信）：mysql:3306 / redis:6379 / minio:9000
宿主机（仅本机可用）：  127.0.0.1:3307 → mysql:3306
```

> 对外只暴露 Nginx 一个端口。容器 MySQL 额外映射到 `127.0.0.1:3307` 且**只监听回环地址**，专供本机的数据库客户端使用，局域网内其他机器连不上。
> 因此容器化部署**不会与本地开发的 3306/6379/9000/8080 冲突**，两套可以同时在跑。

### 数据备份

```powershell
# 立即备份一次：写入 deploy/backups/，自动清理 7 天前的文件
powershell -ExecutionPolicy Bypass -File deploy\mysql-backup.ps1

# 注册为每天 02:30 的计划任务（把 <仓库绝对路径> 换成实际路径）
schtasks /Create /TN "diary-mysql-backup" /SC DAILY /ST 02:30 `
  /TR "powershell -NoProfile -ExecutionPolicy Bypass -File <仓库绝对路径>\deploy\mysql-backup.ps1" /F
```

Linux 服务器改用 `deploy/mysql-backup.sh` + crontab，脚本头部有示例。

### 首次部署注意事项

- **WSL2 首次启用需重启一次**：`wsl --install --no-distribution` 会启用 `VirtualMachinePlatform`，重启前 `wsl --status` 会报「WSL2 无法启动」，Docker 守护进程起不来。
- **镜像拉取慢**：国内网络可在 Docker Desktop 的 `Settings → Docker Engine` 中配置 `registry-mirrors`。
- **生产密钥**：`application-prod.yml` 里 `JWT_SECRET` 没有默认值，未配置则后端启动失败——刻意设计，避免用开发密钥上线。
- **接口文档在生产默认关闭**（`springdoc.api-docs.enabled=false`）。临时排查时给 backend 服务加 `SPRINGDOC_ENABLED=true` 再重启。

### 运维速查（查看已部署的内容）

> 在脚本或管道里调用 `docker compose exec` 必须加 `-T`，否则报 `the input device is not a TTY`。

**整体状态**

```powershell
docker compose ps            # 服务状态 + 健康检查结果 + 端口映射
docker stats --no-stream     # 实时 CPU / 内存 / 网络占用
docker compose images        # 各服务所用镜像与大小
docker compose top           # 容器内运行的进程
```

`PORTS` 列中，`0.0.0.0:80->80/tcp` 是 Nginx 对外入口，`127.0.0.1:3307->3306/tcp` 是容器 MySQL 供本机客户端使用的映射；其余服务（backend / redis / minio）显示的只是**容器内**端口，宿主机连不上——这是设计意图。若还需从宿主机直连 Redis 或 MinIO，按同样方式给对应服务加 `ports:` 即可。

**日志**

```powershell
docker compose logs -f backend                                # 跟踪后端
docker compose logs --tail 50 nginx                           # 最后 50 行
docker compose logs --since 10m                               # 最近 10 分钟（全部服务）
docker compose logs nginx | Select-String -NotMatch 'Wget'    # 滤掉每 15 秒的健康检查噪音
```

**进容器里看**

```powershell
docker compose exec backend sh                            # 交互式 shell
docker compose exec -T backend env | sort                 # 环境变量
docker compose exec nginx ls -la /usr/share/nginx/html    # 部署进去的前端产物
```

**看数据**

```powershell
# MySQL：密码由容器内 shell 展开（PowerShell 侧用 ` 转义 $），无需手输，也不会进命令行历史。
# --default-character-set=utf8mb4 不能省，原因见下方说明
docker compose exec -T mysql sh -c "mysql -uroot -p`$MYSQL_ROOT_PASSWORD --default-character-set=utf8mb4 --database=diary --table -e 'show tables;'"

# 表清单 + 引擎 + 行数估算 + 注释
docker compose exec -T mysql sh -c "mysql -uroot -p`$MYSQL_ROOT_PASSWORD --default-character-set=utf8mb4 --database=diary --table -e 'SELECT TABLE_NAME AS tbl, ENGINE, TABLE_ROWS AS rows_est, TABLE_COMMENT AS note FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME;'"

# Redis
docker compose exec -T redis redis-cli --scan
docker compose exec -T redis redis-cli dbsize
```

> **这段的引号写法不能想当然**。`sh -c` 里的 SQL 必须用**单引号**，PowerShell 侧用**双引号**包裹并把 `$` 转义为 `` `$ ``。
> 若按常见写法在 PowerShell 单引号字符串里嵌双引号（`sh -c '... -e "use diary; show tables;"'`），PS 5.1 会把内层双引号吃掉，
> mysql 只收到 `-e use`，报 `ERROR at line 1: USE must be followed by a database name`。
> 同理不要用管道把 SQL 喂给 mysql 的 stdin——PowerShell 会在开头写入 BOM，导致 `syntax error`。
> `TABLE_ROWS` 是 InnoDB 的**估算值**，要精确行数请用 `SELECT COUNT(*)`。

> **`--default-character-set=utf8mb4` 同样不能省**。容器内 `LANG` 为空（`LC_CTYPE=POSIX`），
> mysql 客户端会把 `character_set_client / connection / results` 全部降级为 **latin1**：
> 读中文显示成 `?`；**写中文（INSERT / UPDATE / 表注释）会被存成双重编码的乱码**。
> 这个坑格外阴险——乱码数据配上 latin1 客户端读回时会反向抵消而「看起来正常」，
> 必须用 `CHAR_LENGTH` 与 `LENGTH` 对比才能识破：正常中文 3 字 = 9 字节，双重编码会变成 9 字 = 19 字节。
> `sql/init.sql` 顶部已加 `SET NAMES utf8mb4;` 使其免疫，手动敲 SQL 时请自行带上该参数。

> **MinIO 不要直接翻数据卷**：每个对象在磁盘上是一个目录，数据放在 `xl.meta` 里（小对象直接内联），
> 翻出来的文件既难读也容易误判。要走 S3 协议访问：

```powershell
docker compose exec -T backend sh -c "curl -sI http://minio:9000/diary/2026/09/xxx.png"
curl.exe -sI http://localhost/files/diary/2026/09/xxx.png     # 经 Nginx 同源访问
```

**卷 / 网络 / 端口**

```powershell
docker volume ls --filter name=diary
docker volume inspect diary_mysql-data    # 看宿主机上的实际存储路径
docker network inspect diary_diary-net --format '{{range .Containers}}{{.Name}} = {{.IPv4Address}}{{println}}{{end}}'
docker compose port nginx 80              # 反查某个端口映射到宿主机哪里
```

**排查部署问题最有用的两条**

```powershell
docker compose config                                     # 变量插值后的完整配置，确认 .env 是否生效
docker inspect diary-backend --format '{{.State.Health}}' # 健康检查明细，含失败时的真实输出
```

服务一直 `unhealthy` 时，先看 `.State.Health.Log` 里健康检查命令的真实输出，比翻应用日志快得多。

### Docker Desktop 图形界面（可选）

不习惯命令行时，图形界面能做同样的事。当前实例在 GUI 里会看到：

```text
Containers  按 compose 项目分组，项目名 diary，5 个服务
Images      6 个     2.136 GB
Containers  5 个     225.3 kB
Volumes     3 个     219 MB
Build Cache 51 条    2.366 GB（其中 1.648 GB 可回收）
```

**Containers 页**

| GUI 位置 | 作用 | 等价命令 |
|---|---|---|
| Logs 标签 | 带搜索与时间戳的日志，可自动滚动 | `docker compose logs -f <服务>` |
| Exec / Terminal 标签 | **在容器内开终端**，最省事的入口 | `docker compose exec <服务> sh` |
| Files 标签 | 浏览容器文件系统 | `docker compose exec nginx ls /usr/share/nginx/html` |
| Stats 标签 | CPU / 内存实时曲线 | `docker stats` |
| Inspect 标签 | 完整 JSON，含 `.State.Health` | `docker inspect <容器>` |

在 Exec 标签里不必再拼 `docker compose exec -T`，而且容器内是普通 shell（Oracle Linux 9，`sh` / `bash` 都有，默认 root），没有 PowerShell 那套引号问题，命令更简单：

```sh
# 进 MySQL 交互式提示符（--default-character-set 必须带上，否则中文显示为 ? 且写入会变乱码）
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 diary

# 只出一条结果，不进提示符
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 diary --table -e "show tables;"

# 看表结构 / 数据
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 diary --table -e "desc t_diary;"

# Redis
redis-cli --scan
```
想确认前端产物是否真的进了镜像，打开 `nginx` 容器看 `/usr/share/nginx/html` 最直接。

**Images / Volumes / Builds 页**

- **Images**：可查看镜像分层，直观看出哪一层被缓存复用——这正是改代码后重建只要约 20 秒的原因。
- **Volumes**：3 个具名卷。但**别用它判断 MinIO 是否存了图片**——磁盘结构是「目录 + `xl.meta`」，小对象数据内联其中，要改用 S3 协议访问。
- **Builds**：构建历史与缓存占用。清理用 `docker builder prune`。

**Settings**

| 位置 | 用途 |
|---|---|
| Resources | 分配给 WSL2 虚拟机的 CPU / 内存 / 磁盘 |
| Docker Engine | 编辑 `daemon.json`，国内镜像加速的 `registry-mirrors` 配在这里 |
| General | 开机自启、WSL2 引擎开关 |

**数据在 Windows 上的位置**

```text
C:\Users\<用户名>\AppData\Local\Docker\wsl\disk\docker_data.vhdx   ← 全部镜像 + 容器 + 卷
C:\Users\<用户名>\AppData\Local\Docker\wsl\main\ext4.vhdx
```

所有数据都在这两个虚拟磁盘里，**无法用资源管理器直接取出数据库文件**。要取数据只有两条路：容器 Exec 里操作，或走 `deploy/mysql-backup.ps1` 导出。
`wsl -l -v` 只显示一个发行版 `docker-desktop`（Docker Desktop 4.30 起把 `docker-desktop-data` 合并了进来）。

> **不要混用 GUI 与命令行操作同一套服务**：在 GUI 里 Stop 某个容器不会反映到 `.env`，下次执行 `docker compose up -d` 又会把它拉起来，状态容易让人困惑。排查前先用 `docker compose ps` 确认一遍。

## 认证说明

- 登录成功返回 `accessToken`（默认 30 分钟）与 `refreshToken`（默认 7 天）
- 业务请求携带请求头 `Authorization: Bearer <accessToken>`
- accessToken 过期时，前端拦截器自动用 refreshToken 换取新令牌并重放原请求，用户无感知
- refreshToken 每次刷新都会**轮换**，旧令牌立即失效，防止被重复使用
- 登出会删除服务端 Redis 中的 refreshToken，使其立刻失效
- 放行路径：`/api/v1/auth/**`、`/api/v1/public/**`、`/api/v1/ping`、Swagger 文档路径；其余接口均需认证

## 统一约定

- 接口统一前缀：`/api/v1`
- 统一响应体：`{ "code": 0, "message": "ok", "data": ... }`，`code = 0` 表示成功
- HTTP 状态码保持语义正确（如 401/403/404），业务码同时放在 `code` 字段
- 前端响应拦截器已解包，业务代码直接拿到 `data`，无需写 `res.data.data`

## 开发进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 | 环境与工程初始化 | ✅ 已完成 |
| P1 | 数据库与后端基础设施 | ✅ 已完成 |
| P2 | 认证全链路 | ✅ 已完成 |
| P3 | 日记 CRUD | ✅ 已完成 |
| P4 | 标签与筛选 | ✅ 已完成 |
| P5 | 图片上传 MinIO | ✅ 已完成 |
| P6 | 前端体验完善 | ✅ 已完成 |
| P7 | 统计页 | ✅ 已完成 |
| P8 | 部署与上线 | ✅ 已完成 |

详细任务拆解、验收标准与风险见 `doc/development-plan.md`。
