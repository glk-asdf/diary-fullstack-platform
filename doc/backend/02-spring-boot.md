# 模块 02 · Spring Boot 框架

> 读者：已读完 `01-java-basics.md`，能看懂类、接口、注解
> 目标：**知道一个请求从 Nginx 到数据库经过哪些层；能自己加一个完整接口并调通**
> 时长：约 1.5 天
> 上一篇：`01-java-basics.md`

---

## 0. 这一篇解决什么问题

后端代码里你会看到两种完全不同的「阅读体验」：

| 类型 | 例子 | 难度 |
|---|---|---|
| **业务逻辑** | `DiaryServiceImpl.page()` 里的条件拼装、摘要生成 | 读懂不难，就是 Java 代码 |
| **框架胶水** | `@RestController`、`@Transactional`、`@Bean`、`@MapperScan` | **每个词都认识，但不知道谁在什么时候调它** |

这一篇专门讲第二种。核心心智模型只有一句话：

> **你写的类和方法，绝大多数不是「被别的代码调用」的，而是「被框架在特定时机回调」的。**

理解这句话，那些注解就从「魔法」变成了「注册声明」。

---

## 1. IoC 与依赖注入：为什么不用 `new`

### 1.1 没有 Spring 的世界长什么样

假设手写装配（Node 里你就是这么干的）：

```java
// ❌ 手动 new：每个用到的地方都要自己装配整条依赖链
public class DiaryController {
    private DiaryService diaryService = new DiaryServiceImpl(
            new DiaryMapperImpl(),          // Mapper 其实是接口，根本没法 new
            new DiaryTagMapperImpl(),
            new TagMapperImpl());
}
```

问题很直接：

1. **Mapper 是接口**，实现是 MyBatis 运行期动态生成的，你没法 `new`
2. **每个使用点都要写一遍装配**，改一个构造器签名就要改所有调用处
3. **单例无法保证**，每次 `new` 都是新对象，连接池之类的资源会被反复创建

### 1.2 有 Spring 之后

```java
@Service
public class DiaryServiceImpl implements DiaryService {
    private final DiaryMapper diaryMapper;

    public DiaryServiceImpl(DiaryMapper diaryMapper) {   // ← 只声明「我需要什么」
        this.diaryMapper = diaryMapper;
    }
}
```

```java
@RestController
public class DiaryController {
    private final DiaryService diaryService;

    public DiaryController(DiaryService diaryService) {  // ← 同样只声明依赖
        this.diaryService = diaryService;
    }
}
```

**谁把它们组装起来的？** Spring 容器：

```text
启动时：
1. 扫描 com.example.diary 包下所有带 @Component/@Service/@RestController/@Configuration 的类
2. 对每个类：看它的构造器需要什么参数
3. 递归地先把参数创建出来（DiaryMapper 由 MyBatis 生成代理对象）
4. 用反射调用构造器，把实例存进「容器」
5. 需要 DiaryController 时，从容器里取出已经建好的 DiaryService 塞进去
```

**三个术语**：

| 术语 | 含义 | 类比 |
|---|---|---|
| **Bean** | 被容器管理的对象实例 | NestJS 的 provider |
| **容器（Context）** | 存放所有 Bean 的地方 | NestJS 的 DI 容器 |
| **注入（Injection）** | 把依赖塞给需要它的对象 | NestJS 的 `constructor(private x: X)` |

### 1.3 本项目为什么用「构造器注入」而不是 `@Autowired`

```java
// ✅ 本项目的写法：构造器注入，没有 @Autowired
private final DiaryMapper diaryMapper;

public DiaryServiceImpl(DiaryMapper diaryMapper) {
    this.diaryMapper = diaryMapper;
}

// ❌ 能跑但不推荐：字段注入
@Autowired
private DiaryMapper diaryMapper;
```

对比（这也是 Java 社区多年争论后形成的共识）：

