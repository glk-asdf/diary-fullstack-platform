# 日记全栈平台 · 分步开发方案

> 文档版本：v1.1
> 创建日期：2026-09-23
> 最后更新：2026-09-23（P0 完成，回填「实际落地版本」与 P0「完成记录」）
> 对应架构文档：`doc/deepseek_markdown_20260923_c1afe0.md`
> 目标：把架构设计拆成**可独立验收、可增量交付**的开发阶段，每阶段结束都能跑起来、能演示。

---

## 0. 使用说明

- 阶段划分原则：**纵向切片**优先于横向分层。即"认证一条线跑通"优于"先把所有 Entity 写完"。
- 每个阶段包含：目标 / 任务清单 / 交付物 / 验收标准（DoD）/ 风险。
- 阶段之间存在硬依赖，标注 `依赖` 的阶段必须先完成前置阶段。
- 每个阶段完成后建议打一个 Git tag：`v0.1-init`、`v0.2-auth` …… 便于回滚。
- 时间估算按 1 人全职、每天 4~6 小时有效编码计算，仅供参考。

### 阶段总览

| 阶段 | 名称 | 依赖 | 预估 | 里程碑 | 状态 |
|---|---|---|---|---|---|
| P0 | 环境与工程初始化 | - | 0.5 天 | 前后端能各自启动 | ✅ 已完成（2026-09-23） |
| P1 | 数据库与后端骨架 | P0 | 1 天 | 建表完成 + 分页/逻辑删除/自动填充生效 | ✅ 已完成（2026-09-23） |
| P2 | 认证全链路 | P1 | 2 天 | 注册登录刷新登出闭环 | ✅ 已完成（2026-09-23） |
| P3 | 日记 CRUD | P2 | 2 天 | 列表分页 + 详情 + 增删改 | ⬜ 待开始 |
| P4 | 标签与筛选 | P3 | 1.5 天 | 多条件组合筛选 | ⬜ 待开始 |
| P5 | 图片上传 MinIO | P3 | 1.5 天 | Markdown 插图可用 | ⬜ 待开始 |
| P6 | Markdown 渲染与编辑器完善 | P3 | 1 天 | 编辑/预览/防 XSS | ⬜ 待开始 |
| P7 | 统计页 | P4 | 1.5 天 | 热力图 + 心情分布 | ⬜ 待开始 |
| P8 | 部署与上线 | P3~P7 | 1.5 天 | 单机一键部署 | ⬜ 待开始 |
| P9 | 可选扩展 | P8 | 按需 | 分享 / 导出 / 备份 | ⬜ 待开始 |

> **关键路径**：P0 → P1 → P2 → P3。P2 是整个项目最大风险点，认证打通后剩余功能基本是同一套模式复制。
> P5、P6、P7 在 P3 完成后可并行开发。

---

## 0.1 实际落地版本（2026-09-23 确认）

本机环境探测结果与初版设计的差异，已按下表落地：

| 项 | 初版设计 | 实际落地 | 原因 |
|---|---|---|---|
| JDK | 17 / 21 | **25.0.4.1 LTS** | 本机仅装 JDK 25，不再额外安装 |
| 后端框架 | Spring Boot 3.2 | **3.5.16** | 3.2 官方仅支持到 Java 21，需新版适配 JDK 25 |
| 后端构建 | 本机 Maven | **Maven Wrapper 3.9.16** | 本机无 Maven，wrapper 随仓库提交，团队成员零配置 |
| 中间件 | Docker Compose | **MySQL 8.4.9 免安装版，脚本启停** | 本机无 Docker；`docker-compose.yml` 保留到 P8；Redis / MinIO 分别在 P2 / P5 安装 |
| React | 18 | **19.2** | 脚手架默认版本 |
| UI 组件 | Ant Design 5 | **5.29 + `@ant-design/v5-patch-for-react-19`** | 保留 antd 5 的 API 习惯，用官方补丁适配 React 19 |
| 路由 | React Router v6 | **7.x** | 数据路由 API 与 v6 完全一致 |
| 构建工具 | Vite | **8.3** | 脚手架默认版本 |
| TypeScript | - | **6.0** | `baseUrl` 已弃用，路径别名只使用 `paths` |
| 代码生成 | Lombok | **不使用 Lombok** | JDK 25 下触发 `Unsafe` 告警，且 JDK 26 起预计收紧为 `deny`；改用 record / 显式访问器，详见 P0「遗留警告」 |

**依赖引入节奏**：为避免 Spring Security 默认链拦截健康检查接口、导致 P0 无法验收，后端依赖按阶段引入——
P0 只含 web / validation / lombok / springdoc；MyBatis-Plus + MySQL + Redis 在 P1 引入；Spring Security + JJWT 在 P2 引入。

**已提前到 P0 的能力**：`Result` / `ResultCode` / `BizException` / `GlobalExceptionHandler`
（原计划在 P1），因为 P0 验收要求 `/api/v1/ping` 返回统一响应结构。

---

## P0 · 环境与工程初始化（0.5 天）✅ 已完成（2026-09-23）

> 实际执行结果与验证数据见本阶段文末「完成记录」；与初版设计的版本差异见上文「0.1 实际落地版本」。
> 下文任务清单为初版计划，其中「本机 Maven / Docker Desktop」两项因环境差异改为 Maven Wrapper 与本机直装中间件。

### 目标
建立可运行的前后端空壳工程，统一目录、依赖版本、代码规范、本地开发代理。

