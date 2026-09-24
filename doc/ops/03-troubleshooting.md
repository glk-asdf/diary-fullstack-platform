# 模块 ops-03 · 排障：分层定位与实战复盘

> 读者：已读完 `ops/01-docker.md`、`ops/02-deploy.md`
> 目标：**能在 10 分钟内定位「站点打不开」这类问题出在哪一层；能读懂健康检查日志；能完成一次备份恢复演练**
> 时长：约 1 天
> 上一篇：`ops/02-deploy.md`

---

## 0. 这一篇解决什么问题

前面两篇讲「怎么把系统跑起来」。这一篇讲**它出问题时怎么办**——而这恰恰是从前端转全栈时**最缺、也最没有教程**的一块能力。

前端的排障体验是舒适的：报错就在浏览器控制台里，堆栈能点回源码。**后端与运维没有这个体验**——一个「站点打不开」背后可能是七八种原因，每一种都要用不同的手段去查。

所以本篇的核心不是罗列命令，而是**给出一套可以重复使用的定位顺序**。

---

## 1. 排障的第一原则：分层定位

### 1.1 请求要穿过几层

```text
浏览器
  │ ①
  ▼
宿主机端口（80 是否在监听？防火墙？）
  │ ②
  ▼
Nginx 容器（配置对不对？静态文件在不在？）
  │ ③                                    ④（/files/ 走这条）
  ▼                                        ▼
后端容器（Spring 起来了吗？）           MinIO 容器
  │ ⑤
  ▼
MySQL / Redis（连得上吗？）
```

**「站点打不开」这五个字，可能对应上面任意一层的问题。** 分层定位的意思是：**从上往下逐层确认，而不是同时怀疑所有层。**

### 1.2 为什么不能从代码看起

这是我在本项目里**真实犯过**的错误：

> 我改完 `GlobalExceptionHandler` 后跑测试，**17 个测试全部报错**。第一反应是「我刚才的改动有问题」，于是去读刚改的代码——看了几分钟没找到问题。
>
> 实际情况是：**机器刚重启，本机 MySQL 和 Redis 都没启动**，测试连不上数据库。**和我的改动毫无关系。**

**正确的顺序**是：

```text
① 环境在吗？（进程、端口）
② 服务能连上吗？（网络、认证）
③ 才有可能是代码
```

**代码是最后才该怀疑的对象**，因为它是变化最慢、最不可能突然坏掉的东西。

### 1.3 症状 → 层的对照表

先学会「看症状猜层」，能省掉大量时间：

| 症状 | 最可能的层 | 先查什么 |
|---|---|---|
| 浏览器「无法访问此网站」 | ① 宿主端口 | 端口有没有在监听 |
| 502 Bad Gateway | ③ 后端 | 后端容器活着吗？IP 变了吗？ |
| 504 Gateway Timeout | ③ 后端 | 后端卡住了（长事务？死锁？） |
| 404 | ② Nginx 或后端路由 | 静态文件在不在 / 路由是否存在 |
| 401 / 403 | ⑤ 应用（认证） | token 是否有效 |
| 500 | ⑤ 应用 | **看后端日志的堆栈** |
| 静态页面正常但接口全挂 | ③ 后端 | 后端容器状态 |
| 接口正常但图片不显示 | ④ MinIO | minio 健康状态、`/files/` 反代配置 |
| 页面能开但很慢 | ⑤ 数据库 | 慢查询、锁等待 |
| 数据「丢了」 | ⑤ 数据库 | 是不是被逻辑删除了？是不是连错库了？ |

---

## 2. 第一层：网络与入口

### 2.1 三个必须会的检查

```powershell
# ① 宿主机上 80 端口有没有在监听
Get-NetTCPConnection -LocalPort 80 -State Listen | Select-Object LocalAddress, LocalPort, OwningProcess
# 期望：LocalAddress=0.0.0.0 或 ::，OwningProcess 是 com.docker.backend

# ② 端口映射是否正确
docker compose ps --format 'table {{.Service}}\t{{.Status}}\t{{.Ports}}'
# 期望：nginx 那行有 0.0.0.0:80->80/tcp

# ③ 直连容器自身（绕过宿主机端口映射）
docker compose exec -T nginx wget -qO- http://127.0.0.1/ | Select-Object -First 3
```