| | 构造器注入 | 字段注入 |
|---|---|---|
| 字段能否 `final` | ✅ 不可变 | ❌ 必须是可变字段 |
| 依赖是否显式 | ✅ 看构造器就知道 | ❌ 藏在字段上 |
| 能否在单测里手写 | ✅ `new DiaryServiceImpl(mockMapper)` | ❌ 必须启动容器 |
| 是否可能循环依赖 | ✅ 启动就报错 | ❌ 运行期才炸 |

> **Spring 的自动规则**：一个类**只有一个构造器**时，不需要写 `@Autowired`。本项目所有 Service / Controller 都符合这一条，所以你在代码里搜不到 `@Autowired`。

### 1.4 你不是在「调用」框架，而是在「注册」

这一点值得反复强调。看下面三个注解，它们做的事都是**登记**，不是执行：

| 注解 | 你登记的是 | 框架在何时用 |
|---|---|---|
| `@Service` | 「这是个 Bean，请管理它」 | 启动扫描时 |
| `@GetMapping` | 「这个方法处理 GET /xxx」 | 收到请求时 |
| `@Transactional` | 「这个方法要在事务里跑」 | 方法执行前后（AOP 代理） |

`@Transactional` 尤其典型——它**不是你写的代码在开事务**，而是 Spring 给你的类生成了一个代理对象（`DiaryServiceImpl` 的包装），代理在调用真方法前 `BEGIN`、返回后 `COMMIT`、抛异常时 `ROLLBACK`。

> 由此可以推出一个常见坑：**同类内部方法互相调用时 `@Transactional` 不生效**——因为内部调用走的是 `this`，绕过了代理对象。本项目把 `requireOwned()`、`applyDto()` 都写成 `private`，正好不会误用。

---

## 2. 启动类与自动配置

### 2.1 逐行读 `DiaryApplication.java`

```java
package com.example.diary;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.diary.mapper")
public class DiaryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiaryApplication.class, args);
    }
}
```

**`main` 方法**：`SpringApplication.run(...)` 是整个后端的入口——建容器、扫描 Bean、启动内嵌 Tomcat、然后**阻塞**（进程不退出，一直等请求）。

**`@SpringBootApplication` 是三合一的组合注解**：

| 它包含 | 作用 |
|---|---|
| `@SpringBootConfiguration` | 声明这是配置类 |
| `@EnableAutoConfiguration` | **自动配置**：根据 classpath 上的依赖，自动装配对应的组件 |
| `@ComponentScan` | 从当前包（`com.example.diary`）开始向下扫描 Bean |

> **自动配置**是 Spring Boot 最核心的便利点。等价于 Node 世界里的「装了 `express` 就自动给你配好路由 + 中间件 + 监听端口」。你引入了 `spring-boot-starter-web`，它就自动配好 Tomcat + Jackson；引入 `spring-boot-starter-data-redis`，它就按 `spring.data.redis.*` 配置建好 `RedisTemplate`。

**`@MapperScan("com.example.diary.mapper")` 为什么必须写**：`mapper/` 下的接口（如 `DiaryMapper`）**没有实现类**，需要 MyBatis 在启动时为每个接口生成动态代理并注册成 Bean。`@MapperScan` 就是告诉它「去这个包下面找」。

> 顺带说明：`DiaryMapper` 上还有个 `@Mapper` 注解。两者作用重叠——`@MapperScan` 已经覆盖了整包，`@Mapper` 是冗余但无害的（本项目两者都写了）。

### 2.2 启动时到底发生了什么

```text
1. 建 ApplicationContext（IoC 容器）
2. 扫描 com.example.diary.** 下所有类，识别注解，决定「谁是 Bean」
3. 按依赖顺序实例化 Bean
     ├─ 配置文件读取 → DataSource / RedisTemplate / MinioClient
     ├─ MyBatis-Plus 为 mapper 包下每个接口生成代理
     └─ 你写的 Service / Controller
4. 执行所有 ApplicationRunner
     └─ MinioBucketInitializer.run()   ← 建 bucket 并设公开只读（失败只 WARN）
5. 启动内嵌 Tomcat，监听 8080
6. 打印 "Started DiaryApplication in X seconds"
```

