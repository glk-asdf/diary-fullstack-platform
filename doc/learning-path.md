# 从 Vue 到本项目 · 前端开发者学习路径

> 读者：熟悉 Vue 3（`<script setup>` + Pinia + Vue Router + Element Plus / antd-vue），第一次接触本项目使用的 React 生态与 Java 后端
> 目标：**能独立改动本项目的前端功能，并能自己定位与后端交互时的问题**
> 更新日期：2026-09-24

---

## 0. 这份文档怎么用

### 0.1 三步法

每个知识点都按同一节奏，**不要只读不跑**：

| 步骤 | 做什么 | 为什么 |
|---|---|---|
| **读** | 打开文中指出的具体文件，对照「Vue 写法 ↔ 这里写法」 | 你缺的不是编程能力，是**映射关系** |
| **跑** | 执行文中的命令，核对「应该看到什么」 | 建立「我的操作 → 系统反应」的因果感 |
| **改** | 按练习题改一处，验证后再改回来 | 只读代码会产生「好像懂了」的错觉 |

### 0.2 模块优先级

你是前端，所以分配不均：

| 模块 | 重要度 | 说明 |
|---|---|---|
| 模块 2 React 核心 | 🔴 必须掌握 | 占你 60% 的学习时间 |
| 模块 1 TypeScript | 🔴 必须掌握 | 本项目没有「模板层豁免」，TS 全量生效 |
| 模块 3 Zustand | 🔴 必须掌握 | 与 Pinia 差异小，但有 2 个反直觉点 |
| 模块 4 TanStack Query | 🔴 必须掌握 | 前端最反直觉的一块，也是本项目数据层的核心 |
| 模块 5 路由 | 🟡 会用即可 | Vue Router 经验能迁移 80% |
| 模块 6 UI / 主题 | 🟡 会用即可 | 组件库换了个名字而已 |
| 模块 7 请求层 | 🟡 会用即可 | 但 401 静默刷新那段值得精读 |
| 模块 8–10 后端 | 🟢 知道即可 | 目标是**能读能改**，不是能重写 |
| 模块 11 构建部署 | 🟢 知道即可 | 出问题时知道去哪看 |

### 0.3 建议节奏

```text
第 1 天：模块 0（跑起来） + 模块 1、2        ← 打地基
第 2 天：模块 3、4、5、7                      ← 前端骨架
第 3 天：模块 6、8、9                        ← 补齐 UI 与后端接口认知
第 4 天：模块 10、11 + 附录 B 练习            ← 收尾
```

进度自检：

```text
[ ] 模块 0  跑起来并登录成功
[ ] 模块 1  TypeScript：能看懂泛型与联合类型
[ ] 模块 2  React 核心：能独立写一个受控组件
[ ] 模块 3  Zustand：能加一个 store
[ ] 模块 4  TanStack Query：能自己加一个查询
[ ] 模块 5  路由：能加一个页面
[ ] 模块 6  UI/主题：能改主题变量
[ ] 模块 7  请求层：理解 401 静默刷新
[ ] 模块 8  后端：能读懂一个接口的实现
[ ] 模块 9  认证：能用 curl 走通登录到刷新
[ ] 模块 10 数据库：能查表并理解逻辑删除
[ ] 模块 11 部署：能重建镜像并看日志
```

---

## 模块 0 · 先看到东西在动（15 分钟）

**别急着读代码。先让系统跑起来、点几下，建立实感。**

### 步骤 0.1 启动

**路线 A：容器部署**（推荐，一条命令，不需要本机装 JDK / Node / 数据库）

```powershell
cd <仓库根目录>
Copy-Item .env.example .env      # 首次需要，按注释改掉必填项
docker compose up -d --build     # 首次约 5~10 分钟
```

**路线 B：本地开发**（你要改前端代码就用这条，有热更新）

```powershell
# 1. 中间件（重启电脑后必须重新执行）
powershell -ExecutionPolicy Bypass -File D:\business\tools\mysql-start.ps1
powershell -ExecutionPolicy Bypass -File D:\business\tools\redis-start.ps1
powershell -ExecutionPolicy Bypass -File D:\business\tools\minio-start.ps1

# 2. 后端
cd backend; .\mvnw.cmd spring-boot:run      # → 8080

# 3. 前端（在另一个终端）
cd frontend; npm install; npm run dev       # → http://localhost:5173
```

### 步骤 0.2 登录并点一遍

- 容器部署访问 `http://localhost`；本地开发访问 `http://localhost:5173`
- 账号 `tester` / 密码 `123456`
- 依次点：**日记列表 → 写日记（随便填）→ 保存 → 标签 → 统计 → 个人中心 → 切暗色主题**

### 步骤 0.3 打开 DevTools 观察

这是建立「前端 ↔ 后端」直觉最快的方式。F12 → Network：

| 观察 | 你会看到 |
|---|---|
| 登录请求 | `POST /api/v1/auth/login`，响应 `{code, message, data:{accessToken, refreshToken, user}}` |
| 任何业务请求的 Header | `Authorization: Bearer eyJ...`（JWT） |
| 列表请求的 Query | `?page=1&size=9&keyword=...` |
| 上传图片 | `POST /api/v1/files/upload`，`multipart/form-data`，字段名 `file` |

**关键认知**：本项目**所有接口都返回同一个信封** `{code, message, data}`，且 **HTTP 状态码同时是语义正确的**（401 真的是 401，404 真的是 404）。前端代码里 `code !== 0` 就报错、`=== 0` 就直接用 `data`。

---

## 模块 1 · TypeScript（🔴 必须掌握）

### 1.1 Vue 里的 TS 只覆盖了一半

你在 Vue 里写 `<script setup lang="ts">` 时，**模板部分几乎不做类型检查**：

```vue
<!-- Vue：这样写，TS 不会报错，运行时才发现 -->
<template>
  <div>{{ user.nickname.toUpperCase() }}</div>   <!-- user 可能是 null -->
</template>
```

React 的 JSX **就是 JS 表达式**，全部参与类型检查，同样的错误会在编译期直接拦住。所以你会感觉「这里的 TS 更严格」——不是变严格了，是以前没检查到。

### 1.2 本项目用到的 TS 特性（够用清单）

| 特性 | 本项目实例 | 位置 |
|---|---|---|
| 泛型 | `http.get<T>(url): Promise<T>` | `src/api/request.ts` |
| 接口 + 可选属性 | `interface DiaryVO { summary?: string }` | `src/types/diary.ts` |
| 联合类型 | `type ThemeMode = 'light' \| 'dark'` | `src/store/useThemeStore.ts` |
| `as const` | `all: ['diaries'] as const` | `src/hooks/useDiaryList.ts` |
| 类型导入 | `import type { UserVO } from '@/api/auth'` | 随处可见 |
| 非空断言 `!` | `document.getElementById('root')!` | `src/main.tsx` |
| 可选链 + 空值合并 | `searchParams.get('page') ?? 1` | `src/pages/DiaryList/index.tsx` |

>`import type` 是必须养成的习惯：它只导入类型、编译后消失，**不会**在运行时产生 import（避免无意义的模块依赖与循环引用）。

### 1.3 动手练习