**这三步能把问题切成两半**：

| ① | ② | 结论 |
|---|---|---|
| ✅ | ✅ | 宿主机端口没问题，问题在后面几层 |
| ❌ | ✅ | **宿主机端口冲突或 Docker 端口转发没生效**（常见：别的程序占了 80） |
| ❌ | ❌ | 容器没起来 |

### 2.2 `LocalAddress` 的读法（接 `ops/01` 第 7.2 节）

| 显示 | 含义 |
|---|---|
| `0.0.0.0:80` 或 `[::]:80` | 所有网卡都能访问（局域网可访问） |
| `127.0.0.1:3307` | **只有本机能访问**（本项目故意这么配 MySQL） |

**如果你的服务在局域网里访问不到，先看是不是绑成了 `127.0.0.1`。**

### 2.3 HTTP 状态码的语义（排障时最有用的信息）

```text
     ┌─ 连不上（Connection refused / timeout）  →  ① 端口/网络
HTTP ┼─ 502 Bad Gateway                        →  ③ nginx 连不上后端
     ├─ 504 Gateway Timeout                    →  ③ 后端响应太慢
     ├─ 404                                    →  ② 静态文件不存在 或 ⑤ 路由不存在
     ├─ 401 / 403                              →  ⑤ 认证/授权
     └─ 500                                    →  ⑤ 应用内部异常（看日志）
```

**502 是部署后最常见的一个**，它有个专门的原因（nginx 缓存了失效的上游 IP），本项目已经修过——见第 6 节案例 3。

### 2.4 一个容易搞错的坑：nginx 会「兜住」404

本项目的 nginx 有 SPA 回退：

```nginx
location / {
    try_files $uri $uri/ /index.html;
}
```

**后果**：**任何**未匹配的路径都会返回 `200 + index.html`，而不是 404。

```powershell
curl.exe -s -o NUL -w '%{http_code}' http://localhost/a/b/c/d
# → 200（不是 404！）
```

**所以用 `curl http://localhost/xxx` 判断「某个路径是否存在」是不可靠的。** 判断方法：

```powershell
# 看返回的是不是前端首页
curl.exe -s http://localhost/some/path | Select-String '<div id="root"'
# 命中 → 被 SPA 回退兜住了，不代表这个路径真实存在
```

**这也解释了我在 P8 验收时的一次误判**：我访问 `http://localhost/v3/api-docs` 得到 `200`，一度以为「生产没关掉接口文档」。实际那是 nginx 返回了首页 HTML，**要在后端容器内测才准**：

```powershell
docker compose exec -T backend curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/v3/api-docs
# → 404（这才是真实情况：生产已关闭）
```

---

## 3. 第二层：容器

### 3.1 状态解读

`docker compose ps` 里的 `STATUS` 列可能值：

| 状态 | 含义 | 下一步 |
|---|---|---|
| `Up (health: starting)` | 正在跑，健康检查还没通过 | 等；或看健康检查日志 |
| `Up (healthy)` | 健康 | 正常 |
| `Up (unhealthy)` | **健康检查连续失败超过 retries** | 进容器手动跑那条检查命令 |
| `Up`（无健康标记） | 没有定义健康检查 | 只能看日志 |
| `Restarting` | 反复重启 | `docker compose logs <服务>` 看退出原因 |
| `Exited (0)` | 正常退出 | 通常是命令跑完就结束了（本项目不该出现） |
| `Exited (1)` / `(137)` | 异常退出 / **被 OOM Kill** | 看日志；137 = 收到 SIGKILL |

> **`137` 是内存不足的典型信号**（128 + 9 = SIGKILL）。本项目没配容器内存上限，所以暂时不会遇到；一旦配了 `deploy.resources.limits`，就要留意它。