**其中第 4 步是本项目实际用到的扩展点**——`MinioBucketInitializer` 实现了 `ApplicationRunner`，容器启动完成后回调它：

```java
@Component
public class MinioBucketInitializer implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        try {
            // 建 bucket、设公开只读策略
        } catch (Exception e) {
            log.warn("MinIO bucket 初始化失败，上传接口将不可用: {}", e.getMessage());  // ← 只记警告，不阻断启动
        }
    }
}
```

这个设计在 P8 部署时产生了后果：因为「MinIO 没就绪」不会让后端启动失败，所以 compose 里**必须**给 MinIO 加健康检查，否则后端会先起来、bucket 建不出来、上传接口一直不可用（见 `development-log.md` 的 P8 设计取舍 #7）。

### 2.3 配置文件与 profile

本项目的配置文件是**三个文件协作**：

```text
resources/
├── application.yml          主配置：端口、Jackson、JWT、MinIO、SpringDoc
├── application-dev.yml      开发环境：本地 MySQL / Redis / 打印 SQL
└── application-prod.yml     生产环境：容器内网地址 / 关闭 SQL 日志与接口文档
```

由主配置里的这一行决定加载哪个：

```yaml
spring:
  profiles:
    active: dev            # 默认 dev；容器部署时被环境变量 SPRING_PROFILES_ACTIVE=prod 覆盖
```

**加载与覆盖规则**：

```text
application.yml（总会加载）
      ↓ 被同名属性覆盖
application-{active}.yml
      ↓ 再被覆盖
环境变量 / 命令行参数        ← 优先级最高
```

**这就是为什么容器部署不用改代码**：`application-prod.yml` 里写的是 `${DB_HOST:mysql}`，而 compose 注入 `DB_HOST=mysql`。

**本项目的 env 变量命名对照**：

| 配置文件里的占位符 | 容器注入的环境变量 | 谁来注入 |
|---|---|---|
| `${DB_HOST:mysql}` | `DB_HOST` | `docker-compose.yml` |
| `${JWT_SECRET}` | `JWT_SECRET` | `.env` |
| `${MINIO_ENDPOINT:http://minio:9000}` | `MINIO_ENDPOINT` | `docker-compose.yml` |

> ⚠️ `application-prod.yml` 里 `JWT_SECRET` **没有默认值**（写作 `${JWT_SECRET}` 而不是 `${JWT_SECRET:xxx}`），所以缺失时会**启动失败**。这是刻意设计：宁可起不来，也不要带着开发密钥上线。

---

## 3. 注解全景（按层分组）

把本项目用到的注解按「你会在哪一层看到」整理，遇到不认识的回来查：

### 3.1 启动与配置

| 注解 | 位置 | 作用 |
|---|---|---|
| `@SpringBootApplication` | 启动类 | 三合一：配置类 + 自动配置 + 组件扫描 |
| `@MapperScan("...")` | 启动类 | 为指定包下的接口生成 MyBatis 代理 |
| `@Configuration` | `config/*` | 声明配置类 |
| `@Bean` | 配置类的方法上 | 「这个方法的返回值注册为 Bean」 |

`@Configuration` + `@Bean` 的实例（`config/MybatisPlusConfig.java`）：

```java
@Configuration
public class MybatisPlusConfig {

    @Bean                                              // ← 方法名 mybatisPlusInterceptor 就是 Bean 名字
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(50L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
```

**什么时候用 `@Bean` 而不是 `@Component`**：当你要注册的类**是第三方库的**（你不能在别人的类上加注解）时，就在自己的配置类里写个 `@Bean` 方法把它造出来。

