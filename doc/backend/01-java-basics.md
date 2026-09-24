# 模块 01 · Java 语言基础（写给 JS/TS 开发者）

> 读者：熟悉 TypeScript，没写过 Java，或只扫过一眼
> 前置：无
> 目标：**能看懂本项目任何 `.java` 文件，能手写一个类与一段 Stream 转换，能读懂 Java 编译错误**
> 时长：约 1 天
> 上一份：`doc/fullstack-roadmap.md`（总索引）

---

## 0. 先建立一个正确预期

你不是要「学 Java 开发」，你是要**读懂并小改这个项目的后端**。这两件事的工作量差十倍。

所以本模块的取舍很明确：

| 讲 | 不讲（或只提一句） |
|---|---|
| 类 / 接口 / 泛型 / 集合 / Stream / 异常 | 反射、注解处理器、类加载机制 |
| 本项目用到的全部语法 | 多线程、JUC、JVM 调优 |
| 能读懂编译错误 | 写框架级代码 |

**另一个需要提前接受的事**：Java 比 TS 啰嗦得多。一个数据类要写 getter/setter，一行判断要写成三行。这不是落后，是**刻意的显式**——它的设计哲学是「让意图在编译期就暴露出来」，代价是打字量。

---

## 1. 执行模型：从「解释执行」到「编译 + JVM」

**JS/TS 的链路**

```text
.ts ──tsc/babel──► .js ──► Node / 浏览器解释执行（类型信息全部丢弃）
```

**Java 的链路**

```text
.java ──javac──► .class（字节码）──► JVM 执行（JIT 即时编译成机器码）
```

关键差异：

| 问题 | TypeScript | Java |
|---|---|---|
| 类型不匹配时 | **编译报错**（如果你开了 `tsc --noEmit`） | **编译报错**（不可避免） |
| 运行前必须有产物吗 | 否（`vite dev` 直接跑） | **是**，必须先编译出 `.class` |
| 类型信息在运行时 | 完全不存在 | 部分保留（反射可见类名/字段名，但泛型被擦除，见第 6 节） |

**为什么必须编译**：`mvnw spring-boot:run` 会先编译再启动。所以你改完 Java 代码**必须重新启动进程**（不像前端的 HMR 会即时生效）。这也是为什么本项目改后端要重建镜像。

一个实用的心智模型：**Java 的编译期 ≈ 前端开启严格模式的构建期**，只不过它是强制且无法绕过的。

---

## 2. 包与目录（`package`）

打开 `backend/src/main/java/com/example/diary/entity/Diary.java`，第一行是：

```java
package com.example.diary.entity;
```

**规则：`package` 声明必须与目录路径一一对应。**

```text
src/main/java/                      ← 源码根
└── com/example/diary/              ← 根包
    ├── entity/Diary.java           ← package com.example.diary.entity
    ├── service/impl/DiaryServiceImpl.java
    └── common/result/Result.java
```

| JS/TS | Java |
|---|---|
| 一个文件可以有多个 export | **一个文件只能有一个 `public` 类，且文件名必须与类名完全相同**（`Diary.java` 里只能有 `public class Diary`） |
| `import { X } from './x'` | `import com.example.diary.entity.Diary;`（用**全限定名**，没有相对路径） |
| 目录结构是约定 | 目录结构**强制**绑定包名 |

> `com.example.diary` 这种反写域名是 Java 的世界惯例（避免公司间包名冲突），与 npm 的 `@scope/name` 是同一个动机。

---

## 3. 类：字段、方法、构造器、访问修饰符

用本项目最典型的 Entity（`entity/Diary.java`，已省略 getter/setter）当教材：

```java
@TableName("t_diary")
public class Diary {

    @TableId(type = IdType.AUTO)
    private Long id;                    // ← 字段

    private String title;

    /** Markdown 原文，列表查询禁止读取该字段 */
    private String content;

    @TableLogic
    private Integer deleted = 0;        // ← 字段带初始值

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }              // ← getter
    public void setId(Long id) { this.id = id; }    // ← setter（注意 void + this）

    @Override
    public String toString() {                      // ← 覆写父类方法
        return "Diary{id=" + id + ", title='" + title + "'}";
    }
}
```

### 3.1 与 TS 的对照

| TS | Java |
|---|---|
| `type Diary = { id: number }` | `class Diary { private Long id; }` |
| `interface`（仅类型） | `class`（**类型 + 实现**，能 new） |
| `readonly id: number` | `private final Long id;` |
| 属性直接暴露 | 字段 `private`，对外用 **getter/setter** |
| `?.` 可选链 | 无（要手写 null 判断） |
| 无对应 | `@Override` 注解（建议写，能挡掉拼写错误） |