### 3.2 健康检查日志怎么读（最有价值的一招）

服务一直 `unhealthy` 时，**别急着翻应用日志**。先看健康检查自己的输出：

```powershell
docker inspect diary-backend --format '{{json .State.Health}}' | ConvertFrom-Json |
  Select-Object Status, FailingStreak -ExpandProperty Log | Select-Object -Last 3
```

**输出里包含**：每次检查的时间、退出码、**以及命令的真实 stdout/stderr**。

**为什么这条比应用日志有用**：健康检查就是一条命令，它的输出直接告诉你「命令为什么失败」——是连不上？返回了非 2xx？超时？

**本项目的健康检查命令**（回顾 `ops/02` 第 3.4 节）：

| 服务 | 命令 | 失败时的典型输出 |
|---|---|---|
| mysql | `mysqladmin ping ...` | `Access denied` / `Can't connect` |
| redis | `redis-cli ping` | `Could not connect` |
| minio | `curl -fsS /minio/health/live` | `curl: (7) Failed to connect` |
| backend | `curl -fsS /api/v1/ping` | `curl: (7)` 连不上 / `curl: (22)` 返回 4xx-5xx |

> `-f` 是 curl 的「HTTP 错误时以非 0 退出」开关，**没有它健康检查就形同虚设**（HTTP 500 也会被子命令成功执行）。

### 3.3 容器反复重启怎么查

```powershell
# 看上次退出的原因和退出码
docker inspect diary-backend --format 'ExitCode={{.State.ExitCode}} OOMKilled={{.State.OOMKilled}} Error={{.State.Error}}'

# 看重启次数
docker inspect diary-backend --format 'RestartCount={{.RestartCount}}'

# 看上次退出的日志（容器重启后 docker compose logs 只会显示当前这次）
docker logs --tail 100 diary-backend
```

**本项目最可能的启动失败原因**（按概率排序）：

| 原因 | 日志特征 |
|---|---|
| 环境变量缺失 | `Could not resolve placeholder 'JWT_SECRET'` |
| 数据库连不上 | `Communications link failure` |
| 数据库表不存在 | `Table 'diary.t_diary' doesn't exist` |
| 依赖缺失 | `NoClassDefFoundError: net/sf/jsqlparser/...` |

### 3.4 资源问题

```powershell
docker stats --no-stream
```

**本项目实测**：

```text
diary-mysql     0.43%   462.3MiB / 15.46GiB
diary-backend   0.14%   476.8MiB / 15.46GiB
diary-minio     1.75%   100.4MiB / 15.46GiB
diary-nginx     0.00%    25.9MiB / 15.46GiB
diary-redis     0.35%     8.484MiB / 15.46GiB
合计约 1.07 GB
```

**注意 `LIMIT` 列**：显示的是 **15.46GiB**（WSL2 虚拟机的内存），说明**没配容器上限**。

| 情况 | 后果 |
|---|---|
| 不配上上限（当前） | 单个容器可以吃光内存，导致其他容器被拖慢或 OOM |
| 配 `deploy.resources.limits` | 单个容器被限制；JVM 的 `MaxRAMPercentage` 会自动跟着调整 |

**磁盘也要看**：

```powershell
docker system df
```

**本项目实测**：构建缓存 2.366GB，其中 **1.648GB 可回收**。这是磁盘持续增长的头号来源。

---

## 4. 第三层：应用

### 4.1 日志的三个维度

```powershell
# ① 按服务
docker compose logs backend

# ② 按时间（最有用——你知道问题是什么时候出现的）
docker compose logs --since 10m backend
docker compose logs --since "2026-09-23T17:00:00" backend

# ③ 按行数（快速看尾部）
docker compose logs --tail 100 backend

# 跟踪（一边操作一边看）
docker compose logs -f backend
```

**组合使用的例子**：

```powershell
# 我在 18:05 点了「保存日记」但失败了，只想看那一分钟
docker compose logs --since "'2026-09-23T18:04:00'" --until "2026-09-23T18:06:00" backend
```