### 3.2 分层组件

| 注解 | 用在哪 | 语义 |
|---|---|---|
| `@RestController` | `controller/` | 这是个 HTTP 接口类，返回值直接序列化成 JSON |
| `@Service` | `service/impl/` | 业务层组件 |
| `@Mapper` | `mapper/` | 数据访问接口（配合 `@MapperScan`） |
| `@Component` | 其他（如 `MinioBucketInitializer`、`MyMetaObjectHandler`） | 通用组件 |
| `@Configuration` | `config/` | 配置类 |

> 这四个注解在**功能上几乎等价**（都是「注册为 Bean」），区别只是**语义标注**，便于人和工具理解分层。用错不会报错，但会被同事说。

### 3.3 Web 层

| 注解 | 作用 | Express 类比 |
|---|---|---|
| `@RequestMapping("/api/v1/diaries")` | 类级路径前缀 | `router.prefix()` |
| `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` | 方法级路径与方法 | `router.get()` |
| `@PathVariable` | 路径占位符 → 参数 | `req.params.id` |
| `@RequestBody` | 请求体 → 对象 | `req.body` |
| `@RequestParam` | query 参数 → 参数 | `req.query` |
| `@Valid` | 触发字段校验 | 校验中间件 |
| **（不写注解）** | 复杂对象自动从 query 绑定 | `Object.assign({}, req.query)` |
| `@RestControllerAdvice` | 全局异常处理类 | 全局错误中间件 |
| `@ExceptionHandler(X.class)` | 处理某类异常 | `if (err instanceof X)` |

### 3.4 数据与安全

| 注解 | 作用 |
|---|---|
| `@Transactional` | 该方法在数据库事务内执行（见模块 03） |
| `@TableName` / `@TableId` / `@TableField` / `@TableLogic` | 实体 ↔ 表字段映射（见模块 03） |
| `@NotBlank` / `@NotNull` / `@Min` / `@Max` / `@Size` / `@Email` | 参数校验规则 |

### 3.5 文档

| 注解 | 作用 |
|---|---|
| `@Tag(name, description)` | 接口分组（Swagger UI 上的分类） |
| `@Operation(summary)` | 单个接口的说明 |
| `@Schema(description, example)` | 字段说明 |

---

## 4. 一次请求的完整链路（🔴 本节是核心）

以 `GET /api/v1/diaries?page=1&size=10&keyword=爬山` 为例。

### 4.1 全景图

