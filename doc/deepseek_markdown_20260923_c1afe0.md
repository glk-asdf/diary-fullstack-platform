# 日记全栈平台架构设计文档

> 文档版本：v1.1（2026-09-23 技术选型对齐实际落地版本）  
> 创建日期：2026-09-23  
> 项目名称：diary-fullstack-platform  
> 技术栈：React + TypeScript + Spring Boot + MySQL + Redis + MinIO  
> 适用场景：个人日记项目、全栈学习、后续扩展为多用户日记平台

---

## 目录

1. 项目概述
2. 总体架构
3. 技术选型
4. 后端架构设计
5. 数据库设计
6. API 设计
7. 前端架构设计
8. 部署方案
9. 开发计划
10. 风险与注意事项
11. 附录

---

## 1. 项目概述

### 1.1 项目目标

构建一个前后端分离的个人日记平台，支持：

- 用户注册、登录、JWT 认证
- 日记的新增、编辑、删除、查询、分页
- Markdown 正文编辑与渲染
- 心情、天气、日期、标签管理
- 图片上传与展示
- 日历热力图、心情分布等统计
- Docker Compose 一键部署

### 1.2 设计原则

- 前后端完全分离，仅通过 REST API 通信
- 后端负责业务规则、权限校验、数据隔离
- 前端负责渲染、交互、路由和状态管理
- 使用 JWT 无状态认证，Refresh Token 存 Redis
- 数据库设计预留扩展能力，支持后续公开分享、导出、搜索等功能

### 1.3 非功能需求

| 类型 | 要求 |
|---|---|
| 性能 | 列表接口 P95 < 300ms，详情接口 P95 < 200ms |
| 安全 | 密码 BCrypt、JWT 鉴权、防越权、防 XSS |
| 可用性 | 单机 Docker Compose 可运行，支持每日备份 |
| 可维护性 | 分层清晰、统一响应、统一异常、接口文档自动生成 |
| 扩展性 | 后续可接 Elasticsearch、对象存储、消息队列 |

---

## 2. 总体架构

```text
┌─────────────────────────────────────────────┐
│                 浏览器                       │
│   React SPA (Vite + TS + Ant Design)        │
└──────────────────┬──────────────────────────┘
                   │ HTTPS / JSON
┌──────────────────▼──────────────────────────┐
│              Nginx (静态资源 + 反向代理)       │
│   /            → React dist                  │
│   /api/**      → Spring Boot :8080           │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│           Spring Boot 应用                   │
│  Controller → Service → Mapper → DB         │
│  Security(JWT) / 全局异常 / 参数校验          │
└──────┬──────────────┬──────────────┬────────┘
       │              │              │
   ┌───▼───┐     ┌────▼────┐    ┌────▼────┐
   │ MySQL │     │  Redis  │    │  MinIO  │
   │ 业务数据│    │ 缓存/Token│   │ 图片存储 │
   └───────┘     └─────────┘    └─────────┘
```

### 2.1 请求链路

1. 浏览器访问 Nginx。
2. 静态资源由 Nginx 返回 React 构建产物。
3. `/api/**` 请求由 Nginx 反向代理到 Spring Boot。
4. Spring Boot 经过 Security 过滤器校验 JWT。
5. Controller 接收请求，Service 处理业务，Mapper 访问 MySQL。
6. 图片上传写入 MinIO，数据库仅保存 URL。
7. Refresh Token、验证码、热点统计等写入 Redis。

---

## 3. 技术选型

| 层 | 技术 | 说明 |
|---|---|---|
| 前端框架 | React 19.2 + TypeScript 6.0 | 类型安全，生态成熟 |
| 构建工具 | Vite 8 | 启动快，配置简单 |
| 路由 | React Router 7 | SPA 路由，数据路由 API 与 v6 一致 |
| 状态管理 | Zustand 5 + TanStack Query 5 | Zustand 管客户端状态，Query 管服务端状态 |
| UI 组件 | Ant Design 5.29 | 表单、日历、表格组件齐全；React 19 需引入 `@ant-design/v5-patch-for-react-19` |
| 请求库 | Axios 1.x | 拦截器统一处理 token 和错误 |
| Markdown 编辑 | @uiw/react-md-editor 4 | 支持实时预览 |
| Markdown 渲染 | react-markdown 10 + rehype-sanitize 6 | 防 XSS |
| 图表 | Recharts 3 / ECharts | 心情统计、写作热力图 |
| 后端框架 | Spring Boot 3.5.16 + Java 25 | 本机 JDK 为 25，Spring Boot 由 3.2 上调至 3.5.x |
| 后端构建 | Maven Wrapper 3.9.16 | 随仓库提交，无需本机安装 Maven |
| 安全 | Spring Security + JJWT 0.12.7 | JWT 认证 |
| ORM | MyBatis-Plus 3.5.17 | CRUD 高效，分页方便 |
| 数据库 | MySQL 8 | 业务数据，本机直装（不使用容器） |
| 缓存 | Redis 7 | Refresh Token、热点数据，本机直装 |
| 对象存储 | MinIO / 阿里云 OSS | 图片附件，本机直装 |
| 接口文档 | SpringDoc OpenAPI 2.9.1 + Swagger UI | 自动生成 API 文档 |
| 部署 | Docker Compose + Nginx | 单机一键部署（P8 阶段落地） |