1. 打开 `frontend/src/types/diary.ts`，给 `DiaryVO` 加一个必填字段 `weatherDetail: string`
2. 运行 `npm run build`，观察 TS 在**哪些文件**报错——**这些位置就是「这个类型被用在哪」的完整答案**，比全局搜索还准
3. 把字段改回可选（`weatherDetail?: string`），再看报错是否消失
4. 最后删掉这个字段

> **这个练习的真正价值**：你以后想知道「改这个接口字段会影响哪些界面」，直接改类型然后构建，比读代码快十倍。

---

## 模块 2 · React 核心（🔴 占一半学习时间）

### 2.1 JSX ↔ template

| Vue | React |
|---|---|
| `<template>` 独立块 | 直接写在函数 `return` 里 |
| `v-if="cond"` | `{cond && <div/>}` |
| `v-else` | 三元 `{cond ? <A/> : <B/>}` |
| `v-for="item in list"` | `{list.map(item => <X key={item.id}/>)}` |
| `:class="{active: isActive}"` | `className={isActive ? 'active' : ''}` |
| `@click="fn"` | `onClick={fn}`（**注意是函数本身，不是调用**） |
| `{{ value }}` | `{value}` |
| `<img :src="url">` | `<img src={url}>` |
| 属性名 kebab-case | **camelCase**（`stroke-width` → `strokeWidth`） |

最容易犯的错：

```tsx
<button onClick={handleClick()}>   // ❌ 渲染时就执行了
<button onClick={handleClick}>     // ✅ 传函数引用

{count && <div>...</div>}          // ❌ count 为 0 时会渲染出 "0"
{count > 0 && <div>...</div>}      // ✅
```

### 2.2 组件即函数，props 即参数

**Vue**

```vue
<script setup lang="ts">
const props = defineProps<{ value?: number; onChange?: (v?: number) => void }>()
</script>
```

**React（本项目真实代码，`src/components/MoodPicker/index.tsx` 简化版）**

```tsx
interface Props {
  value?: number
  onChange?: (value?: number) => void
}

export default function MoodPicker({ value, onChange }: Props) {
  return <div>{/* ... */}</div>
}
```

差异要点：

- **没有 `defineProps`**，props 就是函数的第一个参数，用**解构**取用
- 没有 `.value`，**变量直接就是值**（这是从 Vue 转过来最需要重新训练肌肉记忆的一点）
- 默认导出即可（`export default function`），不需要额外注册

### 2.3 状态：`useState` ↔ `ref` / `reactive`

| Vue | React |
|---|---|
| `const count = ref(0); count.value++` | `const [count, setCount] = useState(0); setCount(count + 1)` |
| `const state = reactive({ a: 1 })` | 拆成多个 `useState`，或用 `useReducer` |
| 改对象属性直接改：`obj.a = 1` | **必须返回新对象**：`setObj({ ...obj, a: 1 })` |

**不可变更新是硬性要求**——React 靠**引用是否变化**来判断要不要重渲染，直接改属性它不会知道：

```tsx
// ❌ 界面不会更新
obj.a = 1

// ✅
setObj({ ...obj, a: 1 })
// 数组同理
setList([...list, newItem])
setList(list.filter(x => x.id !== id))
```

另外两个 Vue 里没有的概念：

```tsx
// 1. 函数式更新：新值依赖旧值时用它，避免闭包拿到过期值
setCount(prev => prev + 1)

// 2. 状态更新是「批处理 + 异步」的，set 之后立刻读 state 读到的还是旧值
setCount(count + 1)
console.log(count)      // 还是旧值
```

### 2.4 副作用：`useEffect` ↔ `watch` / `onMounted`

本项目的真实例子（`src/main.tsx`）：

```tsx
const mode = useThemeStore((state) => state.mode)

// 把主题同步到 <html data-theme="...">，供自定义 CSS 适配暗色
useEffect(() => {
  document.documentElement.dataset.theme = mode
}, [mode])            // ← 依赖数组：mode 变化时才执行
```

对照：

| Vue | React |
|---|---|
| `onMounted(() => {...})` | `useEffect(() => {...}, [])` |
| `watch(x, () => {...})` | `useEffect(() => {...}, [x])` |
| `onUnmounted(() => {...})` | `useEffect(() => { return () => {...} }, [])` |
| `watchEffect` | 无直接对应（依赖数组必须显式写） |

⚠️ **依赖数组是 React 里最容易出 bug 的地方**：

```tsx
useEffect(() => {
  fetchData(id)
}, [])           // ❌ 空数组 = 只跑一次；id 变了不会重新请求

useEffect(() => {
  fetchData(id)
}, [id])         // ✅ 正确
```

⚠️ **`StrictMode` 会让 `useEffect` 在开发环境执行两次**（`src/main.tsx` 里有 `<StrictMode>`）。这是 React 故意为之，用来暴露「没写清理函数」的 bug。**Vue 里没有这个行为**，很多人第一次看到会以为请求发重了——实际上生产环境只执行一次。

### 2.5 受控组件 ↔ `v-model`

Vue 的 `v-model` 是语法糖，React 里要**手动写两件事**：

```tsx
// Vue
// <MyInput v-model="name" />

// React：父组件
const [name, setName] = useState('')
<MyInput value={name} onChange={setName} />

// React：子组件（MoodPicker 就是这么写的）
interface Props {
  value?: number
  onChange?: (value?: number) => void
}
export default function MoodPicker({ value, onChange }: Props) {
  const active = value === option.value
  return (
    <div onClick={() => onChange?.(active ? undefined : option.value)}>
      {/* 再次点击已选中项可取消选择 */}
    </div>
  )
}
```

> `onChange?.(x)` 里的 `?.` 是因为 props 可能没传——这也是为什么 `MoodPicker` 能直接塞进 antd 的 `<Form.Item>`：**antd 的 Form 会给子组件自动注入 `value` 和 `onChange`**，这套约定叫「受控组件」，与 Element Plus 的 `v-model` 是一回事。

### 2.6 组合：`children` ↔ `slot`；Context ↔ `provide/inject`

| Vue | React |
|---|---|
| `<slot />` | `props.children` |
| 具名插槽 | 传普通 props（`header={<X/>}`） |
| `provide` / `inject` | `createContext` + `useContext` |

本项目**大量使用 children**，例如布局与路由：

```tsx
// src/router/index.tsx 片段
<AuthGuard>
  <MainLayout />       {/* MainLayout 内部通过 <Outlet /> 渲染子路由页面 */}
</AuthGuard>
```

`<Outlet />` 就是 React Router 的 `<router-view />`。

### 2.7 性能：`useMemo` / `useCallback`

| Vue | React |
|---|---|
| `computed` 自动缓存 | 手动 `useMemo` |
| 无对应 | 手动 `useCallback`（缓存函数引用） |

Vue 的响应式系统能自动追踪依赖，React 不能，所以需要手动声明。

**但不要滥用**：本项目只在真正有计算开销或必须稳定引用时使用，例如 `src/pages/Stats/index.tsx` 里那两处（生成整年热力图格子、把分布数据转成图表数据）——它们每次渲染都会重建数组，值得缓存。

### 2.8 动手练习

