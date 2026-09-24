# 模块 05 · 数据库：MySQL 与 SQL

> 读者：会熟练使用数组方法，但没写过手写 SQL 或只写过简单的
> 目标：**能设计表与索引；能写 JOIN / 聚合 / EXISTS；能判断慢查询原因；能完成一次真实的备份恢复**
> 时长：约 1.5 天
> 相关：`03-persistence.md`（框架怎么用 SQL）、`doc/ops/03-troubleshooting.md`（线上排障）

---

## 0. 这一篇解决什么问题

模块 03 讲的是「怎么用框架写查询」。但框架替你做的事情太多，反而容易让人对底层失去感觉。这一篇补上：

| 问题 | 属于哪一层 |
|---|---|
| 表该分几张？字段该用什么类型？ | **设计**（第 1–2 节） |
| 为什么这个查询慢？索引到底用上了没？ | **索引**（第 3 节） |
| 框架生成的 SQL 我看不懂 | **SQL 基本功**（第 4 节） |
| 中文为什么乱码？ | **字符集**（第 5 节） |
| 什么时候需要事务？ | **事务**（第 6 节） |
| 线上出事了怎么救？ | **备份恢复**（第 8 节） |

**学习建议**：这一篇光读没用，一定要在 Database Client 里把每条 SQL 敲一遍。**SQL 是肌肉记忆，不是知识点。**

---

## 1. 五张表与它们的关系

本项目**没有建物理外键**（实测 `foreign_key_count = 0`），关系靠应用层维护。先看关系图：

```text
        ┌──────────┐
        │  t_user  │  1 个用户
        └────┬─────┘
             │ user_id
     ┌───────┴────────┐
     │                │
┌────▼─────┐    ┌─────▼────┐
│ t_diary  │    │  t_tag   │  1 个用户有多篇日记、多个标签
└────┬─────┘    └─────┬────┘
     │ diary_id       │ tag_id
     │      ┌─────────┘
     └──────►┌───────────────┐
             │  t_diary_tag  │  多对多关联（联合主键）
             └───────────────┘

┌──────────────┐
│ t_attachment │  diary_id → t_diary（可为 NULL：草稿上传）
└──────────────┘
```

**实测行数**（本地开发库）：

```text
t_user        1
t_diary       9     其中 deleted=0 的 6 条
t_tag         3
t_diary_tag  11     ← 关联表，行数最多，这是多对多的常态
t_attachment  2
```

### 1.1 为什么不用物理外键

| 方案 | 优点 | 缺点 |
|---|---|---|
| **物理外键**（`FOREIGN KEY`） | 数据库保证一致性，删父行会拦住你 | 每次写操作都要检查；高并发下有锁开销；**分库分表时完全失效**；DDL 迁移麻烦 |
| **逻辑外键**（本项目） | 性能好；迁移灵活；逻辑删除场景更自然 | **一致性要靠代码保证**；误删父行不会被拦住 |

**互联网项目的普遍选择是逻辑外键**，代价是**你必须自己记住哪些地方要级联清理**。本项目就有一处：

```java
// DiaryServiceImpl.delete()
diaryMapper.deleteById(id);                                                    // 逻辑删日记
diaryTagMapper.delete(Wrappers.<DiaryTag>lambdaQuery().eq(DiaryTag::getDiaryId, id));  // 手动清关联
```

> **如果这里漏了**，就会留下指向不存在日记的孤儿关联。没有外键约束的话，数据库不会提醒你。

### 1.2 逻辑删除让「外键」这件事更复杂

物理外键的语义是「父行不存在了，子行也不能存在」。但逻辑删除后**父行还在表里**（只是 `deleted = 1`），外键约束根本不会触发。所以在这个项目里，**物理外键本来就帮不上忙**——这也是逻辑外键更自然的原因之一。

---

## 2. 逐表设计解读

**看表设计时，要问的是「为什么用这个类型」，而不是「有哪些字段」**。

### 2.1 `t_user`

```sql
CREATE TABLE `t_user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`   VARCHAR(50)  NOT NULL COMMENT '登录名',
    `password`   VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
    `nickname`   VARCHAR(50)           DEFAULT NULL COMMENT '昵称',
    `avatar`     VARCHAR(255)          DEFAULT NULL COMMENT '头像地址',
    `email`      VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
    `created_at` DATETIME     NOT NULL COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
)
```

| 字段 | 类型选择的理由 |
|---|---|
| `id` **BIGINT** | 不用 `INT`——`INT` 上限约 21 亿，看似够用，但主键往往要作为其他表的外键长期存在。**BIGINT 是主键的默认选择** |
| `password` **VARCHAR(100)** | BCrypt 密文固定 60 字符，留 100 是余量。**注意长度是按字符算的**（utf8mb4 下 VARCHAR(100) 最多占 400 字节） |
| `nickname` **可为 NULL** | 有默认值逻辑（`register` 里 nickname 为空时用 username 兜底），所以数据库允许 NULL |
| `created_at` **NOT NULL** | 每条记录都必须有创建时间（由 `MyMetaObjectHandler` 自动填） |

**`UNIQUE KEY uk_username`** 是最关键的一行——它保证用户名唯一，且在并发下真正可靠（回顾模块 03 第 10 节）。

### 2.2 `t_diary`