### 任务清单

**通用**
- [ ] 确认本机 JDK 17/21、Maven 3.9+、Node 18+、Docker Desktop 已就绪
- [ ] 创建仓库根目录结构：

```text
diary-fullstack-platform/
├── backend/          # Spring Boot 工程
├── frontend/         # Vite + React 工程
├── sql/              # 建表与初始化脚本
├── doc/              # 设计文档与开发方案
├── .env.example      # 环境变量模板
├── docker-compose.yml
├── nginx.conf
└── README.md
```

**后端**
- [ ] 用 Spring Initializr 生成工程：Spring Web / Spring Security / Validation / MyBatis(-Plus) / MySQL Driver / Redis / Lombok
- [ ] 确定版本：Spring Boot 3.2.x、Java 17、MyBatis-Plus 3.5.x、JJWT 0.12.x、SpringDoc 2.x
- [ ] 配置 `application.yml` + `application-dev.yml`，端口 8080，`/api/v1` 为统一前缀
- [ ] 建包结构（`common` / `config` / `security` / `controller` / `service` / `mapper` / `entity` / `dto` / `vo` / `converter`）
- [ ] 接入 SpringDoc，能打开 `/swagger-ui/index.html`

**前端**
- [ ] `npm create vite@latest frontend -- --template react-ts`
- [ ] 安装依赖：`antd`、`react-router-dom`、`zustand`、`@tanstack/react-query`、`axios`、`@uiw/react-md-editor`、`react-markdown`、`rehype-sanitize`、`recharts`
- [ ] 配置路径别名 `@ → src`
- [ ] 配置 `vite.config.ts` 的 `server.proxy`：

```ts
server: {
  port: 5173,
  proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } }
}
```
- [ ] 接入 antd 主题、全局样式重置

**本地中间件**
- [ ] 编写仅含 mysql / redis / minio 的 `docker-compose.dev.yml`，本地开发只跑中间件

### 交付物
- `backend/` 可 `mvn spring-boot:run` 启动，`/swagger-ui/index.html` 可访问
- `frontend/` 可 `npm run dev` 启动，页面渲染正常
- `docker-compose.dev.yml` 一键拉起 MySQL/Redis/MinIO
- `README.md` 写明启动步骤

### 验收标准（DoD）
1. 后端启动无报错，Swagger 页面能列出（空的）接口分组。
2. 前端启动后访问 `http://localhost:5173` 正常渲染 antd 页面。
3. 前端请求 `/api/ping` 能通过 proxy 命中后端并返回 JSON。
4. `.env.example` 与 `.gitignore` 就位，密钥不入库。

### 风险
- 后端/前端代理配置错误导致 404 → 用 `/api/ping` 先验证链路。
- Maven 依赖下载慢 → 提前配置国内镜像。

### 完成记录（2026-09-23）✅

**产出结构**

```text
diary-fullstack-platform/
├── backend/
│   ├── mvnw / mvnw.cmd
│   ├── .mvn/wrapper/maven-wrapper.properties                     # Maven 3.9.16
│   ├── pom.xml                                                   # Spring Boot 3.5.16 + Java 25
│   └── src/main/
│       ├── java/com/example/diary/
│       │   ├── DiaryApplication.java
│       │   ├── common/result/{Result, ResultCode}.java
│       │   ├── common/exception/{BizException, GlobalExceptionHandler}.java
│       │   ├── config/OpenApiConfig.java
│       │   └── controller/PingController.java
│       └── resources/application.yml
├── frontend/
│   ├── vite.config.ts          # @ 别名 + /api 代理到 :8080
│   ├── tsconfig.app.json       # paths 别名（TS 6.0 不用 baseUrl）
│   └── src/
│       ├── api/{request,ping}.ts
│       ├── main.tsx / App.tsx / index.css
├── .gitignore
├── .env.example
└── README.md
```

**验收实测结果**

| 验收项 | 命令 / 方式 | 结果 |
|---|---|---|
| 后端编译 | `.\mvnw.cmd -DskipTests compile` | ✅ BUILD SUCCESS，7 个源文件 |
| 后端启动 + 统一响应 | `GET /api/v1/ping` | ✅ `{"code":0,"message":"ok","data":{...}}` |
| Swagger 可用 | `GET /v3/api-docs` | ✅ OpenAPI 3.1.0，含「健康检查」分组 |
| 前端类型检查 + 构建 | `npm run build` | ✅ 1517 modules，产出 `dist/` |
| 代理链路 | `GET localhost:5173/api/v1/ping` | ✅ 200，与 `:8080` 直连返回一致 |

**遗留警告**

1. ~~Lombok 在 JDK 25 下输出 `sun.misc.Unsafe` 弃用警告~~ → ✅ **已彻底解决（2026-09-23）：移除 Lombok**
   - 根因：Lombok 的 `lombok.permit.Permit` 类为绕过 JDK 模块强封装而调用 `Unsafe.objectFieldOffset`。实测升级到当时最新的 **1.18.48 依然复现**，属 Lombok 未修复项，升级无效。
   - 临时方案（已废弃）：曾在 `backend/.mvn/jvm.config` 写入 `--sun-misc-unsafe-memory-access=allow` 静音告警。该方案只是绕过，且 JDK 26 起此开关默认值预计收紧为 `deny`，届时必然失效。
   - **最终方案**：移除 Lombok 依赖，改用 JDK 原生能力，从此不依赖任何注解处理器：

     | 原用法 | 替代方案 |
     |---|---|
     | `@Getter`（`ResultCode`、`BizException`） | 显式手写访问器 |
     | `@Slf4j`（`GlobalExceptionHandler`） | `private static final Logger log = LoggerFactory.getLogger(X.class)` |
     | `@Data` / `@Builder`（P3 起用于 Entity/DTO/VO） | DTO/VO 用 `record`；Entity 保留显式 setter（MyBatis-Plus 需要无参构造 + setter） |

   - 收益：编译告警归零、`pom.xml` 减少约 30 行配置、编译期不再运行注解处理器、摆脱注解处理器与 JDK 版本的强耦合。
   - 代价：Entity / DTO / VO 需显式书写访问器（P3 按上表策略处理）。
