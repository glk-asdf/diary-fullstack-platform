# 日记全栈平台 · 开发实录

> 记录日期：2026-09-23
> 读者：接手本项目的开发者
> 定位：**过程记录**——讲清「为什么这么选」「当时怎么发现的问题」「凭什么判定已修好」

**相关文档分工**

| 文档 | 内容 |
|---|---|
| `deepseek_markdown_20260923_c1afe0.md` | 架构设计（最初的方案，仍是设计的源头） |
| `development-plan.md` | 分步开发方案：每个阶段的目标 / 任务 / 验收标准 / 风险 + 完成记录 |
| **本文** | 开发过程实录：决策依据、问题发现与修复的来龙去脉、验证方法 |
| `usage-guide.md` | 使用手册：部署、日常使用、备份恢复、故障排查 |
| `README.md` | 项目入口：技术栈、目录结构、环境要求、命令速查 |

---

## 1. 结论先行

项目按 **P0–P8 八个阶段**推进，全部完成并通过验收：

| 指标 | 结果 |
|---|---|
| 后端单元测试 | 17 个全通过 |
| 部署冒烟测试 | 17 项断言全通过 |
| 前端构建 | 通过，主 chunk 474 KB（gzip 148 KB） |
| 迭代 | 5 个容器全部 healthy，`http://localhost` 可注册登录使用 |

过程中发生 **3 处计划偏离**、修复 **7 个真实缺陷/告警**。其中最有价值的三个教训：

1. **「显示正常」可能是损坏的证据**——容器 MySQL 把中文写成了双重编码，而因为读回时字节反向抵消，界面显示反而是对的（详见 P8 · 缺陷 6）。
2. **交付物本身会制造故障**——部署文档推荐的 `docker compose up -d --build` 会导致 nginx 持续 502，因为 nginx 缓存了上游 IP（详见 P8 · 缺陷 5）。
3. **改错地方之前先确认环境**——一次测试全挂被误判为刚改的代码有问题，实际是重启后本机中间件没启动（详见 P8 · 踩坑 1）。

---

## 2. 起点：环境探测与三个决策

### 2.1 探测结果

| 项 | 状态 |
|---|---|
| JDK | ✅ 25.0.4.1 LTS（在 PATH 中，但 `JAVA_HOME` 未设置） |
| Node / npm | ✅ 22.21.0 / 10.9.4 |
| Git | ✅ 2.51.0 |
| Maven | ❌ 未安装（无 `~/.m2`） |
| Docker | ❌ 未安装（无 Docker Desktop） |
| winget | ✅ 可用 |

### 2.2 三个决策

| # | 问题 | 选项 | 选定 | 理由 |
|---|---|---|---|---|
| 1 | 本机无 Docker，中间件怎么跑 | 装 Docker Desktop / 本机直装 / 先只写代码 | **本机直装** | 当时不想为中间件引入 Docker 依赖；代价是每个中间件都要写启停脚本，且重启后需手动启动 |
| 2 | JDK 25 与文档要求的 17/21 不一致 | 装 JDK 21 / 用 JDK 25 + Spring Boot 3.5.x / JDK 25 编译到 17 | **JDK 25 + Spring Boot 3.5.x** | 不额外装 JDK；代价是偏离文档版本，且撞上 Lombok 的 `Unsafe` 问题（见 3.2） |
| 3 | Maven 未安装 | 装系统级 Maven / Maven Wrapper | **Maven Wrapper** | 随仓库提交，团队成员零配置 |

> 决策 1 在 P8 被推翻：容器化部署最终引入了 Docker。本机直装的中间件保留为**本地开发**用途，两套并存且不冲突（端口规划见 `usage-guide.md`）。

### 2.3 版本落地

| 层 | 初版设计 | 实际落地 | 原因 |
|---|---|---|---|
| JDK | 17 / 21 | **25.0.4.1 LTS** | 本机仅装 JDK 25 |
| 后端 | Spring Boot 3.2 | **3.5.16** | 3.2 官方仅支持到 Java 21 |
| 后端构建 | 本机 Maven | **Maven Wrapper 3.9.16** | 本机无 Maven |
| 中间件 | Docker Compose | **本机直装**（P8 起容器化并行） | 本机无 Docker |
| React | 18 | **19.2** | 脚手架默认 |
| UI 组件 | Ant Design 5 | **5.29 + `@ant-design/v5-patch-for-react-19`** | 保留 antd 5 的 API 习惯 |
| 路由 | React Router v6 | **7.x** | 数据路由 API 与 v6 一致 |
| 构建工具 | Vite | **8.3** | 脚手架默认 |
| TypeScript | — | **6.0** | `baseUrl` 已弃用，路径别名只用 `paths` |
| 代码生成 | Lombok | **不使用** | JDK 25 下触发 `Unsafe` 告警（见 3.2） |

