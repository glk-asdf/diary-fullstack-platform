# 日记全栈平台 · 使用手册

> 更新日期：2026-09-23
> 读者：使用或运维本系统的人（不需要读源码）
> 定位：**按场景讲操作**——「我要做什么 → 怎么做 → 应该看到什么」

**相关文档**

| 文档 | 内容 |
|---|---|
| `README.md` | 项目入口：技术栈、目录结构、环境要求、命令速查 |
| `development-plan.md` | 分步开发方案与各阶段验收标准 |
| `development-log.md` | 开发实录：决策依据、踩坑与修复过程 |
| **本文** | 使用手册 |

---

## 1. 两条上手路线

系统有两种跑法，**可以同时存在、互不冲突**：

| 路线 | 适用 | 特点 |
|---|---|---|
| **A. 容器部署** | 想要一个能用的系统；不装 JDK / Node / 数据库 | 一条命令起全栈，只暴露 80 端口 |
| **B. 本地开发** | 要改代码、调试 | 前后端热更新，中间件直装在本机 |

---

## 2. 路线 A：容器部署（推荐）

**前置**：只需 Docker Desktop。首次启用 WSL2 后**必须重启一次电脑**，否则守护进程起不来（`wsl --status` 会报「WSL2 无法启动」）。

```powershell
cd <仓库根目录>

# 1. 准备环境变量。必填项缺失时 compose 会直接报错退出，不会用默认弱密码启动
Copy-Item .env.example .env
#    编辑 .env，至少改掉 DB_PASSWORD / JWT_SECRET / MINIO_ACCESS_KEY / MINIO_SECRET_KEY

# 2. 构建并启动（首次需拉镜像 + 在容器内编译前后端，约 5~10 分钟；改代码后重建仅约 20 秒）
docker compose up -d --build

# 3. 冒烟测试：17 项断言，覆盖链路 / 注册登录 / CRUD / 上传 / 统计 / 401 拦截
powershell -ExecutionPolicy Bypass -File deploy\smoke-test.ps1
```

**应该看到**：

```text
SERVICE   STATUS                    PORTS
backend   Up (healthy)              8080/tcp
minio     Up (healthy)              9000/tcp
mysql     Up (healthy)              127.0.0.1:3307->3306/tcp
nginx     Up (healthy)              0.0.0.0:80->80/tcp
redis     Up (healthy)              6379/tcp

通过 17 项，失败 0 项
```

然后浏览器打开 **http://localhost**（`APP_PORT` 非 80 时为 `http://localhost:<APP_PORT>`），用内置账号登录：

| 用户名 | 密码 |
|---|---|
| `tester` | `123456` |

> 首次启动时 `sql/init.sql` 自动建库建表并写入该账号。

---

## 3. 路线 B：本地开发

**前置**：JDK 25、Node 22、MySQL 8.4.9 / Redis / MinIO（均已直装在本机 `D:\business\tools`）。Maven 用仓库自带的 `mvnw`，无需安装。

```powershell
# 1. 启动中间件（三个脚本幂等，重复执行会直接返回）
powershell -ExecutionPolicy Bypass -File D:\business\tools\mysql-start.ps1
powershell -ExecutionPolicy Bypass -File D:\business\tools\redis-start.ps1
powershell -ExecutionPolicy Bypass -File D:\business\tools\minio-start.ps1

# 2. 后端
cd backend; .\mvnw.cmd spring-boot:run        # → http://localhost:8080

# 3. 前端
cd frontend; npm install; npm run dev          # → http://localhost:5173
```

**注意**：这三个中间件**没有注册为 Windows 服务**，重启电脑后需要手动启动。否则后端测试会报 `Communications link failure`、17 个测试全挂——遇到这种情况**先看服务状态，再看代码**。

---

## 4. 端口规划（两套并存不冲突）

| 用途 | 容器部署 | 本地开发 |
|---|---|---|
| 前端入口 | `http://localhost:80`（Nginx） | `http://localhost:5173`（Vite） |
| 后端 API | 经 Nginx `/api/` 反代，不直接暴露 | `http://localhost:8080` |
| MySQL | `127.0.0.1:3307`（仅回环，供本机客户端） | `127.0.0.1:3306` |
| Redis | 仅在容器内网 | `127.0.0.1:6379` |
| MinIO | 仅在容器内网，经 Nginx `/files/` 访问 | `127.0.0.1:9000`（控制台 9001） |