> **版本修订（2026-09-23）**：上表已按实际落地环境对齐。与初版设计的差异：后端 Spring Boot 3.2 → 3.5.16、Java 17/21 → 25；前端 React 18 → 19、Vite → 8、React Router v6 → v7；中间件由容器改为本机直装。变更原因与验证记录见 `development-plan.md` 的「实际落地版本」与 P0「完成记录」。

---

## 4. 后端架构设计

### 4.1 分层结构

```text
Controller  →  接收请求、参数校验、返回 VO，不写业务逻辑
Service     →  业务逻辑、事务边界、权限校验
Mapper      →  数据访问（MyBatis-Plus）
Entity      →  数据库映射
DTO/VO      →  入参 / 出参，与 Entity 隔离
```

### 4.2 包结构

```text
com.example.diary
├── DiaryApplication.java
├── common
│   ├── result          # Result<T>、PageResult<T>
│   ├── exception       # BizException、GlobalExceptionHandler
│   ├── enums           # MoodEnum、ResultCode
│   └── util            # JwtUtil、DateUtil、SecurityUtil
├── config
│   ├── SecurityConfig
│   ├── MybatisPlusConfig     # 分页插件、自动填充
│   ├── CorsConfig
│   ├── RedisConfig
│   └── OpenApiConfig
├── security
│   ├── JwtAuthenticationFilter
│   ├── UserDetailsServiceImpl
│   └── LoginUser             # 存入 SecurityContext 的当前用户
├── controller
│   ├── AuthController
│   ├── DiaryController
│   ├── TagController
│   ├── FileController
│   └── StatsController
├── service
│   ├── DiaryService
│   └── impl/DiaryServiceImpl
├── mapper
├── entity
├── dto     # LoginDTO、DiaryCreateDTO、DiaryQueryDTO
├── vo      # DiaryVO、DiaryDetailVO、UserVO
└── converter   # MapStruct：Entity <-> VO
```

### 4.3 统一响应体

```java
public record Result<T>(int code, String message, T data) {
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }
}
```

响应示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "title": "周末爬山"
  }
}
```

HTTP 状态码保持语义正确，业务码放在 `code` 字段中。

### 4.4 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("参数错误");
        return Result.fail(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "服务器开小差了");
    }
}
```

### 4.5 安全设计

- 密码使用 `BCryptPasswordEncoder`，数据库永不明文存储密码。
- Access Token：JWT，有效期 30 分钟。
- Refresh Token：有效期 7 天，存 Redis，Key 建议为 `refresh:{userId}:{jti}`。
- 支持主动登出、踢下线、刷新 token。
- Security 过滤器链放行 `/api/v1/auth/**`、`/api/v1/public/**`，其余请求需要认证。
- 所有查询、更新、删除操作必须校验当前用户身份，防止越权。

### 4.6 数据隔离

所有日记、标签、附件查询必须强制携带 `user_id = 当前登录用户`。

```java
public DiaryDetailVO getById(Long id) {
    Long userId = SecurityUtil.getCurrentUserId();
    Diary diary = diaryMapper.selectOne(
        new LambdaQueryWrapper<Diary>()
            .eq(Diary::getId, id)
            .eq(Diary::getUserId, userId)
    );

    if (diary == null) {
        throw new BizException(404, "日记不存在");
    }

    return converter.toDetailVO(diary);
}
```

### 4.7 自动填充

```java
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
```

---

## 5. 数据库设计

### 5.1 表清单

| 表名 | 说明 |
|---|---|
| t_user | 用户表 |
| t_diary | 日记表 |
| t_tag | 标签表 |
| t_diary_tag | 日记标签关联表 |
| t_attachment | 附件表 |