2. 前端单 chunk 772 KB（gzip 245 KB）超 500 KB 阈值 → 交由 P6 路由懒加载分包解决。
3. SpringDoc 提示生产环境应设 `springdoc.swagger-ui.enabled=false` → 交由 P8 处理。
4. 未执行独立 code-reviewer 审查（当前环境无该代理），改用 `read_lints` + 真实启动/构建/网络请求做实证验证。

**下一步（P1）**：编写 `sql/init.sql`，引入 MyBatis-Plus + MySQL + Redis 依赖，落地分页插件、逻辑删除与字段自动填充。

---

## P1 · 数据库与后端基础设施（1 天）✅ 已完成（2026-09-23）

### 目标
建表落地 + 统一响应/异常/分页/自动填充等横切能力，为后续业务开发铺平道路。

### 任务清单

**数据库**
- [ ] 编写 `sql/init.sql`：`t_user` / `t_diary` / `t_tag` / `t_diary_tag` / `t_attachment`（字段、索引、字符集严格按架构文档 5.2）
- [ ] 确认 `utf8mb4` + `utf8mb4_unicode_ci`、`serverTimezone=Asia/Shanghai`
- [ ] 准备一条测试用户数据（密码用 BCrypt 生成）

**后端基础**
- [ ] `Result<T>` / `PageResult<T>` 统一响应体
- [ ] `ResultCode` 枚举（0 成功、400 参数、401 未认证、403 无权限、404 不存在、500 系统）
- [ ] `BizException` + `GlobalExceptionHandler`（业务异常 / 参数校验异常 / 兜底异常）
- [ ] `MybatisPlusConfig`：分页插件 + 逻辑删除（`deleted`）
- [ ] `MyMetaObjectHandler`：`createdAt` / `updatedAt` 自动填充
- [ ] `CorsConfig`（仅 dev 生效）
- [ ] `OpenApiConfig`：JWT Bearer 鉴权按钮
- [ ] `RedisConfig`：`StringRedisTemplate` + JSON 序列化
- [ ] 一个 `/api/v1/ping` 健康检查接口用于联调

### 交付物
- `sql/init.sql` 可在 MySQL 8 直接执行成功
- 统一响应与全局异常生效（可写临时接口验证）
- 分页与自动填充在测试 Mapper 上验证通过

### 验收标准（DoD）
1. 执行 `init.sql` 无报错，5 张表结构、索引与文档一致。
2. 任意接口返回结构统一为 `{code, message, data}`。
3. 抛 `BizException` 时 HTTP 语义正确、`code` 为业务码、日志有记录。
4. 插入一条记录，`created_at` / `updated_at` 自动填充；逻辑删除后查询不出。

### 风险
- MyBatis-Plus 逻辑删除与手写 SQL 冲突 → 自定义 SQL 需手动加 `deleted = 0`。
- 时间字段时区不一致 → 统一 `LocalDateTime` + 连接串时区参数。

### 完成记录（2026-09-23）✅

**中间件落地**

本机无 Docker，按「官方免安装 ZIP + 手动启停」方式部署 MySQL：

| 项 | 值 |
|---|---|
| 版本 | MySQL 8.4.9（官方免安装 ZIP，与 winget 的 `Oracle.MySQL` 同版本） |
| 安装目录 | `D:\business\tools\mysql-8.4.9-winx64` |
| 配置文件 | `D:\business\tools\mysql-8.4.9-winx64\my.ini`（utf8mb4 / `+08:00` / 仅监听 127.0.0.1） |
| 启停脚本 | `D:\business\tools\mysql-start.ps1`、`mysql-stop.ps1`（幂等，可重复执行） |
| 连接信息 | `127.0.0.1:3306`，`root` / `123456`，库名 `diary` |

> Redis 与 MinIO 尚未安装：Redis 在 P2（存 Refresh Token）时安装，MinIO 在 P5（图片上传）时安装。

**新增代码**

| 文件 | 作用 |
|---|---|
| `common/result/PageResult.java` | 分页统一响应体，支持由 `IPage` 转换 |
| `config/MybatisPlusConfig.java` | 分页插件；`maxLimit=50` 防 size 越界，`overflow=false` |
| `config/MyMetaObjectHandler.java` | `createdAt` / `updatedAt` 自动填充 |
| `config/CorsConfig.java` | 仅 `dev` profile 生效，生产由 Nginx 同源代理 |
| `config/RedisConfig.java` | Key 字符串序列化 + Value JSON 序列化 |
| `entity/Diary.java` | 日记实体，含 `@TableLogic` 与 `@TableField(fill)` |
| `mapper/DiaryMapper.java` | 继承 `BaseMapper` |
| `resources/application-dev.yml` | 数据源 / Redis / MyBatis-Plus / SQL 日志 |
| `test/.../DiaryMapperTest.java` | 自动填充 + 逻辑删除验收测试 |
| `sql/init.sql` | 建库 + 5 张表 |

