# 模块 03 · 持久层：MyBatis-Plus 与数据库交互

> 读者：已读完 `01-java-basics.md`、`02-spring-boot.md`
> 目标：**能写条件查询与分页；能识别并消除 N+1；知道逻辑删除的代价；知道事务边界该画在哪**
> 时长：约 1.5 天
> 上一篇：`02-spring-boot.md`

---

## 0. 这一篇解决什么问题

前端你用的是「拿数据 → 展示」，数据从哪来不用管。到了后端，**数据怎么持久化**是核心工作，这里有三类问题：

| 问题类型 | 具体表现 | 本篇对应章节 |
|---|---|---|
| **怎么写查询** | 条件构造器、分页、字段裁剪 | 第 3–6 节 |
| **怎么不写查询** | 逻辑删除、自动填充（框架替你写 SQL） | 第 7–8 节 |
| **怎么写得快** | N+1、索引、事务边界 | 第 9–12 节 |

**先记住一句话**：本项目的持久层几乎没有手写 SQL——**95% 的查询是「条件构造器拼出来的」**，剩下 5% 是统计类聚合（用 `@Select` 注解写 SQL，见模块 05）。

---

## 1. 地图：数据从 Java 对象到表的往返

```text
Java 世界                                 数据库世界
─────────────────────────────────────    ─────────────────
Diary 对象                                 t_diary 表的一行
  id=7, userId=1, title="爬山",     ←→     id | user_id | title | ...
  content="## ...", summary="...",
  mood=2, diaryDate=2026-09-21
                                            ↑
                                      表名/列名映射靠注解
                                      （@TableName / 字段名 → snake_case）
```

**两个方向的映射**：

| 方向 | 谁负责 | 靠什么 |
|---|---|---|
| Java 对象 → SQL 参数 | MyBatis-Plus | `Diary` 的字段 |
| 查询结果 → Java 对象 | MyBatis | 列名 `user_id` → 字段 `userId`（下划线转驼峰） |

下划线转驼峰是**开启状态**（`application.yml`）：

```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true      # user_id → userId，diary_date → diaryDate
```

> 所以你写 `private LocalDate diaryDate;` 就能对上 `diary_date` 列，**不需要额外注解**。这也是为什么 `Diary` 里只有 `id` 和 `deleted` 带了注解——其他字段靠命名约定自动映射。

---

## 2. Entity 映射（逐行读 `entity/Diary.java`）

```java
@TableName("t_diary")                          // ① 类 → 表
public class Diary {

    @TableId(type = IdType.AUTO)               // ② 主键 + 自增策略
    private Long id;

    private Long userId;                       // ③ 靠命名约定映射到 user_id
    private String title;
    /** Markdown 原文，列表查询禁止读取该字段 */
    private String content;
    private String summary;
    private Integer mood;
    private String weather;
    private LocalDate diaryDate;
    private Integer isPublic;

    @TableLogic                                // ④ 逻辑删除标记
    private Integer deleted = 0;               //    显式初始化，见 7.3

    @TableField(fill = FieldFill.INSERT)        // ⑤ 插入时自动填充
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE) // ⑥ 插入与更新时都填充
    private LocalDateTime updatedAt;

    // ... getter / setter / toString
}
```

### 注解说明

| 注解 | 作用 | 必填吗 |
|---|---|---|
| `@TableName("t_diary")` | 类名 `Diary` 与表名 `t_diary` 不一致，必须显式声明 | 表名与类名一致时可省 |
| `@TableId(type = IdType.AUTO)` | 主键字段；`AUTO` 表示用数据库自增 | 字段名叫 `id` 时可省（默认就是主键），显式写更清楚 |
| `@TableLogic` | 该字段是逻辑删除标记 | 见第 7 节 |
| `@TableField(fill = ...)` | 由 `MyMetaObjectHandler` 自动填值 | 需要自动填充时必填 |
| `@TableField("xxx")` | 列名与字段名不一致时指定 | 本项目靠下划线转驼峰，**没用到** |

### 为什么 Entity 是 `class` 而不是 `record`

模块 01 说过「DTO / VO 用 record」。Entity 必须是 `class`，原因很硬：