---

## 3. P0 · 环境与工程初始化

### 3.1 实际做法

- 前端用 `npm create vite@latest frontend -- --template react-ts` 脚手架，再补齐依赖（309 个包，0 漏洞）
- 后端手写 `pom.xml`，下载 `maven-wrapper-distribution 3.3.4` 的 **only-script** 包
- 横切能力（`Result` / `ResultCode` / `BizException` / `GlobalExceptionHandler`）从 P1 提前到 P0，因为 P0 的验收就要求 `/api/v1/ping` 返回统一结构

### 3.2 关键决策：按阶段引入依赖

**P0 刻意不引入 Spring Security**：它的默认过滤链会拦截 `/api/v1/ping` 返回 401，直接导致 P0 无法验收。

| 依赖 | 引入阶段 |
|---|---|
| web / validation / springdoc | P0 |
| MyBatis-Plus / MySQL / Redis | P1 |
| Spring Security / JJWT | P2 |

### 3.3 踩坑

1. **Maven Wrapper 的 only-script 包不含 `maven-wrapper.properties`**：解压后只有 `mvnw` / `mvnw.cmd`，需自己手写 `.mvn/wrapper/maven-wrapper.properties` 指定 Maven 3.9.16 的分发地址。
2. **TypeScript 6.0 弃用 `baseUrl`**：`paths` 自 TS 4.1 起可独立使用，删掉 `baseUrl` 即可。

### 3.4 Lombok 的 `Unsafe` 告警 —— 最终选择移除

**现象**：JDK 25 下每次编译固定打印 4 行 `sun.misc.Unsafe` 弃用警告。

**排查**：

- Lombok 1.18.46（Spring Boot 3.5.16 管理）
- 升级到当时最新的 **1.18.48，警告依然复现** → 确认是 Lombok 自身未修复，升级这条路走不通
- 根因：Lombok 的 `lombok.permit.Permit` 为绕过 JDK 模块强封装，调用 `Unsafe.objectFieldOffset`；JEP 471（JDK 23）将该 API 标记为 terminally deprecated

**两步处理**：

| 步骤 | 做法 | 结局 |
|---|---|---|
| 临时 | 在 `backend/.mvn/jvm.config` 写 `--sun-misc-unsafe-memory-access=allow` | 告警消失，但只是**静音不是修复**；JDK 26 起该开关默认预计收紧为 `deny`，届时必然失效 |
| **最终** | **彻底移除 Lombok** | 编译告警归零，`pom.xml` 102 → 74 行 |

**移除后的替代策略**（对后续阶段有约束力）：

| 原用法 | 替代 |
|---|---|
| `@Getter`（`ResultCode`、`BizException`） | 显式手写访问器 |
| `@Slf4j` | `private static final Logger log = LoggerFactory.getLogger(X.class)` |
| `@Data` / `@Builder`（Entity / DTO / VO） | **DTO / VO 用 `record`**；**Entity 保留显式 setter**（MyBatis-Plus 需要无参构造 + setter 完成 ResultSet 映射） |

**副产品约束**：P3 若引入 MapStruct，必须在 `pom.xml` 的 `maven-compiler-plugin` 配 `annotationProcessorPaths`——JDK 23 起 javac 不再从 classpath 隐式发现注解处理器。最终 P3 选择了手写转换，未引入 MapStruct（见 5.3）。

**验证**：`mvnw clean compile` BUILD SUCCESS 且 **0 WARNING**；全仓搜索 `lombok` **0 命中**；实际启动后 `/api/v1/ping` 返回 200，运行期 stderr 完全为空。

### 3.5 验收证据

