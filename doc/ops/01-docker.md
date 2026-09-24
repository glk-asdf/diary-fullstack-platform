# 模块 ops-01 · Docker：镜像、容器、卷、网络

> 读者：没用过 Docker，或只用过 `docker run hello-world`
> 目标：**能读懂并修改 Dockerfile；能解释「改一行代码重建只要 20 秒」；知道数据存在哪、容器之间怎么通信**
> 时长：约 1 天
> 上一篇：`doc/fullstack-roadmap.md`

---

## 0. 这一篇解决什么问题

你会在这份仓库里看到这些文件，本篇逐个讲清它们的作用：

| 文件 | 作用 | 对应章节 |
|---|---|---|
| `backend/Dockerfile` | 后端镜像怎么造出来 | 第 4 节 |
| `frontend/Dockerfile` | 前端镜像怎么造出来 | 第 5 节 |
| `docker-compose.yml` | 5 个容器怎么一起编排 | `ops/02-deploy.md` |
| `frontend/nginx.conf` | 网关怎么分流请求 | `ops/02-deploy.md` |
| `.env` / `.env.example` | 密钥从哪来 | `ops/02-deploy.md` |

**先建立一句话心智模型**：

> **镜像是「安装包」，容器是「运行中的程序」，Dockerfile 是「安装脚本」。**

---

## 1. 从「装在我机器上」到「装进一个盒子里」

### 1.1 传统部署的痛点

本项目在 P8 之前就是这么部署的：JDK 装本机、Node 装本机、MySQL/Redis/MinIO 都是免安装 ZIP 解压到 `D:\business\tools`。问题很实际：

| 问题 | 具体表现 |
|---|---|
| **环境漂移** | 换台机器就要重新装一遍，版本稍有差别（MySQL 8.4.9 vs 8.0）行为就可能不同 |
| **污染宿主机** | 全局装了 JDK 25，别的项目要 JDK 17 就冲突了 |
| **启停靠脚本** | 本项目为此写了 `mysql-start.ps1` / `redis-start.ps1` / `minio-start.ps1`，**而且重启电脑后要手动再跑一遍**（这是我们踩过的坑：重启后 17 个测试全挂，原因是 MySQL 没起） |
| **交付困难** | 「在我机器上是好的」——因为没有把「我的机器」一起交付出去 |

### 1.2 容器的解法

把「应用 + 它需要的运行环境」一起打包成一个**镜像**，在任何装了 Docker 的机器上都能以完全相同的方式运行。

```
传统部署：你的代码 ──► 依赖宿主机碰巧装了什么
容器部署：你的代码 + JDK + 依赖 ──► 打包成镜像 ──► 哪里都能跑
```

### 1.3 容器不是虚拟机（这是最需要纠正的直觉）

```text
虚拟机：   [应用] [Guest OS 完整内核]  ← 硬件虚拟化
           ─────────────────────────
           [Hypervisor] → [宿主 OS] → [硬件]

容器：     [应用] [共享宿主内核]
           ─────────────────────────
           [容器运行时] → [宿主 OS] → [硬件]
```

| | 虚拟机 | 容器 |
|---|---|---|
| 内核 | 每个 VM 一套完整内核 | **共享宿主内核** |
| 启动时间 | 几十秒 | **秒级**（本项目 5 个容器几秒起齐） |
| 体积 | GB 级 | MB~百 MB 级 |
| 隔离强度 | 强（硬件级） | 较弱（内核级，共享内核） |

> **推论**：容器里**不能装一个不同的内核**，也不能跑 Windows 容器在 Linux 上。本项目在 Windows 上跑 Linux 容器，靠的是 WSL2 提供的 Linux 内核——这就是为什么 P8 必须先装 WSL2 并**重启一次**。

### 1.4 三个词

| 词 | 一句话 | 类比 |
|---|---|---|
| **镜像（Image）** | 只读的模板，包含文件系统 + 启动命令 | 安装包 / 类（class） |
| **容器（Container）** | 镜像的一个运行实例，可读写（改动在可写层） | 安装好的程序 / 对象（instance） |
| **仓库（Registry）** | 存放镜像的地方 | npm registry |
| **Dockerfile** | 描述如何构建镜像的脚本 | `package.json` 的 build 脚本 |