**MyBatis-Plus 映射查询结果时需要「无参构造 + setter」**——它先 `new Diary()` 建空对象，再逐个调 setter 填值。record 只有全参构造器且字段 `final`，填不进去。

```java
// MyBatis 内部大致在做这件事（伪代码）
Diary diary = Diary.class.getDeclaredConstructor().newInstance();
diary.setId(rs.getLong("id"));
diary.setTitle(rs.getString("title"));
```

> 由此也能理解为什么 `Diary` 结尾有一个手写的 `toString()`——Entity 调试时经常要打印，而 record 自带、class 得自己写。

---

## 3. `BaseMapper`：白送的 CRUD

```java
@Mapper
public interface DiaryMapper extends BaseMapper<Diary> {
    // 一行都不用写
}
```

`BaseMapper<T>` 已经提供了常用方法：

| 方法 | 生成的 SQL | 本项目用在哪 |
|---|---|---|
| `insert(entity)` | `INSERT INTO t_diary (...) VALUES (...)` | `create()` |
| `updateById(entity)` | `UPDATE t_diary SET ... WHERE id = ?` | `update()` |
| `deleteById(id)` | **`UPDATE t_diary SET deleted = 1 WHERE id = ?`**（因为有 `@TableLogic`） | `delete()` |
| `selectById(id)` | `SELECT ... WHERE id = ? AND deleted = 0` | — |
| `selectOne(wrapper)` | `SELECT ... WHERE <条件> AND deleted = 0` | `requireOwned()` |
| `selectList(wrapper)` | 同上，返回列表 | `loadTags()` |
| `selectCount(wrapper)` | `SELECT COUNT(*)` | `StatsServiceImpl` |
| `selectPage(page, wrapper)` | 分页（自动加 LIMIT + COUNT） | `page()` |

**对照 Prisma / TypeORM**：

```ts
// Prisma
await prisma.diary.create({ data })
await prisma.diary.update({ where: { id }, data })
await prisma.diary.findMany({ where: { userId } })
```

概念完全对应，只是 Java 这边：
- 方法名不同（`insert` / `updateById` / `selectList`）
- **逻辑删除是框架自动加的**，Prisma 需要你自己写 `deletedAt: null`

---

## 4. 条件构造器（`LambdaQueryWrapper`）—— 本项目最常用的写法

### 4.1 从简单到复杂

```java
// ① 最简单：按用户过滤
Wrappers.<Diary>lambdaQuery().eq(Diary::getUserId, userId)

// ② 多条件
Wrappers.<Diary>lambdaQuery()
        .eq(Diary::getUserId, userId)
        .ge(Diary::getDiaryDate, startDate)     // >=
        .le(Diary::getDiaryDate, endDate)       // <=
        .orderByDesc(Diary::getDiaryDate)

// ③ 分组条件（括号）：title LIKE ? OR summary LIKE ?
wrapper.and(w -> w.like(Diary::getTitle, keyword).or().like(Diary::getSummary, keyword));
```

**`Diary::getUserId` 是方法引用**（模块 01 第 7.3 节）——用来拿字段名，等价于 Prisma 里写 `userId`。

### 4.2 常用条件方法速查

| 方法 | SQL | 说明 |
|---|---|---|
| `.eq(col, v)` | `col = v` | 等于 |
| `.ne(col, v)` | `col <> v` | 不等于 |
| `.gt` / `.ge` / `.lt` / `.le` | `>` / `>=` / `<` / `<=` | 比较 |
| `.like(col, v)` | `col LIKE '%v%'` | 模糊匹配 |
| `.in(col, list)` | `col IN (...)` | 集合包含 |
| `.isNull(col)` | `col IS NULL` | 空判断 |
| `.and(w -> ...)` / `.or(w -> ...)` | `(...)` / `OR (...)` | **组合条件，注意括号** |
| `.orderByDesc(col)` | `ORDER BY col DESC` | 排序 |
| `.select(col...)` | 只查这些列 | **字段裁剪，见 4.4** |
| `.apply("SQL片段", 参数)` | 原样拼入（带参数化） | 见 4.5 |

### 4.3 本项目真实用法（`DiaryServiceImpl.page()`）

