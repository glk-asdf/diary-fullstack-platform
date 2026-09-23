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
│   └── src/
│       ├── main/java/com/example/diary/
│       │   ├── common/
│       │   │   ├── result/     # Result / PageResult / ResultCode
│       │   │   ├── exception/  # BizException / GlobalExceptionHandler
│       │   │   └── util/       # JwtUtil / SecurityUtil
│       │   ├── config/         # Security / MyBatis-Plus / 填充 / Redis / OpenAPI
│       │   ├── security/       # JWT 过滤器 / EntryPoint / AccessDeniedHandler / LoginUser
│       │   ├── controller/     # Auth / User / Diary / Tag / File / Stats / Ping
│       │   ├── service/        # 接口与 impl/
│       │   ├── entity/         # User / Diary / Tag / DiaryTag / Attachment
│       │   ├── mapper/         # UserMapper / DiaryMapper / TagMapper / DiaryTagMapper / AttachmentMapper
│       │   ├── converter/      # 实体 → 出参转换（UserConverter / DiaryConverter）
│       │   ├── dto/            # 入参对象
│       │   └── vo/             # 出参对象
│       └── test/java/          # 单元测试
├── frontend/
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
│   ├── deepseek_markdown_20260923_c1afe0.md   # 架构设计
│   └── development-plan.md                    # 分步开发方案
├── sql/
│   └── init.sql                # 建库建表 + 测试账号
├── .env.example
└── .gitignore
```

## 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 25 | — |
| Node.js | 22 | — |
| MySQL | 8.4.9 | 免安装版，位于 `D:\business\tools\mysql-8.4.9-winx64` |
| Redis | 5.0.14.1 | 绿色版，位于 `D:\business\tools\redis-5.0.14.1` |
| MinIO | 归档版 `RELEASE.2025-07-23` | 单文件免安装，位于 `D:\business\tools\minio`（详见下方说明） |

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

Redis 使用 `redis.redis-for-vscode` 扩展，连接地址 `redis://127.0.0.1:6379`（无密码）。

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
| P8 | 部署与上线 | ⬜ 待开始 |

详细任务拆解、验收标准与风险见 `doc/development-plan.md`。
