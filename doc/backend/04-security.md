# 模块 04 · 认证与鉴权

> 读者：已读完 `01`–`03`
> 目标：**能讲清 Access / Refresh Token 的分工与轮换；能自己实现一个过滤器；记住 5 个常见安全坑**
> 时长：约 1 天
> 上一篇：`03-persistence.md`

---

## 0. 这一篇解决什么问题

前端你已经很熟悉这套流程了：登录拿 token、请求带上、过期自动刷新。但**为什么这么设计**、**后端每一步在做什么**、**哪里最容易出安全事故**，是这一篇的内容。

先给一个总览图，后面每节展开一块：

```text
                      ┌──────────────────────────────────────┐
   ① 登录              │ AuthServiceImpl.login()              │
   浏览器 ──────────►  │   查库 → BCrypt.matches 校验密码      │
                      │   签发 access(30min) + refresh(7d)    │
                      │   refresh 存 Redis                    │
                      └──────────────────────────────────────┘
   ② 业务请求           ┌──────────────────────────────────────┐
   浏览器 ──────────►  │ JwtAuthenticationFilter              │
   Header: Bearer      │   解析 → 验签 → 塞进 SecurityContext   │
                      │ SecurityConfig 判断路径是否放行        │
                      │ Service 用 SecurityUtil 取 userId    │
                      └──────────────────────────────────────┘
   ③ access 过期        ┌──────────────────────────────────────┐
   后端 401 ────────►  │ AuthServiceImpl.refresh()            │
   前端自动刷新         │   验签 → 查 Redis 是否存在 → 删除      │
                      │   重新签发一对（轮换）                 │
                      └──────────────────────────────────────┘
   ④ 登出              ┌──────────────────────────────────────┐
                      │ AuthServiceImpl.logout()             │
                      │   从 Redis 删掉该 refresh             │
                      └──────────────────────────────────────┘
```

---

## 1. 为什么不用 Session

传统 Session 方案（也是你写 Express 时最自然的做法）：

```text
登录 → 服务端创建 session 存内存/Redis → 返回 sessionId 给浏览器（Cookie）
后续请求 → 浏览器自动带 Cookie → 服务端拿 sessionId 查 session
```

**问题在「无状态」这三个字上**：

| 问题 | 说明 |
|---|---|
| 服务端要存状态 | 多实例部署时 session 必须共享（Redis），否则用户跳到另一台就被登出 |
| Cookie 的跨域限制 | 前后端不同域时（尤其移动端 / 第三方客户端）Cookie 规则很麻烦 |
| CSRF | 浏览器自动带 Cookie 的特性本身就是 CSRF 的土壤，必须额外防护 |
| 无法跨服务 | 网关、微服务之间传 Cookie 很别扭 |

**JWT 的解法**：把「我是谁」这个事实**签个名交给客户端保管**，服务端不存。请求来了只需验签，不用查任何存储。

**代价（必须一起记住）**：

| 代价 | 后果 | 本项目的缓解方式 |
|---|---|---|
| 无法主动失效 | 令牌签发后在有效期内一直管用 | Access 只有 30 分钟；真正的撤销能力交给 Refresh（存 Redis） |
| 令牌变大 | 每个请求都带几百字节 | 只放最少信息（`uid` / `username` / `jti`） |
| 时钟依赖 | 客户端/服务端时间差会影响 `exp` 判断 | 服务端**只信自己的时间**，不看客户端 |

> 这里有一个**关键的架构取舍**：JWT 给不了「立即踢下线」的能力。本项目用「短命 Access + 可撤销 Refresh + Refresh 存在 Redis」这三件事组合，把「无法撤销」的窗口压到 30 分钟以内。

---

## 2. JWT 结构（三段）

### 2.1 拆开看

登录后拿到的 token 长得像：

```text
eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIiwidWlkIjoxLCJ1c2VybmFtZSI6InRlc3RlciIsImp0aSI6ImFiMDcxZGVhLTk1NWItNGRkNy04YTM4LTQ3MjgzMzE3MWFlZiIsImlhdCI6MTc2MDAwMDAwMCwiZXhwIjoxNzYwMDAxODAwfQ.<signature>
└──────── ① header ─────┘└──────────────────── ② payload ────────────────────┘└── ③ signature ──┘
```