**pom 依赖变化**

新增 `mybatis-plus-spring-boot3-starter`、**`mybatis-plus-jsqlparser`**（3.5.9 起分页插件已拆为独立模块，不引入会 `NoClassDefFoundError`）、`mysql-connector-j`、`spring-boot-starter-data-redis`。

**验收实测结果**

| 验收项 | 方式 | 结果 |
|---|---|---|
| 建表脚本可执行 | `mysql -e "source sql/init.sql"` | ✅ 5 张表创建成功 |
| 字符集 / 时区 | `SELECT @@character_set_database, @@collation_database, @@time_zone` | ✅ `utf8mb4` / `utf8mb4_unicode_ci` / `+08:00` |
| 分页插件加载 | Spring 上下文启动 | ✅ 无 `ClassNotFoundException` |
| 自动填充 | `DiaryMapperTest` 断言 `createdAt` / `updatedAt` 非空 | ✅ |
| 逻辑删除 | `deleteById` 后 `selectById` 返回 null | ✅ |
| 测试整体 | `.\mvnw.cmd test` | ✅ `Tests run: 1, Failures: 0, Errors: 0`，BUILD SUCCESS |

**踩坑记录**

1. **`deleted` 插入后为 null**：MyBatis-Plus 默认 `FieldStrategy.NOT_NULL`，null 字段不写入 SQL，由数据库 `DEFAULT 0` 兜底，但 Java 对象不会回填。解法：实体上 `private Integer deleted = 0;` 显式初始化。
2. **免安装版初始化**：`--initialize-insecure` 生成空密码 root，必须随后 `ALTER USER` 设置密码，否则 JDBC 空密码配合 `caching_sha2_password` 容易踩坑。
3. **PowerShell 只读变量**：脚本中不可使用 `$home`（与内置 `$HOME` 冲突），改用 `$mysqlHome`。

**遗留警告**

1. ~~Mockito 在 JDK 25 下提示 `self-attaching to enable the inline-mock-maker`~~ → ✅ **已解决（2026-09-23）**
   - 根因：Mockito 5.x 默认使用 inline mock maker，运行时通过动态 attach 把自身挂到测试 JVM；JDK 21+ 将该方式标记为即将移除，因此每次跑测试都会打印警告。
   - 解法：在 `pom.xml` 为 surefire 配置显式 javaagent（mockito-core jar 自带 `Premain-Class`），版本号取自 Spring Boot 管理的 `${mockito.version}`，无需手工同步：

     ```xml
     <argLine>-javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar</argLine>
     ```

   - 已确认 Spring Boot 仅管理 surefire **版本**（3.5.6）、未设置 `argLine`，故自定义不会覆盖任何既有配置。
   - 效果：`.\mvnw.cmd test` 输出中该警告消失。
2. ~~测试用户数据推迟到 P2 生成~~ → ✅ **已在 P1 完成（2026-09-23）**
   - `sql/init.sql` 内置账号 `tester` / `123456`，密文 `$2b$10$...`（60 字符）由 Node `bcryptjs` 生成并 `compareSync` 自校验通过；Spring Security 的 `BCryptPasswordEncoder` 支持 `$2b$` 前缀。
   - 使用 `INSERT ... SELECT ... WHERE NOT EXISTS` 保证脚本可重复执行（实测二次执行后 `t_user` 仍为 1 行、表仍为 5 张）。
   - P2 接入 Spring Security 后会补一次 `matches()` 真实性校验。
3. IDE 可能在新增依赖后短暂报 `PaginationInnerInterceptor cannot be resolved`，Maven 已编译通过，重新导入 Maven 项目即可消除。

**下一步（P2）**：安装 Redis；引入 Spring Security + JJWT；打通注册 / 登录 / 刷新 / 登出全链路。

---

## P2 · 认证全链路（2 天）🔥 关键风险点 ✅ 已完成（2026-09-23）

### 目标
打通"注册 → 登录 → 携带 token 访问 → 刷新 → 登出"完整链路，前后端联调通过。

### 任务清单

**后端**
- [ ] `User` Entity + `UserMapper`
- [ ] `AuthController`：`/auth/register`、`/auth/login`、`/auth/refresh`、`/auth/logout`
- [ ] `UserController`：`GET /users/me`、`PUT /users/me`
- [ ] `JwtUtil`：签发/解析 Access Token（30min），携带 `userId`、`jti`
- [ ] Refresh Token：随机串存 Redis，Key `refresh:{userId}:{jti}`，7 天过期
- [ ] `BCryptPasswordEncoder` 加密；注册时用户名唯一校验
- [ ] `UserDetailsServiceImpl` + `LoginUser`
- [ ] `JwtAuthenticationFilter`：解析 Header → 注入 `SecurityContext`
- [ ] `SecurityConfig`：放行 `/auth/**`、`/public/**`、Swagger，其余需认证
- [ ] `SecurityUtil.getCurrentUserId()`
- [ ] `JwtAuthenticationEntryPoint` / `AccessDeniedHandler` 返回统一 401/403 JSON
- [ ] 登出：删除 Redis 中对应 refresh token（支持踢下线）