### 4.2 日志级别决定排查效率

本项目的级别约定（回顾 `doc/backend/02-spring-boot.md` 第 6 节）：

| 级别 | 含义 | 排障时该做什么 |
|---|---|---|
| `ERROR` | 需要人介入，**带完整堆栈** | **首先看这个** |
| `WARN` | 异常但可自愈 / 客户端错误 | 次要；量突然变大说明有问题 |
| `INFO` | 关键流程 | 用来看「服务在做什么」 |
| `DEBUG` | 细节 | 生产环境默认关闭 |

**一个实用的过滤技巧**：

```powershell
# 只看 ERROR（排除大量 HTTP 访问日志的干扰）
docker compose logs backend | Select-String -Pattern 'ERROR'

# 统计各类日志的数量——量级异常往往就是线索
$logs = docker compose logs backend | Out-String
"ERROR: $(([regex]::Matches($logs,'ERROR')).Count)"
"WARN : $(([regex]::Matches($logs,'WARN')).Count)"
```

### 4.3 三个必须会的验证动作

```powershell
# ① 绕过 nginx，直连后端（判断问题在 nginx 还是后端）
docker compose exec -T backend curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/v1/ping

# ② 从后端容器访问其他服务（判断网络问题）
docker compose exec -T backend curl -s http://minio:9000/minio/health/live -o /dev/null -w '%{http_code}\n'

# ③ 从宿主机经 nginx 访问（完整链路）
curl.exe -s -o NUL -w '%{http_code}\n' http://localhost/api/v1/ping
```

**这三步的价值**：同样是「接口不通」，

| ① | ② | ③ | 结论 |
|---|---|---|---|
| ✅ | ✅ | ✅ | 没问题 |
| ❌ | — | ❌ | **后端自己有问题**（看后端日志） |
| ✅ | — | ❌ | **nginx 到后端这一段有问题**（DNS、端口、proxy_pass） |
| ❌ | ❌ | — | 后端连依赖都有问题（可能是网络/DNS） |

---

## 5. 第四层：数据库

### 5.1 三类错误要分清

| 错误信息 | 含义 | 排查方向 |
|---|---|---|
| `Communications link failure` | **连不上**（网络/服务没起） | 服务在跑吗？端口对吗？ |
| `Access denied for user 'root'@'172.18.0.1'` | **连上了，但认证失败** | 密码对吗？（`172.18.x.x` 是 Docker 网段，说明网络没问题） |
| `Table 'diary.t_diary' doesn't exist` | 连上了、认证过了，**但没有表** | `init.sql` 执行了吗？ |
| `Unknown database 'diary'` | 库不存在 | 同上（数据卷为空时才会执行 init.sql） |

**第二类特别值得记住**：`Access denied` 里的 IP 是 **Docker 网桥网关**，它出现在错误里就证明**网络通路完全正常**，问题纯粹在密码上。这是个能快速缩小范围的信号。

### 5.2 手动连一次

```powershell
# 通过容器内的客户端（推荐，不需要宿主机装 MySQL）
docker compose exec -T mysql sh -c "mysql -uroot -p\$MYSQL_ROOT_PASSWORD --default-character-set=utf8mb4 --table -e 'show databases;'"

# 从宿主机连（需 compose 里映射了 3307）
$pw = (Get-Content .env -Encoding UTF8 | Where-Object { $_ -like 'DB_PASSWORD=*' }) -replace '^DB_PASSWORD=', ''
$env:MYSQL_PWD = $pw
& "D:\business\tools\mysql-8.4.9-winx64\bin\mysql.exe" -u root -h 127.0.0.1 -P 3307 --default-character-set=utf8mb4 -e "select 1;"
Remove-Item Env:MYSQL_PWD
```

### 5.3 数据「不见了」的三种可能

按概率排序：