**每一段都是 `Base64URL 编码的 JSON`。** 解出来看看（实测）：

```powershell
$h = $token.Split('.')[0]                                        # 取令牌头
$padded = $h.PadRight($h.Length + (4 - $h.Length % 4) % 4, '=')  # base64 补位
[System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($padded))
# 输出：{"alg":"HS512"}
```

payload 解出来：

```json
{
  "sub": "1",                                       // 标准声明：subject
  "uid": 1,                                         // 自定义：用户 ID
  "username": "tester",
  "jti": "ab071dea-955b-4d57-8a38-472833171aef",    // 标准声明：令牌唯一 ID
  "iat": 1760000000,                                // 签发时间
  "exp": 1760001800                                 // 过期时间
}
```

### 2.2 ⚠️ payload 不是加密的

**这一条必须刻在脑子里**：JWT 的 payload **只是 Base64 编码**，任何人都能解开。

```text
Base64 编码 ≠ 加密
签名的作用是「防篡改」，不是「防偷看」
```

**推论**：**任何敏感信息都不能放进 payload**——密码、手机号、身份证号、内部权限位（如果泄露有风险的话）。本项目只放了 `userId` / `username` / `jti`，这三样即使被看到也无害。

> 想验证这一点：把你的 token 复制到 `jwt.io` 或上面那段 PowerShell 里，payload 会原样显示。

### 2.3 签名算法由密钥长度决定（本项目的一个细节）

`JwtUtil` 里没有指定算法：

```java
byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
if (keyBytes.length < 32) {
    throw new IllegalStateException("diary.jwt.secret 不足 32 字节，无法满足 HS256 要求");
}
this.secretKey = Keys.hmacShaKeyFor(keyBytes);   // ← 只给密钥，不给算法
```

```java
.signWith(secretKey)                             // ← 由 JJWT 按密钥长度自动选择
```

| 密钥长度 | 实际算法 | 令牌头 |
|---|---|---|
| 32~47 字节 | HS256 | `{"alg":"HS256"}` |
| 48~63 字节 | HS384 | `{"alg":"HS384"}` |
| ≥ 64 字节 | **HS512** | `{"alg":"HS512"}` |

本项目 `.env` 里是 64 字节（随机生成），所以**实际是 HS512**。

> **注意**：异常消息写的是「HS256 要求」，那是沿用初版设计的措辞，实际算法由长度决定。知道这点，排查令牌问题时就不会被算法名误导。

---

## 3. 两段令牌的分工与轮换

### 3.1 分工

| | Access Token | Refresh Token |
|---|---|---|
| 有效期 | 30 分钟（`JWT_ACCESS_EXPIRE`） | 7 天（`JWT_REFRESH_EXPIRE`） |
| 服务端是否存储 | **不存**（纯验签） | **存 Redis** |
| 用途 | 每个业务请求的 `Authorization` 头 | 只能用于换新令牌 |
| 能否撤销 | 不能 | **能**（删 Redis 记录即失效） |
| 验证成本 | 一次签名计算 | 签名计算 + **一次 Redis 查询** |

**为什么要两段**：校验频率与撤销需求是矛盾的——业务请求要快（不查库/不查 Redis），撤销要准（必须有服务端状态）。拆成两段，让高频的那个无状态、低频的那个有状态，是最优解。

### 3.2 轮换（Rotation）

```java
@Override
public TokenVO refresh(String refreshToken) {
    Claims claims = parseRefreshTokenOrThrow(refreshToken);
    Long userId = jwtUtil.getUserId(claims);
    String jti = claims.getId();

    // ① 必须能在 Redis 里找到这一枚（证明没被用过/撤销过）
    if (userId == null || jti == null || !refreshTokenStore.exists(userId, jti)) {
        throw new BizException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
    }

    User user = userMapper.selectById(userId);
    if (user == null) {
        throw new BizException(ResultCode.UNAUTHORIZED, "登录已失效，请重新登录");
    }

    // ② 令牌轮换：旧 Refresh Token 立即失效，防止被重复使用
    refreshTokenStore.remove(userId, jti);
    return issueTokens(user);
}
```