1. 打开 `frontend/src/components/MoodPicker/index.tsx`，**给选中的心情加一个 ✓ 角标**（提示：`position: 'absolute'` + 一个条件渲染的 `<span>`）
2. 打开 `frontend/src/pages/DiaryList/index.tsx`，把「共 N 篇」改成「共 N 条记录」
3. 在 `DiaryList` 里加一个 `useState` 计数按钮，点一次 +1，验证你理解了 `useState` 与不可变更新
4. 全部改回

---

## 模块 3 · 状态管理：Zustand ↔ Pinia（🔴 必须掌握）

### 3.1 对照

| Pinia | Zustand |
|---|---|
| `defineStore('user', () => {...})` | `create<UserState>()(...)` |
| `state` / `getters` / `actions` | state 与 action 写在同一个对象里（没有 getters，需要派生值就用 `useMemo` 或选择器） |
| `useUserStore()` 拿到整个 store | `useUserStore(state => state.user)` **按需订阅** |
| 外部使用需 `useStore()` 且要在组件内 | **`useUserStore.getState()` 可在任何地方调用** |
| `pinia-plugin-persistedstate` | `persist` 中间件（内置） |

### 3.2 逐行读 `src/store/useUserStore.ts`

```tsx
interface UserState {
  accessToken: string | null
  refreshToken: string | null
  user: UserVO | null
  setAuth: (payload: AuthPayload) => void
  setTokens: (payload: TokenPayload) => void
  setUser: (user: UserVO) => void
  clear: () => void
}

export const useUserStore = create<UserState>()(
  persist(
    (set) => ({                                    // set 是唯一的写入口
      accessToken: null,
      refreshToken: null,
      user: null,
      setAuth: ({ accessToken, refreshToken, user }) =>
        set({ accessToken, refreshToken, user }),  // 部分更新即可，不用展开旧值
      setTokens: ({ accessToken, refreshToken }) => set({ accessToken, refreshToken }),
      setUser: (user) => set({ user }),
      clear: () => set({ accessToken: null, refreshToken: null, user: null }),
    }),
    { name: 'diary-auth' },                        // ← localStorage 的 key
  ),
)
```

**与 Pinia 的两处关键差异**：

1. **`set()` 是浅合并**，不像 React 的 `setState` 那样要手动展开：`set({ user })` 只改 `user`，另两个字段保留。
2. **订阅要写选择器**，否则任何字段变化都会触发重渲染：

```tsx
const user = useUserStore((state) => state.user)          // ✅ 只在 user 变时重渲染
const store = useUserStore()                              // ⚠️ 任何字段变都会重渲染
```

### 3.3 反直觉点：组件外取值

Vue 里你几乎不会在组件外读 store。但本项目的 **axios 拦截器必须这么做**（`src/api/request.ts`）：

```tsx
instance.interceptors.request.use((config) => {
  const token = useUserStore.getState().accessToken   // ← 不能在文件顶层 useUserStore()，那是 Hook，只能在组件里调
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
```

记住这条规则：**`useXxxStore()` 只能在 React 组件/自定义 Hook 里调用；组件外一律用 `getState()`**。文件里那行注释「不要改为异步取值」就是在提醒这一点。

### 3.4 动手练习

1. 打开 `src/store/useThemeStore.ts`，新增一个 `fontSize: 'small' | 'middle' | 'large'` 字段与 `setFontSize` action
2. 在 `MainLayout` 里加一个 Select 切换它，并在 `ConfigProvider` 的 `token` 里映射到 `fontSize`
3. 验证切换后刷新页面仍然生效（`persist` 生效）
4. 全部改回

---

## 模块 4 · 服务端状态：TanStack Query（🔴 最反直觉）

### 4.1 先理解它替代了什么

Vue 里你大概是这么写列表的：

```ts
const list = ref([])
const loading = ref(false)
const error = ref(null)

async function load() {
  loading.value = true
  try { list.value = await api.page(params) }
  catch (e) { error.value = e }
  finally { loading.value = false }
}

watch(params, load)
onMounted(load)
```

**问题**：`loading` / `error` / 缓存 / 重试 / 失效，每一项都要手写一遍；多个页面共享同一份数据时，同步极其麻烦。

TanStack Query 把这套东西变成**声明式**的：

```tsx
const { data, isPending } = useQuery({
  queryKey: ['diaries', 'list', params],   // 缓存键
  queryFn: () => diaryApi.page(params),    // 怎么取
})
```

### 4.2 逐行读 `src/hooks/useDiaryList.ts`

```tsx
export const diaryKeys = {
  all: ['diaries'] as const,
  list: (params: DiaryQuery) => [...diaryKeys.all, 'list', params] as const,
  detail: (id: number) => [...diaryKeys.all, 'detail', id] as const,
}

/** 列表查询，翻页时保留上一页数据避免闪烁 */
export const useDiaryList = (params: DiaryQuery) =>
  useQuery({
    queryKey: diaryKeys.list(params),
    queryFn: () => diaryApi.page(params),
    placeholderData: keepPreviousData,
  })
```

**要点**：

- **`queryKey` 决定缓存身份**。它必须**包含所有会影响结果的参数**——翻页时 `params.page` 变了，key 变了，才会去请求新数据。这就是为什么 `params` 整个对象都进了 key。
- **`as const`** 让 TS 把数组推断成只读元组，`diaryKeys.list(p)` 的类型才能精确，`invalidateQueries({ queryKey: diaryKeys.all })` 也就能匹配所有以 `['diaries']` 开头的 key。
- **`keepPreviousData`** 解决翻页闪烁：新数据到达前先显示上一页内容。

还有两个常用参数：

```tsx
// 条件查询：id 还没准备好时不发请求
export const useDiaryDetail = (id?: number) =>
  useQuery({
    queryKey: diaryKeys.detail(id ?? 0),
    queryFn: () => diaryApi.detail(id as number),
    enabled: typeof id === 'number' && id > 0,     // ← 为 false 时完全不请求
  })

// 数据变形：把接口返回的数组转成 Map，组件里直接查表
select: (data) => new Map(data.map(item => [item.diaryDate, item.total])),
```

### 4.3 三种「加载中」的区别（容易混）

| 字段 | 含义 | 用在哪 |
|---|---|---|
| `isPending` | **首次**加载，还没有任何数据 | 显示骨架屏 |
| `isFetching` | 正在请求（含翻页、后台刷新） | 禁用分页器 |
| `isLoading` | `isPending && isFetching` | 本项目基本不用 |

`src/pages/DiaryList/index.tsx` 里就是 `isPending` 控制骨架屏、`isFetching` 禁用分页。

### 4.4 写操作：`useMutation` + 缓存失效

```tsx
export const useDeleteDiary = () => {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: number) => diaryApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: diaryKeys.all })   // ← 让所有日记缓存失效
      message.success('已删除')
    },
  })
}
```

**心智模型**：`useMutation` 负责「改」，但它**不知道改完之后哪些列表过期了**，所以要显式告诉它。`invalidateQueries` 会让匹配的查询重新请求。

`delete` 后列表会自动刷新，不需要你手动 `load()`——这是与 Vue 里手写那一套最大的体验差异。

### 4.5 全局默认值

`src/main.tsx`：