| 可能 | 怎么确认 |
|---|---|
| **逻辑删除**（最常见） | `select * from t_diary where deleted = 1;` 如果查得到，说明数据还在，只是被标记删除了 |
| **连错库了** | 容器库在 3307、本地开发库在 3306，两套数据不同（见 `usage-guide.md`） |
| **真的丢了**（执行过 `down -v`） | 检查卷是否还在：`docker volume ls --filter name=diary` |

> **第一条是最容易误判的**：界面上看不到 = 「数据删了」是错的。逻辑删除的语义就是「应用看不见，数据库还在」。

---

## 6. 实战复盘（本项目真实案例）

这一节记录本项目实际遇到的四个问题。**每个都包含「我最初判断错在哪」**——因为排障能力主要来自「错误判断被纠正」的经验。

### 案例 1：重启后 17 个测试全挂

```text
症状：改完 GlobalExceptionHandler 后跑 mvnw test，17 个测试全部 Errors，
      都在 1 秒左右失败

我的第一判断（错）：我刚改的异常处理器有问题
```

**排查路径**：

```text
① 读 surefire 报告 → 看到 "Communications link failure"
② 这个错误跟异常处理器毫无关系 → 是数据库连不上
③ 查端口 → 3306/6379 都没在监听
④ 想起来：刚重启过机器，而中间件的启停脚本没注册成 Windows 服务
```

**根因**：**环境没起来，不是代码问题。**

**修复**：

```powershell
powershell -ExecutionPolicy Bypass -File D:\business\tools\mysql-start.ps1
powershell -ExecutionPolicy Bypass -File D:\business\tools\redis-start.ps1
```

**验证**：17/17 通过。

**教训**（已写进 `development-log.md`）：

> **排查顺序上应先看服务状态，再看代码。** 代码是变化最慢的东西，突然坏掉的概率最低。

### 案例 2：容器里的中文变成乱码（最隐蔽的一个）

```text
症状：用容器内的 mysql 客户端查数据，中文显示成 ?????
      但同一个客户端查 information_schema 的表注释却显示正常
```

**这个「一半正常一半不正常」的矛盾是唯一的突破口。**

**排查路径**：

```text
① 不用「看」来判断，改用服务端计算的字符数/字节数对比
   SELECT CHAR_LENGTH(title), LENGTH(title), HEX(title) ...

② 结果：
   t_diary.title          →  5 字符 / 15 字节 / E68C81E4B985...   ✅ 正确 UTF-8
   表注释                  →  9 字符 / 19 字节 / C3A6E28094...   ❌ 双重编码

③ 结论反转：显示「正常」的那条才是坏的
```

**根因**：容器内 `LANG` 为空、`LC_CTYPE=POSIX`，mysql 客户端据此把字符集降级为 **latin1**。`init.sql` 由镜像 entrypoint 在容器内执行，文件里的中文被按 latin1 解读后**双重编码**写入。

**为什么难发现**：双重编码的数据配上同样是 latin1 的客户端读回时，**字节会反向抵消**——显示反而是对的。**「显示正常」恰恰是乱码的证据。**

**修复**：`sql/init.sql` 顶部加 `SET NAMES utf8mb4;`，让脚本不依赖客户端默认字符集。

**验证**：`docker compose down -v` 删卷重建后，表注释变为 3 字符 / 9 字节、`tester` 昵称变为 4 字符 / 12 字节，冒烟测试仍 17/17。

**教训**：

> **当「现象」与「预期」矛盾时，不要相信眼睛，要找可量化的证据**（这里是字符数与字节数）。

### 案例 3：重建后端后持续 502

```text
症状（潜在）：按 README 推荐的方式 docker compose up -d --build 重新部署后，
             所有 /api/** 请求返回 502，直到手动 restart nginx
```

**发现方式**：**在审查自己的交付物时**意识到的——README 里推荐的命令会重建后端容器，而 nginx 只在启动时解析一次上游 IP。

**排查路径**：