| 项 | 结果 |
|---|---|
| 后端编译 | ✅ BUILD SUCCESS，7 个源文件 |
| 后端启动 + 统一响应 | ✅ `{"code":0,"message":"ok","data":{...}}` |
| Swagger | ✅ `/v3/api-docs` 返回 OpenAPI 3.1.0，含「健康检查」分组 |
| 前端构建 | ✅ `tsc -b && vite build` 通过，1517 modules |
| 代理链路 | ✅ `localhost:5173/api/v1/ping` 与 `:8080` 直连返回一致 |

---

## 4. P1 · 数据库与后端基础设施

### 4.1 中间件落地

按决策 1 用**官方免安装 ZIP** 部署 MySQL 8.4.9（267 MB，下载 89s，解压 4s）：

| 项 | 值 |
|---|---|
| 目录 | `D:\business\tools\mysql-8.4.9-winx64` |
| 配置 | `my.ini`：`utf8mb4` / `utf8mb4_unicode_ci` / `+08:00` / 仅监听 `127.0.0.1` |
| 启停 | `mysql-start.ps1`、`mysql-stop.ps1`（幂等，可重复执行） |
| 连接 | `127.0.0.1:3306`，`root` / `123456`，库 `diary` |

### 4.2 踩坑

1. **`--initialize-insecure` 生成的是空密码 root**，必须紧跟 `ALTER USER` 设置密码，否则 JDBC 空密码配合 `caching_sha2_password` 容易踩坑。
2. **PowerShell `$home` 是只读变量**（与内置 `$HOME` 冲突），脚本里改用 `$mysqlHome`。
3. **`deleted` 字段插入后为 `null`**：MyBatis-Plus 默认 `FieldStrategy.NOT_NULL`，null 字段不写入 SQL，由数据库 `DEFAULT 0` 兜底——但 **Java 对象不会回填**，测试断言直接失败。解法：实体上显式 `private Integer deleted = 0;`。
4. **测试用户数据的前置坑**：`deleted` 之外，`INSERT ... SELECT ... WHERE NOT EXISTS` 保证 `init.sql` 可重复执行（实测二次执行后 `t_user` 仍 1 行、表仍 5 张）。

### 4.3 关键依赖：`mybatis-plus-jsqlparser`

MyBatis-Plus **3.5.9 起把分页插件的 JSqlParser 依赖拆成了独立模块**，不显式引入会在运行期 `NoClassDefFoundError`。这是纯文档难以发现的坑，属于必须实测才能确认的一类。

### 4.4 Mockito self-attaching 告警

**现象**：JDK 25 下每次跑测试打印 `Mockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK.`

**根因**：Mockito 5.x 默认 inline mock maker，运行时通过动态 attach 把自己挂到测试 JVM；JDK 21（JEP 451）起发出警告，未来计划默认禁止。

**处理**：在 `pom.xml` 给 surefire 配显式 javaagent（`mockito-core` jar 自带 `Premain-Class`）：

```xml
<argLine>-javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar</argLine>
```

三个设计要点：

1. agent 在 JVM **启动时**就绪，Mockito 检测到已挂载便不再 self-attach
2. 版本用 `${mockito.version}`（Spring Boot 管理），升级自动跟随
3. **动手前先确认**：Spring Boot 3.5.16 只管理 surefire 的**版本**（3.5.6），未设置 `argLine`，因此自定义不会覆盖既有配置

**验证**：`mvnw test` 输出中该警告消失。

---

## 5. P2–P6 · 业务功能

### 5.1 P2 · 认证全链路（最大风险点）

**中间件**：Redis 5.0.14.1（tporadowski Windows 移植版，绿色免安装）。之所以不用 winget 的 `Redis.Redis`——那只有 3.0.504，是微软 2016 年归档的版本。

**核心设计**

| 决策 | 理由 |
|---|---|
| **未引入 `UserDetailsService` / `AuthenticationManager`** | 登录直接在 `AuthServiceImpl` 里查库 + `BCryptPasswordEncoder.matches`。原设计多一层间接，对「用户名 + 密码」场景无收益 |
| **CORS 由 `SecurityConfig` 统一声明**，删除 P1 的 `CorsConfig.java` | 两处同时配置会产生重复的 `Access-Control-Allow-Origin` 响应头 |
| **`JwtAuthenticationFilter` 解析失败时不直接响应**，放行给 `EntryPoint` | 从而兼容匿名接口，否则匿名请求会被过滤器拦掉 |
| **`JwtUtil` 返回 `IssuedToken(token, jti, expiresIn)`** | 避免调用方为了拿 `jti` 再解析一次 token |
| **Refresh Token 轮换** | 每次刷新旧令牌立即失效，防止被重复使用 |