```tsx
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,                    // 失败自动重试 1 次
      refetchOnWindowFocus: false,  // 切回标签页不自动刷新
      staleTime: 30_000,            // 30 秒内认为数据新鲜，不重复请求
    },
  },
})
```

### 4.6 动手练习

1. 打开 `src/pages/DiaryList/index.tsx`，在筛选区加一个「天气」输入框（`Input`），写进 URL（模仿 `keyword` 的写法）
2. 在 `src/hooks/useDiaryList.ts` 不用改——`params` 变了 key 自然变
3. 在 `src/types/diary.ts` 的 `DiaryQuery` 里加 `weather?: string`
4. 验证：输入天气后列表请求的 query 里带上了 `weather`，且 URL 可分享复原
5. 改回

---

## 模块 5 · 路由：React Router 7 ↔ Vue Router（🟡）

### 5.1 配置方式

Vue 用**路由文件 + 约定**，React Router 7 用**配置对象**：

```tsx
// frontend/src/router/index.tsx（结构简化）
export const router = createBrowserRouter([
  { path: '/login', element: withSuspense(<Login />) },
  { path: '/register', element: withSuspense(<Register />) },
  {
    path: '/',
    element: (
      <AuthGuard>          {/* ← 守卫是「包一层组件」，不是全局钩子 */}
        <MainLayout />
      </AuthGuard>
    ),
    children: [
      { index: true, element: <Navigate to="/diaries" replace /> },   // ← 默认重定向
      { path: 'diaries', element: withSuspense(<DiaryList />) },
      { path: 'diaries/new', element: withSuspense(<DiaryEdit />) },
      { path: 'diaries/:id', element: withSuspense(<DiaryDetail />) },
      { path: 'diaries/:id/edit', element: withSuspense(<DiaryEdit />) },
      { path: 'tags', element: withSuspense(<TagManage />) },
      { path: 'stats', element: withSuspense(<Stats />) },
      { path: 'profile', element: withSuspense(<Profile />) },
      { path: '*', element: withSuspense(<NotFound />) },
    ],
  },
])
```

| Vue Router | 这里 |
|---|---|
| `routes: [{ path, component }]` | `createBrowserRouter([{ path, element }])` |
| `{ path: '', redirect: '/x' }` | `{ index: true, element: <Navigate to="/x" replace /> }` |
| `meta: { requiresAuth: true }` | 外层包 `<AuthGuard>` |
| `component: () => import(...)` | `lazy(() => import(...))` |
| `<router-view />` | `<Outlet />`（在 `MainLayout` 里） |
| `{ path: '*', component: NotFound }` | `{ path: '*', element: <NotFound /> }` |

注意：**子路由的 `path` 不写开头斜杠**（`'diaries'` 不是 `'/diaries'`），因为它们拼接在父级 `/` 之后。

### 5.2 懒加载与守卫

**懒加载**（`defineAsyncComponent` 的对应物）：

```tsx
const DiaryList = lazy(() => import('@/pages/DiaryList'))
const withSuspense = (node: ReactNode) => <Suspense fallback={<PageLoading />}>{node}</Suspense>
```

> 本项目**刻意不懒加载 `MainLayout`**（文件里有注释）：它是所有受保护页面的外壳，懒加载收益小且会引入额外闪烁。

**守卫**：`AuthGuard` 是一个**组件**，不是全局钩子：

```tsx
<AuthGuard><MainLayout /></AuthGuard>
```

它内部做两件事：没登录就 `<Navigate to="/login" />`；已登录就渲染 `children`。**登录后回跳原路径**也是在这里记下来的（相当于 Vue 里 `beforeEach` 的 `next('/login?redirect=...')`）。

### 5.3 筛选状态放 URL 里（本项目的一个重要约定）

`src/pages/DiaryList/index.tsx` 的开头：

```tsx
const [searchParams, setSearchParams] = useSearchParams()

// URL 是筛选条件的唯一数据源，刷新或分享链接后状态可复原
const keyword = searchParams.get('keyword') ?? ''
const mood = searchParams.get('mood') ? Number(searchParams.get('mood')) : undefined
const page = Number(searchParams.get('page') ?? 1)
```

对应 Vue 的 `useRoute().query` + `router.replace()`。

**为什么不全用 `useState`**：用了 state 的话，刷新页面筛选就丢了，链接也没法分享。这里的做法是——**能进 URL 的都进 URL**。

一个易错点：搜索框**不能直接写 URL**（否则每敲一个字就发一次请求），所以：

```tsx
const [keywordInput, setKeywordInput] = useState(keyword)     // 本地态给输入框
const debouncedKeyword = useDebounce(keywordInput, 300)       // 防抖 300ms

useEffect(() => {                                            // 防抖后才回写 URL
  const next = debouncedKeyword.trim()
  if (next !== (searchParams.get('keyword') ?? '')) {
    patchParams({ keyword: next || undefined })
  }
}, [debouncedKeyword, searchParams, patchParams])
```

### 5.4 动手练习

1. 在 `src/pages/` 下新建 `About/index.tsx`，内容随意
2. 在 `router/index.tsx` 里用 `lazy` + `withSuspense` 注册 `/about`
3. 在 `src/layouts/MainLayout.tsx` 的侧边栏加一个菜单项
4. 验证：地址栏直接输入 `/about` 能打开，刷新不 404
5. 改回

---

## 模块 6 · UI 与主题：antd 5（🟡）

### 6.1 组件对照（Element Plus → antd）

| Element Plus | antd 5 | 说明 |
|---|---|---|
| `ElInput` | `Input` | — |
| `ElSelect` / `ElOption` | `Select` + `options` prop | antd 用数组配置项，更少嵌套 |
| `ElForm` / `ElFormItem` | `Form` / `Form.Item` | 校验规则写法不同（`rules` 配置） |
| `ElTable` | `Table` | `columns` + `dataSource` |
| `ElPagination` | `Pagination` | — |
| `ElDatePicker` | `DatePicker` / `DatePicker.RangePicker` | — |
| `ElMessage` | `message` **或** `App.useApp().message` | 见 6.3 |
| `ElSkeleton` | `Skeleton` | — |
| `ElEmpty` | `Empty` | — |
| `ElCard` | `Card` | — |
| `ElSpace` | `Space` / `Flex` | antd 5 新增 `Flex`，比 `Space` 更灵活 |

### 6.2 为什么需要 `@ant-design/v5-patch-for-react-19`

`src/main.tsx` 第 7 行：

```tsx
import '@ant-design/v5-patch-for-react-19'
```

antd 5 内部用了 React 18 的某些旧 API，React 19 移除了它们。这个包是**官方补丁**，只做兼容桥接。**不引入会在运行时出现组件行为异常**（而不是编译报错，比较难查）。这就是「为什么不做成 antd 6」的答案——保留 antd 5 的 API 习惯，用补丁适配。

### 6.3 主题：ConfigProvider + algorithm

```tsx
<ConfigProvider
  locale={zhCN}                                                   // ← 中文化，类似 Element 的 locale
  theme={{
    algorithm: mode === 'dark' ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: { colorPrimary: '#1677ff', borderRadius: 6 },
  }}
>
```

- `algorithm` 是**整套设计变量的切换开关**，不需要你自己写暗色样式
- `token` 是全局设计变量（类似 Element 的 SCSS 变量，但运行时生效）