**轮换解决什么问题**：如果 Refresh Token 不轮换，它就是一个「7 天内可以无限次换新 access 的长期凭证」。一旦泄露，攻击者可以持续换新令牌，而合法用户毫无察觉。

**轮换之后**：每枚 Refresh Token **只能用一次**。如果攻击者偷用了一次，合法用户下次刷新就会失败（因为 Redis 里那枚已被删）——**用户被动发现异常**。这就是「可检测性」。

**实测验证**（`usage-guide.md` 第 9.3 节有完整命令）：

```text
第 4 步：刷新令牌            → 新 refreshToken 与旧的不同: True
第 5 步：用旧 refreshToken 再刷 → HTTP 401
```

### 3.3 Redis 的 Key 设计

```java
private static final String KEY_PREFIX = "diary:refresh:";

private String buildKey(Long userId, String jti) {
    return KEY_PREFIX + userId + ":" + jti;
}
```

Key 形如 `diary:refresh:1:ab071dea-955b-4d57-8a38-472833171aef`。三个设计点：

| 设计 | 为什么 |
|---|---|
| 前缀 `diary:refresh:` | 与其他业务键区分；多项目共用一个 Redis 时不会串 |
| 中间放 `userId` | **按用户组织**，便于「踢下线」时一次性清除某人全部令牌（`keys("diary:refresh:1:*")`） |
| 末尾放 `jti` | 区分同一用户的多个设备/多次登录——**同一个用户可以在多台设备各持一枚** |

**value 存的是用户名**（而不是空值或 `1`）——方便运维时直接 `redis-cli get` 看到是谁的令牌，不用再查库。

**TTL 由 Redis 自动过期**（`Duration.ofSeconds(ttlSeconds)`）——不需要清理任务，令牌到期自动消失。这是 Redis 相比 MySQL 的天然优势。

**一个已知的待优化点**：

```java
/** 踢下线：清除该用户全部 Refresh Token。使用 KEYS 匹配，属低频管理操作，可接受 */
public long removeAll(Long userId) {
    Set<String> keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
    ...
}
```

`KEYS` 命令会**阻塞 Redis**（它要遍历全部键），生产环境通常禁用。注释里已经标明了「若并发量增大可换成 SCAN」——这是一个**有意识留下的技术债**，不是疏忽。

---

## 4. 密码存储：BCrypt

### 4.1 为什么不能用 MD5 / SHA256

```text
MD5("123456") = e10adc3949ba59abbe56e057f20f883e
```

问题有两个：

1. **无盐**：所有用户相同的密码得到相同的摘要 → 一次彩虹表查询就能还原，或一眼看出「这两个用户密码一样」
2. **太快**：GPU 每秒能算几十亿次 → 8 位以内的密码几小时就能暴力枚举完

**密码哈希的第一原则：要慢。**

### 4.2 BCrypt 怎么解决

```text
$2b$10$8YLZJsdPYwLQA.QjYG91g.cmYqZlNgJAzX9O.j/OKzcaaSjS3ot0q
└┬─┘└┬┘└─────────────── 22 字符 salt ───────────┘└──── 31 字符摘要 ────┘
 │   └── cost = 10（2^10 = 1024 轮迭代）
 └── 版本号（$2b$ 是当前标准）
```

| 特性 | 说明 |
|---|---|
| **自带盐** | 盐就存在哈希串里（所以不需要单独的 salt 列） |
| **可调 cost** | cost 每加 1，计算耗时翻倍。10 大约 50~100ms——够慢到拖垮暴力破解，又快到不影响登录体验 |
| **同一密码每次结果不同** | 因为盐随机。所以**只能用 `matches()` 验证，不能用 `equals()` 比对** |