### 3.2 访问修饰符

| 修饰符 | 可见范围 | 类比 |
|---|---|---|
| `private` | 仅本类 | TS 的 `#field` |
| `public` | 所有 | 默认 |
| `protected` | 本类 + 子类 + 同包 | 无对应 |
| （不写） | 同包 | 无对应 |

**本项目的约定**：

- Entity 的字段一律 `private` + 提供 getter/setter
- Service 的依赖字段一律 `private final`（见第 5 节）
- 工具类用 `private` 构造器阻止实例化（见第 4 节）

### 3.3 为什么不用 Lombok

你在 Vue/TS 世界可能见过类似 `@Data` 的代码生成。**本项目刻意不用 Lombok**，原因见 `doc/development-log.md`：JDK 25 下它会触发 `Unsafe` 弃用告警，且 JDK 26 起会彻底失效。

代价就是你会看到**大量手写的 getter/setter**。把心态调整成「这是项目为了摆脱代码生成器所做的取舍」，就不会觉得它啰嗦了。

### 3.4 基本类型 vs 包装类型（Java 特有，重要）

```java
int    n = 0;        // 基本类型，不能为 null，有默认值 0
Integer n = null;    // 包装类型，可以为 null，默认值也是 null

long   total = 5L;   // 基本类型
Long   id = null;    // 包装类型
```

**为什么本项目 Entity 里全用包装类型**：数据库字段可以为 `NULL`，基本类型装不下 `null`。所以 `private Long id;` 而不是 `private long id;`。

**另一个必须知道的后果**：

```java
Long a = null;
long b = a;          // ❌ 运行期抛 NullPointerException（自动拆箱）
```

### 3.5 动手：读一个类

打开 `backend/src/main/java/com/example/diary/entity/Diary.java`，回答：

1. `@TableName("t_diary")` 这行是干什么的？（提示：类名是 `Diary`，表名是 `t_diary`）
2. 为什么 `id` 是 `Long` 而不是 `long`？
3. `deleted` 为什么写成 `private Integer deleted = 0;` 而不是让它默认？（答案在 `development-log.md` 的 P1 踩坑记录里）

---

## 4. `static`：类成员 vs 实例成员

与 JS 的 `static` 语义基本一致，但 Java 里用得更频繁。

**本项目的典型：工具类**（`common/util/SecurityUtil.java`）

```java
public final class SecurityUtil {

    private SecurityUtil() {        // ← 私有构造器：禁止 new，这个类只用来装静态方法
    }

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
}
```

三个值得注意的点：

1. **`private` 构造器**是用 Java 表达「这是个工具类，不要实例化」的惯用写法
2. **`instanceof LoginUser loginUser`** 是「模式匹配」——既做类型判断又把结果赋给变量，等价于 TS 里的类型收窄但不能更简洁
3. **静态方法可以直接调**：`SecurityUtil.getCurrentUserId()`，任何地方都能用（这也是它成为「数据隔离唯一入口」的原因）

**本项目另一个高频用法：常量**

```java
private static final int SUMMARY_MAX_LENGTH = 120;   // DiaryServiceImpl
```

`static final` = JS 里的模块级 `const`。

---

## 5. 接口与实现 —— 本项目最核心的分层方式

这是**从 JS 转 Java 最需要建立的新习惯**。

### 5.1 JS 里你只用 duck typing

```ts
// JS：只要有 list() 方法就能用，不需要声明「我是 DiaryService」
const diaryService = { list: () => {...} }
```

### 5.2 Java 里必须显式声明契约

本项目每个业务模块都是**两个文件**：

```java
// service/DiaryService.java —— 接口，只有方法签名，没有实现
public interface DiaryService {
    PageResult<DiaryVO> page(DiaryQueryDTO query);
    DiaryDetailVO detail(Long id);
    DiaryDetailVO create(DiarySaveDTO dto);
    // ...
}
```

```java
// service/impl/DiaryServiceImpl.java —— 实现
@Service
public class DiaryServiceImpl implements DiaryService {
    // ...具体实现
}
```

**为什么要多写一个文件**：

| 理由 | 说明 |
|---|---|
| **依赖注入按接口注入** | Controller 依赖 `DiaryService`（接口）而不是实现类，替换实现不影响调用方 |
| **契约与实现分离** | 读接口就知道这个模块提供哪些能力，不用翻 200 行实现 |
| **框架需要** | Spring 的 AOP（事务、缓存）在接口/代理层做增强 |