```text
① 确认 nginx 的上游解析方式 → proxy_pass http://backend:8080 是字面量
② 查 nginx 文档行为 → 字面量形式只在启动时解析并缓存 IP
③ 容器重建 → IP 变了 → nginx 仍指向旧 IP → 502
④ 手动 restart nginx → 恢复。确认因果关系成立
```

**修复**：

```nginx
resolver 127.0.0.11 valid=10s ipv6=off;
location /api/ {
    set $backend_upstream "http://backend:8080";
    proxy_pass $backend_upstream;      # 变量形式 → 按 TTL 重新解析
}
```

**验证时我犯的第二个错误（值得记录）**：

```text
第一次验证：只重建 backend，测 → 通过
问题：Docker 复用了刚释放的同一个 IP（172.18.0.5），
      nginx 本来就指向它，测试不具说服力

第二次验证：用占位容器抢占旧 IP，强制后端换到 172.18.0.7
            nginx 全程未重启
            结果：/api/v1/ping 与 /files/ 均 200  ✅
```

**教训**：

> **验证「某个变量引起的问题」时，必须先确认那个变量真的变了。** 否则「测试通过」什么也证明不了。

### 案例 4：未匹配路径返回 500 而不是 404

```text
症状：生产关闭 SpringDoc 后，访问 /v3/api-docs 返回 500
```

**排查路径**：

```text
① 看响应体 → {"code":500,"message":"服务器开小差了"} → 走了统一异常处理器的兜底分支
② 看后端日志 → NoResourceFoundException: No static resource v3/api-docs
③ 读 GlobalExceptionHandler → 只有 @ExceptionHandler(Exception.class) 兜底，
   没有为 NoResourceFoundException 单独声明
```

**根因**：Spring 6.1+ 对未映射路径抛 `NoResourceFoundException`，被 `catch (Exception)` 吞成 500。

**真实影响**（比「接口文档打不开」严重得多）：

> **任何拼错的 URL、或扫描器对任意路径的探测，都会往日志里写一条 ERROR 级完整堆栈。** 应用一暴露到公网，日志很快被灌满，真实问题被淹没。

**修复**：显式声明 `NoResourceFoundException` / `NoHandlerFoundException` → 404 + `log.warn`（不带堆栈）。

**验证**：`/v3/api-docs` 从 500 变 404；日志 `ERROR` 计数归零，`路径不存在` WARN 3 条。

**顺带确认的一件正确行为**：`/api/v1/not-exist` 仍返回 **401** —— Security 在路由解析前就拦截了未认证请求。**这是对的**，不泄露「路由是否存在」。

**教训**：

> **`catch (Exception)` 这种兜底是必要的，但必须为已知的具体异常留出更精确的分支。** 否则会把「客户端错误」误判成「服务端故障」，污染日志与监控。

### 四个案例的共同点

| 共同点 | 说明 |
|---|---|
| **症状的位置 ≠ 根因的位置** | 案例 1 症状在测试、根因在环境；案例 4 症状在接口文档、根因在异常处理器 |
| **矛盾的现象是最好的线索** | 案例 2 的「一半正常」、案例 3 的「重启就好」 |
| **可量化的证据胜于观察** | 案例 2 用字节数、案例 3 用 IP 变化、案例 1 用错误信息 |
| **验证要能排除其他解释** | 案例 3 的第一次验证就是「没排除其他解释」 |

---

## 7. 备份与恢复演练

### 7.1 只有「恢复成功过」才算有备份

**这是运维里最重要的一条认知**：

> 备份文件存在，不等于备份有效。**没验证过恢复的备份等于没有备份。**

常见的失败方式：备份脚本少写了一个库、dump 文件是空的、恢复时字符集弄坏了、恢复后应用连不上。

### 7.2 完整演练（我在本项目实际做过的）