```java
LambdaQueryWrapper<Diary> wrapper = Wrappers.<Diary>lambdaQuery()
        // 列表页显式指定字段，绝不读取 LONGTEXT 的 content
        .select(Diary::getId, Diary::getTitle, Diary::getSummary, Diary::getMood, Diary::getWeather,
                Diary::getDiaryDate, Diary::getIsPublic, Diary::getCreatedAt, Diary::getUpdatedAt)
        // 数据隔离：必须带 user_id
        .eq(Diary::getUserId, userId);

if (StringUtils.hasText(query.keyword())) {
    String keyword = query.keyword().trim();
    wrapper.and(w -> w.like(Diary::getTitle, keyword).or().like(Diary::getSummary, keyword));
}
if (query.mood() != null) {
    wrapper.eq(Diary::getMood, query.mood());
}
if (query.startDate() != null) {
    wrapper.ge(Diary::getDiaryDate, query.startDate());
}
if (query.endDate() != null) {
    wrapper.le(Diary::getDiaryDate, query.endDate());
}
if (query.tagId() != null) {
    // 使用 {0} 占位符参数化，避免字符串拼接
    wrapper.apply("EXISTS (SELECT 1 FROM t_diary_tag dt WHERE dt.diary_id = t_diary.id AND dt.tag_id = {0})",
            query.tagId());
}

wrapper.orderByDesc(Diary::getDiaryDate).orderByDesc(Diary::getId);
```

**四个必须看懂的点**：

| 位置 | 为什么这么写 |
|---|---|
| `.select(...)` 列出字段 | **绝不查 `content`（LONGTEXT）**。列表页一次查 10 行正文＝白搬几百 KB |
| `.eq(Diary::getUserId, userId)` | **数据隔离的底线**。没有它就会串号（这是最严重的一类漏洞） |
| `if (x != null) wrapper.eq(...)` | 条件构造器**不是链式可选**的，要用 Java 的 `if` 来控制「要不要加这个条件」 |
| `and(w -> ...or()...)` | 不加括号会变成 `user_id = ? AND title LIKE ? OR summary LIKE ?`，**OR 会把 user_id 条件也架空** |

> 最后一条是**初学者最容易写出的安全漏洞**：少一对括号，用户 A 就能搜到用户 B 的日记。用 Lambda 形式的 `and(w -> ...)` 让框架帮你加括号，比手拼字符串安全得多。

### 4.4 字段裁剪

`.select(...)` 会直接改变生成的 SQL 的 SELECT 子句。**实测证据**（打开 SQL 日志后观察）：

```sql
-- 列表查询实际发出（注意：没有 content）
SELECT id,title,summary,mood,weather,diary_date,is_public,created_at,updated_at
FROM t_diary WHERE deleted=0 AND (user_id = ?) ORDER BY diary_date DESC, id DESC LIMIT ?
```

**这就是为什么前端 `DiaryVO`（列表项）没有 `content`，只有 `DiaryDetailVO` 有**（回顾 `frontend/src/types/diary.ts`）——不是后端漏了字段，是**刻意不查**。

### 4.5 `apply()` 与 SQL 注入

```java
wrapper.apply("EXISTS (SELECT 1 FROM t_diary_tag dt WHERE dt.diary_id = t_diary.id AND dt.tag_id = {0})",
        query.tagId());
```

`{0}` 是**参数占位符**，会被转成 `?` 交给 JDBC 预编译。**如果写成字符串拼接就是注入漏洞**：

```java
// ❌ 绝对不要这样
wrapper.apply("EXISTS (... AND dt.tag_id = " + query.tagId() + ")");
```

**规则**：`apply()` 里可以放 SQL 片段，但**变量一律走 `{n}` 占位符**。

---

## 5. 分页：一次列表请求到底发了几条 SQL（实测）

### 5.1 代码

```java
IPage<Diary> page = diaryMapper.selectPage(
        Page.of(query.pageOrDefault(), query.sizeOrDefault()), wrapper);
```

两处防越界：

| 位置 | 措施 | 文件 |
|---|---|---|
| DTO 层 | `sizeOrDefault()` 里 `Math.min(size, MAX_SIZE)`，`MAX_SIZE = 50` | `dto/DiaryQueryDTO.java` |
| 插件层 | `pagination.setMaxLimit(50L)` | `config/MybatisPlusConfig.java` |