### 5.3 依赖注入：为什么字段是 `final` 且没有 `@Autowired`

```java
@Service
public class DiaryServiceImpl implements DiaryService {

    private final DiaryMapper diaryMapper;
    private final DiaryTagMapper diaryTagMapper;
    private final TagMapper tagMapper;

    public DiaryServiceImpl(DiaryMapper diaryMapper,
                            DiaryTagMapper diaryTagMapper,
                            TagMapper tagMapper) {
        this.diaryMapper = diaryMapper;
        this.diaryTagMapper = diaryTagMapper;
        this.tagMapper = tagMapper;
    }
}
```

**这段代码在 JS 里的等价物**：

```ts
// NestJS 风格（Nest 也是 DI 框架，概念一致）
@Injectable()
export class DiaryServiceImpl implements DiaryService {
  constructor(
    private readonly diaryMapper: DiaryMapper,
    private readonly diaryTagMapper: DiaryTagMapper,
  ) {}
}
```

**要点**：

- **没有 `@Autowired`**：Spring 对「只有一个构造器」的类会自动注入，不需要注解。写出来反而是冗余
- **字段是 `final`**：构造后不可变，避免运行期被意外替换
- **你不需要 `new` 它们**：Spring 容器负责创建并把依赖塞进来。`DiaryMapper` 的实例是 MyBatis-Plus 在启动时**动态生成代理**的（接口没有实现类，框架帮你造一个）

> 这也是为什么你用 IDE 点 `DiaryMapper` 的「跳转到实现」会跳到框架源码——实现是运行期生成的。

### 5.4 本项目还有哪些「接口 + 实现」

```text
service/DiaryService.java      ←→ service/impl/DiaryServiceImpl.java
service/TagService.java        ←→ service/impl/TagServiceImpl.java
service/AuthService.java       ←→ service/impl/AuthServiceImpl.java
service/UserService.java       ←→ service/impl/UserServiceImpl.java
service/StatsService.java      ←→ service/impl/StatsServiceImpl.java
service/FileService.java       ←→ service/impl/FileServiceImpl.java
```

**看代码时的捷径**：想找一个接口的实现，直接去 `impl/` 目录找同名类。

---

## 6. 泛型

与 TS 泛型形似，但有一处**根本差异**。

### 6.1 相同的地方

```java
// Java
public record Result<T>(int code, String message, T data) { }

Result<PageResult<DiaryVO>> result = Result.ok(page);
```

```ts
// TS
interface Result<T> { code: number; message: string; data: T }
const result: Result<PageResult<DiaryVO>> = ok(page)
```

### 6.2 根本差异：类型擦除

```java
List<Diary> a = new ArrayList<>();
List<Tag>   b = new ArrayList<>();

System.out.println(a.getClass() == b.getClass());   // true！
```

**运行期泛型信息被擦除了**——`List<Diary>` 和 `List<Tag>` 在 JVM 眼里都是 `java.util.ArrayList`。这是为了兼容 Java 5 之前没有泛型的代码所做的妥协（TS 的「类型全部消失」是同一类思路，只是 Java 保留了部分）。

**对你的实际影响**：

| 现象 | 说明 |
|---|---|
| 不能写 `new T()` | 运行期不知道 `T` 是什么 |
| 不能 `instanceof List<Diary>` | 只能 `instanceof List` |
| `List<int>` 非法 | **泛型参数必须是包装类型**，要写 `List<Integer>` —— 因为擦除后需要 `Object`，基本类型不是 `Object` |

### 6.3 本项目里的泛型用法速查

```java
Result<T>                          // 统一响应体
PageResult<DiaryVO>                // 分页结果
List<DiaryVO> records              // 列表
Map<Long, List<TagVO>> tagMap      // 日记 id → 它的标签列表
IPage<Diary>                       // MyBatis-Plus 的分页对象
List<Long> diaryIds
```

---

## 7. 集合与 Stream（🔴 本模块重点）

### 7.1 三种集合与 JS 的对应

| Java | JS | 备注 |
|---|---|---|
| `List<T>`（常用 `ArrayList`） | `Array<T>` | 有序、可重复、按索引访问 |
| `Set<T>`（常用 `HashSet`） | `Set<T>` | 无序、去重 |
| `Map<K,V>`（常用 `HashMap`） | `Map<K,V>` / 普通对象 | 键值对 |