```sql
CREATE TABLE `t_diary` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT       NOT NULL COMMENT '所属用户',
    `title`      VARCHAR(200) NOT NULL COMMENT '标题',
    `content`    LONGTEXT              COMMENT 'Markdown 原文',
    `summary`    VARCHAR(500)          DEFAULT NULL COMMENT '列表页摘要，冗余存储',
    `mood`       TINYINT               DEFAULT NULL COMMENT '1开心 2平静 3难过 4焦虑 5生气',
    `weather`    VARCHAR(20)           DEFAULT NULL,
    `diary_date` DATE         NOT NULL COMMENT '日记归属日期，可与创建时间不同（支持补写）',
    `is_public`  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否公开：0否 1是',
    `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1已删',
    `created_at` DATETIME     NOT NULL,
    `updated_at` DATETIME     NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `diary_date`),
    KEY `idx_user_mood` (`user_id`, `mood`)
)
```

**四个值得解释的决策**：

| 决策 | 理由 |
|---|---|
| `content` 用 **LONGTEXT** | Markdown 正文可能很长（限 100000 字）。**LONGTEXT 不能有默认值、不能整列索引**，这正是「列表查询绝不读 content」的原因 |
| `summary` 用 **VARCHAR(500)** 且是**冗余存储** | 摘要由后端从正文生成（剥掉 Markdown 标记）。**存下来是为了列表页不用每次都解析正文** ——典型的「用存储换计算」 |
| `diary_date` 用 **DATE 而非 DATETIME** | 日记的归属是「哪一天」，不是「哪一刻」。**而且按天统计（热力图、连续天数）时 DATE 处理起来干净得多** |
| `mood` 用 **TINYINT** | 取值只有 1~5，TINYINT（1 字节）足够。用 VARCHAR 存 '开心' 既费空间又难做范围查询 |
| `deleted` **NOT NULL DEFAULT 0** | 逻辑删除标记；**数据库有默认值，但 Java 对象仍要显式初始化**（模块 03 第 7.3 节的坑） |

**为什么区分 `diary_date` 与 `created_at`**：`diary_date` 是**业务日期**（用户说这篇日记属于哪天，可以补写历史），`created_at` 是**技术时间**（记录何时被创建）。**两者混用会导致「补写昨天日记」的功能无法实现**。

### 2.3 `t_tag`

```sql
CREATE TABLE `t_tag` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT      NOT NULL COMMENT '所属用户',
    `name`       VARCHAR(30) NOT NULL COMMENT '标签名',
    `color`      VARCHAR(10)          DEFAULT '#1677ff' COMMENT '标签颜色',
    `created_at` DATETIME    NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_name` (`user_id`, `name`)
)
```

**两处要注意**：

1. **没有 `updated_at`** ——标签不支持修改时间追踪（对比 `t_diary` 有）。不是遗漏，是取舍：标签很少改
2. **没有 `deleted`** ——标签是**物理删除**（回顾模块 03 第 7.4 节的取舍对比）

### 2.4 `t_diary_tag`（多对多关联表）

```sql
CREATE TABLE `t_diary_tag` (
    `diary_id` BIGINT NOT NULL COMMENT '日记 ID',
    `tag_id`   BIGINT NOT NULL COMMENT '标签 ID',
    PRIMARY KEY (`diary_id`, `tag_id`),
    KEY `idx_tag` (`tag_id`)
)
```

**关联表设计的三个要点**：

| 设计 | 作用 |
|---|---|
| **没有自增 `id`** | 关联表不需要自己的主键，用**联合主键**即可 |
| **`PRIMARY KEY (diary_id, tag_id)`** | 天然阻止「同一篇日记重复挂同一个标签」——**这就是幂等保证** |
| **额外的 `idx_tag(tag_id)`** | 支持「从标签反查日记」。因为主键是 `(diary_id, tag_id)`，**按 `tag_id` 单独查用不上主键索引**（最左前缀原则） |

> 关联表**不需要 `created_at`**——关联的存在性本身就是信息，没人在意它是何时建立的。（如果要做「按关注时间排序」，那才需要。）

### 2.5 `t_attachment`