**前端**
- [ ] `api/request.ts`：Axios 实例 + 请求拦截器注入 token + 响应拦截器解包 + 401 自动刷新重放（按文档 7.2，用 `refreshing` 防并发）
- [ ] `api/auth.ts`：register / login / refresh / logout / getMe
- [ ] `store/useUserStore.ts`（Zustand + persist）：`accessToken`、`user`、`setToken`、`clear`
- [ ] 页面：`Login`、`Register`（antd Form + 校验 + 错误提示）
- [ ] `router/AuthGuard.tsx`：无 token 跳登录
- [ ] `layouts/MainLayout.tsx`：侧边栏 + 顶栏 + Outlet + 用户下拉（登出）
- [ ] `router/index.tsx` 路由骨架（按文档 7.4）
- [ ] `main.tsx` 挂载 `QueryClientProvider`

### 交付物
- 认证 4 个接口 + 用户信息 2 个接口，Swagger 可自测
- 前端登录/注册页可用，登录后进入主布局，刷新页面登录态保持
- 401 自动刷新链路可复现（手动改短 token 过期时间验证）

### 验收标准（DoD）
1. 注册后密码在库中为 BCrypt 密文；重复用户名返回业务错误码而非 500。
2. 登录返回 `accessToken` + `refreshToken`，Redis 中存在 `refresh:{userId}:{jti}`。
3. 携带合法 token 访问 `/users/me` 返回当前用户；不携带返回 401 且为统一 JSON 结构。
4. Access Token 过期后用 refreshToken 静默换新并重放原请求，用户无感知。
5. 登出后 refreshToken 失效，无法再刷新。
6. 前端刷新浏览器后仍处于登录态；未登录访问 `/diaries` 被重定向到 `/login`。

### 风险
- **最大风险阶段**：JWT 过滤器与 Security 链顺序错误会导致全部接口 401 或全部放行。
- 循环依赖：`SecurityConfig` ↔ `UserDetailsService` → 用构造注入 + `@Lazy` 或拆分配置。
- CORS 与 Security 预检冲突 → 开发用 Vite proxy 同源，不要散写 `@CrossOrigin`。
- 前后端字段名必须严格对齐（如 `accessToken` vs `access_token`），先冻结接口契约再开发。

### 完成记录（2026-09-23）✅

**中间件落地**

| 项 | 值 |
|---|---|
| 版本 | Redis 5.0.14.1（tporadowski Windows 移植版，绿色免安装） |
| 安装目录 | `D:\business\tools\redis-5.0.14.1` |
| 配置文件 | `redis-diary.conf`（bind 127.0.0.1 / RDB 持久化 / 256MB 上限 / 不设密码） |
| 启停脚本 | `D:\business\tools\redis-start.ps1`、`redis-stop.ps1` |
| 连接信息 | `127.0.0.1:6379`，无密码 |

> **踩坑**：Windows PowerShell 5.1 读取无 BOM 的 UTF-8 `.ps1` 会按 GBK 解码，中文提示变成乱码并直接导致脚本解析失败。**所有 .ps1 必须保存为 UTF-8 with BOM**（本项目 4 个脚本已统一转换）。

**新增代码**

后端：

| 文件 | 作用 |
|---|---|
| `common/util/JwtUtil.java` | JWT 签发/解析；返回 `IssuedToken(token, jti, expiresIn)`，避免调用方为拿 jti 再解析一次 |
| `common/util/SecurityUtil.java` | 从 SecurityContext 读取当前用户，业务层数据隔离的唯一入口 |
| `security/LoginUser.java` | 存入 SecurityContext 的当前用户（record） |
| `security/JwtAuthenticationFilter.java` | 解析 Bearer token；**失败时不直接响应**，放行给 EntryPoint，从而兼容匿名接口 |
| `security/JwtAuthenticationEntryPoint.java` | 401 统一 JSON |
| `security/AccessDeniedHandlerImpl.java` | 403 统一 JSON |
| `config/SecurityConfig.java` | 无状态过滤链 + CORS + BCryptPasswordEncoder |
| `entity/User.java`、`mapper/UserMapper.java` | 用户表映射 |
| `dto/{Register,Login,RefreshToken,UpdateProfile}DTO.java` | 入参，全部为 record + Jakarta Validation |
| `vo/{UserVO,TokenVO}.java` | 出参，UserVO 刻意不含 password |
| `service/RefreshTokenStore.java` | Redis 存取 Refresh Token，Key 为 `diary:refresh:{userId}:{jti}` |
| `service/{Auth,User}Service.java` + `impl/` | 认证与用户业务 |
| `controller/{Auth,User}Controller.java` | 6 个接口 |
| `config/OpenApiConfig.java` | 增加 JWT Bearer 鉴权入口 |

前端：

| 文件 | 作用 |
|---|---|
| `api/request.ts` | 注入 token；401 静默刷新并重放原请求；`refreshing` 并发去重；刷新使用独立 axios 实例，避免递归与循环依赖 |
| `api/auth.ts` | 认证接口与类型定义 |
| `store/useUserStore.ts` | Zustand + persist 持久化登录态 |
| `pages/Login/`、`pages/Register/` | 登录 / 注册页（仅 dev 环境提示测试账号） |
| `layouts/MainLayout.tsx` | 侧边栏 + 顶栏 + 用户下拉 + 登出 |
| `router/index.tsx`、`router/AuthGuard.tsx` | 路由表与登录守卫（记录来源路径，登录后回跳） |
| `pages/DiaryList/`、`pages/NotFound/` | 落地页占位与 404 |