**顺带修掉 P0 的遗留问题**：`GlobalExceptionHandler` 此前所有异常都返回 HTTP 200，不符合架构文档「HTTP 状态码保持语义正确」。已改为按业务码映射（400/401/403/404，其余仍 200）。

**踩坑**：**所有 `.ps1` 必须保存为 UTF-8 with BOM**。Windows PowerShell 5.1 会把无 BOM 的 UTF-8 按 GBK 解码，中文提示变乱码并直接导致脚本解析失败。（本项目 4 个脚本已统一转换；P8 新增的两个脚本也因同类问题返工过一次。）

**验证**：3 个单元测试通过；放行/拦截/登录/鉴权/错误密码/令牌轮换均实测；**Node `bcryptjs` 生成的 `$2b$` 密文可被 Spring Security 校验**（这是 P1 留下唯一未在 Java 侧证实的环节，此处补上）。

### 5.2 P3 · 日记 CRUD

**核心设计**

| 决策 | 理由 |
|---|---|
| **列表查询显式 `select()` 裁剪字段**，绝不读取 LONGTEXT 的 `content` | 列表接口拖全文字段会显著放大 IO |
| **`size` 上限 50**（`DiaryQueryDTO.sizeOrDefault()`） | 防 size 越界拖垮数据库 |
| **标签装配避免 N+1** | 先按 `diary_id IN (...)` 查关联，再按 `tag_id IN (...)` 取标签，最后内存分组 |
| **越权一律返回 404 而非 403** | 避免通过状态码探测他人资源是否存在 |
| **删除日记时保留关联表记录** | 日记是逻辑删除，保留关联便于日后做回收站恢复 |
| **`DiaryCreateDTO` 与 `DiaryUpdateDTO` 合并为 `DiarySaveDTO`** | 两者字段完全一致（都是全量提交），拆两份只是重复 |
| **不使用 MapStruct**，手写 `converter/*` 静态方法 | 转换点少且字段固定；与「不用 Lombok、减少编译期魔法」的取向一致 |

**踩坑**

1. **record 的访问器是 `id()` 而不是 `getId()`**：测试里写 `DiaryVO::getId` 直接编译失败。
2. **PowerShell 5.1 的 `Invoke-RestMethod` 会写坏中文**：未在 `ContentType` 指定 charset 时按 Latin-1 编码请求体，中文全部变成 `?` 落库。**这不是应用问题**（curl 与浏览器均正常）。修法是传 `[System.Text.Encoding]::UTF8.GetBytes($json)` 作为 body。后续用 PowerShell 做接口验证都沿用了这个写法（P8 的冒烟脚本也建立在它之上）。
3. 后端 jar 正在运行时无法重新 `package`（文件被占用），需先停进程。

### 5.3 P4 · 标签与筛选

| 决策 | 理由 |
|---|---|
| **筛选条件以 URL 为唯一数据源** | 刷新或分享链接后筛选状态可复原。搜索框用本地 state + 300ms 防抖后回写 URL——既不逐字触发请求，也让 URL 成为单一真相来源 |
| **标签删除：事务内先解除关联再删除** | 与 P3「删除日记时保留关联」互补——标签是主数据，删除后保留关联只会产生指向空标签的脏数据 |
| **`TagSelect` 支持下拉内直接创建** | 创建后自动选中，省掉一次页面跳转 |

**验证**：跨用户同名标签可共存且互不可见；越权改/删他人标签返回 404。

### 5.4 P5 · 图片上传 MinIO

**存储选型被迫变更（重要）**：执行前探测发现 **MinIO 开源版已被官方归档**——

```text
HTTP/1.1 410 Gone
The open-source MinIO Server, MinIO Client (mc) and MinIO KES projects are
archived and no longer maintained. MinIO does not provide product support,
security updates, or security advisories for them...
```

官方已转向商业产品 AIStor，社区版不再提供下载、安全更新与漏洞响应。确认后采用**归档版** `RELEASE.2025-07-23T15-54-02Z`（国内镜像 112 MB）。

> 这是本项目最大的**外部风险**：归档版无安全更新，只适合本地 / 内网。公网部署应换 SeaweedFS 或云厂商 OSS——两者都能通过 AWS S3 SDK 平滑替换，代码改动集中在 `MinioConfig` / `FileServiceImpl`。