### 5.2 建表 SQL

```sql
CREATE TABLE `t_user` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `username`   VARCHAR(50)  NOT NULL UNIQUE,
  `password`   VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密',
  `nickname`   VARCHAR(50),
  `avatar`     VARCHAR(255),
  `email`      VARCHAR(100),
  `created_at` DATETIME     NOT NULL,
  `updated_at` DATETIME     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `t_diary` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`    BIGINT       NOT NULL,
  `title`      VARCHAR(200) NOT NULL,
  `content`    LONGTEXT     COMMENT 'Markdown 原文',
  `summary`    VARCHAR(500) COMMENT '列表页摘要，冗余存储',
  `mood`       TINYINT      COMMENT '1开心 2平静 3难过 4焦虑 5生气',
  `weather`    VARCHAR(20),
  `diary_date` DATE         NOT NULL COMMENT '日记归属日期',
  `is_public`  TINYINT      NOT NULL DEFAULT 0,
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at` DATETIME     NOT NULL,
  `updated_at` DATETIME     NOT NULL,
  KEY `idx_user_date` (`user_id`, `diary_date`),
  KEY `idx_user_mood` (`user_id`, `mood`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `t_tag` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`    BIGINT      NOT NULL,
  `name`       VARCHAR(30) NOT NULL,
  `color`      VARCHAR(10) DEFAULT '#1677ff',
  `created_at` DATETIME    NOT NULL,
  UNIQUE KEY `uk_user_name` (`user_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `t_diary_tag` (
  `diary_id` BIGINT NOT NULL,
  `tag_id`   BIGINT NOT NULL,
  PRIMARY KEY (`diary_id`, `tag_id`),
  KEY `idx_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `t_attachment` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `diary_id`   BIGINT,
  `user_id`    BIGINT       NOT NULL,
  `url`        VARCHAR(500) NOT NULL,
  `filename`   VARCHAR(200),
  `size`       BIGINT,
  `mime_type`  VARCHAR(100),
  `created_at` DATETIME     NOT NULL,
  KEY `idx_diary` (`diary_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 5.3 设计要点

- 使用逻辑删除 `deleted`，避免误删。
- `diary_date` 与 `created_at` 分离，支持补写昨天或指定日期的日记。
- `summary` 冗余存储，列表页不读取 LONGTEXT。
- 标签与日记多对多，通过 `t_diary_tag` 关联。
- 后续搜索量增大时，可增加 FULLTEXT 索引或接入 Elasticsearch。

---

## 6. API 设计

统一前缀：`/api/v1`

### 6.1 接口列表

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/register` | 注册 |
| POST | `/auth/login` | 登录，返回 accessToken + refreshToken |
| POST | `/auth/refresh` | 刷新 token |
| POST | `/auth/logout` | 登出 |
| GET | `/users/me` | 当前用户信息 |
| PUT | `/users/me` | 修改昵称、头像 |
| GET | `/diaries` | 分页列表，支持筛选 |
| GET | `/diaries/{id}` | 日记详情 |
| POST | `/diaries` | 新建日记 |
| PUT | `/diaries/{id}` | 更新日记 |
| DELETE | `/diaries/{id}` | 删除日记 |
| GET | `/tags` | 标签列表 |
| POST | `/tags` | 新建标签 |
| PUT | `/tags/{id}` | 修改标签 |
| DELETE | `/tags/{id}` | 删除标签 |
| POST | `/files/upload` | 上传图片，返回 URL |
| GET | `/stats/calendar` | 日历热力数据 |
| GET | `/stats/overview` | 总篇数、连续天数、心情分布 |

### 6.2 列表查询示例

```http
GET /api/v1/diaries?page=1&size=10&keyword=旅行&tagId=3&mood=1&startDate=2026-01-01&endDate=2026-09-23&sort=diaryDate,desc
```

### 6.3 分页响应示例

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 128,
    "pages": 13,
    "current": 1,
    "records": [
      {
        "id": 1,
        "title": "周末爬山",
        "summary": "今天天气很好，和朋友一起去爬山……",
        "mood": 1,
        "diaryDate": "2026-09-20",
        "tags": [
          { "id": 3, "name": "旅行", "color": "#52c41a" }
        ]
      }
    ]
  }
}
```

### 6.4 新建日记请求体

```json
{
  "title": "周末爬山",
  "content": "# 今天...\n\n![图](https://example.com/a.jpg)",
  "mood": 1,
  "weather": "晴",
  "diaryDate": "2026-09-20",
  "tagIds": [3, 5],
  "isPublic": false
}
```

---

## 7. 前端架构设计

### 7.1 目录结构

```text
src/
├── api/                 # 接口层
│   ├── request.ts       # Axios 实例 + 拦截器
│   ├── auth.ts
│   ├── diary.ts
│   └── tag.ts
├── components/          # 通用组件
│   ├── DiaryCard/
│   ├── MoodPicker/
│   ├── TagSelect/
│   └── MarkdownEditor/
├── pages/
│   ├── Login/
│   ├── Register/
│   ├── DiaryList/
│   ├── DiaryEdit/
│   ├── DiaryDetail/
│   ├── Stats/
│   └── Profile/
├── layouts/
│   └── MainLayout.tsx   # 侧边栏 + 顶栏 + Outlet
├── router/
│   ├── index.tsx
│   └── AuthGuard.tsx    # 路由守卫
├── store/
│   ├── useUserStore.ts  # Zustand：token、用户信息
│   └── useThemeStore.ts
├── hooks/
│   ├── useDiaryList.ts  # 封装 TanStack Query
│   └── useDebounce.ts
├── types/
│   └── diary.d.ts
├── utils/
│   ├── date.ts
│   └── storage.ts
└── main.tsx
```

### 7.2 Axios 封装

```ts
// api/request.ts
import axios from 'axios';
import { message } from 'antd';
import { useUserStore } from '@/store/useUserStore';

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
});