**设计取舍（与架构文档的差异）**

1. **未引入 `UserDetailsService` / `AuthenticationManager`**：登录直接在 `AuthServiceImpl` 中查库 + `BCryptPasswordEncoder.matches` 校验。原设计多一层间接，对「用户名 + 密码」场景无收益。
2. **CORS 由 SecurityConfig 统一声明**，删除了 P1 的 `config/CorsConfig.java`。两处同时配置会产生重复的 `Access-Control-Allow-Origin` 响应头。
3. **修正 P0 遗留问题**：`GlobalExceptionHandler` 此前所有异常都返回 HTTP 200，不符合架构文档 4.3「HTTP 状态码保持语义正确」。已改为按业务码映射 HTTP 状态（400/401/403/404，其余仍 200）。

**验收实测结果**

| 验收项 | 方式 | 结果 |
|---|---|---|
| 单元测试 | `.\mvnw.cmd test` | ✅ 3 个测试全通过 |
| 内置账号 BCrypt 校验 | `AuthServiceTest.builtinTesterAccountCanLogin` | ✅ 证明 Node `bcryptjs` 生成的 `$2b$` 密文可被 Spring Security 校验 |
| 放行规则 | `GET /api/v1/ping` 无 token | ✅ HTTP 200 |
| 未认证访问 | `GET /api/v1/users/me` 无 token | ✅ HTTP 401 + `{"code":401,"message":"未认证或登录已失效"}` |
| 登录 | `POST /api/v1/auth/login` | ✅ `code=0`，返回 244 字符 accessToken 与 refreshToken |
| 鉴权访问 | 带 token 的 `GET /api/v1/users/me` | ✅ `code=0`，`nickname=测试用户` |
| 错误密码 | `POST /api/v1/auth/login` | ✅ HTTP 400 + `{"code":400,"message":"用户名或密码错误"}` |
| 令牌轮换 | 连续两次 `POST /api/v1/auth/refresh` | ✅ 第一次成功且轮换，第二次（旧令牌）返回 401 |
| 前端代理链路 | 经 `localhost:5173` 调用登录与 `/users/me` | ✅ 均 `code=0` |
| 前端构建 | `npm run build` | ✅ 3204 modules，无类型错误 |

**遗留事项**

1. 浏览器 UI 交互（登录后刷新保持登录、401 无感刷新、路由守卫跳转）尚未自动化验证，需人工确认或后续引入浏览器自动化工具。
2. 前端单 chunk 已达 1.0 MB（gzip 320 KB）→ 交由 P6 路由懒加载处理。
3. Refresh Token 的「踢下线」使用 `KEYS` 匹配，属低频管理操作；并发量上升后应改 `SCAN`。

**下一步（P3）**：日记 CRUD —— DTO/VO/Service/Controller + 前端列表页、详情页、编辑页。

---

## P3 · 日记 CRUD（2 天）

### 目标
日记的新增、编辑、删除、分页列表、详情全部可用，前端列表页与编辑页跑通。

### 任务清单

**后端**
- [ ] `Diary` Entity、`DiaryMapper`
- [ ] `DiaryCreateDTO` / `DiaryUpdateDTO` / `DiaryQueryDTO`（page、size、keyword、tagId、mood、startDate、endDate、sort）
- [ ] `DiaryVO`（列表）与 `DiaryDetailVO`（详情，含 tags、attachments）
- [ ] `DiaryService` 接口 + `DiaryServiceImpl`
  - 创建：自动生成 `summary`（正文前 N 字，去 Markdown 标记）
  - 列表：**显式 select 字段，不查 LONGTEXT**；强制 `user_id` 条件；按 `diary_date desc`
  - 详情：`id + user_id` 双条件，查不到抛 404
  - 更新 / 删除：先校验归属，删除用逻辑删除
  - 标签关联：事务内写 `t_diary_tag`
- [ ] `MapStruct` Converter：Entity ↔ VO
- [ ] 参数校验注解（`@NotBlank`、`@Size`、`@NotNull`）+ 统一错误提示
- [ ] `@Transactional` 用在哪：写操作（日记 + 标签关联）

**前端**
- [ ] `types/diary.d.ts`：`Diary`、`DiaryDetail`、`DiaryQuery`、`PageResult<T>`
- [ ] `api/diary.ts`：list / getById / create / update / remove
- [ ] `hooks/useDiaryList.ts`：TanStack Query 查询 + 增删改 mutation + `invalidateQueries`
- [ ] `components/DiaryCard/`：卡片展示（标题、摘要、日期、心情、标签）
- [ ] `pages/DiaryList/`：卡片/时间轴切换、分页、空状态、加载骨架
- [ ] `pages/DiaryDetail/`：详情展示 + 编辑/删除入口
- [ ] `pages/DiaryEdit/`：新建/编辑复用同一页（按 `:id` 区分）、表单校验、提交后跳转
- [ ] 删除二次确认（`Modal.confirm`）

### 交付物
- 日记 5 个接口 + 前端列表/详情/编辑三个页面
- 从"新建 → 列表可见 → 详情 → 编辑 → 删除"完整操作闭环