```sql
CREATE TABLE `t_attachment` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `diary_id`   BIGINT                DEFAULT NULL COMMENT '关联日记，可为空（草稿上传）',
    `user_id`    BIGINT       NOT NULL COMMENT '所属用户',
    `url`        VARCHAR(500) NOT NULL COMMENT '文件访问地址',
    `filename`   VARCHAR(200)          DEFAULT NULL COMMENT '原始文件名',
    `size`       BIGINT                DEFAULT NULL COMMENT '文件大小（字节）',
    `mime_type`  VARCHAR(100)          DEFAULT NULL,
    `created_at` DATETIME     NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_diary` (`diary_id`)
)
```

**关键设计：`diary_id` 可为 NULL**，注释说明是「草稿上传」。

**这个决策解决了一个真实的时序问题**：用户在编辑页上传图片时，**日记可能还没保存**（还没有 id）。所以上传接口只能先落库一条 `diary_id = NULL` 的附件记录，返回 URL；等日记保存时再回填关联（或者说，关联通过 Markdown 里的 URL 建立）。

**如果不允许 NULL**：上传接口就必须先创建日记 → 用户取消编辑 → 产生一堆空日记。**这是很典型的「为了支持草稿态而放宽约束」的设计**。

---

## 3. 索引（🔴 本节是重点）

### 3.1 先建立直觉

**索引 = 书的目录**。没有目录时找一章要翻完整本书（全表扫描）；有目录时直接跳到那一页。

MySQL（InnoDB）的索引底层是 **B+ 树**——一种多路平衡查找树。你不需要懂它的实现，只需要记住两个推论：

1. **索引是有序的** → 所以它既能加速 `WHERE` 等值/范围查询，**也能加速 `ORDER BY`**
2. **索引是按列顺序组织的** → 所以「最左前缀原则」成立（见 3.3）

**两种索引**：

| 类型 | 说明 | 本项目实例 |
|---|---|---|
| **聚簇索引（主键）** | 叶子节点直接存整行数据；一张表只有一个 | `PRIMARY KEY(id)` |
| **二级索引** | 叶子节点存「索引列 + 主键值」，需要回表 | `idx_user_date` |

> **回表**：用二级索引找到主键后，再拿主键去聚簇索引取整行。这是为什么「查询的列越少、越可能用到覆盖索引」——如果查询的列全在二级索引里，就不用回表了。

### 3.2 本项目的索引清单

```text
+--------------+---------------+--------------------+------------+
| TABLE_NAME   | INDEX_NAME    | cols               | 唯一?      |
+--------------+---------------+--------------------+------------+
| t_user       | PRIMARY       | id                 | 唯一       |
| t_user       | uk_username   | username           | ✅ 唯一     |
| t_diary      | PRIMARY       | id                 | 唯一       |
| t_diary      | idx_user_date | user_id,diary_date | 普通       |
| t_diary      | idx_user_mood | user_id,mood       | 普通       |
| t_tag        | PRIMARY       | id                 | 唯一       |
| t_tag        | uk_user_name  | user_id,name       | ✅ 唯一     |
| t_diary_tag  | PRIMARY       | diary_id,tag_id    | 唯一       |
| t_diary_tag  | idx_tag       | tag_id             | 普通       |
| t_attachment | PRIMARY       | id                 | 唯一       |
| t_attachment | idx_diary     | diary_id           | 普通       |
+--------------+---------------+--------------------+------------+
```

**逐个说明「为什么建它」**：

| 索引 | 服务的查询 | 从哪来 |
|---|---|---|
| `uk_username` | 登录时按用户名查、注册时查重 | `WHERE username = ?` |
| `idx_user_date` | **列表页**：按用户查 + 按日期筛选 + 按日期排序 | `WHERE user_id=? AND diary_date BETWEEN ? AND ? ORDER BY diary_date DESC` |
| `idx_user_mood` | 按心情筛选；`countByMood` 的 `GROUP BY mood` | `WHERE user_id=? AND mood=?` |
| `uk_user_name` | 标签查重 | `WHERE user_id=? AND name=?` |
| `idx_tag` | 从标签反查日记 | `WHERE tag_id = ?` |
| `idx_diary` | 查某篇日记的所有附件 | `WHERE diary_id = ?` |

> **注意每个索引的第一个列都是 `user_id`**（除了 `idx_tag` 和 `idx_diary`）。这不是巧合——**本项目的所有查询都从「当前用户」出发**（数据隔离的底线），所以把 `user_id` 放最左列是必然选择。

### 3.3 最左前缀原则（EXPLAIN 实测）

**规则**：复合索引 `(a, b)` 能服务 `WHERE a = ?` 和 `WHERE a = ? AND b = ?`，**但不能服务 `WHERE b = ?`**。

为了拿到有意义的执行计划，我临时建了一个 2000 行的同结构表（**为什么要这样：见 3.4**）。

**A. 带 `user_id`（索引生效）**

```text
type: ref
possible_keys: idx_user_date,idx_user_mood
key: idx_user_date              ← 命中了
key_len: 8
ref: const
rows: 100                       ← 预估扫描 100 行（2000 行里的 1/20，因为 user_id 有 20 个不同值）
Extra: Using where; Backward index scan
```

**`Backward index scan` 是个惊喜**：查询里有 `ORDER BY diary_date DESC`，**索引反向扫描直接满足了排序需求，没有 filesort**。这就是 3.1 说的「索引有序，能同时服务过滤和排序」。

**B. 不带 `user_id`（跳过了最左列，索引完全用不上）**

```sql
EXPLAIN SELECT id,title FROM demo_index.t_diary
WHERE deleted=0 AND diary_date = '2026-03-01'
```

```text
type: ALL
possible_keys: NULL             ← 注意：连「可能的索引」都没有了
key: NULL
rows: 2000                      ← 全表扫描
Extra: Using where
```

**`possible_keys` 直接变成 NULL** ——优化器认定「没有任何索引能用在这个查询上」。

> **实践结论**：写查询时要**保证能带上复合索引的最左列**。本项目通过「所有查询强制带 `user_id`」自然满足了这一点——这也是为什么「数据隔离」这个安全要求顺带带来了性能收益。

### 3.4 一个反直觉现象：小表上优化器会主动放弃索引

**这一个发现非常重要，因为几乎每个初学者都会被它误导。**

我用本项目真实的 9 行数据跑同一个查询：

```sql
EXPLAIN SELECT id,title,summary,mood,weather,diary_date FROM t_diary
WHERE deleted=0 AND user_id=1 ORDER BY diary_date DESC, id DESC LIMIT 10
```

```text
type: ALL                       ← 全表扫描！
possible_keys: idx_user_date,idx_user_mood
key: NULL                       ← 索引「可能可用」，但优化器没用
rows: 9
Extra: Using where; Using filesort
```

**索引明明存在，为什么不用？**

因为**全表只有 9 行，且只占 1 个数据页**。读一个页就能拿到全部数据，走索引反而要「查索引 → 回表」两次开销，更慢。优化器的成本模型算出「全表扫更便宜」，就选了全表扫。

**对你的三个实际影响**：

| 影响 | 说明 |
|---|---|
| **本地 EXPLAIN 不代表生产** | 本地几行数据看不出索引问题，上线后数据量上来才会暴露 |
| **不要为了「让 EXPLAIN 好看」乱加索引** | 优化器是对的，它只是在当前数据量下做了最优选择 |
| **验证索引要看数据量** | 想验证索引是否有效，先造足够数据（几千行以上），或看 `possible_keys` 有没有列出你的索引 |

> **`possible_keys` vs `key` 的区别**就是为这个场景准备的：`possible_keys` 是「理论上能用的」，`key` 是「优化器实际选的」。**如果 `possible_keys` 有你的索引而 `key` 是 NULL，通常只是数据量太少**；如果 `possible_keys` 是 NULL，才是真的用不上（如 3.3 的 B 场景）。

### 3.5 索引的代价

索引不是免费的：

| 代价 | 说明 |
|---|---|
| **写变慢** | 每次 INSERT / UPDATE / DELETE 都要维护所有索引的 B+ 树 |
| **占空间** | 索引本身也占磁盘（本项目的索引还不算多） |
| **优化器可能选错** | 索引太多时，优化器的选择空间变大，反而可能选错 |

**实践原则**：

1. **先有查询，再有索引**——别凭想象加索引
2. **每个索引都要能说清它在服务哪条 SQL**（就像 3.2 的表格那样）
3. **联合索引优先于多个单列索引**——`(user_id, diary_date)` 通常比 `(user_id)` + `(diary_date)` 两个单列索引更好

---

## 4. SQL 基本功（对 JS 开发者）

### 4.1 用你熟悉的数组方法来理解

| JS 数组操作 | SQL | 例子 |
|---|---|---|
| `.filter(x => x.a > 1)` | `WHERE` | `WHERE mood = 1` |
| `.map(x => x.title)` | `SELECT title` | `SELECT id, title` |
| `.sort((a,b) => ...)` | `ORDER BY` | `ORDER BY diary_date DESC` |
| `.slice(0, 10)` | `LIMIT 10` | `LIMIT 10 OFFSET 0` |
| `.find(x => ...)` | `WHERE ... LIMIT 1` | `WHERE id = 7 LIMIT 1` |
| `.some(x => ...)` | `EXISTS (...)` | 见 4.3 |
| `[...new Set(arr)]` | `DISTINCT` | `SELECT DISTINCT diary_date` |
| `.reduce()` 做分组统计 | `GROUP BY` + 聚合函数 | 见 4.2 |
| `Object.groupBy(arr, fn)` | `GROUP BY` | 同上 |
| **不同：先 WHERE 后 SELECT** | SQL 是**声明式**的：你说「要什么」，不规定「怎么遍历」 | — |

**最后一个区别最重要**：JS 里你写的是**步骤**（先 filter 再 map），SQL 里你写的是**结果描述**，执行顺序由优化器决定。

**SQL 的实际执行顺序**（和你写的顺序不一样，这点容易搞错）：

```text
FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT
```

> 所以**不能在 `WHERE` 里用 `SELECT` 中定义的别名**（那时还没算出来），但可以在 `ORDER BY` 里用。

### 4.2 本项目三条手写 SQL 逐条讲

框架生成 SQL 很方便，但**统计类聚合查询必须手写**（框架的条件构造器表达不了 `GROUP BY`）。本项目在 `DiaryMapper` 里有三条：

**① 热力图：某年每天的篇数**

```java
@Select("""
        SELECT diary_date AS diaryDate, COUNT(*) AS total
        FROM t_diary
        WHERE user_id = #{userId} AND deleted = 0
          AND diary_date BETWEEN #{start} AND #{end}
        GROUP BY diary_date
        ORDER BY diary_date
        """)