```text
浏览器
  │  GET /api/v1/diaries?page=1&keyword=爬山    Header: Authorization: Bearer eyJ...
  ▼
┌─────────────────────────────────────────────────────────────┐
│ Nginx（容器部署时）                                          │
│   location /api/ → proxy_pass http://backend:8080           │
│   同源反代 ⇒ 浏览器不发跨域请求                              │
└─────────────────────────────────────────────────────────────┘
  ▼
┌─────────────────────────────────────────────────────────────┐
│ 内嵌 Tomcat                                                  │
│   ├─ JwtAuthenticationFilter   ← 自定义过滤器（第 1 个把关）  │
│   │     解析 Authorization → 验签 → 把 userId 放进 SecurityContext │
│   │     解析失败不响应，放行给后面的 EntryPoint               │
│   ├─ Spring Security 授权判断                                │
│   │     路径在白名单？没认证 → 401（JwtAuthenticationEntryPoint）│
│   │     已认证但无权限 → 403（AccessDeniedHandlerImpl）       │
│   └─ DispatcherServlet         ← Spring MVC 的前端控制器      │
│         ① 根据 URL 找到 DiaryController.page 方法            │
│         ② 把 query 参数绑定到 DiaryQueryDTO（构造器绑定）      │
│         ③ 调用方法                                            │
└─────────────────────────────────────────────────────────────┘
  ▼
┌─────────────────────────────────────────────────────────────┐
│ DiaryController.page(DiaryQueryDTO query)                    │
│   return Result.ok(diaryService.page(query));                │
│   ← 只做转发，不写业务逻辑                                    │
└─────────────────────────────────────────────────────────────┘
  ▼
┌─────────────────────────────────────────────────────────────┐
│ DiaryServiceImpl.page(query)                                 │
│   ① SecurityUtil.getCurrentUserId()   ← 从 SecurityContext 取，不从参数取 │
│   ② 拼条件：select 裁剪字段 / eq(userId) / keyword / mood / 日期 / tagId │
│   ③ diaryMapper.selectPage(...)       ← 分发到 MyBatis          │
│   ④ loadTags(diaryIds)                ← 批量查标签，避免 N+1    │
│   ⑤ DiaryConverter.toListVO(...)      ← Entity → VO            │
│   ⑥ PageResult.of(page, records)                              │
└─────────────────────────────────────────────────────────────┘
  ▼
┌─────────────────────────────────────────────────────────────┐
│ MyBatis-Plus                                                 │
│   · 自动追加 deleted = 0（@TableLogic）                       │
│   · 分页插件把查询改写成 LIMIT（并额外发一条 COUNT）          │
│   · 用连接池（HikariCP）取连接                                │
└─────────────────────────────────────────────────────────────┘
  ▼
MySQL
  ▼
        逐层返回 → Jackson 把 Result<PageResult<DiaryVO>> 序列化成 JSON
  ▼
浏览器收到 {"code":0,"message":"ok","data":{"total":1,"records":[...]}}
```

### 4.2 三个「把关点」，排错时要能分清

| 状态码 | 谁返回的 | 典型原因 |
|---|---|---|
| **401** | `JwtAuthenticationFilter` + `JwtAuthenticationEntryPoint` | token 缺失、过期、签名错 |
| **403** | `AccessDeniedHandlerImpl` | 已认证但没有该资源的权限（本项目基本用不到，因为用 404 代替） |
| **400** | `GlobalExceptionHandler`（`MethodArgumentNotValidException` 分支） | 参数校验失败 |
| **404** | `BizException(NOT_FOUND)` | **可能是业务层主动抛的**，不一定是路由不存在 |
| **500** | `GlobalExceptionHandler` 兜底分支 | 未预期异常，**看后端日志的堆栈** |

> **最容易被误判的一条**：`404` 有两种来源——① URL 真的不存在（Spring 抛 `NoResourceFoundException`）；② **Service 里主动抛的越权判断**。本项目刻意用 404 而不是 403，避免通过状态码探测他人资源是否存在（`requireOwned()` 里那句 `throw new BizException(ResultCode.NOT_FOUND, "日记不存在")`）。

### 4.3 参数绑定：`DiaryQueryDTO` 是怎么从 query 参数变出来的

这是**从 Node 转过来最需要适应的一处**——Java 里能自动把 query 参数装配成一个对象。

```java
@GetMapping
public Result<PageResult<DiaryVO>> page(DiaryQueryDTO query) {   // ← 没有注解！
    return Result.ok(diaryService.page(query));
}
```

```java
public record DiaryQueryDTO(
        Integer page,
        Integer size,
        String keyword,
        Long tagId,
        Integer mood,
        LocalDate startDate,
        LocalDate endDate) { /* ... */ }
```

**绑定规则**：请求 `?page=1&size=10&keyword=爬山` → Spring 找 `DiaryQueryDTO` 的对应属性名（record 的组件名）→ 用**构造器**把值塞进去 → 类型自动转换（`"1"` → `Integer 1`，`"2026-01-01"` → `LocalDate`）。

**没传的字段** → 保持 `null`（因为都是包装类型，见模块 01 第 3.4 节）。

**这带来一个很好的工程习惯**：DTO 里可以直接写「取默认值」的方法，让 Controller 保持干净：