**核心设计**

| 决策 | 理由 |
|---|---|
| **`endpoint` 与 `publicEndpoint` 分离** | 后端访问地址与浏览器访问地址解耦。容器部署时前者 `http://minio:9000`、后者形如 `/files`，无需改代码——这正是原方案风险清单里「内网地址浏览器不可达」的解法 |
| **bucket 公开只读**（仅 `s3:GetObject`，不允许列举） | 日记里的图片 URL 需长期有效，故不用会过期的预签名 URL；对象名含 UUID 不可枚举 |
| **上传走后端中转，不开放 MinIO 直传** | 可在后端统一做鉴权、类型与大小校验，也免去浏览器跨域配置 |
| **扩展名与 MIME 双重白名单** | 防止改扩展名绕过 |
| **对象名按 `yyyy/MM/<uuid>.<ext>`** | 避免文件名冲突，也杜绝路径穿越 |
| **`MinioBucketInitializer` 失败只告警不阻断启动** | 后面 P8 因此付出了代价（见 P8 · 设计取舍 7） |

**验证**：上传返回的 URL 直接 GET 可得到 HTTP 200 `image/png`；MinIO data 目录出现 `data/diary/2026/09/<uuid>.png/xl.meta`；附件落库；`.txt` 被拒（400）；未认证 401。

### 5.5 P6 · 前端体验完善

**范围说明**：Markdown 编辑与渲染已在 P3 / P5 落地，本阶段聚焦加载体验、主题、错误兜底、响应式与产物分包。

| 决策 | 理由 |
|---|---|
| **主题用 CSS 变量而非全量覆盖 antd** | 组件层交给 `darkAlgorithm`，自定义部分（Markdown 渲染、登录页背景）只需在 `[data-theme='dark']` 下切换变量，改动面最小 |
| **`MainLayout` 不懒加载** | 它是所有受保护页面的外壳，懒加载收益小且引入额外闪烁 |
| **草稿保护用 ref 放行而不是 state** | `setIsDirty(false)` 是异步的，`navigate()` 执行时未必已生效；用 `allowNavigateRef` 才能可靠实现「保存后直接跳转」 |
| **错误边界放在 `ConfigProvider` 内层** | 使错误页也能继承 antd 主题 token |
| **补齐 `Profile` / `Stats` 页面** | 侧边栏此前已有这两个菜单项但无对应路由，点击会落到 404 |

**关键收益**：主 chunk **2504 KB → 474 KB（gzip 148 KB），降幅 81%**，38 个分包。这是从 P2 起一路累积、连续 5 个阶段被记录为「遗留事项」的问题，终于在此关闭。

---

## 6. P7 · 统计页

### 6.1 产品定义（原方案风险清单要求明确并写单测）

「当前连续打卡天数」以**今天**为基准向前计数：

| 最近一篇的日期 | 当前连续 |
|---|---|
| 今天 | 从今天起算 |
| 昨天 | **延续**（今天还没结束，不算断） |
| 早于昨天 | 0 |

### 6.2 核心设计

1. **热力图只返回有记录的日期**，空白格子由前端补齐——一年 365 天多数为空，全量返回纯属浪费。
2. **心情分布用 `IFNULL(mood, 0)`** 把未记录心情的日记归入 0，从而保证「各段之和恒等于总篇数」；前端把 0 渲染为「未记录」。
3. **连续天数只查一次全部日期**，首末日期与两个连续指标复用同一份结果，避免 3 次往返。
4. **未做 Redis 缓存**：单次聚合查询走索引，当前数据量下无必要（YAGNI），留待真实性能问题出现再加。
5. **MyBatis 的 record 结果映射可用**：`DayCountVO` / `MoodCountVO` 直接接聚合结果，无需 `@Results` 手工映射——前提是编译带 `-parameters` 保留构造器参数名（Spring Boot 父 POM 默认开启）。

### 6.3 踩坑

1. **测试依赖了真实数据导致失败**：`StatsServiceTest` 最初沿用 `tester`（id=1）并断言「总篇数 = 4」，实际得到 **5**。查库发现该账号下有一条通过 UI 创建的真实日记（id=16、日期为当天），既让计数多 1，也让「当前连续」从 0 变成 1。**改用独立用户 ID（90001+）** 后彻底隔离。
   > **教训：断言绝对数值的测试必须自带隔离数据。** 同一类脆弱性也存在于 `TagServiceTest.isolation`（对用户 999 用了 `containsExactly`），当前通过只因该用户从未产生数据。