### 验收标准（DoD）
1. 列表接口返回分页结构 `{total, pages, current, records}`，SQL 中不含 `content` 字段。
2. 用 A 用户 token 访问 B 用户的日记 id，返回 404（不是 403 也不是数据泄露）。
3. `diary_date` 可指定为历史日期，列表按该字段排序。
4. 删除为逻辑删除（库中 `deleted=1`），列表不再出现。
5. `summary` 自动生成且不含 Markdown 符号（如 `#`、`**`）。
6. 前端新增后列表自动刷新（Query 缓存失效生效）。

### 风险
- 列表查 `SELECT *` 拖慢响应 → 强制显式列。
- 分页参数越界 → 后端限制 `size` 上限（如 ≤ 50）。
- 编辑页表单与详情数据回填异步竞态 → 用 `enabled: !!id` 控制查询。

---

## P4 · 标签与筛选（1.5 天）

### 目标
标签可管理，日记列表支持关键词、标签、心情、日期区间多维筛选。

### 任务清单

**后端**
- [ ] `Tag` Entity + `TagMapper`（依赖 `t_diary_tag` 做关联查询）
- [ ] `TagController`：列表 / 新建 / 修改 / 删除
- [ ] 标签重名校验（`uk_user_name`）+ 删除时清理关联表
- [ ] 日记列表补充筛选条件：`keyword`（标题 + 摘要 LIKE）、`tagId`（EXISTS 子查询）、`mood`、`startDate/endDate`
- [ ] 列表 VO 带 `tags` 数组（避免 N+1：先批量查关联再内存拼装）
- [ ] 自定义 XML SQL（复杂筛选用 MyBatis XML 而非 LambdaWrapper）

**前端**
- [ ] `api/tag.ts` + `hooks/useTags.ts`
- [ ] `components/TagSelect/`：多选 + 可创建 + 颜色
- [ ] `components/MoodPicker/`：5 种心情（开心/平静/难过/焦虑/生气）图标选择器
- [ ] 列表页筛选区：关键词搜索框（`useDebounce` 300ms）、标签筛选、心情筛选、日期范围（`RangePicker`）
- [ ] 筛选条件同步到 URL query（刷新/分享保持状态）
- [ ] 标签管理入口（Profile 页或独立弹窗）

### 交付物
- 标签 4 个接口 + 筛选 UI 完整可用
- 多条件组合筛选结果正确

### 验收标准（DoD）
1. 同一用户标签不可重名；不同用户可用同名标签。
2. 关键词为空、标签为空时不产生多余 SQL 条件（动态 `<if>`）。
3. 组合筛选（关键词 + 标签 + 心情 + 日期区间）结果准确。
4. 列表页刷新后筛选条件不丢失。
5. 50 条日记列表接口 P95 < 300ms（本地环境）。

### 风险
- 多表关联 N+1 → 批量查询后内存拼装。
- 标签删除后残留脏关联 → 事务内双删。

---

## P5 · 图片上传 MinIO（1.5 天）

### 目标
支持图片上传到 MinIO，返回可访问 URL，Markdown 编辑器可插图。

### 任务清单

**后端**
- [ ] 引入 MinIO SDK，配置 `MinioClient` Bean
- [ ] 启动时检查/创建 bucket，设置读策略
- [ ] `FileController`：`POST /files/upload`（`MultipartFile`）
- [ ] 校验：白名单 MIME（jpg/png/gif/webp）、大小上限（如 5MB）
- [ ] 随机文件名（UUID + 后缀），按日期分目录，防路径穿越
- [ ] `t_attachment` 入库（user_id、url、filename、size、mime_type）
- [ ] 统一异常码：文件过大、类型不支持

**前端**
- [ ] `api/file.ts`：upload（`FormData`）
- [ ] `components/MarkdownEditor/`：封装 `@uiw/react-md-editor`
  - 支持拖拽上传图片
  - 上传成功自动插入 `![](url)`
  - 上传中 loading、失败提示
- [ ] `pages/DiaryEdit/` 接入编辑器
- [ ] `pages/DiaryDetail/` 用 `react-markdown` + `rehype-sanitize` 渲染

### 交付物
- 上传接口 + Markdown 编辑器 + Markdown 渲染

### 验收标准（DoD）
1. 上传 jpg/png 成功返回 URL，浏览器可直接打开。
2. 上传 exe/超大文件被拒绝并返回友好提示。
3. 编辑器拖拽上传后图片自动插入正文，保存后详情页正常渲染。
4. 恶意内容（`<script>`、`onerror`）在详情页被 `rehype-sanitize` 过滤。
5. 上传记录在 `t_attachment` 中可查。

### 风险
- MinIO 内网地址 `http://minio:9000` 浏览器不可达 → 配置对外 endpoint（dev 用 `localhost:9000`）。
- 图片过大影响加载 → 后续可加压缩/缩略图。
- 跨域直传 → 单机方案走后端中转即可，不开放 MinIO 直传。

---

## P6 · 前端体验完善（1 天，可与 P5/P7 并行）

### 目标
补齐加载态、空态、错误态、主题、响应式等体验细节。

### 任务清单
- [ ] 全局 Loading / 骨架屏（列表、详情）
- [ ] 空状态（无日记、无标签、无筛选结果）
- [ ] 错误边界 + 网络异常统一提示
- [ ] `useThemeStore`：亮色/暗色切换 + antd ConfigProvider
- [ ] 主布局响应式（窄屏侧边栏折叠）
- [ ] `utils/date.ts`：相对时间（"3 天前"）、日期格式化
- [ ] 路由懒加载（`React.lazy` + `Suspense`）
- [ ] 404 页面
- [ ] 表单草稿保护（编辑未保存离开前提示）