```java
public static final int DEFAULT_PAGE = 1;
public static final int DEFAULT_SIZE = 10;
public static final int MAX_SIZE = 50;

public long pageOrDefault() {
    return page == null || page < 1 ? DEFAULT_PAGE : page;
}

public long sizeOrDefault() {
    if (size == null || size < 1) {
        return DEFAULT_SIZE;
    }
    return Math.min(size, MAX_SIZE);      // ← 上限收敛，防 size 越界
}
```

> **安全提醒**：`MAX_SIZE` 是**必须有的**。没有它，用户传 `?size=1000000` 就能让数据库吐出全表——这类「参数越界」是最常见的接口层漏洞之一。（本项目还有第二道防线：分页插件也设了 `maxLimit=50L`，见模块 03。）

### 4.4 返回值怎么变成 JSON

```java
return Result.ok(diaryService.page(query));
```

1. `Result.ok(...)` 是 record 的静态工厂（见模块 01 第 10.1 节）
2. Spring MVC 发现方法返回的不是 `ResponseEntity`，就用 **Jackson** 序列化
3. Jackson 读 `application.yml` 的全局配置：

```yaml
spring:
  jackson:
    time-zone: Asia/Shanghai
    date-format: yyyy-MM-dd HH:mm:ss
    default-property-inclusion: non_null      # ← null 字段不序列化
```

**`non_null` 的后果**：如果 `DiaryVO.summary` 是 `null`，返回的 JSON 里**根本没有 `summary` 这个键**。前端 TypeScript 里必须写成可选（`summary?: string`）——这就是 `frontend/src/types/diary.ts` 里那些 `?` 的来源。

> **HTTP 状态码与业务码是两套东西**：`Result.code` 是业务码，HTTP 状态码由 `GlobalExceptionHandler` 的 `toHttpStatus()` 映射。两者同时有意义——前端既能用 HTTP 状态码驱动拦截器（401 触发刷新），也能用业务码做细分支。

---

## 5. 参数校验（Jakarta Validation）

### 5.1 声明规则

在 DTO 上用注解声明约束（`DiarySaveDTO` 简化版）：

```java
public record DiarySaveDTO(
        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题不能超过 200 字")
        String title,

        @Size(max = 100000, message = "正文过长")
        String content,

        @Min(value = 1, message = "心情取值不合法")
        @Max(value = 5, message = "心情取值不合法")
        Integer mood,

        @NotNull(message = "日记日期不能为空")
        LocalDate diaryDate,

        List<Long> tagIds,
        Integer isPublic) {
}
```

然后在 Controller 参数上加 `@Valid` **才会触发**：

```java
@PostMapping
public Result<DiaryDetailVO> create(@Valid @RequestBody DiarySaveDTO dto) {
```

> ⚠️ **不加 `@Valid` 注解就不校验**——这是最常见的「明明写了规则却不生效」的原因。

### 5.2 没查到就直接动手：实测结果

发一个标题为空的请求：

```powershell
# 完整命令见 usage-guide.md 的「亲手走一遍」，核心是带 token 的 POST
# body: {"title":"","content":"x","diaryDate":"2026-09-24"}
```

实测返回：

```text
HTTP 400
{"code":400,"message":"标题不能为空"}
```

再试缺日期：

```text
HTTP 400
{"code":400,"message":"日记日期不能为空"}
```

**两个值得注意的点**：

1. **record 上的校验注解是生效的**（Hibernate Validator 8 支持 record），不用担心「record 不能被校验」
2. **HTTP 状态码是 400，不是 200 带错误码**——由 `GlobalExceptionHandler.handleValid` 决定