```text
① 取一份备份
   powershell -ExecutionPolicy Bypass -File deploy\mysql-backup.ps1
   → diary-20260923-180642.sql

② 记录当前状态，并插入一条哨兵数据
   INSERT INTO t_user (username='sentinel_user', ...)
   → 此时 t_user = 5 行

③ 执行恢复（注意 PowerShell 不支持 < 重定向）
   docker compose cp <dump> mysql:/tmp/restore.sql
   docker compose exec -T -e "MYSQL_PWD=$pw" mysql sh -c 'mysql -uroot --default-character-set=utf8mb4 < /tmp/restore.sql'
   → 退出码 0

④ 核对结果
   sentinel_left = 0     ← 哨兵消失
   total_users   = 4     ← 回到备份时的行数
   表注释仍是「日记表」   ← 字符集没被弄坏

⑤ 确认应用还能用
   deploy\smoke-test.ps1  → 17/17 通过
```

**第 ④ 步为什么关键**：哨兵数据消失**证明表确实被 dump 重建了**。如果只是简单追加数据，哨兵不会消失——那样「行数看起来对」其实是巧合。

### 7.3 恢复的两种方式与那个 PowerShell 坑

```powershell
# ❌ 这样写跑不通
docker compose exec -T mysql mysql -uroot < dump.sql
# 报错："<"运算符是为将来使用而保留的
```

**PowerShell 不支持 `<` 输入重定向**。两种可行写法：

```powershell
# ① 容器部署：先拷进容器，在容器内重定向
docker compose cp deploy\backups\diary-20260923-180642.sql mysql:/tmp/restore.sql
$pw = (Get-Content .env -Encoding UTF8 | Where-Object { $_ -like 'DB_PASSWORD=*' }) -replace '^DB_PASSWORD=', ''
docker compose exec -T -e "MYSQL_PWD=$pw" mysql sh -c 'mysql -uroot --default-character-set=utf8mb4 < /tmp/restore.sql'
docker compose exec -T mysql rm -f /tmp/restore.sql          # 清理临时文件

# ② 本地开发：用 mysql 客户端的 source 命令
mysql.exe -u root -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4 -e "source <dump 绝对路径，用正斜杠>"
```

### 7.4 恢复是覆盖式的

`mysqldump --databases` 的产物包含 `CREATE DATABASE IF NOT EXISTS` + `DROP TABLE IF EXISTS` + `CREATE TABLE`，所以恢复会**把同名表整个重建**。

| 想做的事 | 命令 |
|---|---|
| 恢复到备份时的状态 | 直接执行上面的恢复命令 |
| 彻底重来（连库一起重建） | `docker compose down -v` 后 `up -d`，再恢复 |
| 只恢复某张表 | 需要手工从 dump 里截取该表的段落（逻辑备份的优势） |

---

## 8. 建立自己的排查清单

排障能力最终会沉淀成一张**自己的清单**。给一个起点：

```text
【症状：站点打不开】
1. 宿主端口在监听吗？   Get-NetTCPConnection -LocalPort 80 -State Listen
2. 容器都健康吗？       docker compose ps
3. 不健康的是谁？       docker inspect <容器> --format '{{json .State.Health}}'
4. 后端自己通吗？       docker compose exec -T backend curl ... /api/v1/ping
5. 经 nginx 通吗？      curl.exe -sI http://localhost/api/v1/ping
6. 看后端日志           docker compose logs --since 10m backend

【症状：接口报 500】
1. 响应体是统一格式吗？  是 → 走了 GlobalExceptionHandler，看日志的堆栈
2. 日志里最近的 ERROR 是什么？  docker compose logs backend | Select-String ERROR
3. 是不是数据库问题？    错误信息里有没有 SQL / 连接字样

【症状：数据不对】
1. 是不是逻辑删除？      select * from t_diary where deleted = 1;
2. 是不是连错库？        3306（本地）vs 3307（容器）
3. 时间差 8 小时？       检查 TZ / --default-time-zone / serverTimezone 三处
4. 中文乱码？            客户端要带 --default-character-set=utf8mb4

【症状：磁盘一直涨】
1. docker system df      看哪一项占比最大
2. docker builder prune  构建缓存（本项目可回收 1.6GB）
3. 容器日志未轮转？      配置 logging.options.max-size
```

---