```java
user.setPassword(passwordEncoder.encode(dto.password()));       // 注册：加密

if (user == null || !passwordEncoder.matches(dto.password(), user.getPassword())) {   // 登录：验证
    throw new BizException(ResultCode.BAD_REQUEST, "用户名或密码错误");
}
```

> `matches(明文, 密文)` 内部会从密文里解析出盐和 cost，再用同样参数算一遍明文去比对。**所以你永远不需要知道盐是什么。**

### 4.3 本项目的一个跨语言验证（值得记住）

`init.sql` 里测试账号 `tester` 的密文是**用 Node 的 `bcryptjs` 生成的**：

```sql
INSERT INTO `t_user` ...
SELECT 'tester', '$2b$10$8YLZJsdPYwLQA.QjYG91g.cmYqZlNgJAzX9O.j/OKzcaaSjS3ot0q', '测试用户', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `t_user` WHERE `username` = 'tester');
```

**Java 的 `BCryptPasswordEncoder` 能直接校验它**——这一点被 P2 的单元测试 `builtinTesterAccountCanLogin` 专门验证过。

**为什么值得强调**：BCrypt 是**跨语言有标准实现**的算法。这意味着你可以在 Node / Python / Go 里生成密文，Java 侧照样能验。反过来，如果你用某种「自创的加盐方式」，就完全不具备这种互操作性。

---

## 5. Spring Security 过滤链

### 5.1 全景

一个请求进入 Tomcat 后，会**按顺序**穿过一串过滤器。本项目相关的是这三个：

```text
请求 ──► JwtAuthenticationFilter ──► Spring Security 授权判断 ──► DispatcherServlet ──► Controller
             （自定义，第 1 个）              │
                                              ├─ 路径在白名单？→ 直接放行
                                              ├─ 已认证？→ 放行
                                              └─ 未认证 → JwtAuthenticationEntryPoint（401 JSON）
```