**同一个镜像可以起多个容器**，就像同一个类可以 new 多个对象。

---

## 2. 本项目的 5 个容器（实测全貌）

后端起服务后，实测的整体情况：

```text
镜像（docker images）
  diary-backend   local                          620MB    ← 我们自己构建的
  diary-frontend  local                          97.6MB   ← 我们自己构建的
  mysql           8.4.9                          1.12GB
  quay.io/minio/minio  RELEASE.2025-09-07...     241MB
  redis           7.4.11-alpine                  57.8MB
  alpine          latest                         13MB     ← 验证用的临时镜像，可删

卷（docker system df -v）
  diary_mysql-data   219MB     ← 数据库数据
  diary_minio-data   34.71kB   ← 图片对象
  diary_redis-data   2.252kB   ← Refresh Token

容器与网络（docker network inspect diary_diary-net）
  diary-mysql   = 172.18.0.2
  diary-minio   = 172.18.0.3
  diary-redis   = 172.18.0.4
  diary-backend = 172.18.0.5
  diary-nginx   = 172.18.0.6

运行态（docker stats --no-stream）
  diary-backend  0.14%   476.8MiB / 15.46GiB
  diary-mysql    0.43%   462.3MiB / 15.46GiB
  diary-minio    1.75%   100.4MiB / 15.46GiB
  diary-nginx    0.00%    25.9MiB / 15.46GiB
  diary-redis    0.35%     8.484MiB / 15.46GiB
  ─────────────────────────────────────────────
  合计约 1.07 GB 内存
```

**三个值得注意的点**：

1. **`LIMIT` 显示 15.46GiB 意味着没有设上限**——这是宿主 WSL2 虚拟机分到的内存。本项目是单机自用，没配 `deploy.resources.limits`；若要防止某个容器吃光内存，就该配上（见第 8 节）。
2. **MySQL 用了 462MB 内存**，是几个容器里最重的之一。这是 MySQL 8 的默认缓冲池设置，小数据量下偏高但可接受。
3. **Redis 只占 8.5MB 内存 / 2.25KB 磁盘**——因为它只存 Refresh Token。

---

## 3. 镜像分层：为什么改一行代码重建只要 20 秒（🔴 本节是核心）

### 3.1 实测：一次重建的耗时对比

| 场景 | 耗时 |
|---|---|
| 首次构建（拉镜像 + 容器内下 Maven 依赖 + npm 依赖） | **约 5 分钟** |
| 改一行 Java / TS 代码后重建 | **19 秒** |

差 15 倍。原因不是 Docker 有什么魔法，而是**分层缓存**。

### 3.2 镜像是一层层叠起来的

`docker history diary-backend:local` 的真实输出（节选）：

```text
SIZE      COMMAND
0B        ENTRYPOINT ["sh" "-c" "exec java $JAVA_OPTS -jar /app/app.jar"]
0B        ENV JAVA_OPTS=-XX:MaxRAMPercentage=75 ...
0B        EXPOSE [8080/tcp]
0B        USER diary
68.8MB    COPY --chown=diary:diary ... diary-backend-*.jar /app/app.jar   ← 我们的应用
8.19kB    WORKDIR /app
41kB      RUN groupadd --system --gid 1001 diary && useradd ...           ← 非 root 用户
11.4MB    RUN apt-get update && apt-get install -y curl                   ← 健康检查需要 curl
4.1kB     RUN ... java --version ...
201MB     RUN ... （安装 JRE）                                             ← 基础镜像最大的一层
39.6MB    RUN apt-get update ...
```

**关键结论**：

| 观察 | 说明 |
|---|---|
| **应用 jar 只有 68.8MB，整个镜像 620MB** | **应用只占 11%**。基础镜像（JRE + apt 层）才是大头 |
| 每一层有独立的大小 | 层是**按指令**产生的，不是按文件 |
| 修改某一层会让**它及它之上的所有层**失效 | 这就是缓存的关键规则 |

### 3.3 缓存的规则（一句话）

> **从第一条「内容发生变化」的指令开始，其后所有层都必须重建；之前的层直接从缓存复用。**

所以**把「很少变的东西」写在 Dockerfile 靠前的位置**，是唯一有效的优化手段。

### 3.4 我们的 Dockerfile 是怎么安排的