## 9. 动手练习

1. **制造一次 502 并修复**：手动 `docker compose stop backend`，访问 `http://localhost/api/v1/ping` 观察 502；然后 `docker compose start backend` 恢复
2. **读一次健康检查日志**：把 backend 健康检查的路径临时改成 `/api/v1/not-exist`，重建后等它变 unhealthy，用 `docker inspect` 读出失败原因。**验证后改回**
3. **验证 SPA 回退的干扰**：对比 `curl http://localhost/some-fake-path` 与 `docker compose exec -T backend curl http://127.0.0.1:8080/some-fake-path` 的状态码差异，解释为什么
4. **完成一次恢复演练**：按第 7.2 节的五步做一遍，**务必用哨兵数据验证「表确实被重建了」**
5. **验证逻辑删除**：在界面上删除一篇日记，然后查 `select id, title, deleted from t_diary`，确认数据仍在
6. **制造一次启动失败**：把 `.env` 里的 `JWT_SECRET` 改成 10 个字符（太短），重启后端，观察启动失败的日志信息。**验证后改回**
7. **统计一次日志**：用第 4.2 节的过滤技巧统计后端日志里 ERROR / WARN 的数量，确认没有异常增长

---

## 附录 · 排障命令速查（按层）

```powershell
# ========== ① 网络与入口 ==========
Get-NetTCPConnection -LocalPort 80 -State Listen
docker compose ps
curl.exe -s -o NUL -w '%{http_code}' http://localhost/

# ========== ② 容器 ==========
docker compose ps                                      # 状态
docker compose ps --format 'table {{.Service}}\t{{.Status}}\t{{.Ports}}'
docker inspect diary-backend --format '{{json .State.Health}}'   # 健康检查明细
docker inspect diary-backend --format 'ExitCode={{.State.ExitCode}} OOMKilled={{.State.OOMKilled}}'
docker stats --no-stream
docker system df
docker compose top

# ========== ③ 应用 ==========
docker compose logs --tail 100 backend
docker compose logs --since 10m backend
docker compose logs backend | Select-String 'ERROR'
docker compose exec -T backend curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/v1/ping
docker compose exec -T backend env | Select-String 'DB_|REDIS_|MINIO_|JWT_'

# ========== ④ 数据库 ==========
docker compose exec -T mysql sh -c "mysql -uroot -p`$MYSQL_ROOT_PASSWORD --default-character-set=utf8mb4 --table -e 'show databases;'"
docker compose exec -T redis redis-cli --scan
docker volume ls --filter name=diary
docker volume inspect diary_mysql-data --format '{{.Mountpoint}}'

# ========== 配置校验 ==========
docker compose config                                  # 变量插值后的完整配置
docker compose exec -T backend sh -c 'echo $SPRING_PROFILES_ACTIVE'
```

---

## 学习路径完成 ✅

`doc/ops/` 三篇到此结束。整个学习文档集的全貌：

```text
doc/
├── learning-path.md          前端上手（Vue → React 本项目）
├── fullstack-roadmap.md      全栈总索引
├── backend/
│   ├── 01-java-basics.md     Java 语言基础
│   ├── 02-spring-boot.md     框架与请求链路
│   ├── 03-persistence.md     持久层与 SQL
│   ├── 04-security.md        认证与鉴权
│   └── 05-database.md        MySQL 与数据设计
└── ops/
    ├── 01-docker.md          容器、镜像、卷、网络
    ├── 02-deploy.md          Compose 编排与 Nginx
    └── 03-troubleshooting.md 分层排障与实战复盘
```

**建议的学习顺序**（详见 `fullstack-roadmap.md`）：

```text
01-java-basics → 02-spring-boot → 03-persistence
                     ↓
              04-security        05-database（可穿插）
                     ↓
ops/01-docker → ops/02-deploy → ops/03-troubleshooting
```

**每个模块都要求你产出一个「能跑的东西」**（一个新接口、一条新 SQL、一次成功的恢复）。只读不写，学完就忘。