List<DayCountVO> countByDateRange(...);
```

**逐句拆**：

| 片段 | 作用 |
|---|---|
| `SELECT diary_date AS diaryDate, COUNT(*) AS total` | `AS` 起别名，**别名要能对上 Java 字段名**（`diaryDate` 对应 record 的组件名） |
| `WHERE user_id = #{userId} AND deleted = 0` | **手写 SQL 必须自己加 `deleted = 0`**（框架只管自动生成的 SQL） |
| `BETWEEN #{start} AND #{end}` | `BETWEEN` 是闭区间（含两端） |
| `GROUP BY diary_date` | 按日期分组，每组一行 |
| `COUNT(*)` | 组内计数 |
| `#{userId}` | **MyBatis 的参数占位符**（编译成 `?`，防注入）。**绝不能用 `${}`**——那是字符串拼接，有注入风险 |

**`GROUP BY` + `COUNT(*)` 是 SQL 里最常用的组合**，等价于 JS：

```ts
const countByDate = {}
for (const d of diaries) {
  countByDate[d.diaryDate] = (countByDate[d.diaryDate] ?? 0) + 1
}
```

**② 连续天数：所有写作日期（去重、升序）**

```java
@Select("""
        SELECT DISTINCT diary_date
        FROM t_diary
        WHERE user_id = #{userId} AND deleted = 0
        ORDER BY diary_date
        """)
List<LocalDate> listDistinctDates(@Param("userId") Long userId);
```