```dockerfile
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build

# ① 只复制构建描述文件
COPY mvnw .
COPY .mvn/ .mvn/
COPY pom.xml .
# ② 下载依赖 —— 只要 pom.xml 不变，这一层永远命中缓存
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

# ③ 最后才复制会频繁变化的源码
COPY src/ src/
RUN ./mvnw -B -DskipTests package
```

**这个顺序是刻意设计的**：

```text
第 1 次构建：  复制 pom → 下载 400+ 个依赖（约 3 分钟）→ 编译源码
改一行代码后： 复制 pom（内容未变，缓存命中）→ 下载依赖（缓存命中，跳过）
              → 复制 src（变了）→ 编译（约 19 秒）
```

**反例：如果把顺序写反**

```dockerfile
COPY pom.xml .
COPY src/ src/                                    # ❌ 源码在依赖之前
RUN ./mvnw -B -DskipTests package                 # 这条会把下载依赖和编译合成一层
```

每次改代码，`COPY src/` 就变了 → 后面的 `RUN` 缓存全部失效 → **重新下载 400+ 个依赖**。

**前端同理**：

```dockerfile
COPY package.json package-lock.json ./            # 先复制锁文件
RUN npm ci                                        # 这一层只要锁文件不变就命中
COPY . .                                          # 再复制源码
RUN npm run build
```

> **记住这个模式**：`COPY 依赖清单 → 安装依赖 → COPY 源码 → 构建`。几乎每个语言的 Dockerfile 都是这个骨架。

### 3.5 分层的另一个后果：镜像体积会「虚高」

基础镜像的层被**所有基于它的镜像共享**。所以：

- `mysql:8.4.9` 那 1.12GB 里，大部分层也可能被别的 MySQL 镜像共用
- 删除一个镜像释放的空间，通常小于它显示的 SIZE

实测 `docker system df`：

```text
TYPE            TOTAL     ACTIVE    SIZE      RECLAIMABLE
Images          6         5         2.136GB   88.02kB (0%)      ← 镜像几乎没有可回收的
Containers      5         5         237.6kB   0B (0%)
Local Volumes   3         3         219.1MB   0B (0%)
Build Cache     51        0         2.366GB   1.648GB           ← 这里才是可回收的大头
```

**构建缓存占了 2.366GB，其中 1.648GB 可回收**。清理：

```powershell
docker builder prune          # 交互式确认后回收
```

---

## 4. 逐行读后端 Dockerfile

```dockerfile
# ---------- 构建阶段 ----------
FROM eclipse-temurin:25-jdk AS builder        # ① 带 JDK 的基础镜像（能编译）
WORKDIR /build

COPY mvnw .
COPY .mvn/ .mvn/
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline   # ② 预热依赖
COPY src/ src/
RUN ./mvnw -B -DskipTests package             # ③ 产出 target/*.jar

# ---------- 运行阶段 ----------
FROM eclipse-temurin:25-jre AS runtime        # ④ 只带 JRE（不能编译，但更小）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*            # ⑤ 装 curl 供健康检查；清缓存避免残留
RUN groupadd --system --gid 1001 diary \
    && useradd --system --uid 1001 --gid diary --home-dir /app --shell /usr/sbin/nologin diary
WORKDIR /app
COPY --from=builder --chown=diary:diary /build/target/diary-backend-*.jar /app/app.jar  # ⑥ 只拷产物
USER diary                                     # ⑦ 切换成非 root
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]   # ⑧ exec + sh -c
```

### 为什么要多阶段构建（第 ①④ 行）

| 阶段 | 内容 | 是否进最终镜像 |
|---|---|---|
| builder | JDK（含编译器）+ Maven Wrapper + 依赖缓存 + 源码 | ❌ **全部丢弃** |
| runtime | JRE + curl + 一个 jar | ✅ |

**只用 runtime 的镜像最终只有 JRE + 68.8MB 的 jar**。如果不用多阶段（在同一个镜像里编译完再留着），镜像会多出几百 MB 的编译工具链——**既浪费空间也扩大攻击面**。

### 为什么 `dependency:go-offline`（第 ② 行）

它的字面意思是「把所有依赖下到本地仓库，之后可离线构建」。这里的作用不是真的离线，而是**把「下载依赖」单独变成一层**，从而让改代码时跳过这一步（见 3.4）。