```java
List<String> names = new ArrayList<>();
names.add("a");                 // ← 不是 push
names.get(0);                   // ← 不是 names[0]（Java 的 List 不能用下标语法）

Map<Long, List<TagVO>> tagMap = new HashMap<>();
tagMap.put(1L, tags);           // ← 不是 tagMap[1] = tags
tagMap.get(1L);
```

⚠️ **两个反直觉点**：

1. `List` **不能用 `[]` 下标**，必须用 `get(i)` / `set(i, v)`
2. `Map` 也要用 `get` / `put`，不能用属性语法

### 7.2 Stream ↔ JS 数组方法（核心对照表）

这是**你上手会最快的一节**——Stream 的链式风格与 JS 数组方法几乎一一对应：

| JS 数组方法 | Java Stream | 本项目实例 |
|---|---|---|
| `arr.map(fn)` | `.stream().map(fn).toList()` | `page.getRecords().stream().map(Diary::getId).toList()` |
| `arr.filter(fn)` | `.stream().filter(fn).toList()` | 条件查询里用得少（都在 SQL 层过滤了） |
| `arr.some(fn)` | `.stream().anyMatch(fn)` | — |
| `arr.every(fn)` | `.stream().allMatch(fn)` | — |
| `arr.find(fn)` | `.stream().filter(fn).findFirst()` | `moodOf()` 的 Java 版 |
| `arr.reduce(fn, init)` | `.stream().reduce(init, fn)` | — |
| `arr.map(...).length` | `.stream().count()` | — |
| `arr.sort(fn)` | `.stream().sorted(cmp).toList()` | — |
| `Object.fromEntries(arr.map(...))` | `.collect(Collectors.toMap(k, v))` | 统计测试里用过 |
| `arr.join(',')` | `.collect(Collectors.joining(","))` | — |

**重要区别**：JS 数组方法是**立即执行**的；Java Stream **必须先 `.stream()` 开头、以「终结操作」结尾**（`.toList()` / `.collect()` / `.count()` / `.anyMatch()` 等），只写中间的 `map` 不会执行任何东西。

### 7.3 方法引用 `Diary::getId` ↔ 箭头函数

```java
// 完整写法
page.getRecords().stream().map(diary -> diary.getId()).toList()

// 方法引用（等价，更常用）
page.getRecords().stream().map(Diary::getId).toList()
```

`Diary::getId` 读作「对每个元素调用它的 `getId` 方法」，等价于 JS 的 `d => d.getId()`。

> 注意：**它不带括号**。写成 `Diary::getId()` 是语法错误。

### 7.4 本项目三处真实例子（逐行读）

**例 1：取出所有 id**（`DiaryServiceImpl.page`）

```java
List<Long> diaryIds = page.getRecords().stream().map(Diary::getId).toList();
```

JS 等价：`const diaryIds = page.records.map(d => d.id)`

> 它存在的意义：下一行要用这批 id **一次性**查标签（`loadTags(diaryIds)`），而不是循环里逐条查。这就是「避免 N+1」——**先把 id 收集起来，再批量查**。

**例 2：带上下文的转换**（同文件）

```java
List<DiaryVO> records = page.getRecords().stream()
        .map(diary -> DiaryConverter.toListVO(diary, tagMap.get(diary.getId())))
        .toList();
```

JS 等价：`page.records.map(d => toListVO(d, tagMap.get(d.id)))`

**例 3：有时候循环更好**（`StatsServiceImpl.currentStreak`）

```java
int streak = 0;
for (int i = ascendingDates.size() - 1; i >= 0; i--) {
    if (ascendingDates.get(i).equals(expected)) {
        streak++;
        expected = expected.minusDays(1);
    } else {
        break;
    }
}
```

**为什么这里不用 Stream**：循环需要**中途 `break`**，而且每次迭代都在改外部变量（`expected`）。Stream 适合「每个元素独立处理」，不适合「状态在迭代间传递」。**不要为了用 Stream 而用 Stream**——这是 Java 团队里很常见的争论点，本项目的取舍是「有条件就用 Stream，控制流复杂就老实写循环」。

### 7.5 动手练习

1. 打开 `DiaryServiceImpl`，搜索 `.stream()`，找到全部用法并说出每处等价于哪个 JS 方法
2. 在**你的脑子里**（不必真写 Java）实现：把 `List<DiaryVO>` 转成 `Map<Long, DiaryVO>`，键是 id
   - 提示：`.collect(Collectors.toMap(DiaryVO::id, vo -> vo))`