**为什么自定义样式用 CSS 变量而不是 CSS-in-JS**：

antd 管不到你手写的部分——比如 Markdown 渲染出来的标题、引用块、代码块。本项目没有引入 styled-components / emotion，而是：

```css
/* src/index.css 片段 */
.markdown-body { --md-border: #e8e8e8; border-color: var(--md-border); }

[data-theme='dark'] .markdown-body { --md-border: #303030; }   /* 只覆盖变量 */
```

配合 `main.tsx` 里把主题写到 `<html data-theme="dark">`：

```tsx
useEffect(() => {
  document.documentElement.dataset.theme = mode
}, [mode])
```

**改动面最小**：暗色适配只需要在一个地方改变量值。

### 6.4 动手练习

1. 打开 `src/index.css`，找到 `.markdown-body` 下的变量定义
2. 把亮色主题下的引用块左边框颜色改成红色
3. 切到暗色，确认暗色下的颜色**没有**被影响（因为变量在 `[data-theme='dark']` 里单独定义了）
4. 改回

---

## 模块 7 · 请求层：`src/api/request.ts`（🟡 但 401 那段要精读）

### 7.1 两个很不一样的设计

**① 响应拦截器直接返回业务数据**

```tsx
instance.interceptors.response.use((res) => {
  const { code, message: msg, data } = res.data as ApiResult<unknown>
  if (code !== 0) {
    message.error(msg)
    return Promise.reject(new Error(msg))
  }
  return data as never          // ← 剥掉信封
})
```

于是业务代码里**没有 `res.data.data` 这种嵌套**：

```tsx
const page = await diaryApi.page(params)      // 直接就是 PageResult
```

**② 401 静默刷新 + 并发去重**

```tsx
let refreshing: Promise<string> | null = null      // 模块级变量

async function refreshAccessToken(): Promise<string> {
  const { refreshToken } = useUserStore.getState()
  if (!refreshToken) throw new Error('缺少 refreshToken')

  const res = await rawInstance.post<ApiResult<TokenVO>>('/auth/refresh', { refreshToken })
  if (res.data.code !== 0) throw new Error(res.data.message)

  const tokens = res.data.data
  useUserStore.getState().setTokens({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken })
  return tokens.accessToken
}
```

刷新时的处理：

```tsx
if (response?.status === 401 && config && !(config as RetriableConfig)._retry) {
  original._retry = true                    // ← 标记，防止无限重试

  try {
    refreshing ??= refreshAccessToken().finally(() => { refreshing = null })
    const newToken = await refreshing       // ← 多个请求同时 401 时，只刷新一次
    original.headers.Authorization = `Bearer ${newToken}`
    return instance(original)               // ← 重放原请求
  } catch {
    forceLogout()
    return Promise.reject(error)
  }
}
```

**这段代码解决的问题**：假设页面同时发了 5 个请求，都因为 token 过期返回 401。没有 `refreshing` 的话会触发 5 次刷新，而后端**每次刷新都会轮换 Refresh Token**——后 4 次会拿着已失效的令牌失败，用户直接被登出。`refreshing` 这个 Promise 让它们共享同一次刷新。

**另一个易忽略的细节**：刷新用的是 `rawInstance`（另一个 axios 实例），**不是** `instance`。文件里注释了原因——用 `instance` 会再次进入响应拦截器造成递归，而且 `import authApi` 会形成运行时循环依赖。

### 7.2 动手练习

1. 打开 DevTools → Application → Local Storage，找到 `diary-auth`，**手动删掉 `accessToken` 的字符**制造一个非法 token
2. 在页面上点一次列表刷新，观察 Network：应先出现一个 401，紧接着 `POST /api/v1/auth/refresh`，然后原请求被重放且成功
3. 清理 Local Storage 重新登录
4. 顺手看看 Network 里 `refresh` 请求的响应——`refreshToken` 与上一次**不一样**，这就是「令牌轮换」

---

## 模块 8 · 后端最小必要知识（🟢 能读能改）

> 你是前端，**不需要会写 Spring Boot**。但你需要能回答：「这个接口为什么返回 404 而不是 403」「我要加一个字段该改哪几个文件」。

### 8.1 Spring Boot ↔ Node/Express 对照

| Express / Koa | Spring Boot |
|---|---|
| `app.get('/x', handler)` | `@GetMapping("/x")` 方法 |
| `req.body` | `@RequestBody` 参数（自动反序列化） |
| `req.query.xxx` | `@RequestParam` |
| `req.params.id` | `@PathVariable` |
| 中间件 `app.use((req,res,next)=>{})` | `Filter`（本项目 JWT 过滤器就是） |
| 手写 `router/controller/service` 目录 | 同样的分层，但有注解驱动 DI |
| `require` / `import` 手动实例化 | 构造器注入，容器自动装配 |

### 8.2 一次请求的完整链路

以「查询日记列表」为例：

```text
浏览器 GET /api/v1/diaries?page=1&keyword=爬山
  ↓ Nginx 反代（同源，不加 CORS 头）
  ↓ Tomcat
  ↓ JwtAuthenticationFilter        ← 解析 Authorization，把当前用户放进 SecurityContext
  ↓ SecurityConfig 授权判断         ← 路径是否在白名单？是否已认证？
  ↓ DiaryController.page(DiaryQueryDTO)  ← query 参数自动绑定成 DTO 对象
  ↓ DiaryServiceImpl.page(...)           ← 业务逻辑：字段裁剪、条件拼装、标签装配
  ↓ DiaryMapper                    ← MyBatis-Plus，拼 SQL
  ↓ MySQL
  ↓ 逐层返回 → Result.ok(PageResult) → JSON
```

**排错时这条链路就是你的地图**：

| 症状 | 大概率在哪一层 |
|---|---|
| 401 / 403 | `JwtAuthenticationFilter` 或 `SecurityConfig` 白名单 |
| 400 + 「参数错误」 | Controller 的校验注解（`@NotBlank` 等） |
| 404 | Service 里的越权判断（本项目**刻意用 404 而不是 403**，避免探测他人资源是否存在） |
| 500 | 看后端日志的异常栈，`GlobalExceptionHandler` 会打印 |

### 8.3 注解速查

| 注解 | 作用 | 类比 |
|---|---|---|
| `@RestController` | 声明这是接口类，返回 JSON | 路由模块 |
| `@RequestMapping("/api/v1/diaries")` | 类级路径前缀 | `router.prefix()` |
| `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` | 方法级路径 | `router.get()` 等 |
| `@RequestBody` | 请求体 → 对象 | `req.body` |
| `@Valid` | 触发字段校验注解 | 校验中间件 |
| 无注解的复杂对象参数 | **query 参数自动绑定**（`DiaryQueryDTO query` 对应的就是 `?page=1&keyword=x`） | `req.query` |
| `@PathVariable` | 路径占位符 | `req.params` |
| `@Service` / `@Component` / `@Mapper` | 交给容器管理（可被注入） | 注册到 DI 容器 |
| `@Transactional` | 该方法在一个数据库事务里 | 手动 `BEGIN/COMMIT` |
| `@TableName` / `@TableId` / `@TableField` / `@TableLogic` | Entity ↔ 表字段映射 | ORM 模型定义 |