### 为什么装 curl，还要清 apt 缓存（第 ⑤ 行）

- **装 curl**：`docker-compose.yml` 里后端的健康检查用 `curl -fsS http://127.0.0.1:8080/api/v1/ping`，镜像里必须有 curl
- **`rm -rf /var/lib/apt/lists/*`**：apt 的索引文件装完就没用了，但在同一层里占空间。**必须在同一条 `RUN` 里删**——如果分成两条 `RUN`，第一层已经把文件写进镜像了，第二条只是「标记删除」，体积不会变小

> **这是一条通用的 Dockerfile 规则**：**同一次操作产生的临时文件，必须在同一条 `RUN` 指令里清理。** 你会看到大量 Dockerfile 写成 `RUN xxx && yyy && rm -rf zzz` 这种一长串，就是这个原因。

### 为什么非 root（第 ⑦ 行）

容器默认以 root 运行。一旦应用被攻破（比如某个依赖有 RCE 漏洞），攻击者就拿到了容器内的 root——虽然还在容器里，但**逃逸的难度会低很多**，而且如果挂了宿主目录就会有实际破坏。

```dockerfile
RUN groupadd --system --gid 1001 diary \
    && useradd --system --uid 1001 --gid diary --home-dir /app --shell /usr/sbin/nologin diary
USER diary
```

三个细节：

| 细节 | 原因 |
|---|---|
| **固定 uid/gid 1001** | 后续如果要挂载卷，宿主机侧能对上同一个 uid |
| `--shell /usr/sbin/nologin` | 这个账号不允许登录（它只用来跑进程） |
| `--chown=diary:diary` 在 COPY 上 | 复制文件时就设好属主，避免之后再 `chown`（会多一层） |

### 为什么用 `MaxRAMPercentage` 而不是 `-Xmx`（第 ⑧ 行）

```dockerfile
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
```

| 方式 | 问题 |
|---|---|
| `-Xmx4g` 写死 | **容器限额如果只有 2g，JVM 会以为有 4g 可用，一路申请到被 OOM Kill** |
| `MaxRAMPercentage=75` | JVM 读取**容器内存限制**，取 75% 作为堆上限。限额变了不用改配置 |

`UseContainerSupport` 在 JDK 10+ 默认开启，这里显式写出来是为了让意图明确。

> **顺便指出本项目的现状**：因为没配 `deploy.resources.limits`，容器看到的是 WSL2 虚拟机的全部内存（15.46GiB），所以堆上限约 11.6GB。单机自用没问题，但**如果哪天限制了容器内存，这个配置会自动跟着调整**——这就是它的价值。

### 为什么是 `ENTRYPOINT ["sh", "-c", "exec java ..."]`（第 ⑧ 行）

三个知识点叠在一起：

| 写法 | 为什么 |
|---|---|
| **JSON 数组形式**（`["sh","-c",...]`） | 否则 Docker 会套一层 shell，**信号传不到 java 进程** |
| **`sh -c`** | 为了能展开 `$JAVA_OPTS` 变量。JSON 数组形式不会做变量替换 |
| **`exec`** | 用 java 进程**替换掉 shell 进程**，让 java 成为 PID 1 —— `docker stop` 发的 SIGTERM 才能被 java 收到，触发优雅关闭 |

**如果漏了 `exec`**：`docker stop` 会等超时（默认 10 秒）然后 SIGKILL，**应用来不及优雅关闭**（连接池不会正常断开、正在处理的请求会中断）。

---

## 5. 逐行读前端 Dockerfile

```dockerfile
# ---------- 构建阶段 ----------
FROM node:22-alpine AS builder
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci                        # ① 锁文件不变则命中缓存
COPY . .
RUN npm run build                 # ② 产出 /build/dist

# ---------- 运行阶段 ----------
FROM nginx:1.31.6-alpine AS runtime
COPY --from=builder /build/dist /usr/share/nginx/html        # ③ 只要静态产物
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=5 \
    CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1     # ④ alpine 没有 curl，用 wget
```

### 三个要点

**① 镜像里没有 Node**

```text
阶段 1（丢弃）：Node 22 + node_modules + 源码 + 构建产物
阶段 2（保留）：Nginx + 静态文件
```

**这是一个必须知道的后果**：**改完前端代码不能在容器里跑 `npm run dev`**，必须重新构建镜像。日常改前端要用本地开发模式（`npm run dev` + 5173 端口），改完再重建镜像。