3. 回答：为什么 `.map()` 之后必须跟 `.toList()` 或 `.collect()`？

---

## 8. `null` 与 `Optional`

Java 只有 `null`，**没有 `undefined`**。

```ts
// TS：两件事都能表达「没有值」
let a: string | undefined = undefined
let b: string | null = null
```

```java
// Java：只有 null
String a = null;
```

**危险之处**：基本类型和包装类型之间会自动转换，null 包装类型转基本类型会**立刻抛 `NullPointerException`**：

```java
Integer mood = null;
int m = mood;              // ❌ NullPointerException
if (mood != null) {        // ✅ 先判断
    int safe = mood;
}
```

### 8.1 本项目怎么处理 null

**A. 显式判断 + 兜底**

```java
// DiaryServiceImpl.page 的条件拼装
if (StringUtils.hasText(query.keyword())) {      // ← 比 keyword != null 更严谨：空串也算「没传」
    String keyword = query.keyword().trim();
    wrapper.and(w -> w.like(Diary::getTitle, keyword).or().like(Diary::getSummary, keyword));
}
if (query.mood() != null) {
    wrapper.eq(Diary::getMood, query.mood());
}
```

**B. 返回时兜底**

```java
// StatsServiceImpl.overview 片段（简化）
return new StatsOverviewVO(
        totalCount == null ? 0 : totalCount,
        monthCount == null ? 0 : monthCount,
        // ...
        dates.isEmpty() ? null : dates.get(0),     // ← VO 里允许 null（Jackson 配置了 non_null，不会序列化）
        dates.isEmpty() ? null : dates.get(dates.size() - 1),
        moodDistribution);
```

注意 `dates.get(0)` 之前必须判空——**空列表直接 `get(0)` 会抛 `IndexOutOfBoundsException`**（Java 里常见程度仅次于 NPE）。

**C. `Optional` 用得很少**

`Optional<T>` 是官方提供的「可能没有值」容器，但本项目基本没用它——因为它常被误用成「返回 null 的替代品」，反而增加嵌套。**这里的约定是：DTO/VO 字段允许为 null，业务代码显式判空。**

---

## 9. 异常处理（🔴 与 JS 最大的差异之一）

### 9.1 概念对照

| JS | Java |
|---|---|
| `throw new Error('x')` | `throw new BizException(ResultCode.NOT_FOUND)` |
| `try { } catch (e) { }` | `try { } catch (BizException e) { }` |
| 所有异常一视同仁 | **分「受检异常」与「非受检异常」** |

**受检异常（Checked Exception）在 JS 里没有对应物**：

```java
public String readFile() throws IOException {   // ← 必须在签名里声明
    // ...
}
```

调用方**必须**处理（try/catch）或继续向上声明 `throws IOException`，否则**编译不过**。这是 Java 强制你面对「可能失败」的方式。典型例子：文件 IO、网络请求、`Class.forName`。

本项目**几乎不用受检异常**，而是统一用非受检异常（`RuntimeException` 家族）——这是现代 Spring 项目的主流做法。

### 9.2 本项目的自定义异常

```java
// common/exception/BizException.java
public class BizException extends RuntimeException {      // ← 继承非受检异常

    private final int code;

    public BizException(int code, String message) {
        super(message);                                    // ← 传给父类保存消息
        this.code = code;
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public int getCode() { return code; }
}
```

**用法**（`StatsServiceImpl.calendar`）：

```java
if (year < MIN_YEAR || year > MAX_YEAR) {
    throw new BizException(ResultCode.BAD_REQUEST, "年份超出可统计范围");
}
```

**为什么用异常而不是返回错误对象**：抛出后**中断当前方法**，不用层层向上传值，业务代码保持线性可读。代价是「异常控制流」不够明显——所以要靠命名（`BizException`）和统一的处理入口来约束。