### 5.3 谁把校验失败转成了 JSON

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<Result<Void>> handleValid(MethodArgumentNotValidException e) {
    String message = e.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(FieldError::getDefaultMessage)   // ← 取你自己写的 message
            .findFirst()
            .orElse(ResultCode.BAD_REQUEST.getMessage());
    return ResponseEntity.badRequest()
            .body(Result.fail(ResultCode.BAD_REQUEST.getCode(), message));
}
```

**注意 `findFirst()`**：多个字段同时校验失败时，**只返回第一条**。这是刻意的取舍（前端一次展示一个错误更清晰），但你要知道它意味着「前端拿到的错误信息是不完整的」。

### 5.4 常用校验注解速查

| 注解 | 适用类型 | 含义 |
|---|---|---|
| `@NotNull` | 任意 | 不能为 null |
| `@NotBlank` | String | 不能为 null、空串、纯空白 |
| `@NotEmpty` | String / 集合 | 不能为空 |
| `@Size(min, max)` | String / 集合 | 长度范围 |
| `@Min` / `@Max` | 数字 | 数值范围 |
| `@Email` | String | 邮箱格式 |
| `@Pattern(regexp)` | String | 正则 |

---

## 6. 日志

本项目用的是 **SLF4J + Logback**（Spring Boot 默认组合）。

```java
private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
log.error("系统异常", e);
```

**为什么不用字符串拼接**：

```java
log.warn("业务异常: code=" + code);          // ❌ 即使日志级别关掉，字符串也已经拼好了
log.warn("业务异常: code={}", code);          // ✅ 只有真要输出时才替换 {}，零开销
```

**用 `{}` 占位，不要用 `+`** ——这是 Java 日志的硬性惯例。

**日志级别的选择**（也是本项目排障时的依据）：

| 级别 | 什么时候用 | 本项目实例 |
|---|---|---|
| `ERROR` | **需要人介入**的故障 | 未预期异常（带堆栈） |
| `WARN` | 异常但可自愈 / 客户端错误 | 业务异常、路径不存在、MinIO 不可用 |
| `INFO` | 关键流程节点 | 启动完成、bucket 创建成功 |
| `DEBUG` | 开发调试细节 | 开发环境的 `com.example.diary` |

**改日志级别**（`application-dev.yml`）：

```yaml
logging:
  level:
    com.example.diary: debug     # 只调自己的包，别全局开 debug（第三方库会刷屏）