> **为什么要两道**？DTO 是给「从 HTTP 进来的请求」用的；插件是给「代码里任何地方调 `selectPage`」用的。后者是兜底，防止某个内部调用点忘了收敛。

另外 `setOverflow(false)` 的语义是：**页码超出总页数时返回空列表，而不是回到第一页**。这个选择影响前端行为——翻到第 999 页时看到的是空态，而不是莫名其妙跳回第一页。

### 5.2 实测：7 篇日记，4 条 SQL

我造了 7 篇日记（每篇挂 2 个标签），然后用本地开发环境（`application-dev.yml` 里开了 SQL 日志）请求 `GET /api/v1/diaries?page=1&size=10`，统计日志新增的 `Preparing:` 行：

```sql
① SELECT COUNT(*) AS total FROM t_diary WHERE deleted = 0 AND (user_id = ?)
② SELECT id,title,summary,mood,weather,diary_date,is_public,created_at,updated_at
   FROM t_diary WHERE deleted=0 AND (user_id = ?) ORDER BY diary_date DESC, id DESC LIMIT ?
③ SELECT diary_id,tag_id FROM t_diary_tag WHERE (diary_id IN (?,?,?,?,?,?,?))
④ SELECT id,user_id,name,color,created_at FROM t_tag WHERE (id IN (?,?,?))
```

**结论：4 条，与返回条数无关。**

| 序号 | 来源 | 说明 |
|---|---|---|
| ① | **分页插件自动加** | 算 `total`，为了给前端分页器用 |
| ② | 你的 `wrapper` | 真正的数据查询，`LIMIT` 也是插件加的 |
| ③④ | 组装标签 | 见第 6 节 |

### 5.3 分页响应体

```java
// common/result/PageResult.java 的结构
{ "total": 7, "pages": 1, "current": 1, "size": 10, "records": [...] }
```

字段名是 `current`（不是 `pageNum`）、`records`（不是 `list`）——**前端固定用这套名字**，见 `frontend/src/types/api.ts`。

---

## 6. 避免 N+1（🔴 本节是整个模块最有价值的部分）

### 6.1 什么是 N+1

「查 1 次列表，然后为列表里的 N 条记录各查一次关联」= 1 + N 次查询。数据量一大，数据库就被打爆。

**如果本项目没做优化，上面的请求会长这样**：

```sql
SELECT ... FROM t_diary WHERE user_id = ? LIMIT 10            -- 1 次
SELECT ... FROM t_diary_tag WHERE diary_id = 1               -- 第 1 篇
SELECT ... FROM t_tag WHERE id IN (...)
SELECT ... FROM t_diary_tag WHERE diary_id = 2               -- 第 2 篇
SELECT ... FROM t_tag WHERE id IN (...)
...                                                          -- 重复 7 次
```

**7 篇日记 = 2 + 7×2 = 16 条 SQL。** 如果是 50 条（分页上限），就是 102 条。

### 6.2 本项目的解法：三次遍历 + 批量查询

```java
/**
 * 批量装配日记标签，避免 N+1：先查出关联，再一次性取出标签，最后在内存分组。
 */
private Map<Long, List<TagVO>> loadTags(List<Long> diaryIds) {
    if (CollectionUtils.isEmpty(diaryIds)) {
        return Map.of();
    }

    // ① 一次性查出所有关联（IN 查询）
    List<DiaryTag> relations = diaryTagMapper.selectList(
            Wrappers.<DiaryTag>lambdaQuery().in(DiaryTag::getDiaryId, diaryIds));
    if (relations.isEmpty()) {
        return Map.of();
    }

    // ② 一次性查出涉及到的所有标签（IN 查询）
    List<Long> tagIds = relations.stream().map(DiaryTag::getTagId).distinct().toList();
    List<Tag> tags = tagMapper.selectList(Wrappers.<Tag>lambdaQuery().in(Tag::getId, tagIds));
    if (tags.isEmpty()) {
        return Map.of();
    }

    // ③ 在内存里分组：tagId → Tag
    Map<Long, Tag> tagById = new HashMap<>();
    tags.forEach(tag -> tagById.put(tag.getId(), tag));

    Map<Long, List<TagVO>> result = new HashMap<>();
    for (DiaryTag relation : relations) {
        // ...按 diaryId 聚到 result
    }
    return result;
}
```