2. **连续天数断言算错**：日期集合为 `{t-2, t-1}` 时，从昨天向前回溯实际连续 **2** 天，我最初写成 1。修正后把「今天未写是否延续」这条产品规则拆成独立用例，边界才真正被覆盖——**否则这个 bug 会被一个错误的期望值掩盖过去**。

### 6.4 验证

17 个单元测试全通过；HTTP 实测 `overview` 返回 `totalCount=1, currentStreak=1, moodDistribution=[{1,1}]`，与真实数据完全对应。

---

## 7. P8 · 部署与上线

### 7.1 环境准备

| 项 | 结果 |
|---|---|
| Docker Desktop | 4.91.0（`winget install Docker.DockerDesktop`，耗时 4 分 53 秒） |
| docker CLI / compose | 29.8.0 / v5.5.1 |
| WSL2 | 2.7.14.0（内核 6.18.33.2-2） |
| **必须重启一次** | `VirtualMachinePlatform` 首次启用后，重启前 `wsl --status` 报「WSL2 无法启动」，守护进程连不上 |

### 7.2 关键设计取舍

| # | 决策 | 理由 |
|---|---|---|
| 1 | **对外只暴露 Nginx 一个端口**（`APP_PORT` 默认 80） | 不与本地开发环境的 3306/6379/9000/8080 抢端口，也不把数据库暴露到公网 |
| 2 | **MinIO 不映射宿主端口**，由 Nginx 以 `/files/` 反代 | 前端与图片同源，不需要 CORS，也不用纠结 `MINIO_PUBLIC_ENDPOINT` 填哪个主机名 |
| 3 | **必填密钥用 Compose 的 `${VAR:?}` 语法** | `DB_PASSWORD` / `JWT_SECRET` / `MINIO_*` 缺失时 `docker compose up` 直接报错退出，不会带着默认弱密码上线 |
| 4 | **`application-prod.yml` 中 `JWT_SECRET` 不给默认值** | 缺配置则启动失败，杜绝用开发密钥上线；顺带解决 P0 遗留的「生产应关闭 SpringDoc」 |
| 5 | **镜像 tag 全部写死到具体版本** | 且逐个用 Docker Hub / Quay API 验证存在 |
| 6 | **前端多阶段构建打进 Nginx 镜像**，而非挂载宿主 `dist/` | 全新机器不必先跑一次本地 `npm run build`，符合「一条命令起来」的验收标准 |
| 7 | **MinIO 健康检查不可省** | `MinioBucketInitializer` 初始化失败只记警告不阻断启动；若不保证 MinIO 先就绪，bucket 建不出来且上传接口会一直不可用 |

> 关于第 5 条：MinIO 官方已从 Docker Hub 下架，镜像源在 **quay.io**（`hub.docker.com/v2/repositories/minio/minio` 返回 404）。逐个验证 tag 这一步直接避免了拉不到镜像的部署失败。

### 7.3 验收结果

| # | 验收标准 | 结果 | 证据 |
|---|---|---|---|
| 1 | 全新机器 `up -d` 后可直接注册登录 | ✅ | 5 个容器全部 healthy；注册/登录/`users/me` 全通 |
| 2 | 刷新任意前端路由不 404 | ✅ | `GET /stats` 返回 200 且为 `index.html` |
| 3 | `/api/**` 正确代理，无跨域 | ✅ | `/api/v1/ping` 经 nginx 返回 `status=UP`；同源不触发 CORS |
| 4 | 容器重启后数据不丢 | ✅ | `down` + `up -d` 后，日记与 MinIO 图片均可读回 |
| 5 | 备份脚本生成 dump 并清理过期 | ✅ | 产出 8.7 KB dump；预置的 30 天前假备份被自动清理 |

补充：`deploy/smoke-test.ps1` 17 项断言全通过（退出码 0）；改代码后重建镜像仅 **19 秒**（层缓存命中）。

### 7.4 缺陷 5：未匹配路径被兜底成 500

**发现路径**：验证「生产已关闭 SpringDoc」时，后端对 `/v3/api-docs` 返回 **500** 而非 404。