> 容器部署主动避开了 3306 / 6379 / 9000 / 8080，所以**容器与本地开发可以同时跑**。

---

## 5. 功能使用流程

### 5.1 注册与登录

- 打开站点会自动跳转 `/login`；没有账号点「注册」
- 用户名规则：**4~20 位**，仅字母、数字、下划线；密码 **6~32 位**
- 登录成功后令牌存在浏览器本地；Access Token 默认 **30 分钟**
- Access Token 过期时前端会**自动用 Refresh Token 换新令牌并重放原请求**，用户无感
- Refresh Token 默认 **7 天**，且每次刷新都会**轮换**（旧令牌立即失效）
- 退出登录会删除服务端的 Refresh Token，使其立刻失效

### 5.2 写第一篇日记

1. 左侧「日记」→ 右上角「写日记」（或空态里的「写第一篇」）
2. 填写内容：

| 字段 | 必填 | 说明 |
|---|---|---|
| 标题 | ✅ | 最长 200 字 |
| 日记日期 | ✅ | 默认今天，**可以补写历史日期**——热力图与连续打卡都按这个日期算 |
| 正文 | — | Markdown，最长 100000 字 |
| 心情 | — | 五选一，见下表 |
| 天气 | — | 最长 20 字 |
| 标签 | — | 可多选，也可在下拉里直接新建 |

**心情取值**（前后端约定一致）：

| 值 | 心情 | 颜色 |
|---|---|---|
| 1 | 😄 开心 | `#faad14` |
| 2 | 🙂 平静 | `#52c41a` |
| 3 | 😔 难过 | `#1677ff` |
| 4 | 😰 焦虑 | `#722ed1` |
| 5 | 😠 生气 | `#f5222d` |

3. 正文区支持**实时预览**；编辑器还支持按钮 / 拖拽 / 粘贴三种插图方式
4. 保存后进入详情页

**关于摘要**：列表里显示的摘要由后端自动生成——从正文剥离 Markdown 标记（`#`、`**`、`![图](...)` 等）后截取，**不需要手填**。

### 5.3 标签

左侧「标签」可新建 / 编辑 / 删除。

- 标签名同一用户内不可重复（重复会提示「标签名已存在」）；**不同用户可以有同名标签**
- 不指定颜色时默认 `#1677ff`
- **删除标签会自动解除它与所有日记的关联**（标签是主数据；而删除日记时关联记录会保留，便于日后做回收站）

### 5.4 查找日记

列表页顶部有 4 类筛选，可任意组合：

| 控件 | 作用 |
|---|---|
| 搜索框 | 匹配**标题与摘要**，输入后 300ms 防抖再请求 |
| 标签下拉 | 按标签筛选 |
| 心情下拉 | 按心情筛选 |
| 日期范围 | 按日记日期区间筛选 |

另外：

- **筛选条件会同步到 URL**——刷新页面或把链接发给别人，筛选状态都能复原
- 右上角可切换**卡片 / 时间轴**两种视图
- 有筛选条件时会出现「重置」按钮；无结果时按提示点「清除筛选条件」
- 分页默认每页 9 条，可切 9 / 18 / 27；**接口层每页上限 50**（超出会自动收敛到 50）

### 5.5 在日记里插入图片

**支持格式**：jpg / jpeg / png / gif / webp
**单文件上限**：5 MB（前后端都校验，Nginx 层放行到 10 MB 留余量）

三种方式任选：

1. 编辑器工具栏的插图按钮
2. 把图片直接**拖进**编辑器
3. **粘贴**剪贴板里的图片

图片走后端中转上传（不是浏览器直传 MinIO），因此后端会统一做鉴权、扩展名与 MIME 双重校验。上传成功后返回的地址形如：

```text
容器部署：/files/diary/2026/09/<uuid>.png        ← 同源路径，经 Nginx 反代 MinIO
本地开发：http://127.0.0.1:9000/diary/2026/09/<uuid>.png
```

> bucket 设为**公开只读**（只允许 `s3:GetObject`，不允许列举对象），这样日记里的图片链接长期有效，不会像预签名 URL 那样过期。

### 5.6 Markdown 支持