### 8.4 分层与「四个对象」

这是 Java 项目最常见的困惑——**为什么一个「日记」要有四个类**：

| 层 | 本项目位置 | 职责 | 前端类比 |
|---|---|---|---|
| **Entity** | `entity/Diary.java` | 与数据库表**一一对应** | 数据库行的类型 |
| **DTO** | `dto/DiarySaveDTO.java` | 入参，带校验注解 | 请求 payload 类型 |
| **VO** | `vo/DiaryVO.java` | 出参，**可能少于 Entity**（如不返回 `deleted`） | 响应 data 类型 |
| **Converter** | `converter/DiaryConverter.java` | Entity → VO | 手写的 map 函数 |

**为什么不都用 Entity**：Entity 里有 `password`、`deleted` 这类不该给前端的字段，直接用会造成信息泄露；而入参和出参的字段也往往不同（比如更新时不能改 `createdAt`）。

> 顺带解释一个你会在 `types/diary.ts` 里注意到的事：`DiaryVO`（列表项）**没有 `content`**，只有 `DiaryDetailVO` 有。这不是遗漏，是后端**刻意裁剪**——列表接口绝不读 LONGTEXT 字段。

### 8.5 MyBatis-Plus 必会 5 件事

1. **`BaseMapper<T>` 自带 CRUD**

```java
public interface DiaryMapper extends BaseMapper<Diary> { }
```

就有 `selectById` / `insert` / `updateById` / `deleteById` 等（相当于 Prisma / TypeORM 的通用 CRUD）。

2. **条件构造器写查询**

```java
diaryMapper.selectCount(Wrappers.<Diary>lambdaQuery()
        .eq(Diary::getUserId, userId)
        .ge(Diary::getDiaryDate, monthStart));
```

`Diary::getUserId` 是**方法引用**（不是调用），用来拿到字段名——类似 Prisma 的 `where: { userId }`，但更啰嗦。

3. **`@TableLogic` 逻辑删除**

带这个注解的字段（本项目是 `deleted`）**会被自动附加到所有查询条件里**。所以你调 `deleteById` 实际执行的是 `UPDATE ... SET deleted = 1`。

4. **字段自动填充**

`createdAt` / `updatedAt` 由 `MyMetaObjectHandler` 自动写入，Service 里不用管。

5. **分页插件**

```java
IPage<Diary> page = diaryMapper.selectPage(new Page<>(pageNum, size), wrapper);
```

> **为什么表注释里的中文曾经是乱码**：与上面都无关，是容器里的 mysql 客户端字符集问题，见 `development-log.md`。

### 8.6 为什么没有 Lombok

本项目**刻意不用 Lombok**（`@Data` / `@Getter` 那套）。原因是 JDK 25 下 Lombok 会触发 `Unsafe` 弃用告警，且 JDK 26 起会彻底失效。

所以你会看到：

- **DTO / VO 用 `record`** —— 一行声明不可变数据，自带访问器：

```java
public record DayCountVO(LocalDate diaryDate, long total) { }
// 访问用 diaryDate() / total()，不是 getDiaryDate()
```

- **Entity 保留显式 setter** —— 因为 MyBatis-Plus 需要「无参构造 + setter」来把数据库行映射成对象。

⚠️ **这是写测试时最容易踩的坑**：record 的访问器是 `id()` 而不是 `getId()`。前端传的 JSON 字段名不变（Jackson 支持 record），但 **Java 代码里写法不同**。

### 8.7 动手练习

1. 打开 `backend/src/main/java/com/example/diary/controller/DiaryController.java`，找到列表接口
2. 顺着它找到 `DiaryServiceImpl.page(...)`（第 54 行起），读懂四件事：
   - 为什么 `select(...)` 要**显式列出字段**（提示：`content` 是 LONGTEXT）
   - 分页参数怎么收敛（`query.pageOrDefault()` / `sizeOrDefault()`）
   - `loadTags(diaryIds)` 为什么能**避免 N+1**（对比「循环里逐条查标签」）
   - `tagId` 筛选为什么用 `wrapper.apply("EXISTS (...)", tagId)` 而不是字符串拼接
3. 回答：如果我想给列表加一个「按天气筛选」，需要改哪几个文件？（答案见附录 B 第 5 题）

---

## 模块 9 · 认证与鉴权（🟢 但要亲手走一遍）

### 9.1 两段令牌的分工

| | Access Token | Refresh Token |
|---|---|---|
| 有效期 | 默认 **30 分钟** | 默认 **7 天** |
| 存哪 | 浏览器（`diary-auth`，localStorage） | 同上，**同时后端存一份在 Redis** |
| 用途 | 每个业务请求的 `Authorization` 头 | 只用于换新的 Access Token |
| 能否撤销 | 不能（自包含，无状态） | **能**——删掉 Redis 里那条即失效 |
| 刷新时 | 重新签发 | **轮换**（旧的立即失效） |

**为什么要两段**：Access Token 要频繁校验，所以设计成无状态（不查库、不查 Redis，快）；但它因此无法提前撤销。Refresh Token 用得少，可以存 Redis，于是具备「登出即失效」「多设备管理」的能力。

#### 一个值得知道的小细节：签名算法不是写死的

`JwtUtil` 里用的是 `Keys.hmacShaKeyFor(bytes)` + `signWith(secretKey)`，**没有指定算法**——JJWT 会按密钥长度自动升级：

| 密钥长度 | 算法 | 令牌头解码结果 |
|---|---|---|
| 32~47 字节 | HS256 | `{"alg":"HS256"}` |
| 48~63 字节 | HS384 | `{"alg":"HS384"}` |
| ≥ 64 字节 | **HS512** | `{"alg":"HS512"}` |

本项目 `.env` 里的 `JWT_SECRET` 是 64 字节，所以实际签发的是 **HS512**。可以自己验证（接 9.3 第 1 步的 `$json`）：

```powershell
$h = $json.data.accessToken.Split('.')[0]                        # 取令牌头
$padded = $h.PadRight($h.Length + (4 - $h.Length % 4) % 4, '=')  # base64 补位
[System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($padded))
# 预期：{"alg":"HS512"}
```

> 代码里只校验「至少 32 字节」，且异常消息写的是「HS256 要求」——那是**沿用初版设计的措辞**，实际算法由密钥长度决定。知道这一点，排查令牌问题时就不会被算法名误导。

### 9.2 完整时序

```text
① 登录
   浏览器 ──POST /auth/login {username, password}──> 后端
   后端：查库 → BCrypt.matches 校验 → 签发 accessToken(30min) + refreshToken(7d)
         → 把 refreshToken 存 Redis（key: diary:refresh:{userId}:{jti}）
   浏览器：存入 localStorage

② 业务请求
   浏览器 ──GET /diaries  Header: Authorization: Bearer <accessToken>──> 后端
   后端 JwtAuthenticationFilter：验签 + 查过期 → 把 userId 放进 SecurityContext

③ Access Token 过期
   后端返回 401
   前端拦截器：自动 POST /auth/refresh {refreshToken}
   后端：校验 Redis 里那条记录 → 签发新的一对 → **删掉旧的 refreshToken**
   前端：更新 store → **重放刚才那个失败的请求**（用户完全无感）

④ 登出
   前端 ──POST /auth/logout {refreshToken}──> 后端删掉 Redis 记录
   前端清空 localStorage
```