调用方（`page()`）：

```java
List<Long> diaryIds = page.getRecords().stream().map(Diary::getId).toList();
Map<Long, List<TagVO>> tagMap = loadTags(diaryIds);

List<DiaryVO> records = page.getRecords().stream()
        .map(diary -> DiaryConverter.toListVO(diary, tagMap.get(diary.getId())))   // ← 从 Map 取，不再查库
        .toList();
```

**通用模式（背下来）**：

```text
① 收集主记录的所有 id
② 用 IN 一次性查关联表
③ 从关联里收集出所有从表 id，再用 IN 一次性查从表
④ 在内存里用 HashMap 组装
⑤ 遍历主记录时从 Map 取，不再访问数据库
```

> 注意第 ③ 步的 `.distinct()`：多个日记可能挂同一个标签，去重后 `IN` 的列表更短，也避免「同一个标签查多次」。

### 6.3 怎么自己发现 N+1

**方法一：数 SQL 条数**（最直接）

```powershell
# 1. 确认 application-dev.yml 里开了 SQL 日志
#    mybatis-plus.configuration.log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
# 2. 本地启动后端
cd backend; .\mvnw.cmd spring-boot:run
# 3. 发一个请求，然后在日志里数 Preparing: 的行数
```

**判断标准**：SQL 条数**应该与「表的数量」相关，而不是与「数据条数」相关**。

- 本项目列表接口：**恒定 4 条**（2 分页 + 2 标签），返回 7 条还是 50 条都一样
- 如果变成 16 条、102 条，就是这个 bug

**方法二：把数据量放大**

先造 3 条数据测一次，再造 30 条测一次。SQL 条数**跟着涨**就是 N+1。

---

## 7. 逻辑删除

### 7.1 它替你改写了 SQL

```java
diaryMapper.deleteById(id);        // 你写的
```

实际执行：

```sql
UPDATE t_diary SET deleted = 1 WHERE id = ? AND deleted = 0
```

而所有查询会被**自动追加条件**（实测证据，见 5.2 的 ①②）：

```sql
... FROM t_diary WHERE deleted = 0 AND (user_id = ?)
```

### 7.2 哪里配置的

```yaml
# application.yml
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: deleted      # 全局：名叫 deleted 的字段视为逻辑删除标记
      logic-delete-value: 1
      logic-not-delete-value: 0
```

配合实体上的 `@TableLogic`（两处都配了，冗余但无害）。

> **全局配置 + `@TableLogic` 的区别**：全局配置说的是「如果某个实体有名为 `deleted` 的字段，就当它逻辑删除」；`@TableLogic` 是显式标注。本项目两者都用，所以新加实体时只要字段叫 `deleted` 就自动生效。

### 7.3 一个真实的坑（P1 踩过）

```java
@TableLogic
private Integer deleted = 0;      // ← 那个 = 0 不是装饰，是必需的
```

**为什么必须显式初始化**：MyBatis-Plus 默认 `FieldStrategy.NOT_NULL`——**`null` 字段不会写进 INSERT 语句**。于是插入时依赖数据库的 `DEFAULT 0` 兜底，**但 Java 对象里的 `deleted` 依然是 `null`**，紧接着的断言就挂了。

（记在 `development-log.md` 的 P1 踩坑 #1。）

### 7.4 设计取舍：为什么只有日记是逻辑删除

实测各表的列情况：

```text
+--------------+-------------+-----------+
| TABLE_NAME   | has_deleted | col_count |
+--------------+-------------+-----------+
| t_attachment |           0 |         8 |
| t_diary      |           1 |        12 |   ← 只有它有
| t_diary_tag  |           0 |         2 |
| t_tag        |           0 |         5 |
| t_user       |           0 |         8 |
+--------------+-------------+-----------+
```

| 表 | 删除方式 | 理由 |
|---|---|---|
| `t_diary` | **逻辑删除** | 用户数据宝贵，误删需要可恢复（本项目的取舍是「数据仍在库中，便于日后做回收站」，虽然界面还没做入口） |
| `t_tag` | **物理删除** | 标签是主数据；删除时先解除关联（`TagServiceImpl` 事务内两步），避免留下指向空标签的脏数据 |
| `t_diary_tag` | 物理删除 | 关联表本来就是可重建的中间数据 |