**根因**：Spring 6.1+ 对未映射路径抛 `NoResourceFoundException`，被兜底的 `@ExceptionHandler(Exception.class)` 捕获。

**真实影响**：不止接口文档——**任何拼错的 URL、或扫描器对任意路径的探测，都会往日志里写一条 ERROR 级完整堆栈**，应用一暴露到公网日志很快被灌满，真实问题被淹没。

**修复**：显式声明 `NoResourceFoundException` / `NoHandlerFoundException` → 404 + `log.warn`（不带堆栈）。

**验证**：`/v3/api-docs` 从 500 变 **404**；日志 `ERROR` 计数归零，`路径不存在` WARN 3 条。
**顺带确认**：`/api/v1/not-exist` 仍返回 **401**——Security 在路由解析前就拦截未认证请求，这是正确的，不泄露路由是否存在。

### 7.5 缺陷 6：nginx 缓存上游 IP，重建后端后持续 502

**发现路径**：审查自己的交付物时意识到——`proxy_pass http://backend:8080` 是字面量，nginx 只在**启动时**解析一次主机名并缓存 IP；而我在 README 里推荐的 `docker compose up -d --build` 会重建后端容器、换掉 IP，此后 nginx 一直 502，直到手动 `restart nginx`。

> 也就是说：**我交付的部署方式本身会导致下次改代码后必然故障。**

**修复**：

- 加 `resolver 127.0.0.11 valid=10s;` 并将 `proxy_pass` 改写为**变量形式**（变量形式下 nginx 按 TTL 重新解析）
- `/files/` 在变量形式下无法自动剥前缀，改用 `rewrite ^/files/(.*)$ /$1 break;` 手动剥
- **注意 `set` 必须写在 `rewrite` 之前**，否则会被 `break` 跳过

**验证**（第一次测试无效，值得记录）：

- 直接重建后端，Docker **复用了刚释放的 IP**（172.18.0.5），测试不具说服力
- 于是用占位容器抢走旧 IP，强制后端从 `172.18.0.5` 换到 `172.18.0.7`，**nginx 全程未重启**，`/api/v1/ping` 与 `/files/` 仍返回 200

### 7.6 缺陷 7：容器内 mysql 客户端默认 latin1，把中文写成了双重编码

**发现路径**（这是本项目最有价值的一次排查）：

在容器 Exec 里查数据，中文显示成 `?????`；但**同一个客户端**查 `information_schema.TABLE_COMMENT` 却显示正常。这不合理，于是改用服务端计算的 `CHAR_LENGTH` / `LENGTH` / `HEX` 对比：

| 对象 | 字符数 | 字节数 | HEX | 判定 |
|---|---|---|---|---|
| `t_diary.title`（id=2） | 5 | 15 | `E68C81E4B985...` | ✅ 正确的 UTF-8「持久化验证」 |
| `information_schema` 表注释 | **9** | **19** | `C3A6E28094...` | ❌ 「日记表」的双重编码 |

**「显示正常」的那条才是坏的。**

**根因**：容器内 `LANG` 为空、`LC_CTYPE=POSIX`，mysql 客户端据此把 `character_set_client / connection / results` 全部降级为 **latin1**。`sql/init.sql` 由官方镜像的 entrypoint 在容器内执行，文件里的中文被当作 latin1 解读后**双重编码**写入。

**为什么难发现**：双重编码的数据配上同样是 latin1 的客户端读回时，字节会**反向抵消**，显示反而是对的。「显示正常」恰恰是乱码的证据。

**影响**：表注释（纯元数据）+ `tester` 昵称（**用户可见**，登录后界面显示乱码）。更危险的推论：**这个连接写中文同样会存成乱码**。

**修复**：`sql/init.sql` 顶部加 `SET NAMES utf8mb4;`，使脚本不再依赖客户端默认字符集。容器内手动敲 SQL 须带 `--default-character-set=utf8mb4`。

**验证**：`docker compose down -v` 删卷重建后，表注释变为 3 字符 / 9 字节、`tester` 昵称变为 4 字符 / 12 字节、中文正常显示；冒烟测试仍 17/17。

### 7.7 另一个发现：dev CORS 配置在生产形同虚设（非缺陷，但值得记）