request.interceptors.request.use((config) => {
  const token = useUserStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshing: Promise<string> | null = null;

request.interceptors.response.use(
  (res) => {
    const { code, message: msg, data } = res.data;
    if (code !== 0) {
      message.error(msg);
      return Promise.reject(new Error(msg));
    }
    return data;
  },
  async (error) => {
    const { response, config } = error;

    if (response?.status === 401 && !config._retry) {
      config._retry = true;
      refreshing ??= refreshToken().finally(() => {
        refreshing = null;
      });
      const newToken = await refreshing;
      config.headers.Authorization = `Bearer ${newToken}`;
      return request(config);
    }

    return Promise.reject(error);
  }
);

export default request;
```

要点：

- 响应拦截器直接返回业务数据，业务代码不需要写 `res.data.data`。
- 401 自动刷新 token 并重放原请求。
- 使用 `refreshing` 防止并发刷新。

### 7.3 状态管理策略

| 数据类型 | 方案 |
|---|---|
| 登录态、主题、侧边栏折叠 | Zustand |
| 日记列表、详情、标签、统计 | TanStack Query |
| 表单临时值 | React Hook Form / 组件内部 state |

```ts
// hooks/useDiaryList.ts
import { keepPreviousData, useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { diaryApi } from '@/api/diary';
import { message } from 'antd';

export const useDiaryList = (params: DiaryQuery) =>
  useQuery({
    queryKey: ['diaries', params],
    queryFn: () => diaryApi.list(params),
    placeholderData: keepPreviousData,
  });

export const useCreateDiary = () => {
  const qc = useQueryClient();

  return useMutation({
    mutationFn: diaryApi.create,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['diaries'] });
      message.success('保存成功');
    },
  });
};
```

### 7.4 路由与守卫

```tsx
const router = createBrowserRouter([
  { path: '/login', element: <Login /> },
  { path: '/register', element: <Register /> },
  {
    path: '/',
    element: (
      <AuthGuard>
        <MainLayout />
      </AuthGuard>
    ),
    children: [
      { index: true, element: <Navigate to="/diaries" replace /> },
      { path: 'diaries', element: <DiaryList /> },
      { path: 'diaries/new', element: <DiaryEdit /> },
      { path: 'diaries/:id', element: <DiaryDetail /> },
      { path: 'diaries/:id/edit', element: <DiaryEdit /> },
      { path: 'stats', element: <Stats /> },
      { path: 'profile', element: <Profile /> },
    ],
  },
  { path: '*', element: <NotFound /> },
]);
```

### 7.5 页面功能规划

| 页面 | 功能 |
|---|---|
| DiaryList | 卡片/时间轴切换、关键词搜索、标签筛选、心情筛选、月份日历视图 |
| DiaryEdit | Markdown 编辑器、实时预览、心情选择、标签选择、日期选择、图片拖拽上传 |
| DiaryDetail | Markdown 渲染、编辑、删除 |
| Stats | GitHub 风格写作热力图、心情饼图、连续打卡天数 |
| Profile | 头像、昵称、密码修改 |

---

## 8. 部署方案

### 8.1 docker-compose.yml

```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
      MYSQL_DATABASE: diary
    volumes:
      - mysql-data:/var/lib/mysql
      - ./sql/init.sql:/docker-entrypoint-initdb.d/init.sql
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci

  redis:
    image: redis:7-alpine
    volumes:
      - redis-data:/data

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_PASSWORD}
    volumes:
      - minio-data:/data

  backend:
    build: ./backend
    depends_on:
      - mysql
      - redis
    environment:
      SPRING_PROFILES_ACTIVE: prod
    expose:
      - "8080"

  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./frontend/dist:/usr/share/nginx/html:ro
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - backend