**逻辑删除的代价**（这是取舍的另一面，必须知道）：

1. **每次查询都多一个 `deleted = 0` 条件** → 索引需要把它考虑进去
2. **表会持续增长**，`deleted = 1` 的行永久占空间
3. **手写 SQL 时必须自己加条件**——框架只管到 MyBatis-Plus 的自动 SQL，`@Select` 注解里写的手写 SQL **不会**自动加：

```java
// stats 里的手写 SQL：必须自己写 deleted = 0
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

> **这就是「用命令行查库行数比界面多」的原因**：你在 Navicat / Database Client 里执行 `select * from t_diary`，**没有** `deleted = 0`，自然把已删除的也查出来了。

### 7.5 关联表的删除

```java
@Override
@Transactional
public void delete(Long id) {
    Long userId = SecurityUtil.getCurrentUserId();
    // 先校验归属，再逻辑删除。标签关联同步清除（物理删除），与「删除标签时先解除关联」保持一致：
    // 日记行是逻辑删除（deleted=1，内容仍在库中），但关联若保留就成了指向已删日记的孤儿行。
    // 代价是日后做回收站时只能还原日记内容、标签需用户重选——届时再改为延迟清理关联。
    requireOwned(id, userId);
    diaryMapper.deleteById(id);                                          // ← 逻辑删除
    diaryTagMapper.delete(Wrappers.<DiaryTag>lambdaQuery().eq(DiaryTag::getDiaryId, id));  // ← 物理删除
}
```

**这里曾有一处注释与代码不一致，值得单独讲**：注释原文写的是「关联表记录保留，便于日后做回收站恢复」，而代码里**确实执行了删除关联**。

确认后**以代码为准**，注释与全部相关文档已一并改对。这个案例有两个值得记住的点：

1. **它不是 bug**——两种做法都能跑通。分歧在「设计意图」层面：注释描述的是原计划，代码是实际实现。但**注释留在代码里就会误导后来的人**，包括几个月后的你自己。
2. **修正一处注释，动了 5 个文件**——`development-log.md`、`development-plan.md`、`usage-guide.md` 各复述过一次「保留关联」，其中 P5 那条还把「P3 保留关联」当作前提来对比自己的做法。**只改代码注释，文档之间就会互相矛盾。** 这是「文档一致性」最实际的一课：同一件事被写到多个地方，就注定要维护多份。

**顺带明确这个取舍的代价**（选 B 时确认过的）：关联是物理删除，所以日后做回收站时**只能还原日记内容、标签需要用户重选**。要做完整恢复，需要改成「关联延迟清理」——但那会引入孤儿行，当前不做。

---

## 8. 字段自动填充

```java
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
```

配合实体上的 `@TableField(fill = ...)`，于是：

```java
Diary diary = new Diary();
diary.setTitle("爬山");
diaryMapper.insert(diary);          // createdAt / updatedAt 自动被填上，不用你管
```

> **`strictInsertFill` 的「strict」含义**：只在字段当前为 `null` 时才填。所以如果你手动设置了 `createdAt`（比如数据迁移），框架不会覆盖它——这个语义对迁移场景很重要。

---

## 9. 事务：`@Transactional` 加在哪

### 9.1 本项目的三处

```java
@Override
@Transactional
public DiaryDetailVO create(DiarySaveDTO dto) { ... }

@Override
@Transactional
public DiaryDetailVO update(Long id, DiarySaveDTO dto) { ... }