实测体积收益：**97.6MB**（如果单阶段把 Node 和 node_modules 也留下，会是 400MB+）。

**② HEALTHCHECK 用 wget 而不是 curl**

`nginx:alpine` 里**没有 curl**，但有 `wget`（BusyBox 自带）。这是 alpine 基础镜像的常见坑——**写健康检查前先确认镜像里有什么**。

> 对比：后端用的是 `eclipse-temurin:25-jre`（Debian 系），里面没有 wget 有 curl，所以我们在 Dockerfile 里显式装了 curl。**同一个项目，两个镜像的健康检查工具不一样**——这不是不一致，而是各自基础镜像的情况不同。

**③ `HEALTHCHECK` 写进镜像 vs 写进 compose**

| 位置 | 本项目 |
|---|---|
| Dockerfile | 前端 nginx 的 HEALTHCHECK 在这里 |
| compose | mysql / redis / minio / backend 都在 compose 里 |

**两者都能用**。放在 Dockerfile 里是「镜像自带的健康检查」，任何运行方式都有；放在 compose 里便于按环境调整参数。本项目混用是因为：nginx 不需要被别的服务依赖（没人等它 healthy），所以写在 Dockerfile 里更内聚。

---

## 6. 卷：数据住在哪

### 6.1 一个关键认知：容器的可写层是临时的

```text
docker compose down          → 容器被删除，可写层里的数据全部丢失
docker compose down -v       → 连同卷一起删除
```

**所以数据库数据必须放在卷里**，否则每次 `down` 都会清空。

### 6.2 三种挂载方式

| 方式 | 写法 | 数据位置 | 适用 |
|---|---|---|---|
| **具名卷（volume）** | `mysql-data:/var/lib/mysql` | Docker 管理（WSL2 内部） | **数据库、对象存储**（本项目全用这个） |
| **绑定挂载（bind mount）** | `./sql/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro` | 宿主机上的具体路径 | **配置文件、初始化脚本**（要能看到、能改） |
| 临时文件系统（tmpfs） | `tmpfs: /tmp` | 内存，容器停止即消失 | 敏感临时数据 |

**本项目的 4 处挂载**：

```yaml
mysql:
  volumes:
    - mysql-data:/var/lib/mysql                                     # 具名卷：数据
    - ./sql/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro      # 绑定挂载，只读
redis:
  volumes:
    - redis-data:/data
minio:
  volumes:
    - minio-data:/data
```

**为什么 `init.sql` 用绑定挂载而不是 COPY 进镜像**：

`/docker-entrypoint-initdb.d/` 是 MySQL 官方镜像的**约定目录**——容器**第一次启动**（数据目录为空）时会自动执行里面所有 `.sql` 文件。用绑定挂载的好处是**改完脚本不需要重建镜像**，只需 `down -v` 重新初始化。`:ro` 表示只读，防止容器内误改宿主文件。

### 6.3 卷在宿主机上的真实位置

```text
docker volume inspect diary_mysql-data --format '{{.Mountpoint}}'
→ /var/lib/docker/volumes/diary_mysql-data/_data
```

**但这个路径你在 Windows 资源管理器里找不到** ——它在 WSL2 的虚拟磁盘里：

```text
C:\Users\<用户名>\AppData\Local\Docker\wsl\disk\docker_data.vhdx
```

`wsl -l -v` 里只有一个发行版 `docker-desktop`（Docker Desktop 4.30 起把原来的 `docker-desktop-data` 合并了）。

**推论（很重要）**：

> **不要试图在宿主机上直接备份数据库文件**。要取数据只有两条路：
> ① 通过容器（`docker compose exec` 里操作）
> ② 用导出工具（本项目是 `deploy/mysql-backup.ps1` 跑 mysqldump）

这也解释了为什么备份脚本必须在容器内执行 `mysqldump`——宿主机上根本没有 MySQL 客户端，也没有数据库文件。

### 6.4 具名卷 vs 绑定挂载的选择依据

| 想要 | 用 |
|---|---|
| 数据由 Docker 管理，不想关心路径 | **具名卷** |
| 需要直接看到/编辑宿主文件 | 绑定挂载 |
| 跨平台一致（Windows 挂载 Linux 路径有性能与权限坑） | **具名卷** |