### 验收标准（DoD）
1. 首屏路由懒加载生效，构建产物分包合理。
2. 断网/后端未启动时页面不白屏，有明确错误提示。
3. 暗色主题下所有页面可读。
4. 未保存直接关闭编辑页有二次确认。

---

## P7 · 统计页（1.5 天）

### 目标
提供写作热力图、心情分布、连续打卡天数等可视化统计。

### 任务清单

**后端**
- [ ] `StatsController`：`GET /stats/calendar`、`GET /stats/overview`
- [ ] `calendar`：按年/月返回 `{date, count}`，仅统计当前用户、未删除
- [ ] `overview`：总篇数、本月篇数、最长连续天数、当前连续天数、心情分布 `{mood, count}`
- [ ] 统计 SQL 走 `idx_user_date` / `idx_user_mood` 索引
- [ ] 热点统计结果可缓存 Redis（可选）

**前端**
- [ ] `api/stats.ts` + `hooks/useStats.ts`
- [ ] `pages/Stats/`：
  - GitHub 风格热力图（日历格子，按 count 深浅着色）
  - 心情分布饼图 / 环形图（Recharts 或 ECharts）
  - 数字卡片：总篇数、当前连续、最长连续
- [ ] 年份切换、月份下钻（可选）

### 验收标准（DoD）
1. 热力图日期与日记 `diary_date` 完全对应，补写历史日期也能体现。
2. 心情分布各段之和等于总篇数。
3. 连续天数计算正确（跨月、跨年边界正确）。
4. 统计仅包含当前登录用户数据。
5. 1000 条数据下统计接口 P95 < 500ms。

### 风险
- 连续天数算法边界（当天没写但昨天写了，当前连续应为 0 还是延续）→ 需明确产品定义并写单测。
- 时区导致跨日统计偏移 → 统一以 `diary_date` 为准。

---

## P8 · 部署与上线（1.5 天）

### 目标
Docker Compose 一键启动全栈，Nginx 提供静态资源与反向代理。

### 任务清单
- [ ] 后端 `Dockerfile`（多阶段构建，JRE 基础镜像）
- [ ] `docker-compose.yml`：mysql / redis / minio / backend / nginx
- [ ] `nginx.conf`：`try_files $uri $uri/ /index.html` + `/api/` 反代
- [ ] `.env` 管理密钥（DB/JWT/MinIO），`.env.example` 入库、`.env` 忽略
- [ ] 前端构建产物挂载到 Nginx
- [ ] 配置 `SPRING_PROFILES_ACTIVE=prod`，关闭 dev CORS
- [ ] MySQL 备份脚本（每日 dump，保留 7 天）+ Windows/Linux 定时任务
- [ ] 冒烟测试清单（登录、CRUD、上传、统计）
- [ ] （可选）HTTPS 证书 + 443 配置

### 验收标准（DoD）
1. 全新机器 `docker compose up -d` 后，`http://<host>` 可直接注册登录使用。
2. 刷新任意前端路由不 404。
3. `/api/**` 正确代理到后端，无跨域报错。
4. 容器重启后数据不丢失（volume 生效）。
5. 备份脚本能生成 dump 文件并自动清理过期文件。

### 风险
- 前端构建时 `baseURL` 与生产路径不一致 → 统一使用相对 `/api/v1`。
- 容器启动顺序依赖 → `depends_on` + 后端重试连接（或 healthcheck）。
- MinIO 对外 endpoint 与内网 endpoint 区分。

---

## P9 · 可选扩展（按需）

| 方向 | 内容 | 前置 |
|---|---|---|
| 公开分享 | `is_public` 生效，`/public/diaries/{token}` 免登录访问 | P3 |
| 导出 | 导出 Markdown / PDF（单篇 + 批量） | P3 |
| 全文搜索 | MySQL FULLTEXT 或接入 Elasticsearch | P4 |
| 数据备份 | 用户自助导出 JSON，管理员定时备份 | P8 |
| 多端同步 | Refresh Token 多设备管理、登录设备列表 | P2 |
| 移动端适配 | 响应式优化 或 接入 PWA | P6 |

---

## 附录 A · 阶段检查表（复制到 Issue 使用）

```text
[x] P0 环境与工程初始化
[x] P1 数据库与后端基础设施
[x] P2 认证全链路          ← 关键风险
[ ] P3 日记 CRUD
[ ] P4 标签与筛选
[ ] P5 图片上传 MinIO
[ ] P6 前端体验完善
[ ] P7 统计页
[ ] P8 部署与上线
[ ] P9 可选扩展
```

## 附录 B · 每个阶段的固定动作

1. 开工前：确认前置阶段 DoD 全部满足。
2. 开发中：接口先定契约（DTO/VO 字段名冻结）再并行前后端。
3. 收尾：跑完整冒烟用例 → 更新 README → 打 Git tag。
4. 每阶段至少补一次自测记录（接口返回值截图或 curl 输出）。
5. 阶段完成后回填本方案：总览表状态列、该阶段的「完成记录」、附录 A 勾选。

## 附录 C · 建议时间线（1 人全职）

| 周 | 内容 |
|---|---|
| 第 1 周 | P0 + P1 + P2（认证打通，风险出清） |
| 第 2 周 | P3 + P4（核心业务闭环） |
| 第 3 周 | P5 + P6 + P7（附件、体验、统计） |
| 第 4 周 | P8 + 缓冲（部署、修复、文档） |