### 5.2 `SecurityConfig` 逐段读

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 无需认证即可访问的路径 */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/**",       // 登录 / 注册 / 刷新 / 登出
            "/api/v1/public/**",     // 预留：公开分享
            "/api/v1/ping",          // 健康检查（容器健康检查用它）
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离 + 无状态 token，无需 CSRF
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)     // 401
                        .accessDeniedHandler(accessDeniedHandler))              // 403
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

**逐项解释**：

| 配置 | 作用 | 为什么 |
|---|---|---|
| `.csrf(disable)` | 关闭 CSRF 防护 | CSRF 攻击依赖「浏览器自动带 Cookie」；我们用 Header 传 token，浏览器不会自动带，天然免疫 |
| `.cors(...)` | 跨域规则 | **必须在 Security 层声明**——放在别处会产生重复的 `Access-Control-Allow-Origin` 头（P2 时删掉了 P1 的 `CorsConfig` 就是这个原因） |
| `.formLogin(disable)` / `.httpBasic(disable)` | 关掉默认登录页与 Basic 认证 | 不用它们，留着会干扰响应格式 |
| `.sessionManagement(STATELESS)` | 不创建 HttpSession | 无状态的核心；也让「一次请求一个 session」的内存开销消失 |
| `.authorizeHttpRequests(...)` | 授权规则 | 白名单放行，其余全要认证 |
| `.exceptionHandling(...)` | 401/403 转成统一 JSON | 否则 Spring 默认返回一个 HTML 登录页或空响应，前端没法处理 |
| `.addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)` | 把自定义过滤器插到正确位置 | 必须在授权判断**之前**执行，否则 SecurityContext 还没被填充 |

> **`.formLogin(disable)` 的意义**：Spring Security 默认会给你一个 HTML 登录页。不关掉的话，未认证请求可能被 302 重定向到 `/login` 而不是返回 401 JSON——**这是前后端分离项目最常见的「莫名其妙跳转」来源**。

### 5.3 `JwtAuthenticationFilter` 逐行读

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtUtil.parse(token);
                LoginUser loginUser = new LoginUser(jwtUtil.getUserId(claims), jwtUtil.getUsername(claims));

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("JWT 校验失败，按未认证处理: {}", e.getMessage());     // ← 注意是 debug
            }
        }

        filterChain.doFilter(request, response);       // ← 无论如何都继续往下走
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (!StringUtils.hasText(header) || !header.startsWith(PREFIX)) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();     // 去掉 "Bearer " 前缀
        return token.isEmpty() ? null : token;
    }
}
```

**四个设计要点**：

| 要点 | 为什么这么做 |
|---|---|
| **继承 `OncePerRequestFilter`** | 保证一次请求只执行一次（转发/包含时不会重复） |
| **校验失败不直接返回响应** | 注释里写了：**放行给 EntryPoint**。这样 `/api/v1/auth/login` 这种白名单接口，即使带了一个坏 token 也能正常访问 |
| **`getAuthentication() == null` 判断** | 避免覆盖已经存在的认证信息 |
| **失败只记 `debug`** | 攻击者会大量发送畸形 token 来刷日志；用 debug 级别可以避免日志被灌满 |

**`LoginUser` 为什么只存两个字段**：

```java
public record LoginUser(Long userId, String username) { }
```

注释说得很清楚：「只携带鉴权必需的最小信息，**避免每次请求都查库**」。

> 这是一个重要的性能决策：如果 `LoginUser` 存整个 `User` 对象，就需要每次请求都从数据库加载用户——那 JWT「无状态」带来的性能优势就全没了。

### 5.4 怎么取当前用户：`SecurityUtil`

```java
public static LoginUser getCurrentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
        throw new BizException(ResultCode.UNAUTHORIZED);
    }
    return loginUser;
}

public static Long getCurrentUserId() {
    return getCurrentUser().userId();
}
```

**业务层唯一的取用户入口**：

```java
// DiaryServiceImpl.page()
Long userId = SecurityUtil.getCurrentUserId();
...
.eq(Diary::getUserId, userId)          // ← 数据隔离的底线
```

> 类注释里有一句关键的话：「**所有需要数据隔离的业务都应通过这里拿 userId，禁止从请求参数取**」。
> 如果哪个接口写成 `@RequestParam Long userId`，那用户 A 传 `userId=2` 就能看用户 B 的日记——**这是最典型、也最严重的越权漏洞**。

---

## 6. 登录 / 刷新 / 登出的完整实现

### 6.1 注册

```java
@Override
@Transactional
public UserVO register(RegisterDTO dto) {
    Long exists = userMapper.selectCount(
            Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.username()));
    if (exists != null && exists > 0) {
        throw new BizException(ResultCode.BAD_REQUEST, "用户名已被占用");
    }

    User user = new User();
    user.setUsername(dto.username());
    user.setPassword(passwordEncoder.encode(dto.password()));      // ← 只存密文
    user.setNickname(StringUtils.hasText(dto.nickname()) ? dto.nickname() : dto.username());
    user.setEmail(dto.email());
    userMapper.insert(user);

    log.info("新用户注册成功: id={}, username={}", user.getId(), user.getUsername());
    return UserConverter.toVO(user);                                // ← UserVO 不含 password
}
```

**注意三件事**：

1. **查重 + `uk_username` 唯一索引**是双保险（见模块 03 第 10 节）——查重给友好提示，索引防并发漏网
2. **`password` 字段从不返回给前端**：`UserConverter.toVO()` 只映射 `id/username/nickname/avatar/email`（`UserVO` 里根本没有 password 字段）
3. **日志不打印密码**：`log.info` 只打了 id 和 username

### 6.2 登录：防用户名枚举

```java
@Override
public TokenVO login(LoginDTO dto) {
    User user = userMapper.selectOne(
            Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.username()));

    // 不区分「用户不存在」与「密码错误」，避免用户名枚举
    if (user == null || !passwordEncoder.matches(dto.password(), user.getPassword())) {
        throw new BizException(ResultCode.BAD_REQUEST, "用户名或密码错误");
    }

    return issueTokens(user);
}
```

**为什么合并两种情况**：如果「用户不存在」返回「该用户不存在」、「密码错误」返回「密码错误」，攻击者就能用它来**批量探测哪些用户名已注册**（用户名枚举），为后续撞库做准备。

合并之后，两者返回**完全一样**的响应。实测：

```text
POST /auth/login  {"username":"tester","password":"错误密码"}
→ HTTP 400  {"code":400,"message":"用户名或密码错误"}
```

> **顺带指出一处不可避免的信息泄露**：注册接口返回「用户名已被占用」，这本身就泄露了该用户名存在。这是**注册流程的固有矛盾**——不告诉用户就无法注册。常见的缓解手段（邮件/短信验证后再提示）在个人项目里通常不值得做，知道这个取舍即可。

### 6.3 签发令牌

```java
private TokenVO issueTokens(User user) {
    JwtUtil.IssuedToken access = jwtUtil.createAccessToken(user.getId(), user.getUsername());
    JwtUtil.IssuedToken refresh = jwtUtil.createRefreshToken(user.getId(), user.getUsername());

    refreshTokenStore.save(user.getId(), refresh.jti(), user.getUsername(), jwtUtil.getRefreshExpireSeconds());

    return new TokenVO(access.token(), refresh.token(), access.expiresInSeconds(), UserConverter.toVO(user));
}
```

**注意 `IssuedToken` 这个 record**（回顾模块 01）：

```java
public record IssuedToken(String token, String jti, long expiresInSeconds) { }
```

**为什么签发时就把 `jti` 一起返回**——因为紧接着要把它存进 Redis。如果只返回 token 字符串，调用方为了拿 `jti` 就得**再解析一遍** token。这个小设计省掉了一次重复解析。

### 6.4 登出：容忍失败

```java
@Override
public void logout(String refreshToken) {
    try {
        Claims claims = jwtUtil.parse(refreshToken);
        Long userId = jwtUtil.getUserId(claims);
        if (userId != null && claims.getId() != null) {
            refreshTokenStore.remove(userId, claims.getId());
        }
    } catch (JwtException | IllegalArgumentException e) {
        // 令牌已过期或非法时，登出视为成功，避免前端卡在登出流程
        log.debug("登出时令牌解析失败，按已登出处理: {}", e.getMessage());
    }
}
```

**为什么「解析失败也算登出成功」**：用户点登出时，如果 Refresh Token 已经过期（7 天没登录），解析必然失败。此时应该**让用户顺利退出**，而不是弹一个「登出失败」——那样前端会卡在登出流程里，用户被迫手动清缓存。

**这是一个很好的「按用户意图而非技术事实设计 API」的例子**：从技术上说，删掉一个不存在的键是「无操作」；从用户意图上说，「我要退出登录」已经达成了。

---

## 7. 五个必须记住的安全坑

### 坑 1 · 越权（最严重）

**两种越权**：

| 类型 | 例子 |
|---|---|
| **横向越权**（同级别） | 用户 A 通过 `GET /diaries/7` 读到用户 B 的日记 |
| **纵向越权**（跨级别） | 普通用户调用管理员接口 |

**本项目的三道防线**：

```java
// ① 列表查询：强制带 user_id 条件，不可绕过
.eq(Diary::getUserId, userId)

// ② 单条访问：先校验归属，查不到统一按「不存在」处理
private Diary requireOwned(Long id, Long userId) {
    Diary diary = diaryMapper.selectOne(Wrappers.<Diary>lambdaQuery()
            .eq(Diary::getId, id)
            .eq(Diary::getUserId, userId));           // ← 两个条件一起查
    if (diary == null) {
        throw new BizException(ResultCode.NOT_FOUND, "日记不存在");
    }
    return diary;
}

// ③ 越权返回 404 而不是 403，避免探测他人资源是否存在
```

**第 ③ 点的测试方法**（你也可以自己验证）：

```powershell
# 用 tester 登录拿 token，访问一个明显不属于他的 id
GET /api/v1/diaries/999999  →  404「日记不存在」
```

如果返回 **403**，攻击者就知道「这个 id 存在，只是不属于我」；返回 **404** 则无法区分「不存在」和「不属于我」——**不泄露信息**。

**更隐蔽的一类**：`replaceTags()` 里防止给日记挂上别人的标签——

```java
Long ownedCount = tagMapper.selectCount(Wrappers.<Tag>lambdaQuery()
        .in(Tag::getId, distinctTagIds)
        .eq(Tag::getUserId, userId));
if (ownedCount == null || ownedCount != distinctTagIds.size()) {
    throw new BizException(ResultCode.BAD_REQUEST, "存在无效或不属于你的标签");
}
```

如果少了这个校验，用户就能通过「给自己的日记挂上别人的标签 id」来探测别人有哪些标签。

### 坑 2 · 密钥管理

**JWT 的密钥泄露 = 所有账号沦陷**（攻击者可以伪造任意用户的合法令牌）。

**本项目的四条措施**：

| 措施 | 位置 |
|---|---|
| 不硬编码在代码里 | `application.yml` 用 `${JWT_SECRET:...}` 占位 |
| **生产不给默认值** | `application-prod.yml` 写 `${JWT_SECRET}`，缺失则**启动失败** |
| `.env` 不入库 | `.gitignore` 忽略；只提交 `.env.example` |
| 长度足够 | `JwtUtil` 构造时校验 `< 32` 字节直接抛异常 |

```yaml
# application-prod.yml —— 没有任何默认值
diary:
  jwt:
    # 生产环境不给默认值：缺失则启动失败，杜绝用开发密钥上线
    secret: ${JWT_SECRET}
```

**为什么「缺失就启动失败」比「给个默认值」好**：后者会让系统在「看起来正常」的状态下用着一个公开的弱密钥运行——**故障被推迟到被攻击那一刻才爆发**。

> 同理，`docker-compose.yml` 里用了 `${VAR:?请在 .env 中设置 XXX}` 语法——**变量缺失时 compose 直接报错退出**。

### 坑 3 · 日志泄露

**日志是安全审计最容易忽略的一环**。三类内容绝对不能进日志：

| 内容 | 本项目怎么处理 |
|---|---|
| **密码** | `register` 的 `log.info` 只打 id 与 username |
| **令牌** | 任何地方都没有把 token 写进日志 |
| **用户数据（日记正文、搜索关键词）** | `application-prod.yml` **关闭 SQL 日志** |

第三点值得展开——开发环境开的 SQL 日志长这样：

```text
==>  Preparing: SELECT ... FROM t_diary WHERE deleted=0 AND (user_id = ?) AND (title LIKE ? OR summary LIKE ?)
==> Parameters: 1(Long), %爬山%(String)
```

**参数是明文打印的**。生产环境如果开着这个，用户的每一条搜索词、每一篇日记内容都会进日志文件——**既违反隐私，又可能被合规审计判违规**。

> 顺带一提：`JwtAuthenticationFilter` 里的失败日志用的是 `log.debug` 而不是 `warn`——因为攻击者可以用畸形 token 刷请求，`warn` 级别能让日志文件迅速膨胀。

### 坑 4 · CORS 误配

本项目在 `SecurityConfig` 里：

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    ...
}
```

**最危险的组合**是「`allowCredentials(true)` + 通配符来源」：

```java
config.setAllowedOriginPatterns(List.of("*"));   // ❌ 配合 allowCredentials(true) 等于对所有网站开放带凭证的跨域
config.setAllowCredentials(true);
```

后果：**任意恶意网站都能以用户身份调用你的 API**（浏览器会带上 Cookie/凭证）。本项目只放行 `localhost`，所以安全。

**但要理解本项目的真实处境**：**生产环境这段配置其实不生效**——前端由 Nginx 同源反代，浏览器根本不发跨域请求。它只在本地开发（5173 → 8080）时起作用。

> **推论（值得警惕）**：如果将来把前端部署到 `https://diary.example.com` 而后端在另一个域，**必须在 `allowedOriginPatterns` 里补上真实域名**，否则前端会全部跨域失败。

### 坑 5 · 令牌的使用与存储（前端侧）

这一条虽然在前端，但后端要清楚：

| 做法 | 风险 |
|---|---|
| 把 token 存 `localStorage` | **XSS 可读**——页面里任何一个注入的脚本都能偷走 |
| 把 token 存 `Cookie`（httpOnly） | XSS 读不到，但**需要防 CSRF**（要额外的 CSRF token） |

**本项目选了 `localStorage`**（`frontend/src/store/useUserStore.ts` 的 `persist` 存在 `diary-auth` 这个 key 下），代价是**必须防 XSS**——这也是为什么日记详情页的 Markdown 渲染用了 `rehype-sanitize`：

```tsx
// pages/DiaryDetail —— 渲染用户输入的 Markdown 时必须过滤
import rehypeSanitize from 'rehype-sanitize'
```

**这两个选择是配套的**：`localStorage` 承担 XSS 风险 → 所以必须做 XSS 过滤。如果哪天改用 httpOnly Cookie，风险重心就转到 CSRF 上了。

---

## 8. 动手练习

1. **解令牌**：登录后取 `accessToken`，用第 2.1 节的 PowerShell 片段解出 header 与 payload，确认里面**没有**密码等敏感信息，并看清 `exp` 是多少（应从签发时间起 30 分钟）
2. **验证轮换**：连续调两次 `/auth/refresh`，第二次用**第一次的令牌**，确认返回 401（完整命令见 `usage-guide.md` 第 9.3 节）
3. **看 Redis**：`docker compose exec -T redis redis-cli --scan`，对照 Key 结构 `diary:refresh:{userId}:{jti}` 理解它为什么能支持多设备
4. **验证越权防护**：用两个账号关注同一篇日记，确认跨用户访问返回 **404 而不是 403**
5. **验证用户名枚举防护**：分别用「不存在的用户 + 任意密码」和「存在的用户 + 错误密码」登录，确认返回**完全相同**的响应
6. **制造一次启动失败**：临时把 `.env` 里的 `JWT_SECRET` 改成 20 个字符，重启后端容器，观察是否**启动失败**（`JwtUtil` 的长度校验）。验证完改回来
7. **读过滤器顺序**：在后端启动日志里搜索 `Filter`，看看里面有没有 `SecurityFilterChain` 的信息。理解 `.addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)` 到底把它插在哪儿

---

## 附录 · 认证问题排查清单

按「从前到后」的顺序排查，能快速缩小范围：

```text
① 请求头有没有带？
   看 DevTools 的 Request Headers 是否含 Authorization: Bearer xxx

② 格式对不对？
   少了 "Bearer " 前缀 → JwtAuthenticationFilter.resolveToken 返回 null → 401

③ 令牌过期了吗？
   解 payload 看 exp；过期属正常流程，前端应自动刷新

④ 签名对吗？
   换过 JWT_SECRET 的话，旧令牌全部失效（签名对不上）→ 只能重新登录

⑤ 刷新为什么失败？
   常见原因：Refresh Token 已被用过（轮换后旧的失效）
            Redis 里没有该 jti（重启清过 Redis？被踢下线过？）
            用户已被删除（userMapper.selectById 返回 null）

⑥ 是不是路径问题？
   接口不在 PUBLIC_PATHS 里却想免登录访问 → 401

⑦ 404 还是 403？
   404 = 路由不存在 或 Service 主动抛的越权判断
   403 = 已认证但权限不足（本项目基本用不到）
```

**看 Redis 判断令牌状态**：

```powershell
cd <仓库根目录>
docker compose exec -T redis redis-cli --scan                    # 列出全部 refresh token
docker compose exec -T redis redis-cli get "diary:refresh:1:xxx" # 看某一枚的值（用户名）
docker compose exec -T redis redis-cli ttl "diary:refresh:1:xxx" # 看剩余有效期
```

---

## 下一篇

`doc/backend/05-database.md`：MySQL 与 SQL——5 张表的设计理由、索引的取舍、JOIN 与聚合、事务与隔离级别、慢查询诊断，以及**完成一次真实的备份恢复演练**。