**`DISTINCT` 的必要性**：同一天可能写多篇日记。`DISTINCT` 去重后，Java 侧算连续天数就简单了（列表里每个日期唯一）。

**为什么返回单列也能映射**：MyBatis 内置了 `LocalDate` 的 TypeHandler，`List<LocalDate>` 能直接接收。

**③ 心情分布：各心情的篇数**

```java
@Select("""
        SELECT IFNULL(mood, 0) AS mood, COUNT(*) AS total
        FROM t_diary
        WHERE user_id = #{userId} AND deleted = 0
        GROUP BY IFNULL(mood, 0)
        ORDER BY mood
        """)
List<MoodCountVO> countByMood(@Param("userId") Long userId);
```

**`IFNULL` 是这条 SQL 的核心**：

```sql
IFNULL(mood, 0)     -- 如果 mood 是 NULL，当作 0
```

**为什么需要它**：如果不管 NULL，`GROUP BY mood` 会把 NULL 单独归为一组，而 `SUM(各组)` 会等于「有心情的篇数」，**不等于总篇数**。用 `IFNULL(mood, 0)` 把「未记录心情」归入 0 组后，**各段之和恒等于总篇数**——这正是 P7 的验收标准之一。

**注意 `GROUP BY IFNULL(mood, 0)`**：要重复表达式，不能写成 `GROUP BY mood`（否则 NULL 又会被单独分组）。或者用别名：MySQL 允许 `GROUP BY mood` 引用 SELECT 的别名吗？——**MySQL 允许在 `GROUP BY` 里用别名**（这是 MySQL 的扩展，标准 SQL 不允许），但本项目显式写表达式更清晰、也没歧义。

**常用聚合函数**：

| 函数 | 作用 | 例子 |
|---|---|---|
| `COUNT(*)` | 行数 | 总篇数 |
| `COUNT(col)` | **非 NULL** 的行数 | 有心情的篇数 |
| `SUM(col)` | 求和 | — |
| `MAX` / `MIN` | 最大 / 最小 | `MAX(diary_date)` 取最近一篇 |
| `IFNULL(a, b)` | a 为 NULL 时取 b | 上面那条 |
| `COALESCE(a, b, c)` | 返回第一个非 NULL | 比 IFNULL 更通用 |

### 4.3 `EXISTS` vs `JOIN`

**需求**：查「带有某标签的所有日记」。

**方案一：JOIN**

```sql
SELECT d.* FROM t_diary d
INNER JOIN t_diary_tag dt ON dt.diary_id = d.id
WHERE d.user_id = 1 AND dt.tag_id = 34
```

**方案二：EXISTS**（本项目采用）

```sql
SELECT d.* FROM t_diary d
WHERE d.user_id = 1
  AND EXISTS (SELECT 1 FROM t_diary_tag dt
              WHERE dt.diary_id = d.id AND dt.tag_id = 34)
```

| | JOIN | EXISTS |
|---|---|---|
| 结果重复风险 | **日记挂多个标签时会重复**（要 `DISTINCT`） | **不会重复**（半连接语义） |
| 可读性 | 直观 | 「存在即满足」更贴合需求 |
| 性能 | 通常相当 | 通常相当 |

**本项目选 EXISTS 的关键理由**：**不会有重复行**。用 JOIN 的话，一篇日记挂 3 个标签就会返回 3 行，必须加 `DISTINCT` 或 `GROUP BY`——多一层出错的机会。

**写法细节**：`SELECT 1`（而不是 `SELECT *`）是惯例——`EXISTS` 只关心「有没有行」，不关心内容。

**在 MyBatis 里怎么用**：

```java
wrapper.apply("EXISTS (SELECT 1 FROM t_diary_tag dt WHERE dt.diary_id = t_diary.id AND dt.tag_id = {0})",
        query.tagId());
```

`{0}` 是参数占位符（对应 `?`），**不能拼字符串**（回顾模块 03 第 4.5 节）。

---

## 5. 字符集与排序规则

### 5.1 为什么必须是 `utf8mb4`

**MySQL 的 `utf8` 是个历史陷阱**——它其实只支持 **3 字节**的 UTF-8 字符，**存不了 emoji**（emoji 是 4 字节）。

```sql
-- 表用 utf8（不是 utf8mb4）时
INSERT INTO t_diary (title) VALUES ('今天很开心 😄');
-- ERROR 1366: Incorrect string value: '\xF0\x9F\x98\x84' for column 'title'
```

**本项目的选择**：

```sql
CREATE DATABASE IF NOT EXISTS `diary`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
```

**结论**：**新项目一律用 `utf8mb4`，不要用 `utf8`。**

### 5.2 排序规则（collation）

`utf8mb4_unicode_ci` 里的 `ci` = **case insensitive**（不区分大小写）。

| 排序规则 | 特点 |
|---|---|
| `utf8mb4_general_ci` | 旧版，快但某些语言排序不准 |
| **`utf8mb4_unicode_ci`** | 按 Unicode 标准排序，**本项目用这个** |
| `utf8mb4_0900_ai_ci` | MySQL 8 的新默认，更好（按 Unicode 9.0） |
| `utf8mb4_bin` | 区分大小写（按二进制比较） |

**`ci` 的实际影响**：