@Override
@Transactional
public void delete(Long id) { ... }
```

**判断标准很简单：一个方法里有没有「多次写操作，且必须同时成功或同时失败」。**

看 `create()`：

```java
diaryMapper.insert(diary);                    // 写 1：插入日记
replaceTags(diary.getId(), dto.tagIds(), userId);   // 写 2：删除旧关联 + 插入新关联
```

如果「写 1 成功、写 2 失败」，就会留下一篇**没有标签的日记**（数据不一致）。加事务后，写 2 失败会连写 1 一起回滚。

**而 `page()` / `detail()` 是只读的，不需要事务**——这是有意为之，避免给读操作加无谓的开销。

### 9.2 `replaceTags` 里的两步写

```java
private void replaceTags(Long diaryId, List<Long> tagIds, Long userId) {
    diaryTagMapper.delete(Wrappers.<DiaryTag>lambdaQuery().eq(DiaryTag::getDiaryId, diaryId));  // 先全删

    if (CollectionUtils.isEmpty(tagIds)) {
        return;
    }
    // ...校验标签归属，再逐个插入
    distinctTagIds.forEach(tagId -> diaryTagMapper.insert(new DiaryTag(diaryId, tagId)));
}
```

「先全删再全插」是最简单的「全量替换」实现。**为什么敢这么做**：因为整个方法在事务里，删除的中间状态对外不可见。

顺便看它怎么防越权：

```java
Long ownedCount = tagMapper.selectCount(Wrappers.<Tag>lambdaQuery()
        .in(Tag::getId, distinctTagIds)
        .eq(Tag::getUserId, userId));            // ← 只数「属于当前用户」的