正文用 Markdown 编写，渲染时会经过 `rehype-sanitize` 过滤以**防 XSS**。支持标题、粗体、斜体、引用、有序/无序列表、代码块、表格、链接、图片、分割线。

### 5.7 统计页

左侧「统计」：

- **4 张数字卡片**：总篇数、本月篇数、当前连续、最长连续
- **写作热力图**：GitHub 风格，按年切换（可选年份范围从最早一篇所在年到今年）。颜色深浅按当天篇数分 5 档；鼠标悬停显示「日期：N 篇」
- **心情分布**：环形图，未记录心情的日记归入「未记录」——**各段之和恒等于总篇数**

**「当前连续」的判定规则**（这是产品定义，不是四舍五入）：

| 最近一篇的日期 | 当前连续 |
|---|---|
| 今天 | 从今天起算 |
| 昨天 | **延续计算**（今天还没结束，不算断） |
| 早于昨天 | 0 |

> 同一天写多篇只算一天。补写历史日期会立即反映到热力图与连续天数上。

### 5.8 个人中心

左侧「个人中心」可修改：

- 昵称（最长 20 字）
- 邮箱（校验格式）
- 头像地址（URL，最长 255 字）

> **目前没有「修改密码」功能**——这是已知限制，需要改密码时只能走数据库。

### 5.9 主题与草稿保护

- 顶栏可切换**亮色 / 暗色**，偏好记在浏览器本地
- 编辑页有**草稿保护**：内容有改动时切换页面会弹确认框，关闭/刷新标签页会被浏览器拦截

---

## 6. 数据管理

### 6.1 备份

```powershell
# 立即备份一次：写入 deploy/backups/，自动清理 7 天前的文件
powershell -ExecutionPolicy Bypass -File deploy\mysql-backup.ps1
```

输出示例：

```text
开始备份 diary ...
备份完成: ...\deploy\backups\diary-20260923-171851.sql (8.7 KB)
已清理过期备份: diary-20200101-000000.sql
当前保留 1 份备份（保留 7 天）
```

**设为每日计划任务**：

```powershell
schtasks /Create /TN "diary-mysql-backup" /SC DAILY /ST 02:30 `
  /TR "powershell -NoProfile -ExecutionPolicy Bypass -File <仓库绝对路径>\deploy\mysql-backup.ps1" /F
```

Linux 服务器改用 `deploy/mysql-backup.sh` + crontab（脚本头部有示例）。

> `deploy/backups/` 已在 `.gitignore` 中——**备份含真实用户数据，绝不能提交**。

### 6.2 恢复

备份文件是带 `CREATE DATABASE` 的完整 dump，直接导入即可：

```powershell
# 容器部署：先把 dump 拷进容器，再在容器内做重定向
docker compose cp deploy\backups\diary-20260923-171851.sql mysql:/tmp/restore.sql
$pw = (Get-Content .env -Encoding UTF8 | Where-Object { $_ -like 'DB_PASSWORD=*' }) -replace '^DB_PASSWORD=', ''
docker compose exec -T -e "MYSQL_PWD=$pw" mysql sh -c 'mysql -uroot --default-character-set=utf8mb4 < /tmp/restore.sql'
docker compose exec -T mysql rm -f /tmp/restore.sql      # 清理容器内临时文件

# 本地开发：用 mysql 客户端自带的 source 命令（等价于重定向）
& "D:\business\tools\mysql-8.4.9-winx64\bin\mysql.exe" -u root -h 127.0.0.1 -P 3306 `
  --default-character-set=utf8mb4 -e "source <dump 的绝对路径，用正斜杠>"
```

> **不要写 PowerShell 的 `<` 重定向**——PowerShell 根本不支持该运算符（报「"<"运算符是为将来使用而保留的」）。
> 上面用「拷进容器 + 容器内重定向」与「mysql 的 `source` 命令」两种方式绕开它，同时也顺带避开了 PowerShell 管道对 UTF-8 的编码干扰。
> `source` 命令的路径要用**正斜杠**，例如 `source d:/work/diary/deploy/backups/diary-20260923-180642.sql`。

> 恢复是**覆盖式**的：dump 里含 `CREATE DATABASE IF NOT EXISTS` 与建表语句，同名表已存在时不会重建。要彻底重来，先 `docker compose down -v` 清空数据卷再恢复。