### 9.3 全局捕获（`common/exception/GlobalExceptionHandler.java`）

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(toHttpStatus(e.getCode()))
                .body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)                // ← 兜底
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("系统异常", e);                     // ← 打完整堆栈
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.INTERNAL_ERROR));
    }
}
```

**这相当于 Express 的全局错误中间件**：

```js
app.use((err, req, res, next) => {
  res.status(500).json({ code: 500, message: '服务器开小差了' })
})
```

**一个真实教训**（记在 `development-log.md`）：这里最初只写了兜底的 `Exception.class`，导致 Spring 对未匹配路径抛的 `NoResourceFoundException` 也被当成 500 处理——**任何拼错的 URL 或扫描器探测都会写一条 ERROR 堆栈**。后来显式声明了 `NoResourceFoundException` → 404 才修好。

> **教训**：`catch (Exception)` 这种兜底是必要的，但**必须为「已知的具体异常」留出更精确的分支**，否则会把「客户端错误」误判成「服务端故障」。

### 9.4 动手练习

1. 打开 `GlobalExceptionHandler`，数出它一共处理了几类异常，各自对应什么 HTTP 状态码
2. 写一个会触发 `BizException` 的请求：`GET /api/v1/stats/calendar?year=1900`，观察返回
3. 观察返回的 HTTP 状态码是不是 400（而不是 200 带错误码）——这就是注释里说的「HTTP 状态码保持语义正确」

---

## 10. `record` / `enum` / 注解

### 10.1 `record`：一行声明不可变数据

**本项目所有 DTO 和 VO 都是 record**：

```java
// common/result/Result.java
public record Result<T>(
        int code,
        String message,
        T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
```

等价于 TS：

```ts
class Result<T> {
  constructor(readonly code: number, readonly message: string, readonly data: T) {}
  static ok<T>(data: T) { return new Result(0, 'ok', data) }
}
```

**用法**：

```java
Result<DiaryVO> r = Result.ok(vo);
r.code();          // ← 注意：访问器是 code()，不是 getCode()
r.data();
```

⚠️ **这是本项目踩过的最经典的坑**（记在 P3 记录里）：

```java
vo.getId()     // ❌ 编译错误：record 没有 getId()
vo.id()        // ✅ record 的访问器就是字段名本身
```

**什么时候用 record、什么时候用 class**：

| 用 record | 用 class |
|---|---|
| DTO（入参）、VO（出参）、配置项、简单值对象 | **Entity**（数据库映射） |
| 不可变数据 | 需要无参构造 + setter（MyBatis-Plus 要） |
| 例子：`Result` `DiaryVO` `DayCountVO` `MinioProperties` `LoginUser` | 例子：`Diary` `User` `Tag`（全在 `entity/`） |

> 记住这条分界线：**`entity/` 下是 class，`dto/` 和 `vo/` 下是 record。**

### 10.2 `enum`：比 TS 的 union 强

```java
// common/result/ResultCode.java
public enum ResultCode {

    SUCCESS(0, "ok"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未认证或登录已失效"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器开小差了");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {      // ← 枚举可以有构造器（隐式 private）
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
```

TS 里的等价物通常是：

```ts
const ResultCode = {
  SUCCESS: { code: 0, message: 'ok' },
  NOT_FOUND: { code: 404, message: '资源不存在' },
} as const
```

Java 的 enum **能带字段、构造器、方法**，所以 `ResultCode.NOT_FOUND.getCode()` 能直接拿到 404。

### 10.3 注解：给框架看的元数据

注解**本身不做事**，是框架读它来决定行为：

| 注解 | 谁在读 | 作用 |
|---|---|---|
| `@Override` | 编译器 | 校验这确实在覆写父类方法 |
| `@Service` / `@Component` / `@Mapper` | Spring | 把这个类交给容器管理 |
| `@TableName("t_diary")` | MyBatis-Plus | 类对应哪张表 |
| `@TableLogic` | MyBatis-Plus | 这个字段是逻辑删除标记 |
| `@GetMapping` | Spring MVC | 这个方法是 GET 接口 |
| `@Schema(description=...)` | SpringDoc | 生成接口文档 |

**和你熟悉的类比**：TS 的装饰器（`@Injectable()` / `@Controller()`）——NestJS 用得很像。

---

## 11. `equals` / `hashCode` / 不可变性

### 11.1 `==` 比的是引用

```java
String a = new String("abc");
String b = new String("abc");

a == b            // false！比的是「是不是同一个对象」
a.equals(b)       // true，比的是「内容是否相同」
```

> **这与 JS 的 `===` 行为一致**（对象比引用），但 Java 里更容易踩——因为 Java 有「值类型」（基本类型）和「对象类型」的区分，而 JS 的数字/字符串是原始值。

**实践规则**：

```java
// 基本类型用 ==
if (mood == 1) { }
if (count > 0) { }

// 对象类型用 equals
if (title.equals(other)) { }
if (ResultCode.NOT_FOUND.equals(x)) { }     // 常量在前，避免 null 时 NPE
```

⚠️ **千万别用 `==` 比较 `Integer`**：Java 缓存了 `-128~127` 的 `Integer`，导致 `Integer.valueOf(100) == Integer.valueOf(100)` 是 `true`，但 `200 == 200` 是 `false`。这是著名的陷阱。

### 11.2 `record` 自动生成 `equals` / `hashCode`

```java
DayCountVO a = new DayCountVO(LocalDate.of(2026, 9, 23), 2);
DayCountVO b = new DayCountVO(LocalDate.of(2026, 9, 23), 2);

a.equals(b)     // true —— record 按字段逐一比较
```

而普通 class（如 Entity）**不会自动生成**，所以 `Diary` 之间用 `equals` 是比引用。

---

## 12. Maven：`package.json` 的对应物

### 12.1 目录与命令对照

| npm / Node | Maven / Java |
|---|---|
| `package.json` | `pom.xml` |
| `package-lock.json` | 无（依赖版本写在 `pom.xml` 里） |
| `node_modules/` | `~/.m2/repository/`（**在用户目录，全局共享**） |
| `npm` / `npx` | `mvn` |
| `npm install` | `mvn dependency:resolve` |
| `npm ci` | `mvn dependency:go-offline` |
| `npm run build` | `mvn package` |
| `npm test` | `mvn test` |
| `dist/` | `target/` |
| `.npmrc` | `settings.xml` |

### 12.2 为什么用 `mvnw` 而不是 `mvn`

```powershell
cd backend
.\mvnw.cmd -v              # 用仓库自带的 Wrapper
```

`mvnw` 是 **Maven Wrapper**——它按 `.mvn/wrapper/maven-wrapper.properties` 里指定的版本自动下载 Maven，**本机不需要安装 Maven**。

这与前端的 `npx` / `corepack` 是同一个思路：**把工具链版本写进仓库，保证团队一致**。

### 12.3 `pom.xml` 的关键结构

```xml
<project>
    <properties>
        <!-- 依赖版本集中在这里，避免散落各处 -->
        <mybatis-plus.version>3.5.17</mybatis-plus.version>
        <jjwt.version>0.12.7</jjwt.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>   <!-- ← 引用上面的属性 -->
        </dependency>
    </dependencies>
</project>
```

**`groupId:artifactId:version` 三元组** = npm 里的「包名 + 版本」。`groupId` 类似 npm scope。

### 12.4 你可能会用到的命令

```powershell
cd backend

.\mvnw.cmd -B test                 # 跑测试
.\mvnw.cmd -B -DskipTests package  # 打包（跳过测试）
.\mvnw.cmd -B clean compile        # 清理 + 编译（最快的语法校验）
.\mvnw.cmd spring-boot:run         # 启动
.\mvnw.cmd dependency:tree         # 查看依赖树（类似 npm ls）
```

> PowerShell 里 `-D` 参数后如果带 `:` 需要加引号，例如 `"-Dlombok.version=1.18.48"`——这是本项目踩过的坑。

---

## 13. 动手练习（汇总）

按难度递增，**每道都要求验证**：

1. **读**：打开 `entity/Diary.java`，逐行说明每个注解的作用
2. **读**：打开 `common/util/SecurityUtil.java`，回答「为什么构造器是 private」
3. **找**：在 `service/impl/` 下找出所有「接口 + 实现」的组合，写出对应表
4. **算**：打开 `DiaryServiceImpl` 的 `page()` 方法，把它翻译成 TypeScript（不用编译，写在草稿里即可）
5. **改**：给 `Diary` 实体加一个 `private String location;`（地点）字段 + getter/setter，然后跑 `.\mvnw.cmd -B clean compile` 验证编译通过。**注意：先别建数据库字段**，编译是不需要数据库的
6. **错**：故意把 `location` 的 getter 写成 `getLocation()` 却在别处调用 `location()`，观察编译器报什么错，到附录 B 里找对应解读
7. **收尾**：把第 5 步加的字段删掉，重新编译确认干净

---

## 附录 A · JS/TS ↔ Java 速查表

| 概念 | TS / JS | Java |
|---|---|---|
| 变量 | `let` / `const` | `var`（少用）/ `final` |
| 类型注解 | `name: string` | `String name` |
| 可空 | `string \| null` | `String`（引用类型天然可 null） |
| 类 | `class X { }` | `public class X { }` |
| 接口 | `interface X { }` | `public interface X { }`（有实现类） |
| 实现 | `class A implements X` | `class A implements X` |
| 继承 | `class A extends B` | `class A extends B` |
| 构造器 | `constructor(a) { this.a = a }` | `X(A a) { this.a = a; }` |
| 属性访问 | `obj.a` | `obj.getA()` |
| 空安全调用 | `obj?.a` | 无（手写判空） |
| 数组 | `Array<T>` | `List<T>` |
| 映射 | `Map<K,V>` / `{}` | `Map<K,V>` / `HashMap` |
| 函数 | `(x) => y` | `x -> y` / `X::method` |
| 字符串插值 | `` `a${b}` `` | `"a" + b` / `"a%sb".formatted(b)` |
| 抛出 | `throw new Error()` | `throw new RuntimeException()` |
| 捕获 | `catch (e) { }` | `catch (XxxException e) { }` |
| 判等 | `a === b` | `a == b`（基本类型）/ `a.equals(b)`（对象） |
| 常量 | `const X = 1` | `private static final int X = 1;` |
| 数据类 | `type X = { a: number }` | `record X(int a) { }`（**不可变**） |
| 枚举 | union / `as const` 对象 | `enum`（可带字段与方法） |
| 依赖清单 | `package.json` | `pom.xml` |
| 全局依赖目录 | `node_modules/` | `~/.m2/repository/` |
| 构建产物 | `dist/` | `target/` |

---

## 附录 B · 常见编译错误解读（配本项目真实报错）

Java 的报错比 JS 长，但**信息量大**。学会读它，效率能提升一倍。

### B1. `cannot find symbol` / `找不到符号`

```text
error: cannot find symbol
  symbol:   method getId()
  location: variable vo of type DiaryVO
```

**含义**：调了一个不存在的方法。

**本项目真实案例（P3）**：`DiaryVO` 是 record，访问器是 `id()`，写 `getId()` 就会报这个。

**排查顺序**：① 是 record 还是 class？（record 用字段名，class 用 getXxx）② 拼写？③ 有没有 `import`？

### B2. `不兼容的类型: void 无法转换为 java.lang.Long`

```text
error: 不兼容的类型: void无法转换为java.lang.Long
Long deletedId = create(today, 1);
```

**含义**：把一个 `void`（无返回值）的方法结果赋值给了变量。

**本项目真实案例（P7）**：测试里的辅助方法 `private void create(...)` 被当成有返回值使用。

**修法**：让方法返回需要的值（改成 `private Long create(...)` 并 `return ...`），或不要接收返回值。

### B3. `变量 x 可能尚未初始化`

```text
error: variable order might not have been initialized
```

**含义**：Java 要求**局部变量使用前必须明确赋值**（不像 JS 会给你 `undefined`）。

```java
int order;
if (cond) { order = 1; }
System.out.println(order);   // ❌ 如果 cond 为 false，order 没值

int order = 0;               // ✅ 给个初值
```

### B4. `无法将类 X 中的方法 Y 应用到给定类型`

```text
error: no suitable method found for page(int)
    method DiaryService.page(DiaryQueryDTO) is not applicable
      (argument mismatch; int cannot be converted to DiaryQueryDTO)
```

**含义**：参数类型不对。Java **不做隐式类型转换**（除了基本类型的自动提升）。

**修法**：看 `method XXX(...)` 那行给出的期望签名，对照你传的参数。

### B5. `缺少 return 语句`

```java
public Long getId() {
    id;              // ❌ 表达式不是语句，且没有 return
}
public Long getId() {
    return id;       // ✅
}
```

### B6. `非法的表达式开始`

```text
error: illegal start of expression
```

**含义**：通常是语法符号问题——括号/分号不匹配、方法定义写在了方法外面、或者用了 Java 不支持的写法。

**排查**：看**报错位置的上一行**，多数问题出在那里（少个分号是头号嫌疑）。

### B7. `类 X 是公共的, 应在名为 X.java 的文件中声明`

```text
error: class Diary is public, should be declared in a file named Diary.java
```

**含义**：Java 强制「一个文件只能有一个 public 类，且文件名必须与类名一致」。**这是从 JS 过来最容易犯的错**。

### B8. NullPointerException（运行期，不是编译期）

```text
java.lang.NullPointerException: Cannot invoke "String.length()" because "title" is null
```

**含义**：对 `null` 调了方法。Java 的 NPE 信息在 **JDK 14+ 已经会告诉你具体是哪个变量**（上面这句就是），比老版本友好得多。

**排查**：看报错里 `because "xxx" is null` 的那部分，直接锁定变量。

---

## 下一篇

`doc/backend/02-spring-boot.md`：Spring Boot 框架——IoC 容器、注解全景、一次请求的完整链路、如何自己加一个接口。