if (ownedCount == null || ownedCount != distinctTagIds.size()) {
    throw new BizException(ResultCode.BAD_REQUEST, "存在无效或不属于你的标签");
}
```

**思路**：不逐个校验，而是「数一下我提交的 id 里有多少个真正属于我」。数量对不上就说明夹带了别人的标签。**一次查询解决 N 次校验**——这是很典型的「用聚合代替循环」的技巧。

### 9.3 `@Transactional` 失效的三种典型场景

| 场景 | 为什么不生效 | 本项目是否踩到 |
|---|---|---|
| **同类内部方法互相调用** | 走 `this`，绕过了 Spring 生成的代理对象 | 未踩到（`requireOwned` / `applyDto` 都是 private，不指望有事务） |
| **方法不是 `public`** | Spring AOP 只能代理 public 方法 | 未踩到 |
| **异常被自己 catch 掉了** | 默认只在抛出 `RuntimeException` 时回滚 | 未踩到（`BizException` 继承 `RuntimeException` ✓） |

> 第三条值得展开：**受检异常（`Exception` 的子类但不是 `RuntimeException`）默认不回滚**。所以本项目把 `BizException` 设计成继承 `RuntimeException`，除了「不强制 try/catch」，也顺带保证了事务回滚。

---

## 10. 唯一约束：数据库是最后一道防线

实测的索引情况：

```text
+--------------+---------------+--------------------+------------+
| TABLE_NAME   | INDEX_NAME    | cols               | NON_UNIQUE |
+--------------+---------------+--------------------+------------+
| t_diary      | idx_user_date | user_id,diary_date |          1 |
| t_diary      | idx_user_mood | user_id,mood       |          1 |
| t_tag        | uk_user_name  | user_id,name       |          0 |  ← 唯一索引
| t_user       | uk_username   | username           |          0 |  ← 唯一索引
| t_diary_tag  | PRIMARY       | diary_id,tag_id    |          0 |  ← 联合主键
| t_diary_tag  | idx_tag       | tag_id             |          1 |
| t_attachment | idx_diary     | diary_id           |          1 |
+--------------+---------------+--------------------+------------+
```

### 10.1 两个唯一索引的价值

`uk_user_name(user_id, name)` 保证**同一用户的标签名不重复**。而 `TagServiceImpl` 里也有一段「查重」的逻辑——**这是刻意的双保险**：

| 层 | 作用 |
|---|---|
| Service 层查重 | 给出**友好的错误信息**（「标签名已存在」） |
| 数据库唯一索引 | 防止**并发**下的漏网（两个请求同时查重都通过 → 只有一个能插入成功） |

> **只靠 Service 查重是不够的**：查重与插入之间有窗口期，高并发下会插入两条同名记录。唯一索引是唯一真正可靠的保证。

### 10.2 复合索引的顺序为什么是 `(user_id, diary_date)`

**最左前缀原则**：`(user_id, diary_date)` 这个索引能服务：

- `WHERE user_id = ?` ✅
- `WHERE user_id = ? AND diary_date >= ? AND diary_date <= ?` ✅（本项目的日期筛选）
- `WHERE diary_date = ?` ❌（跳过了最左列）

**本项目所有列表查询都从 `user_id` 开始**（数据隔离的底线），所以把 `user_id` 放最左列是必然选择。

### 10.3 `t_diary_tag` 的联合主键

```sql
PRIMARY KEY (`diary_id`, `tag_id`)
```

**作用**：阻止同一篇日记重复挂同一个标签（天然的幂等保证）。同时这个主键本身也是一个可用索引。

另外单独建了 `idx_tag(tag_id)`——为了支持「从标签反查日记」（`WHERE tag_id = ?`）。因为主键是 `(diary_id, tag_id)`，按 `tag_id` 单独查用不上它。

---

## 11. 常见坑与性能清单

| 坑 | 后果 | 避免方式 |
|---|---|---|
| `and()` 里漏了括号 | **越权**（OR 架空 user_id 条件） | 用 `and(w -> ...)` Lambda 形式 |
| `apply()` 里字符串拼接 | **SQL 注入** | 变量一律走 `{0}` 占位符 |
| 列表查 `content` | 单次响应几百 KB | `.select(...)` 裁剪字段 |
| 循环里查关联 | **N+1**，QPS 上不去 | 批量 `IN` + 内存分组 |
| `size` 不限 | 全表扫描 | DTO 收敛 + `maxLimit` 双保险 |
| 忘了加 `user_id` 条件 | **串号**（最严重的漏洞） | 统一走 `SecurityUtil.getCurrentUserId()` |
| 手写 SQL 忘了 `deleted = 0` | 查出已删除数据 | 手写 SQL 时必须自己加 |
| 事务加在只读方法上 | 无谓的开销 | 只给多步写操作加 |

---

## 12. 动手练习

1. **数 SQL**：按 6.3 的方法，在本地开发环境请求一次列表接口，数出 `Preparing:` 的行数。**预期 4 条**（我已经验证过：7 篇日记时仍是 4 条）
2. **制造 N+1**：把 `page()` 里的 `loadTags` 换成「循环里逐条查标签」，再数一次 SQL 条数。预期变成 `2 + N×2`。**验证完记得改回来**
3. **验证字段裁剪**：在 SQL 日志里找出列表查询的 SELECT 语句，确认**没有 `content`**
4. **验证逻辑删除**：删除一篇日记后，用 Database Client 直接查 `select id, title, deleted from t_diary`，确认 `deleted = 1` 且界面已看不到
5. **体验一次「还原已删除的日记」**：找一篇 `deleted = 1` 的日记，用 SQL 把 `deleted` 改回 `0`，刷新界面确认它回来了；再检查它的标签还在不在（提示：不在了）。这个实验说明两件事：① **逻辑删除的数据确实可以恢复**——这就是当初选逻辑删除而不是物理删除的理由；② 当前方案下**标签关联无法恢复**——这正是第 4 节那个取舍的代价，也是日后真要做回收站时首先要改的地方
6. **加一个查询**：写一个方法「查询当前用户在指定日期区间内、带有任意标签的日记数量」，只用条件构造器，不写 SQL

---

## 附录 · 打开 SQL 日志的方法

**只有 `application-dev.yml` 开了 SQL 打印**（生产环境刻意关闭，因为日志里会有用户的日记正文）：

```yaml
# application-dev.yml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl   # ← 这行
```

**观察一个请求产生的 SQL**：

```powershell
# 本地启动后端
cd backend; .\mvnw.cmd spring-boot:run

# 记下当前日志行数，发请求，再看新增部分
$log = "$env:TEMP\dev-be.log"
$before = (Get-Content $log -Encoding UTF8).Count
# ...执行你的请求...
$newLines = Get-Content $log -Encoding UTF8 | Select-Object -Skip $before
$newLines | Select-String 'Preparing:'
```

日志里的两行是配对的：

```text
==>  Preparing: SELECT ... FROM t_diary WHERE deleted=0 AND (user_id = ?)
==> Parameters: 1(Long)
```

**`==>  Parameters` 值得留意**——它会打印实际的参数值。这就是为什么生产环境必须关掉 SQL 日志：**用户搜索的关键词、日记内容都可能出现在这里**。

---

## 下一篇

`doc/backend/04-security.md`：认证与鉴权——JWT 结构、Spring Security 过滤链、Refresh Token 轮换、密码哈希，以及 5 个常见安全坑（越权、密钥管理、日志泄露、时序攻击、CORS 误配）。