`SecurityConfig` 的 `corsConfigurationSource()` 只放行 `http://localhost:*` / `http://127.0.0.1:*`。生产由 Nginx 同源反代，浏览器根本不发跨域请求，因此这段配置不会生效也不会造成问题——但它意味着**生产环境没有任何 CORS 兜底**。若将来把前端部署到别的域名，必须在此补白名单。

### 7.8 踩坑

1. **重启后本机中间件全部未启动**：MySQL / Redis / MinIO 的启停脚本刻意未注册为 Windows 服务，重启机器后需手动启动。本地跑测试会因此报 `Communications link failure`，17 个测试全挂——当时先怀疑了刚改的代码，实际是环境没起。
   > **排查顺序上应先看服务状态，再看代码。**
2. **Docker 会复用刚释放的 IP**：见 7.5，第一次验证 DNS 缓存问题时测试无效。
3. **PowerShell 5.1 会把 `sh -c` 里的双引号吃掉**：写成 `sh -c '... -e "use diary; show tables;"'` 时，mysql 只收到 `-e use`，报 `ERROR at line 1: USE must be followed by a database name`。正确形态是「外层双引号（给 PowerShell）+ 内层单引号（给 sh/mysql）+ `` `$ `` 转义」。**也不要用管道把 SQL 喂给 mysql 的 stdin**——PowerShell 会在开头写入 BOM，导致 `syntax error`。

---

## 8. 方法论沉淀

### 8.1 验证方式的分级

| 级别 | 手段 | 能证明什么 |
|---|---|---|
| 1 | 编译 / 类型检查 | 语法与类型正确 |
| 2 | 单元测试 | 业务逻辑正确（**前提是数据隔离**，见 6.3） |
| 3 | 真实启动 + HTTP 请求 | 端到端链路正确 |
| 4 | **失败场景实测** | 错误分支真的按预期工作 |
| 5 | **对照实验** | 因果关系确定（如用错误密码复现逐字相同的报错） |

本项目多次依赖第 5 级：确认 Lombok 升级无效、确认 404 缺陷的根因、确认访问被拒是密码问题而非白名单问题——都是靠「构造一个反面样例，看报错是否一致」得出结论。

### 8.2 「修好了」的标准

不是「不再报错」，而是**能说清因果关系，并且有一个可以复现的验证动作**。反例：把 Lombok 的 `Unsafe` 警告加 `--sun-misc-unsafe-memory-access=allow` 静音——告警消失但问题仍在，JDK 26 会再次爆掉。最终选择移除 Lombok 才是真修复。

### 8.3 文档不是抄一遍

每阶段的「完成记录」都记录了**被否决的方案及其理由**（不用 `UserDetailsService`、不用 MapStruct、不用预签名 URL、`MainLayout` 不懒加载）。这些是「为什么不是另一种写法」的答案，比「我写了什么」更有长期价值——半年后重看代码时，最需要的正是这部分。

### 8.4 已知脆弱点（应优先处理）

| 位置 | 问题 | 风险 |
|---|---|---|
| `TagServiceTest.isolation` | 对用户 999 用了 `containsExactly` | 该用户一旦产生数据，测试会假失败（与 P7 同类脆弱性，尚未修） |
| MinIO 归档版 | 无安全更新 | 公网部署前必须替换 |
| `application-dev.yml` | 数据源密码硬编码默认值 `123456` | 仅本地开发，但若误带 dev profile 上线会造成弱口令 |
| 生产 CORS | 只放行 localhost | 见 7.7，换域名时需补白名单 |
| 无容器资源上限与日志轮转 | 长跑可能吃满磁盘 | 单机自用暂可接受 |

---

## 9. 复盘：三个「如果重来」

1. **如果一开始就上 Docker**（决策 1 的另一种选择），可以省掉 3 个中间件的免安装部署与 6 个启停脚本；但代价是开发期就要承担 WSL2 的资源占用。从结果看，两套并存的方案也带来一个意外好处：**容器化的库与本地库互相独立，做「重建数据卷」这类破坏性验证时不用担心弄丢本地联调数据**。
2. **如果 P0 就移除 Lombok**，能省掉一次完整的「临时方案 → 最终方案」往返。但它当时并不构成阻塞，提前处理需要有先见之明。
3. **如果早一点意识到「部署文档也是交付物」**，就会在写 README 命令时同步验证它们——`docker compose up -d --build` 的 nginx 502 问题（7.5）正是在 README 定稿之后才被发现的。