```sql
SELECT * FROM t_user WHERE username = 'Tester';   -- 在 ci 规则下，能匹配到 'tester'
```

> 这对登录功能有影响：`ci` 意味着**用户名不区分大小写**。决定「是否允许 `Tester` 和 `tester` 共存」时要知道这一点。

**实测验证**（在本地开发库上试的，三条结论都验证过）：

```sql
-- ① 现有用户只有 tester
SELECT id, username FROM t_user;                    -- 1 | tester

-- ② 插入大小写不同的 TESTER → 被唯一索引拦下
INSERT INTO t_user (username, ...) VALUES ('TESTER', ...);
-- ERROR 1062 (23000): Duplicate entry 'TESTER' for key 't_user.uk_username'

-- ③ 用任意大小写查询都能命中同一条
SELECT id, username FROM t_user WHERE username = 'TeStEr';   -- 1 | tester   ← 命中了
```

**结论**：

| 行为 | 结果 |
|---|---|
| `Tester` / `tester` 能否共存 | ❌ 不能（唯一索引在 `ci` 规则下视为同名） |
| 登录时输入 `Tester` 能否登录成功 | ✅ 能（查询会匹配到 `tester`） |

> 这是「同一个技术细节同时是好消息和坏消息」的典型：用户不必记大小写（体验好），但也就无法注册仅大小写不同的两个账号（能力受限）。**要改变这个行为，只能把列或索引改成 `utf8mb4_bin`**（二进制比较，区分大小写）。

### 5.3 一个已经踩过的坑

容器部署时 `sql/init.sql` 里的中文表注释和测试用户昵称曾经被写成**双重编码的乱码**。根因是容器内 `LANG` 为空导致 mysql 客户端字符集降级为 latin1。

**已在 `init.sql` 顶部加 `SET NAMES utf8mb4;` 免疫**，完整分析见 `development-log.md` 的 P8 缺陷 7。

**你要记住的操作要点**：命令行连数据库时，**永远带上 `--default-character-set=utf8mb4`**。

---

## 6. 事务与隔离级别（够用版）

### 6.1 ACID 一句话版

| 特性 | 含义 |
|---|---|
| **A** 原子性 | 事务内多步操作，要么全成，要么全败 |
| **C** 一致性 | 事务前后数据满足业务约束（这更多靠应用保证） |
| **I** 隔离性 | 并发事务之间互不干扰（**有级别可选，见 6.3**） |
| **D** 持久性 | 提交后即使断电也不丢 |

### 6.2 本项目的事务用在哪

```java
@Transactional  public DiaryDetailVO create(DiarySaveDTO dto)     // 插入日记 + 替换标签关联
@Transactional  public DiaryDetailVO update(Long id, DiarySaveDTO dto)
@Transactional  public void delete(Long id)                       // 逻辑删日记 + 删关联
```

**判断标准**：**一个方法里有没有多次写操作**。只读方法（`page` / `detail`）**不加**事务——这是有意的，避免无谓开销。

### 6.3 四种隔离级别

| 级别 | 脏读 | 不可重复读 | 幻读 |
|---|---|---|---|
| READ UNCOMMITTED | ✅ 会 | ✅ 会 | ✅ 会 |
| READ COMMITTED | ❌ | ✅ 会 | ✅ 会 |
| **REPEATABLE READ**（MySQL 默认） | ❌ | ❌ | ❌（InnoDB 用间隙锁基本解决） |
| SERIALIZABLE | ❌ | ❌ | ❌ |

**三个概念用人话解释**：

| 概念 | 场景 |
|---|---|
| **脏读** | 读到了别人**还没提交**的数据（别人可能回滚） |
| **不可重复读** | 同一个事务里读两次同一行，结果不一样（别人中间改了） |
| **幻读** | 同一个事务里查两次同一条件，**多出/少了行**（别人中间插了/删了） |

**MySQL 默认 `REPEATABLE READ`**，对本项目足够。本项目**没有显式改过隔离级别**——默认值在「正确性」和「性能」之间已经是个好平衡。

**本项目实际涉及的并发场景**：

| 场景 | 靠什么保证 |
|---|---|
| 两个请求同时注册同一用户名 | **`uk_username` 唯一索引**（不是靠事务） |
| 两个请求同时改同一篇日记 | 后提交的覆盖先提交的（**last write wins**，本项目接受这个语义） |
| 删除日记同时另一请求在改它 | `requireOwned` 各查一次，可能读到不同状态——**本项目接受**（个人日记场景并发极低） |

> **重要的认知**：不是所有并发问题都需要解决。**先判断「这个场景真的会并发吗」**，再决定要不要上锁。个人日记平台里，「两个人同时改同一篇日记」几乎不可能发生——为它设计乐观锁/悲观锁是过度设计。但「同时注册同一用户名」很容易发生（用户手抖双击），所以要靠唯一索引兜住。

---

## 7. 慢查询诊断

### 7.1 `EXPLAIN` 怎么读

```sql
EXPLAIN SELECT ... ;
```

**最该看的四列**：

| 列 | 看什么 |
|---|---|
| `type` | **最重要的**。从好到坏：`const` > `eq_ref` > `ref` > `range` > `index` > **`ALL`**。看到 `ALL` 就是全表扫描 |
| `key` | **实际用了哪个索引**。NULL = 没用索引 |
| `possible_keys` | 理论上能用哪些。**有值但 `key` 为 NULL，通常是数据量太少（见 3.4）** |
| `rows` | 预估扫描行数。**这个数字大 = 慢** |
| `Extra` | `Using filesort` = 额外排序（可能慢）；`Using temporary` = 用了临时表（GROUP BY 常见）；`Backward index scan` = 索引反向扫描（好事） |