```

> 生产环境（`application-prod.yml`）把级别压到 `info`，并且**关闭了 SQL 打印**——因为 SQL 日志里会出现用户的日记内容。

---

## 7. 动手：自己加一个完整接口

**需求**：加一个接口 `GET /api/v1/diaries/count-by-mood`，返回当前用户每种心情的日记篇数。

这与你已有的 `GET /api/v1/stats/overview` 功能重合，所以是**纯粹的练手**——但流程与加真接口完全一样。

### 步骤

**① VO**（`vo/MoodCountVO.java` 已存在，直接复用）

```java
public record MoodCountVO(Integer mood, long total) { }
```

**② Controller 加方法**（`controller/DiaryController.java`）

```java
@Operation(summary = "按心情统计篇数")
@GetMapping("/count-by-mood")
public Result<List<MoodCountVO>> countByMood() {
    return Result.ok(diaryService.countByMood());
}
```

> ⚠️ **路径要写在 `@GetMapping("/{id}")` 之前吗？** 不需要——Spring 的路径匹配会优先选**更具体**的（字面量路径优先于变量路径），所以 `count-by-mood` 不会被 `{id}` 抢走。但**顺序写反在别的框架里可能出问题**，养成「具体路径写在前面」的习惯没坏处。

**③ Service 接口加声明**（`service/DiaryService.java`）

```java
List<MoodCountVO> countByMood();
```

**④ Service 实现**（`service/impl/DiaryServiceImpl.java`）

```java
@Override
public List<MoodCountVO> countByMood() {
    Long userId = SecurityUtil.getCurrentUserId();

    List<Diary> diaries = diaryMapper.selectList(Wrappers.<Diary>lambdaQuery()
            .select(Diary::getMood)                        // ← 只取 mood 字段
            .eq(Diary::getUserId, userId));

    Map<Integer, Long> grouped = diaries.stream()
            .collect(Collectors.groupingBy(
                    d -> d.getMood() == null ? 0 : d.getMood(),   // ← 未记录心情归入 0
                    Collectors.counting()));

    return grouped.entrySet().stream()
            .map(entry -> new MoodCountVO(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingInt(MoodCountVO::mood))
            .toList();
}
```

**⑤ 编译并启动**

```powershell
cd backend
.\mvnw.cmd -B clean compile        # 先确认语法
.\mvnw.cmd spring-boot:run         # 启动
```

**⑥ 调通**

```powershell
$body = [System.Text.Encoding]::UTF8.GetBytes('{"username":"tester","password":"123456"}')
$res = Invoke-WebRequest -Uri 'http://localhost:8080/api/v1/auth/login' -Method Post `
        -ContentType 'application/json' -Body $body -UseBasicParsing
$json = [System.Text.Encoding]::UTF8.GetString($res.RawContentStream.ToArray()) | ConvertFrom-Json

Invoke-WebRequest -Uri 'http://localhost:8080/api/v1/diaries/count-by-mood' `
  -Headers @{Authorization="Bearer $($json.data.accessToken)"} -UseBasicParsing |
  Select-Object -ExpandProperty Content
```

**⑦ 收尾**：把上面四处改动都删掉，重新编译确认干净。

> **这个练习的真正价值**：它让你完整走了一遍「VO → Controller → Service 接口 → Service 实现 → 编译 → 调通」，而这正是加任何新接口的固定套路。做完之后，`backend/03-persistence.md` 里的查询写法你会觉得理所当然。

---

## 附录 A · 注解速查（贴墙版）

```text
启动/配置    @SpringBootApplication  @MapperScan  @Configuration  @Bean
分层         @RestController  @Service  @Mapper  @Component
Web          @RequestMapping  @GetMapping/@PostMapping/@PutMapping/@DeleteMapping
             @PathVariable  @RequestBody  @RequestParam  @Valid
异常         @RestControllerAdvice  @ExceptionHandler
数据         @Transactional
             @TableName  @TableId  @TableField  @TableLogic
校验         @NotNull  @NotBlank  @NotEmpty  @Size  @Min  @Max  @Email  @Pattern
文档         @Tag  @Operation  @Schema
```

## 附录 B · 启动失败的常见原因

按本项目实际遇到过的顺序排列：

| 症状 | 原因 | 处理 |
|---|---|---|
| `Web server failed to start. Port 8080 was already in use` | 端口被占（本地开发后端 + 容器都可能占） | 停掉占用进程，或改 `server.port` |
| `Communications link failure` | **MySQL 没启动** | 跑 `mysql-start.ps1`。**本项目踩过：重启电脑后中间件都没起，17 个测试全挂** |
| `JWT_SECRET` 相关启动失败 | `application-prod.yml` 里该配置无默认值 | 在 `.env` 里补上 |
| `NoClassDefFoundError: net.sf.jsqlparser...` | 少引入 `mybatis-plus-jsqlparser` | MyBatis-Plus 3.5.9+ 把分页依赖拆成独立模块了 |
| `MinIO bucket 初始化失败，上传接口将不可用` | MinIO 没启动 | **只是 WARN，不影响启动**；上传时才报错 |
| `Table 'diary.xxx' doesn't exist` | 没执行建表脚本 | 跑 `sql/init.sql` |
| SpringDoc 的两条 WARN | 接口文档默认开启 | 生产环境已在 `application-prod.yml` 关闭，dev 下忽略即可 |

---

## 下一篇

`doc/backend/03-persistence.md`：持久层——Entity 映射、条件构造器、分页、**逻辑删除的取舍**、**避免 N+1**、事务边界。会用本项目实测的 SQL 日志来说明每一条。