### 9.3 亲手走一遍（分步，每步都有预期输出）

**第 1 步：登录，拿令牌**

```powershell
$body = [System.Text.Encoding]::UTF8.GetBytes('{"username":"tester","password":"123456"}')
$res = Invoke-WebRequest -Uri 'http://localhost/api/v1/auth/login' -Method Post `
        -ContentType 'application/json' -Body $body -UseBasicParsing
$json = [System.Text.Encoding]::UTF8.GetString($res.RawContentStream.ToArray()) | ConvertFrom-Json
$json.data.accessToken.Substring(0, 30)      # 预期：eyJhbGciOiJIUzI1NiJ9...
$json.data.user.nickname                     # 预期：测试用户
```

> 注意这里用 `-UseBasicParsing` + 手动 UTF-8 解码：PowerShell 5.1 不指定 charset 时会把中文写坏（见 `development-log.md`）。

**第 2 步：带着令牌访问受保护接口**

```powershell
$token = $json.data.accessToken
Invoke-WebRequest -Uri 'http://localhost/api/v1/users/me' -Headers @{Authorization="Bearer $token"} -UseBasicParsing |
  Select-Object -ExpandProperty Content     # 预期：{"code":0,...,"username":"tester"}
```

**第 3 步：不带令牌，应该被拦住**

```powershell
try { Invoke-WebRequest -Uri 'http://localhost/api/v1/users/me' -UseBasicParsing }
catch { $_.Exception.Response.StatusCode.value__ }     # 预期：401
```

**第 4 步：刷新令牌，并验证「轮换」**

```powershell
$rt = $json.data.refreshToken
$r2 = Invoke-WebRequest -Uri 'http://localhost/api/v1/auth/refresh' -Method Post `
      -ContentType 'application/json' `
      -Body ([System.Text.Encoding]::UTF8.GetBytes((@{refreshToken=$rt} | ConvertTo-Json))) `
      -UseBasicParsing
$new = [System.Text.Encoding]::UTF8.GetString($r2.RawContentStream.ToArray()) | ConvertFrom-Json
$new.data.refreshToken -ne $rt          # 预期：True（令牌已轮换）
```

**第 5 步：用旧 refreshToken 再刷一次，应该失败**