### 7.2 索引失效的五种常见原因

| 原因 | 反例 | 修法 |
|---|---|---|
| **对列用函数** | `WHERE DATE(diary_date) = '2026-09-20'` | 改成 `WHERE diary_date >= '2026-09-20' AND diary_date < '2026-09-21'` |
| **前导模糊匹配** | `WHERE title LIKE '%爬山%'` | 无法优化；若必须，考虑全文索引 |
| **跳过最左列** | 索引 `(user_id, diary_date)`，查询只用 `diary_date` | 带上 `user_id` |
| **隐式类型转换** | `user_id` 是 BIGINT，`WHERE user_id = '1'`（字符串） | 传正确类型 |
| **OR 混用不同列** | `WHERE user_id = 1 OR title = 'x'` | 拆成两个查询用 `UNION`，或加索引 |

**实测 C（3.4 里那次）**：

```sql
EXPLAIN SELECT id,title FROM t_diary WHERE DATE(diary_date) = '2026-09-20'
```

```text
type: ALL
possible_keys: NULL        ← 对列用函数后，索引直接不可用
key: NULL
rows: ...
```

**注意 `possible_keys: NULL`** —— 这才是「真的用不上」，与 3.4 的「能用但优化器没选」有本质区别。

### 7.3 深翻页问题

```sql
-- 第 1 页（快）
SELECT * FROM t_diary WHERE user_id = 1 ORDER BY diary_date DESC LIMIT 10 OFFSET 0

-- 第 10000 页（慢）
SELECT * FROM t_diary WHERE user_id = 1 ORDER BY diary_date DESC LIMIT 10 OFFSET 99990
```

**为什么慢**：`OFFSET 99990` 要求数据库**先取出前 100000 行再丢掉**。

**本项目为什么不用担心**：日记是个人数据，不可能有 10 万篇。**但如果是社交类应用就必须处理**——常见方案是「记住上一页的最后一个 id，用 `WHERE id < ?` 代替 `OFFSET`」（叫 keyset 分页）。

> **这体现了一个重要的工程原则**：性能优化要基于**真实的数据规模**。个人日记的最大篇数是「每天 1 篇 × 50 年 ≈ 18000 篇」，连分页深翻的边都摸不到。

---

## 8. 备份与恢复

### 8.1 `mysqldump` 是什么

它把数据库导成一个 **SQL 脚本**（`CREATE TABLE` + 一堆 `INSERT`）。恢复就是重新执行这个脚本。这是**逻辑备份**。

| | 逻辑备份（mysqldump） | 物理备份（拷贝数据文件） |
|---|---|---|
| 产物 | 可读的 SQL 文本 | 二进制文件 |
| 体积 | 大（压缩后小） | 小 |
| 恢复速度 | 慢（要重新执行 SQL） | 快 |
| 跨版本 | ✅ 兼容 | ❌ 通常不行 |
| 单表恢复 | ✅ 可以 | 麻烦 |

**个人项目选逻辑备份**——产物可读、能进版本控制（当然含数据时不要）、跨版本稳。

### 8.2 本项目的备份脚本

```powershell
# 立即备份一次：写入 deploy/backups/，自动清理 7 天前的文件
powershell -ExecutionPolicy Bypass -File deploy\mysql-backup.ps1
```

**它的实现要点**：

1. **在容器内执行 mysqldump** —— 宿主机不需要装 MySQL 客户端
2. **密码经 `MYSQL_PWD` 传入** —— 不出现在命令行（`ps` 看不到）
3. **先 `--result-file` 写到容器内 `/tmp`，再 `docker compose cp` 拷出来** —— 避免 PowerShell 管道的 UTF-8/BOM 问题
4. **`--single-transaction`** —— InnoDB 下不加锁导出，不影响线上读写
5. **保留 7 天** —— 自动清理过期文件

### 8.3 恢复（⚠️ PowerShell 有个坑）

**PowerShell 不支持 `<` 输入重定向**：

```powershell
docker compose exec -T mysql mysql -uroot < dump.sql
# ❌ 报错："<"运算符是为将来使用而保留的
```

**正确的两种写法**：

```powershell
# ① 容器部署：先拷进容器，在容器内重定向
docker compose cp deploy\backups\diary-20260923-180642.sql mysql:/tmp/restore.sql
$pw = (Get-Content .env -Encoding UTF8 | Where-Object { $_ -like 'DB_PASSWORD=*' }) -replace '^DB_PASSWORD=', ''
docker compose exec -T -e "MYSQL_PWD=$pw" mysql sh -c 'mysql -uroot --default-character-set=utf8mb4 < /tmp/restore.sql'
docker compose exec -T mysql rm -f /tmp/restore.sql

# ② 本地开发：用 mysql 客户端自带的 source 命令
mysql.exe -u root -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4 `
  -e "source d:/work/diary/deploy/backups/diary-20260923-180642.sql"