### 6.3 查看数据

**图形界面**（CodeBuddy 装了 Database Client 扩展）：

| 目标 | Host / Port | 账号 |
|---|---|---|
| 容器库 | `127.0.0.1` / **`3307`** | `root` / `.env` 里的 `DB_PASSWORD` |
| 本地开发库 | `127.0.0.1` / `3306` | `root` / `123456` |

取容器库密码到剪贴板（避免手抄出错）：

```powershell
(Get-Content .env -Encoding UTF8 | Where-Object { $_ -like 'DB_PASSWORD=*' }) -replace '^DB_PASSWORD=', '' | Set-Clipboard
```

**命令行**：

```powershell
# 容器库（密码由容器内 shell 展开，无需手输）
docker compose exec -T mysql sh -c "mysql -uroot -p`$MYSQL_ROOT_PASSWORD --default-character-set=utf8mb4 --database=diary --table -e 'show tables;'"
```

**⚠️ `--default-character-set=utf8mb4` 不能省**：容器内 `LANG` 为空，mysql 客户端会把字符集降级为 **latin1**——读中文显示成 `?`，**写中文会被存成双重编码的乱码**。

### 6.4 Docker Desktop 图形界面

| GUI 位置 | 作用 | 等价命令 |
|---|---|---|
| Containers → 项目 `diary` | 5 个服务的状态与健康 | `docker compose ps` |
| Logs 标签 | 带搜索的日志 | `docker compose logs -f <服务>` |
| **Exec 标签** | 在容器内开终端（最省事） | `docker compose exec <服务> sh` |
| Files 标签 | 浏览容器文件系统 | — |
| Stats 标签 | CPU / 内存曲线 | `docker stats` |
| Builds 页 | 构建历史与缓存占用 | `docker builder prune` |

**不要混用 GUI 与命令行操作同一套服务**：在 GUI 里 Stop 容器不会反映到 `.env`，下次 `docker compose up -d` 又会把它拉起来。排查前先用 `docker compose ps` 确认一遍。

---

## 7. 故障排查（按症状）

| 症状 | 原因 | 处理 |
|---|---|---|
| `docker compose up` 直接报错退出 | `.env` 缺必填项（`DB_PASSWORD` / `JWT_SECRET` / `MINIO_*`） | 这是刻意设计。按报错提示补全 `.env` |
| 后端容器启动失败，日志含 `JWT_SECRET` | 同上，`application-prod.yml` 里该值无默认 | 补 `.env` 后重启 |
| 服务一直 `unhealthy` | — | 看 `docker inspect <容器> --format '{{.State.Health}}'`，里面有健康检查命令的**真实输出**，比翻应用日志快 |
| `/api/**` 返回 502 | 后端容器被重建换了 IP | 已用 `resolver` + 变量 `proxy_pass` 修复。若仍出现，`docker compose restart nginx` |
| 数据库客户端报 `Access denied for user 'root'@'172.18.0.1'` | `172.18.0.1` 是 Docker 网桥网关，说明通路正常、**密码不对**——多半填了本地库的 `123456` | 用 6.3 的命令取容器库密码 |
| 会话中看到中文显示成 `?` | 客户端字符集是 latin1 | 命令加 `--default-character-set=utf8mb4` |
| 后端测试全部 `Communications link failure` | 重启电脑后本机 MySQL 没启动 | 跑 `mysql-start.ps1`（**先查服务状态，再怀疑代码**） |
| 前端路由刷新后 404 | SPA 回退没生效 | 已配 `try_files $uri $uri/ /index.html`；确认访问的是 Nginx 而不是别的东西 |
| 上传图片失败 | 超过 5 MB 或格式不在白名单 | 仅支持 jpg / jpeg / png / gif / webp |
| `docker compose exec` 报 `the input device is not a TTY` | 在管道/脚本里调用 | 加 `-T` |
| MinIO 容器起不来 | 官方社区版已从 Docker Hub 下架 | 镜像源在 **quay.io**，compose 里已写全地址 |
| 磁盘占用越来越大 | 构建缓存堆积 | `docker builder prune`（实测可回收约 1.6 GB） |

---

## 8. 附录

### 8.1 页面路由表

| 路径 | 页面 | 需登录 |
|---|---|---|
| `/login` | 登录 | — |
| `/register` | 注册 | — |
| `/` | 重定向到 `/diaries` | ✅ |
| `/diaries` | 日记列表（筛选 / 视图切换 / 分页） | ✅ |
| `/diaries/new` | 写日记 | ✅ |
| `/diaries/:id` | 日记详情（Markdown 渲染） | ✅ |
| `/diaries/:id/edit` | 编辑日记 | ✅ |
| `/tags` | 标签管理 | ✅ |
| `/stats` | 统计 | ✅ |
| `/profile` | 个人中心 | ✅ |
| 其他 | 404 页 | — |

未登录访问受保护路径会自动跳登录页，**登录后回跳到原路径**。

### 8.2 接口清单

统一前缀 `/api/v1`，统一响应体 `{ "code": 0, "message": "ok", "data": ... }`（`code = 0` 为成功）。除下表标注「免认证」外，均需请求头 `Authorization: Bearer <accessToken>`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/ping` | 健康检查（免认证） |
| POST | `/auth/register` | 注册（免认证） |
| POST | `/auth/login` | 登录（免认证） |
| POST | `/auth/refresh` | 刷新令牌（免认证） |
| POST | `/auth/logout` | 登出（免认证） |
| GET | `/users/me` | 当前用户信息 |
| PUT | `/users/me` | 修改昵称 / 邮箱 / 头像 |
| GET | `/diaries` | 列表，query：`page` `size` `keyword` `tagId` `mood` `startDate` `endDate` |
| GET | `/diaries/{id}` | 详情（含正文与标签） |
| POST | `/diaries` | 新建 |
| PUT | `/diaries/{id}` | 更新 |
| DELETE | `/diaries/{id}` | 删除（逻辑删除） |
| GET | `/tags` | 标签列表 |
| POST | `/tags` | 新建标签 |
| PUT | `/tags/{id}` | 修改标签 |
| DELETE | `/tags/{id}` | 删除标签（并解除日记关联） |
| POST | `/files/upload` | 上传图片，multipart 字段名 **`file`** |
| GET | `/stats/calendar` | 热力图，query：`year`（可空，默认今年） |
| GET | `/stats/overview` | 统计概览 |

分页响应结构：`{ total, pages, current, size, records }`。

**接口文档**：本地开发访问 `http://localhost:8080/swagger-ui.html`。**生产环境默认关闭**（避免对外暴露完整 API 结构）；临时要看，给 backend 服务加 `SPRINGDOC_ENABLED=true` 后重启。

### 8.3 环境变量

配置模板见 `.env.example`，逐项说明见文件内注释。必填项：`DB_PASSWORD`、`JWT_SECRET`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`。

> 本文件**只被 `docker compose` 读取**。本地开发不需要 `.env`——相关默认值在 `backend/src/main/resources/application-dev.yml`。

### 8.4 命令速查

```powershell
# 启停与查看
docker compose up -d --build      # 构建并启动
docker compose ps                 # 状态与健康检查
docker compose logs -f backend    # 跟踪后端日志
docker compose restart backend    # 重启单个服务
docker compose down               # 停止，保留数据卷
docker compose down -v            # 停止并删除数据卷（清空所有数据）

# 验证与备份
powershell -ExecutionPolicy Bypass -File deploy\smoke-test.ps1     # 冒烟测试（17 项）
powershell -ExecutionPolicy Bypass -File deploy\mysql-backup.ps1   # 备份
```

### 8.5 已知限制

| 项 | 说明 |
|---|---|
| **无修改密码功能** | 个人中心只能改昵称 / 邮箱 / 头像 |
| **MinIO 为归档版** | 开源版已被官方归档，无安全更新。**公网部署前必须换成 SeaweedFS 或云厂商 OSS** |
| 图片未压缩 | 未做压缩与缩略图，大图影响加载速度 |
| 无 HTTPS | 证书与 443 未配置，公网部署需自行补 |
| 无容器资源上限与日志轮转 | 单机自用可接受，长期运行建议补 `deploy.resources.limits` 与 `logging.options.max-size` |
| 分享 / 导出 / 全文搜索 | 未实现（原方案的 P9 可选扩展） |
| 删除不可恢复 | 日记是逻辑删除、数据仍在库中，但**界面没有回收站入口** |