```powershell
try {
  Invoke-WebRequest -Uri 'http://localhost/api/v1/auth/refresh' -Method Post `
    -ContentType 'application/json' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes((@{refreshToken=$rt} | ConvertTo-Json))) -UseBasicParsing
} catch { $_.Exception.Response.StatusCode.value__ }    # 预期：401（旧令牌已失效）
```

**第 6 步：亲眼看看 Redis 里的令牌**

```powershell
cd <仓库根目录>
docker compose exec -T redis redis-cli --scan          # 预期：diary:refresh:1:<uuid>
```

### 9.4 后端过滤链在做什么

`backend/src/main/java/com/example/diary/config/SecurityConfig.java` 里这几行是前端最该知道的：

```java
private static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/**",      // 登录/注册/刷新/登出
        "/api/v1/public/**",
        "/api/v1/ping",         // 健康检查
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/error"
};
```

```java
.csrf(AbstractHttpConfigurer::disable)        // 无状态 JWT，不需要 CSRF token
.cors(cors -> cors.configurationSource(...))  // CORS 统一在这里声明
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
.authorizeHttpRequests(auth -> auth
        .requestMatchers(PUBLIC_PATHS).permitAll()
        .anyRequest().authenticated())        // 其余全部要认证
```

**两个前端相关的结论**：

1. **你新加的前端接口默认就需要认证**。如果要免登录（比如公开分享页），必须把路径加进 `PUBLIC_PATHS`。
2. **生产环境几乎不涉及 CORS**：前端由 Nginx 同源反代，浏览器根本不发跨域请求。那段 `corsConfigurationSource` 只放行 `localhost`，是给本地开发用的。

### 9.5 动手练习

1. 完整执行 9.3 的 6 个步骤，确认每步输出符合预期
2. 把第 3 步的 URL 换成 `/api/v1/ping`，观察是否变成 200（验证白名单生效）

---

## 模块 10 · 数据库（🟢）

### 10.1 五张表

```text
t_user            用户
t_diary           日记（user_id → t_user）
t_tag             标签（user_id → t_user）
t_diary_tag       日记-标签关联（diary_id + tag_id，联合主键）
t_attachment      附件（上传的图片，user_id → t_user）
```

### 10.2 逻辑删除（最容易踩的坑）

`t_diary` / `t_tag` 等表有 `deleted` 字段。**应用层看不到已删除数据**，因为 MyBatis-Plus 会自动加上 `deleted = 0`。

但**你手写 SQL 查数据时它不会**：

```sql
select * from t_diary;                  -- ❌ 会把已删除的也查出来
select * from t_diary where deleted = 0; -- ✅ 这才是应用看到的数据
```

这就是为什么用命令行查库时，看到的行数往往比界面上多。

### 10.3 动手：查数据

**容器库**（密码在 `.env`）：

```powershell
cd <仓库根目录>
docker compose exec -T mysql sh -c "mysql -uroot -p`$MYSQL_ROOT_PASSWORD --default-character-set=utf8mb4 --database=diary --table -e 'select id,title,diary_date,mood,deleted from t_diary;'"
```

**本地开发库**：用 CodeBuddy 的 Database Client 扩展连 `127.0.0.1:3306`（`root` / `123456`）。

> ⚠️ `--default-character-set=utf8mb4` **不能省**，否则中文显示为 `?`。

### 10.4 动手练习

1. 打开界面上的一条日记，记住标题
2. 用上面的命令查到它的 `id` 与 `deleted`（应为 0）
3. 在界面上删除它，**再查一次**——行还在，但 `deleted` 变成 1
4. 验证：界面上已经看不到它了，但数据仍在库里（这就是「逻辑删除」）

---

## 模块 11 · 构建与部署（🟢）

### 11.1 你熟悉的部分

`frontend/vite.config.ts`：

```ts
resolve: { alias: { '@': path.resolve(__dirname, 'src') } }   // 与 Vue 项目里配 @ 别名一样
server: { proxy: { '/api': { target: 'http://localhost:8080' } } }   // 与 vue.config.js 的 devServer.proxy 同理
```

`npm run build` 是 `tsc -b && vite build`——**先类型检查再打包**，类型错误会直接失败。

### 11.2 多阶段构建在干什么

`frontend/Dockerfile`：

```text
阶段 1（node:22-alpine）        阶段 2（nginx:1.31.6-alpine）
  COPY package*.json                COPY --from=阶段1 /build/dist  →  /usr/share/nginx/html
  npm ci                            COPY nginx.conf  →  /etc/nginx/conf.d/default.conf
  COPY . .
  npm run build   ───────────────►  产物拷过来
```

**为什么要两个阶段**：`node_modules` 和源码在最终镜像里完全不需要，只要 `dist`。这样镜像从「几百 MB 的 Node 环境」变成「几十 MB 的 Nginx」（实测 97.6 MB）。

**顺带解释一个你会关心的问题**：镜像里**没有 Node**，所以改完前端代码必须重新构建镜像，不能在容器里跑 `npm run dev`。日常改前端用**路线 B（本地开发）**，改完再构建。

### 11.3 Nginx 同源反代 = 为什么不用管跨域

`frontend/nginx.conf` 三段：

```nginx
location /api/   { proxy_pass http://backend:8080; }   # 接口
location /files/ { proxy_pass http://minio:9000/;  }   # 图片
location /       { try_files $uri $uri/ /index.html; } # 静态 + SPA 回退
```

因为浏览器只访问**一个源**（`http://localhost`），`/api` 和 `/files` 都是同源的，**跨域根本不发生**。

`try_files ... /index.html` 是 SPA 路由的必需配置——否则刷新 `/stats` 会 404（服务器上并没有 `stats` 这个文件）。

### 11.4 动手练习

1. 运行 `docker compose ps`，对照 `usage-guide.md` 的端口表理解每个服务
2. 改一行前端文案（比如登录按钮的文字），运行 `docker compose up -d --build`
3. 观察构建耗时——**应该只有 20 秒左右**（`npm ci` 那层被缓存了），这就是多阶段 + 分层缓存的收益
4. 刷新页面确认文案变了，然后改回

---

## 附录 A · 一页速查对照表

| 概念 | Vue 3 | 本项目（React 19） |
|---|---|---|
| 单文件组件 | `.vue`（template/script/style） | `.tsx`（一个函数） |
| 模板语法 | `{{ }}` / `v-if` / `v-for` | `{}` / `&&` / `map` |
| 双向绑定 | `v-model` | `value` + `onChange` 手动配对 |
| 事件 | `@click="fn"` | `onClick={fn}` |
| 本地状态 | `ref` / `reactive` | `useState`（不可变更新） |
| 派生状态 | `computed` | `useMemo` |
| 副作用 | `watch` / `onMounted` | `useEffect`（依赖数组必写） |
| 全局状态 | Pinia | Zustand |
| 持久化 | `pinia-plugin-persistedstate` | `persist` 中间件 |
| 服务端状态 | 手写 loading + 缓存 | **TanStack Query** |
| 路由 | Vue Router | React Router 7 |
| 路由守卫 | `beforeEach` | `<AuthGuard>` 组件包裹 |
| 懒加载 | `defineAsyncComponent` | `lazy` + `<Suspense>` |
| 插槽 | `<slot />` | `props.children` |
| 依赖注入 | `provide` / `inject` | `createContext` / `useContext` |
| UI 库 | Element Plus / antd-vue | antd 5 |
| 主题 | SCSS 变量 / `el-config-provider` | `ConfigProvider` + `algorithm` |
| 路由出口 | `<router-view />` | `<Outlet />` |
| 严格模式双执行 | 无 | `<StrictMode>` 下 `useEffect` 跑两次 |

---

## 附录 B · 练习清单（按难度）

### 入门（改文案 / 加字段）

1. 把日记列表空态的「还没有日记，开始记录吧」改成你自己的文案
2. 在 `src/types/diary.ts` 给 `DiaryVO` 加字段，用构建报错定位所有引用点
3. 给「写日记」按钮换成另一个 antd 图标

### 进阶（加交互）

4. `DiaryEdit` 里的「天气」目前是个自由输入的 `Input`（第 179 行）。把它改成 `Select` 下拉（晴 / 多云 / 雨 / 雪），验证提交后数据库 `weather` 有值
5. **【推荐】给列表加「按天气筛选」**——需要改 4 处，完整走通前后端：
   - `frontend/src/types/diary.ts`：`DiaryQuery` 加 `weather?: string`
   - `frontend/src/pages/DiaryList/index.tsx`：加一个 `Input`（或 `Select`），把值写进 URL（模仿 `keyword` 的 `patchParams` 写法）
   - `backend/.../dto/DiaryQueryDTO.java`：加 `String weather` 字段
   - `backend/.../service/impl/DiaryServiceImpl.java` 的 `page(...)` 里，**模仿已有的 `mood` 写法**加：

     ```java
     if (query.weather() != null) {
         wrapper.eq(Diary::getWeather, query.weather());
     }
     ```

   > 注意后端用的是「if 判空 + 追加条件」的写法，而不是 `eq(boolean, ...)` 重载——**照着周围代码的风格写**比用你更喜欢的写法更重要。
6. 给 `MoodPicker` 加键盘可访问性（`tabIndex` + `onKeyDown` 支持回车选择）

### 挑战（理解架构）

7. 在统计页加一个「本月每天篇数」的柱状图（`recharts` 的 `BarChart`）
8. 给 `DiaryVO` 增加 `tagCount` 字段，在后端避免 N+1 的前提下算出来
9. 实现「编辑页离开前确认」的完整逻辑（`DiaryEdit` 里已有草稿保护，读懂它的 `allowNavigateRef` 为什么必须是 ref 而不是 state）

---

## 附录 C · 卡住时先查哪里

| 现象 | 先看这里 |
|---|---|
| 类型报错看不懂 | 报错文件 + 报错字段名，反查类型定义；`npm run build` 会给出完整清单 |
| 界面没更新 | 是不是直接改了对象/数组属性而没返回新对象？（见 2.3） |
| 请求发重了 | 开发环境下 `StrictMode` 会让 `useEffect` 跑两次（见 2.4） |
| 接口 401 | token 过期？看 DevTools 是否触发了 `/auth/refresh`。若刷新也失败 → 重新登录 |
| 接口 403 | 路径不在 `SecurityConfig.PUBLIC_PATHS`（见 9.4） |
| 接口 404 | 可能不是路由不存在，而是**后端 Service 里的越权判断**（本项目刻意用 404） |
| 列表数据对不上 | 手写 SQL 没加 `deleted = 0`（见 10.2） |
| 中文变 `?` | 命令行查库缺 `--default-character-set=utf8mb4` |
| 图片不显示 | 看图片 URL 是否以 `/files/` 开头（容器部署）；本地开发应是 `http://127.0.0.1:9000/...` |
| 刷新页面 404 | SPA 回退没生效，检查 `nginx.conf` 的 `try_files` |
| 改了前端容器里没变 | 前端镜像内没有 Node，必须重新构建（见 11.2） |
| 不知道某个命令怎么用 | `usage-guide.md`（操作）、`README.md`（速查） |
| 想知道某个坑是怎么来的 | `development-log.md`（开发实录） |

### 代码入口速查

```text
frontend/src/
├── api/            接口封装（request.ts 是请求层核心）
├── types/          类型定义（先看这里，再看页面）
├── hooks/          数据 hooks（TanStack Query 都在这）
├── store/          Zustand store
├── pages/          页面组件（按路由命名）
├── components/     复用组件
├── layouts/        MainLayout（侧边栏 + Outlet）
└── router/         路由表 + AuthGuard

backend/src/main/java/com/example/diary/
├── controller/     接口入口（从这找某个 API 怎么实现的）
├── service/impl/   业务逻辑（排错主战场）
├── entity/         表映射
├── mapper/         数据访问
├── dto/ vo/        入参与出参
└── config/         Security / MyBatis-Plus / MinIO 等配置
```