```

> `source` 命令的路径要用**正斜杠**。

### 8.4 恢复演练（我实际做过一次）

**备份的价值在于「恢复得回来」**。我做过一次完整演练：

```text
① 取一份备份        → diary-20260923-180642.sql
② 插入哨兵数据      → INSERT 一个 username = 'sentinel_user' 的用户，此时 t_user = 5 行
③ 执行恢复          → mysql 退出码 0
④ 核对结果          → sentinel_user 消失（sentinel_left = 0），t_user 回到 4 行
                     表注释仍是正确的「日记表」（字符集没被弄坏）
```

**第 ④ 步能证明「表确实被 dump 重建了」** ——如果只是简单追加数据，哨兵用户不会消失。

**恢复后要做的检查**（这个演练同时验证了）：

| 检查 | 为什么 |
|---|---|
| 行数对不对 | 数据没丢 |
| 中文显示正常 | 恢复过程没引入编码问题 |
| 应用能正常访问 | 表被重建后，连接池里的旧连接仍然可用（实测冒烟测试 17/17 通过） |

> **恢复是覆盖式的**：dump 里含 `CREATE DATABASE IF NOT EXISTS` 与 `DROP TABLE IF EXISTS` + `CREATE TABLE`（`mysqldump --databases` 的行为），所以会**把同名表整个重建**。要彻底重来就先 `docker compose down -v` 清空数据卷。

---

## 9. 动手练习

1. **画关系图**：不看本文，自己在纸上画出 5 张表的关系与关联字段
2. **验证最左前缀**：用你在模块 03 练习里造的临时表（或自己造 2000 行），分别 `EXPLAIN` 带/不带 `user_id` 的查询，对比 `type` 与 `key`
3. **观察 `possible_keys` 与 `key` 的差异**：在本项目真实的 9 行数据上 `EXPLAIN` 列表查询，确认 `possible_keys` 有值而 `key` 是 NULL，并解释原因
4. **写一条聚合 SQL**：统计「当前用户每周写了多少篇日记」
   - 提示：`YEARWEEK(diary_date)` 可以做周分组
5. **验证逻辑删除的影响**：在 Database Client 里执行 `SELECT COUNT(*) FROM t_diary` 与 `SELECT COUNT(*) FROM t_diary WHERE deleted = 0`，确认数字不同
6. **完成一次恢复演练**：备份 → 改一行数据 → 恢复 → 确认数据回退。**这一步一定要做**——备份脚本没验证过恢复等于没有备份
7. **制造一次索引失效**：对 `diary_date` 用 `YEAR()` 函数做筛选，`EXPLAIN` 观察 `possible_keys` 变成 NULL，然后改成范围查询对比

---

## 附录 · SQL 速查（对照 JS）

```sql
-- 过滤
WHERE mood = 1                              -- filter(x => x.mood === 1)
WHERE mood IN (1, 2)                        -- [1,2].includes(x.mood)
WHERE title LIKE '%爬山%'                    -- x.title.includes('爬山')
WHERE title LIKE '爬山%'                     -- x.title.startsWith('爬山')
WHERE deleted = 0 AND user_id = 1           -- x.deleted === 0 && x.userId === 1
WHERE diary_date BETWEEN '2026-01-01' AND '2026-12-31'   -- 闭区间

-- 排序与分页
ORDER BY diary_date DESC, id DESC           -- sort((a,b) => ...)
LIMIT 10 OFFSET 0                           -- slice(0, 10)

-- 去重与聚合
SELECT DISTINCT diary_date                  -- [...new Set(...)]
COUNT(*)                                    -- arr.length
COUNT(mood)                                 -- 非 NULL 的个数
SUM(size)                                   -- reduce 求和
MAX(diary_date) / MIN(diary_date)           -- 最大 / 最小

-- 分组
GROUP BY diary_date                         -- Object.groupBy(...)
HAVING COUNT(*) > 1                         -- 分组后过滤（WHERE 是分组前过滤！）

-- 空值
IFNULL(mood, 0)                             -- x.mood ?? 0
COALESCE(a, b, c)                           -- a ?? b ?? c
IS NULL / IS NOT NULL                       -- x == null / x != null

-- 存在性
EXISTS (SELECT 1 FROM t WHERE ...)          -- arr.some(...)

-- 关联（少用，本项目优先 EXISTS）
INNER JOIN t2 ON t2.id = t1.t2_id           -- 两边都有才保留
LEFT JOIN  t2 ON t2.id = t1.t2_id           -- 左表全保留
```

**易错点提醒**：

| 易错 | 说明 |
|---|---|
| `WHERE` vs `HAVING` | `WHERE` 在分组**前**过滤，`HAVING` 在分组**后**过滤 |
| `COUNT(*)` vs `COUNT(col)` | 后者**不算 NULL 行** |
| `NULL = NULL` 是 NULL | 判空必须用 `IS NULL`，不能用 `=` |
| `NOT IN (含 NULL 的集合)` | 结果恒为空——这是经典陷阱 |
| `LIKE '%x'` | 前导 `%` 无法用索引 |

---

## 后端部分完成 ✅

`doc/backend/` 的五个模块到此结束。接下来是运维部分：

| 文档 | 主题 |
|---|---|
| `doc/ops/01-docker.md` | 镜像分层为什么让重建只要 20 秒、卷与网络、Dockerfile 逐行 |
| `doc/ops/02-deploy.md` | Compose 编排、Nginx 反代、环境变量与密钥管理 |
| `doc/ops/03-troubleshooting.md` | 按四层定位问题的排障方法论、日志、备份恢复演练 |