volumes:
  mysql-data:
  redis-data:
  minio-data:
```

### 8.2 Nginx 配置

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

生产环境注意：

- 数据库密码、JWT 密钥、MinIO 密钥放在 `.env`，不要提交到 Git。
- 配置 HTTPS。
- 定期备份 MySQL 数据卷。

---

## 9. 开发计划

| 阶段 | 内容 |
|---|---|
| 1 | 搭建 Spring Boot + MySQL + 建表；搭建 Vite + React + AntD + 路由 |
| 2 | 打通注册、登录、JWT 全链路，前后端联调 |
| 3 | 日记 CRUD，列表分页、详情、Markdown 编辑 |
| 4 | 标签、心情、日期筛选 |
| 5 | 图片上传 MinIO，Markdown 插图 |
| 6 | 统计页：日历热力图、心情分布 |
| 7 | Docker Compose 部署、Nginx、HTTPS |
| 8 | 可选：公开分享、导出 PDF/Markdown、数据备份 |

> 第 2 步是项目风险点。认证链路跑通后，后续功能基本都是重复模式。

---

## 10. 风险与注意事项

1. **刷新页面 404**  
   Nginx 必须配置 `try_files $uri $uri/ /index.html`。

2. **跨域问题**  
   开发环境用 Vite `server.proxy` 代理 `/api`；生产环境用 Nginx 同源代理。不要在后端到处写 `@CrossOrigin`。

3. **时区问题**  
   MySQL 连接串加 `serverTimezone=Asia/Shanghai`，Java 使用 `LocalDateTime`。

4. **Markdown XSS**  
   `react-markdown` 必须配合 `rehype-sanitize`，否则用户可以注入脚本。

5. **越权访问**  
   查询、更新、删除必须带 `user_id` 条件，不能只按 `id` 查询。

6. **LONGTEXT 拖慢列表**  
   列表查询显式 select 需要的字段，不要 `SELECT *`。

7. **JWT 密钥安全**  
   至少 256 位随机字符串，放在环境变量中，泄露等于所有账号沦陷。

8. **Refresh Token 管理**  
   建议存 Redis，支持主动登出和踢下线。

9. **文件上传安全**  
   限制文件类型、大小，生成随机文件名，防止路径穿越。

10. **数据库备份**  
    至少每天备份一次，保留最近 7 天。

---

## 11. 附录

### 11.1 环境变量示例

```env
# MySQL
DB_HOST=mysql
DB_PORT=3306
DB_NAME=diary
DB_USER=root
DB_PASSWORD=your_password

# Redis
REDIS_HOST=redis
REDIS_PORT=6379

# JWT
JWT_SECRET=至少256位随机字符串
JWT_ACCESS_EXPIRE=1800
JWT_REFRESH_EXPIRE=604800

# MinIO
MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=diary
```

### 11.2 命名规范

| 类型 | 规范 | 示例 |
|---|---|---|
| REST 路径 | 小写、复数、连字符 | `/api/v1/diaries` |
| DTO | 动词 + DTO | `DiaryCreateDTO` |
| VO | 名词 + VO | `DiaryDetailVO` |
| Entity | 与表对应 | `Diary` |
| 时间格式 | ISO 8601 | `2026-09-23T10:00:00` |
| 分页参数 | page、size | `page=1&size=10` |

### 11.3 推荐接口文档

启动后端后访问：

```text
http://localhost:8080/swagger-ui/index.html
```

### 11.4 推荐开发顺序

1. 数据库建表。
2. 后端登录认证。
3. 前端登录页与 Axios 封装。
4. 日记 CRUD。
5. 标签与筛选。
6. 图片上传。
7. 统计页面。
8. 部署上线。