**本项目数据全用具名卷、配置全用绑定挂载**，分工清晰。

---

## 7. 网络：容器之间怎么找到彼此

### 7.1 为什么 `mysql` 这个主机名能用

后端配置里写的是：

```yaml
url: jdbc:mysql://${DB_HOST:mysql}:3306/...
```

`mysql` 是**服务名**。能这样写，是因为 compose 会为项目创建一个**自定义 bridge 网络**，并在上面跑一个**内嵌 DNS 服务器**，把服务名解析成容器 IP。

```text
docker network inspect diary_diary-net
  diary-mysql   = 172.18.0.2
  diary-minio   = 172.18.0.3
  diary-redis   = 172.18.0.4
  diary-backend = 172.18.0.5
  diary-nginx   = 172.18.0.6
```

**对比默认的 `bridge` 网络**：默认网络里**容器之间只能用 IP 互相访问**，没有名字解析。所以 compose 一定会自建网络。

> **这就是为什么 nginx 配置里可以写 `proxy_pass http://backend:8080`** ——`backend` 由 Docker DNS 解析。

### 7.2 端口发布 vs 端口暴露（最容易被误解的一处）

```dockerfile
EXPOSE 8080
```

**这一行几乎不起作用**——它只是「文档性质的声明」，**不代表能从宿主机访问**。真正决定能否访问的是 compose 的 `ports`：

```yaml
nginx:
  ports:
    - "${APP_PORT:-80}:80"       # ← 这才是「发布到宿主机」

backend:                          # 没有 ports → 宿主机访问不到
mysql:
  ports:
    - "127.0.0.1:${DB_HOST_PORT:-3307}:3306"   # ← 只发布到回环地址
```

| 配置 | 宿主机能否访问 | 其他容器能否访问 |
|---|---|---|
| 无 `ports` | ❌ | ✅（同一网络内） |
| `ports: "8080:8080"` | ✅ 所有网卡 | ✅ |
| `ports: "127.0.0.1:3307:3306"` | ✅ **仅本机** | ✅ |

**`127.0.0.1:` 前缀是本项目的一个刻意设计**：MySQL 只对宿主机本机开放（供 Database Client 用），**局域网内其他机器连不上**，不扩大暴露面。

> 验证方法：`docker compose ps` 的 `PORTS` 列。只有 `0.0.0.0:80->80/tcp` 和 `127.0.0.1:3307->3306/tcp` 出现，其余显示的是「容器内端口」。

### 7.3 网络隔离的实际好处

本项目**对外只暴露 nginx 一个端口**。这意味着：

| 好处 | 说明 |
|---|---|
| **攻击面小** | MySQL/Redis/MinIO 从公网完全不可达，不存在「数据库端口暴露被扫描爆破」的风险 |
| **不与本地开发冲突** | 本地开发的 3306/6379/9000/8080 都还在用，但容器这套不占用它们（除了刻意让出的 3307 和 80） |
| **不需要给 MinIO 配 CORS** | 图片经 nginx 以 `/files/` 反代，与前端同源 |

---

## 8. 常用命令与清理

### 8.1 按「查什么」分类

```powershell
# ---------- 状态 ----------
docker compose ps                          # 服务状态 + 健康检查 + 端口
docker stats --no-stream                   # 实时 CPU / 内存
docker compose top                         # 容器内进程
docker compose images                      # 各服务用的镜像

# ---------- 日志 ----------
docker compose logs -f backend             # 跟踪某个服务
docker compose logs --tail 50 nginx
docker compose logs --since 10m

# ---------- 进容器 ----------
docker compose exec backend sh             # 交互式 shell（脚本里要加 -T）
docker compose exec -T backend env | sort  # 看环境变量
docker compose exec nginx ls -la /usr/share/nginx/html

# ---------- 镜像与分层 ----------
docker images                              # 镜像与体积
docker history diary-backend:local         # 分层明细（大小 + 指令）
docker image inspect diary-backend:local --format '{{json .Config.Env}}'

# ---------- 卷 ----------
docker volume ls --filter name=diary
docker volume inspect diary_mysql-data --format '{{.Mountpoint}}'
docker system df -v                        # 各卷实际占用

# ---------- 网络 ----------
docker network ls
docker network inspect diary_diary-net --format '{{range .Containers}}{{.Name}} = {{.IPv4Address}}{{println}}{{end}}'

# ---------- 排查配置 ----------
docker compose config                      # 变量插值后的完整配置（验证 .env 是否生效）
docker inspect diary-backend --format '{{.State.Health}}'   # 健康检查明细（含失败原因）
```

### 8.2 清理（注意别误删数据）

| 命令 | 删什么 | 危险度 |
|---|---|---|
| `docker builder prune` | 构建缓存（本项目可回收 1.648GB） | 🟢 安全，下次构建会重建 |
| `docker image prune` | 悬空镜像（`<none>`） | 🟢 安全 |
| `docker compose down` | 停止并删除容器，**保留卷** | 🟡 数据还在，`up` 就回来 |
| `docker compose down -v` | **连卷一起删** | 🔴 **数据全没了** |
| `docker system prune -a --volumes` | 所有未使用的镜像/容器/卷 | 🔴 **极端危险，别用** |

> **`docker compose down -v` 是本项目里唯一会清空数据的命令**。它有个正当用途：让 `sql/init.sql` 重新执行（我们在修字符集问题时用过一次）。但要么先备份，要么确认数据可以丢。

### 8.3 一个容易被忽略的存储增长点

```text
Build Cache     51        0         2.366GB   1.648GB
```

**每次构建都会产生新的缓存层**，旧的不会自动清理。开发频繁改代码时，这个数字会持续增长。建议每隔一段时间：

```powershell
docker builder prune
```

另外**容器日志默认也不轮转**——长期运行的服务日志会无限增长。本项目还没配：

```yaml
# 可选：给容器限制日志大小
logging:
  options:
    max-size: "10m"
    max-file: "3"
```

---

## 9. 动手练习

1. **看分层**：运行 `docker history diary-backend:local`，找出「哪一层是应用 jar」「哪一层是 curl」「哪一层最大」，并算出应用占镜像的比例
2. **验证缓存**：改一行前端文案 → `docker compose build frontend` → 观察耗时（应约 20 秒）；然后**故意改一下 `package.json`**（比如加个空行）再构建 → 观察耗时（应显著变长，因为 `npm ci` 那层失效了）。**验证完把 `package.json` 改回来**
3. **验证数据在卷里**：`docker compose exec -T mysql sh -c 'ls /var/lib/mysql | head -5'`，确认数据库文件确实在容器内路径下
4. **验证端口发布规则**：从宿主机尝试 `curl http://localhost:9000`（MinIO），应该连不上；再 `docker compose exec -T backend curl -s -o /dev/null -w '%{http_code}' http://minio:9000/minio/health/live`，应该返回 200
5. **验证服务名解析**：`docker compose exec -T backend getent hosts mysql` 或 `ping -c1 mysql`，看 `mysql` 是否解析成容器 IP
6. **算一笔体积账**：`docker images` 列出所有镜像大小并求和，再对比 `docker system df` 的 `Images` 行——为什么两者不一致？（提示：见 3.5）
7. **回收空间**：查看 `docker system df`，执行 `docker builder prune`，再对比一次数字

---

## 附录 · Docker 命令速查（按意图）

```powershell
# 我想知道现在什么在跑
docker compose ps

# 我想看某个服务在报什么错
docker compose logs --tail 100 backend

# 我想进容器里翻东西
docker compose exec backend sh

# 我想确认环境变量注入对不对
docker compose exec -T backend env | sort

# 我想确认 .env 有没有生效
docker compose config | Select-String 'DB_PASSWORD|JWT_SECRET'

# 我想知道服务为什么 unhealthy
docker inspect diary-backend --format '{{.State.Health}}'

# 我改了代码想重新部署
docker compose up -d --build

# 我想只重建一个服务
docker compose up -d --build backend

# 我想确认数据卷有没有在增长
docker system df -v

# 磁盘满了
docker builder prune

# 我想把环境彻底重置（会清空数据！）
docker compose down -v; docker compose up -d --build
```

---

## 下一篇

`doc/ops/02-deploy.md`：Compose 编排与 Nginx——健康检查如何串起启动顺序、环境变量与密钥管理的三层结构、Nginx 三段配置逐行、生产配置与开发的差异，以及**从零部署一遍**的完整流程。
