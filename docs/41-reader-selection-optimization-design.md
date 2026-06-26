# 41 - 阅读器选区模块设计优化方案

> 编写时间：2026-06-26  
> 更新时间：2026-06-26（补充官方资料调研、审核意见修订）  
> 范围：阅读正文长按选区、拖拽把手、选区高亮、选区菜单、连续滚动模式边缘扩选  
> 状态：设计评审稿  
> 原则：正确性优先 · 低分配绘制 · 状态边界清晰 · UI 克制直观 · 本地功能优先

---

## 0. 结论

当前选区系统已经具备较好的基础：中立锚点、把手交叉、拖拽放大镜、选区高亮、触摸处理拆分都已经存在。后续优化不应按“大破大立”的方式一次性改完，而应先修正确性，再做低风险性能优化，最后扩展边缘自动滚动和视觉动效。

推荐路线：

0. **P-1：建立性能与行为基线**  
   先用 Profiler、GPU Rendering、手工压力用例记录当前分配、帧耗时、拖拽稳定性，避免优化后无法判断收益。
1. **P0：修正选区命中与边界行为**  
   先解决 `pixelToChar`、行首/行尾 clamp、行间命中、多行选区命中、空选区策略、交叉后词边界吸附角色判断。
2. **P1：降低绘制链路分配**  
   优先复用 `Paint`、`Path`、`RectF`，移除热点临时集合和 lambda 分配，不先引入复杂 Object Pool。
3. **P2：引入选区几何缓存**  
   缓存 rect/path，但缓存 key 必须包含页面、layout、range、style 信息，不能只按 range 缓存。
4. **P3：优化把手与菜单 UI**  
   做 active handle 放大、alpha 状态、轻量浮岛菜单和稳定避让，不把动画状态放入 `HandleInfo`。
5. **P4：连续滚动边缘扩选**  
   独立 `SelectionAutoScrollController` 驱动，给滚动 delegate 增加受控 API，页面回收后重新命中字符。
6. **P5：高亮混色实验**  
   不直接给 `selectionPaint` 全局加 `MULTIPLY`，先用调色或隔离图层方案验证。

明确不做：

- 基础选区链路不依赖网络、云端服务或外部在线能力。
- 菜单动作优先本地完成；外部应用联动只能作为显式、可关闭的补充能力。
- 不为了“零分配”引入泛化对象池。
- 不把 Path 缓存塞进 `ReaderCanvasView` 当全局状态。
- 不把 `selectionPaint` 全局设置 `Xfermode`。

---

## 1. 当前实现快照

### 1.1 核心类职责

| 模块 | 当前职责 | 后续建议 |
|---|---|---|
| `CanvasTextSelection` | 选区范围、锚点、把手几何、坐标到字符索引命中 | 保持纯状态/几何逻辑，修正命中边界和交叉后的角色判断 |
| `CanvasTouchHandler` | 手势识别、把手拖拽状态、拖拽速度判断 | 继续只负责触摸识别，不承载边缘自动滚动循环 |
| `ReaderCanvasView` | View 状态协调、绘制入口、放大镜、菜单/交互桥接 | 只做编排和 View 层动画状态，不持有复杂几何缓存 |
| `ReaderPageRenderer` | 页面正文、overlay、高亮、把手绘制 | 作为性能优化主战场，减少分配并接入几何缓存 |
| `ScrollPageDelegate` | 连续滚动页面偏移、fling、页面回收 | 暴露受控滚动 API 给选区边缘扩选使用 |

### 1.2 已有正向设计

- `AnchorId.A/B` + `anchorAIsStart` 已经避免了“左把手/右把手”硬编码，是正确方向。
- 把手交叉后样式互换符合阅读器预期。
- `CanvasTouchHandler` 已有拖拽速度判断，可作为词边界吸附策略基础。
- `ReaderCanvasView` 已有放大镜缩放动画，可作为后续 active handle 动效基础。
- 色温滤镜已使用独立 `MULTIPLY` 绘制路径，说明项目具备图层混合经验，但选区高亮不应直接复用同一策略。

### 1.3 官方资料调研结论

本次修订参考 Android 与 Material 官方资料，形成以下约束：

| 主题 | 官方结论 | 对本方案的影响 |
|---|---|---|
| 自定义 View 性能 | Android 官方建议在 `onDraw()` 中减少对象创建，绘制对象应预先创建和复用 | 低分配优化优先处理 `Paint`、`Path`、`RectF`、临时集合 |
| 帧性能基线 | Android Vitals 和 GPU Rendering 工具可用于定位慢帧、冻结帧和绘制阶段耗时 | Phase 0 前增加性能基线，避免无指标优化 |
| 触摸事件拦截 | ViewGroup 可拦截子 View 触摸；子 View 可通过 `requestDisallowInterceptTouchEvent()` 请求父容器不拦截 | 把手拖拽期间应独占选区手势，不把 MOVE 交给翻页/滚动 |
| 无障碍 | 交互控件需要可理解标签、足够触控目标、不能只依赖颜色表达状态 | 把手、菜单动作、选区状态需要 TalkBack 与键盘验证 |
| 触控目标 | Material/Android 生态通常以 48dp 左右作为可触控目标基线 | 把手视觉尺寸可小，但触摸热区不应小于 44-48dp |
| 上下文菜单 | Android 支持上下文菜单、文本处理 Intent、返回键退出上下文模式等本地交互模型 | 选区菜单优先使用本地动作；外部文本处理作为可选扩展 |
| 图层混合 | `saveLayer()` 和复杂混合会带来额外离屏绘制成本 | `MULTIPLY` 只作为隔离图层实验，不进入默认链路 |
| `Path.Op` | Android 官方提供 `Path.Op.UNION` 等布尔运算能力 | 可以使用，但需保留逐 rect 绘制回退和设备兼容测试 |
| Kotlin 集合遍历 | Kotlin 标准库中部分集合操作为 inline，但仍可能引入 lambda/迭代器语义和可读性取舍 | index-based 循环是低优先级微优化，先处理真实对象分配 |

---

## 2. 设计目标与非目标

### 2.1 目标

1. 长按选区行为稳定，拖动到边界、行间、页边缘时结果可预测。
2. 把手交叉后视觉角色自然切换，内部锚点仍保持中立。
3. 拖拽过程低分配，避免每帧创建大量 `RectF`、`Path`、`Paint`、临时集合。
4. 连续滚动模式支持边缘扩选，但不破坏现有翻页/滚动手势。
5. 选区菜单简洁、清晰、轻量，优先本地功能。
6. 高亮、笔记、选区叠加后保持文字可读。

### 2.2 非目标

1. 不把基础选区链路绑定网络、云端服务或外部在线能力。
2. 不把字典、笔记、复制等菜单能力和网络服务绑定。
3. 不在第一阶段重构整个阅读器渲染架构。
4. 不追求理论上的绝对零分配，以代码可维护和可验证为优先。
5. 不在低版本 Android 上强依赖 `RenderEffect Blur`。
6. 不为了菜单“智能化”牺牲选区响应速度和离线可用性。

---

## 3. P0：选区正确性优化

### 3.1 坐标命中必须 clamp

当前风险：

- y 只命中 `line.top..line.bottom`，拖到行间、页面上下边界时容易返回 null。
- x 在行首左侧时可能走完整行字符循环后返回行尾，导致拖到左侧反而跳到该行末尾。
- 边缘自动滚动依赖稳定命中，如果命中不稳定，会出现选区闪跳。

目标行为：

| 手指位置 | 期望命中 |
|---|---|
| x 在行首左侧 | 当前行 `lineStart` |
| x 在行尾右侧 | 当前行 `lineEnd` |
| y 在两行之间 | 距离最近的行 |
| y 在页面顶部上方 | 第一条可选行 |
| y 在页面底部下方 | 最后一条可选行 |

验收标准：

- 慢速拖动到行首左侧，选区不会跳到上一行或当前行末尾。
- 拖动到行间空白，选区仍连续变化，不出现 null 抖动。
- 拖动到屏幕顶部/底部边缘，后续边缘扩选可以稳定推进。

### 3.2 视觉角色基于 range，而不是 AnchorId

`AnchorId.A/B` 是中立锚点，不应承载“开始/结束”的语义。交叉后，A 可以是结束，B 可以是开始。

需要统一以下判断：

- 当前 active anchor 是否为视觉 start。
- 把手绘制使用 start/end 样式。
- fast drag 词边界吸附使用 start/end 角色。
- 菜单锚点和放大镜焦点使用 active anchor 的当前视觉角色。

验收标准：

- 左把手拖过右把手后，样式立即互换。
- 右把手拖过左把手后，样式立即互换。
- 交叉后继续快速拖动，词边界吸附方向仍正确。

### 3.3 多行选区命中不能只用包围盒

跨行选区如果只用两个锚点组成大包围盒，容易把未选中的行尾/行首空白也判断为选区内部。

建议：

- `isPointInSelection` 按行判断选区 rect。
- 单行选区只命中选中字符范围。
- 多行选区按首行、中间行、末行分别计算。

验收标准：

- 多行选区右侧空白处不会被误判为选区内部。
- 点击选区外空白可以正常退出或触发原本阅读区行为。

### 3.4 明确空选区策略

当前逻辑允许坍缩为空选区，这对编辑器合理，但阅读器选区需要明确策略。

推荐策略：

| 场景 | 行为 |
|---|---|
| 长按正文初始选区 | 至少选中一个字符或一个词 |
| 拖拽导致 start == end | 允许短暂存在，但不弹出复制/笔记菜单 |
| 松手后仍为空 | 取消选区或恢复到最近一次非空选区 |
| 菜单动作 | 对空选区全部禁用 |

验收标准：

- 不出现空选区但菜单仍展示“复制/笔记”的状态。
- 不出现复制空字符串、创建空笔记的状态。

### 3.5 fast drag 词吸附要更克制

词边界吸附可以提升快速选择体验，但阈值过低会影响精调。

建议：

- 慢速拖动按字符精调。
- 快速拖动才按词边界吸附。
- 速度判断增加迟滞，避免快慢状态频繁切换。
- 可考虑“暂停后自动回到字符级精调”。

验收标准：

- 普通拖拽不会频繁跳词。
- 快速横扫可以高效扩展到词边界。
- 交叉后吸附方向不反。

### 3.6 选区长度限制

选区本身需要上限，避免超长范围导致高亮几何、菜单动作、复制、笔记和词典查询同时进入高成本路径。

推荐策略：

| 项目 | 建议 |
|---|---|
| 默认最大选区长度 | 5000 字符 |
| 超过上限时继续向外拖拽 | 禁止扩展，只允许缩小或反向调整 |
| 超过上限时菜单 | 仍可显示，但禁用笔记/词典等高成本动作，仅保留复制、清除等安全动作；也可按产品策略禁用复制 |
| 用户反馈 | 轻量提示“选区过长”，不打断拖拽 |
| 配置方式 | 常量集中管理，后续可按设备性能或产品策略调整 |

实现语义：

- `moveHandle` 检测到新 range 超过上限时，不返回 null。
- 返回当前有效 range，并标记 `limitReached = true` 或等价状态。
- View 层不更新 range，不更新把手目标位置，让 active handle 冻结在上限位置。
- 如果下一次拖拽方向是缩小选区，则恢复正常更新。

验收标准：

- 大段文本拖拽不会导致持续卡顿或内存峰值异常。
- 超过上限后 range 不继续扩大，但把手仍可反向缩小。
- 菜单动作不会处理超长文本造成长时间阻塞。

### 3.7 选区取消与生命周期

阅读器选区不是编辑器撤销栈，不建议为“拖拽过程”引入复杂 undo。需要的是清晰、可预测的取消和生命周期规则。

推荐规则：

| 场景 | 行为 |
|---|---|
| 点击选区外空白 | 清除当前选区 |
| 双击空白区域 | 清除当前选区，并恢复正常阅读手势 |
| Android 返回键 / 应用内返回 | 如果选区存在，优先清除选区；没有选区时再执行页面返回 |
| 翻页模式下翻到其他页 | 默认清除当前选区 |
| 连续滚动同章节轻微滚动 | 拖拽中保留；非拖拽状态滚动后清除或收起菜单 |
| Activity/Fragment 销毁 | 选区状态随 `ReaderCanvasView` 生命周期释放，不持久化 |
| 进入笔记编辑 | 将选区文本和 range 复制到笔记流程，退出后不强制恢复选区 |
| 笔记保存成功 | 保存笔记/高亮结果后清除当前临时选区 |
| 外部文本处理返回 | 默认清除选区，不尝试恢复旧菜单和旧把手状态 |

设计取舍：

- 默认不持久化选区，避免翻页回来后出现上下文已失效的选区。
- 笔记、高亮是持久化结果；选区本身是临时交互状态。
- 如果后续需要“恢复上次选区”，应作为独立功能开关，不进入基础链路。

### 3.8 手势优先级

选区拖拽期间必须建立明确优先级，避免与翻页、滚动、返回手势互相抢事件。

优先级：

1. 把手拖拽中：选区最高优先级。
2. 选区菜单打开：菜单点击优先，外部点击用于清除选区。
3. 普通长按识别：优先于单击翻页，但不能阻塞系统返回手势。
4. 无选区状态：恢复正常翻页/滚动手势。

实现要求：

- 把手拖拽开始时，取消当前 fling。
- 把手拖拽期间，不把 MOVE 事件交给翻页 delegate。
- 如存在父容器拦截风险，拖拽期间调用 `requestDisallowInterceptTouchEvent(true)`。
- 应用内返回键先清除选区；系统级边缘返回手势不做强拦截，只保证状态清理正确。

---

## 4. P-1 / P1：性能基线与低分配优化

### 4.1 Phase -1：先建立性能基线

在改绘制链路之前，先记录当前基线。否则无法判断“低分配重构”和“Path 缓存”是否真的带来收益。

基线项目：

| 指标 | 工具 | 场景 |
|---|---|---|
| Java/Kotlin 分配 | Android Studio Memory Profiler / Allocation tracking | 长按选区、拖拽把手、多行选区拖动 |
| 帧耗时 | Android Studio Profiler / Profile GPU Rendering | 慢速拖动、快速横扫、边缘扩选 |
| 绘制阶段耗时 | Profile GPU Rendering | overlay 高亮、把手、放大镜、菜单显示 |
| 行为稳定性 | 手工压力用例 + 日志 | 把手交叉、行间拖动、页面边缘拖动 |
| 内存压力 | Memory Profiler / 低端设备真机 | 大字号、大行距、20+ 行选区、放大镜、菜单同时存在 |
| 设备兼容 | 真机矩阵 | MIUI、ColorOS、Pixel/原生系统、低端机 |

建议先保存以下基线结果：

- 单行选区拖拽 10 秒内的平均分配。
- 多行选区拖拽 10 秒内的平均分配。
- `Path.Op.UNION` 开启时的帧耗时。
- 逐 rect 绘制回退时的帧耗时。
- 打开放大镜和菜单时的最差帧耗时。
- 大字号 32sp + 行距 2.5x + 20+ 行选区的内存峰值。
- 低内存设备或 2GB RAM 级别设备上的拖拽稳定性。

### 4.2 优先处理热点

高频绘制路径主要集中在 overlay：

- 选区背景 rect 计算。
- 多行选区 Path 合并。
- 选区把手绘制。
- 查找高亮 rect 计算。
- 笔记高亮 rect 计算。

当前风险点：

- 每帧创建 `RectF`。
- 每帧创建 `Path` / `tempPath`。
- 每帧创建 `Paint(paint)`。
- 每帧创建 `mutableListOf<RectF>()`。
- 使用 `forEach` / `map` 等产生额外调用和临时对象。

### 4.3 不优先做泛化 Object Pool

不推荐第一步建立通用对象池，原因：

1. 对象池会引入借还生命周期，容易出现状态未 reset。
2. Android 绘制对象通常可以通过成员变量复用解决。
3. 泛化池会降低代码可读性，收益不一定高于固定 buffer。

推荐顺序：

1. `Paint` 成员化，禁止绘制时 `Paint(existingPaint)`。
2. `Path` 成员化，每帧 `reset()`。
3. `RectF` 临时对象成员化或固定数组化。
4. 选区 rect 不再先收集 `List<RectF>`，可直接绘制或写入小型固定 buffer。
5. 热点循环按需改为 index-based `for`，优先级低于对象分配治理。

说明：

- Kotlin 的部分集合函数是 inline，`forEach` 本身不一定是主要瓶颈。
- 只有在 Profiler 证明 lambda、迭代器或捕获对象产生额外成本时，才批量改循环。
- 第一优先级始终是消除 `RectF`、`Path`、`Paint` 和临时集合分配。

### 4.4 `Path.Op` 兼容与回退

多行选区使用 `Path.Op.UNION` 可以获得更自然的圆角融合，但应保留回退路径。

策略：

| 模式 | 用途 |
|---|---|
| `UNION` 融合 Path | 默认高质量模式 |
| 逐 rect 绘制 | 兼容回退模式 |
| Debug 开关 | 快速切换两种模式定位设备问题 |
| 自动回退 | `Path.op` 返回 false 或绘制检测异常时，当前会话切换到逐 rect 绘制 |

验收：

- 官方 API 行为正常设备上优先使用融合 Path。
- 如果某些设备出现 Path 边缘缺失、闪烁或圆角异常，可切换到逐 rect 绘制。
- 回退模式不影响选区 range、把手、菜单锚点等业务逻辑。

自动回退原则：

- `Path.op` 返回 false 时立即回退。
- Debug 构建可记录设备型号、Android 版本、Path 输入 rect 数量。
- Release 构建只做静默回退，不影响用户阅读。
- 自动回退只影响选区背景融合，不影响把手、菜单、复制、笔记等业务功能。

### 4.5 分配预算

目标不是全局零分配，而是拖拽选区每帧主链路接近零分配。

建议指标：

| 指标 | 目标 |
|---|---|
| 把手拖拽期间 Java/Kotlin 分配 | 接近 0 B/frame |
| 选区拖拽帧耗时 | 低于 8 ms/frame，留出系统合成余量 |
| 多行选区 Path 重算 | 仅 range 或 layout 改变时发生 |
| 菜单/动画分配 | 不进入每帧主绘制链路 |

---

## 5. P2：SelectionGeometryCache 设计

### 5.1 缓存内容

建议缓存的是“选区几何”，不是业务状态：

- 选区背景 rect 列表或固定 buffer。
- 多行 rounded path。
- 起止把手 rect。
- 菜单锚点候选区域。

不缓存：

- `activeAnchor`。
- 菜单可见状态。
- 复制/笔记等业务动作状态。
- 当前手势状态。

容量限制：

- 默认最多缓存 5-10 个 `pageKey` 的几何数据。
- 翻页模式通常只需要当前页、上一页、下一页。
- 连续滚动模式只保留可见页和相邻预取页。
- 超出容量时按最近使用或页面距离淘汰。

### 5.2 缓存位置

不建议放在 `ReaderCanvasView` 里。推荐位置：

推荐关系：

```
ReaderCanvasView
    └── ReaderPageRenderer
            └── SelectionGeometryCache
```

说明：

- `ReaderPageRenderer` 持有 `SelectionGeometryCache`，避免双向依赖。
- `ReaderCanvasView` 不直接操作缓存，只通过 renderer 的绘制入口触发缓存命中或失效。
- 如果菜单锚点需要查询几何，可由 renderer 暴露只读结果，或由 `CanvasTextSelection` 提供轻量锚点计算，不让 View 直接改缓存。

`CanvasTextSelection` 可以继续提供 range 和锚点状态，但不负责 View 层缓存生命周期。

### 5.3 缓存 key

缓存 key 不能只有 `SelectionRange`。至少包含：

| 字段 | 原因 |
|---|---|
| `chapterIndex` | 防止跨章复用 |
| `pageIndex` 或 page identity | 防止页面回收后错用 |
| `page.startCharOffset/endCharOffset` | 防止同页索引但内容变化 |
| `range.start/range.end` | 选区范围 |
| layout version | 字号、行距、边距、字体变化后失效 |
| style version | padding、corner radius、颜色策略变化后失效 |
| density/fontScale | 影响几何尺寸 |

为了降低 key 的复杂度，推荐把完整输入压缩为少数字段：

```kotlin
data class SelectionGeometryCacheKey(
    val pageKey: Long,
    val rangeKey: Long,
    val layoutVersion: Int,
    val styleVersion: Int,
)
```

字段含义：

| 字段 | 组成建议 |
|---|---|
| `pageKey` | `chapterIndex`、`pageIndex`、`startCharOffset`、`endCharOffset`、page identity 的稳定 hash |
| `rangeKey` | 用两个 32-bit offset 组合成 64-bit key：`(start.toLong() shl 32) xor (end.toLong() and 0xffffffffL)` |
| `layoutVersion` | 字号、字体、行距、段距、边距、窗口尺寸、density/fontScale 变化时递增 |
| `styleVersion` | 选区 padding、圆角、颜色策略、深色模式 token 变化时递增 |

原则：

- key 对外保持简单，对内通过 version 聚合复杂条件。
- `rangeKey` 不使用普通 hash，避免不同 range hash 碰撞导致复用错误几何。
- layout/style version 由现有设置变更链路统一递增，不在 cache 内部猜测。
- 如果暂时没有统一 version，第一版宁可禁用缓存，也不要使用不完整 key。
- Phase 2 前必须确认或补齐 `SelectionLayoutVersionProvider`：负责在排版参数、窗口尺寸、density/fontScale、页面模型变化时递增 `layoutVersion`。
- Phase 2 前必须确认或补齐 `SelectionStyleVersionProvider`：负责在选区样式、主题、深色模式、高亮策略变化时递增 `styleVersion`。

### 5.4 失效条件

必须明确失效：

- 当前页变化。
- 连续滚动页面回收。
- 章节内容变化。
- 字号、字体、行距、段距、边距变化。
- 横竖屏、窗口尺寸变化。
- 选区视觉样式变化。
- 主题/背景变化，如果影响高亮 alpha 或颜色策略。

页面回收规则：

- 当前页面回收时，清除该 pageKey 对应的几何缓存。
- 连续滚动模式发生页面窗口移动时，只保留可见页和相邻预取页的缓存。
- 章节切换、reflow、字体设置变更时直接清空全部选区几何缓存。

---

## 6. P3：把手、放大镜与菜单 UI

### 6.1 把手动效

推荐保留当前简洁把手形态，在交互状态上增强：

| 状态 | 视觉 |
|---|---|
| 刚进入选区 | 两个把手轻微 scale in |
| 拖拽 active handle | active 圆点放大到 1.15-1.25，alpha 100% |
| inactive handle | 保持正常尺寸，alpha 85%-90% |
| 拖拽结束 | active handle 回到正常尺寸 |

动效参数：

| 动效 | 建议 |
|---|---|
| 把手出现 | spring，`dampingRatio = 0.6f`，`stiffness = 300f`，scale 0.92 → 1.0 |
| active 放大 | 100-120ms ease-out，scale 1.0 → 1.15/1.25 |
| 松手恢复 | 120ms ease-out，scale 回到 1.0 |
| alpha 切换 | 100ms linear/ease-out |

关键边界：

- `HandleInfo` 只保存几何和视觉角色，不保存动画值。
- 动画状态放在 `ReaderCanvasView` 或专用 View 状态类。
- 把手触摸热区要大于视觉尺寸，建议不小于 44-48dp。
- 把手需要提供无障碍语义：起始把手、结束把手、当前是否正在拖拽。
- 把手颜色始终使用 `selectionHandle = accent solid`，不跟随选区填充 alpha 变化。
- 两个把手重叠时，active handle 始终绘制在 inactive handle 上层。

### 6.2 放大镜

已有放大镜缩放动画，应继续沿用。

优化建议：

- 放大镜焦点始终跟随 active anchor。
- 接近屏幕顶部时自动放到下方；阈值为 `magnifierHeight + 24dp`。
- 接近左右边缘时水平 clamp。
- 放大镜内部只显示局部正文和 active handle，不显示菜单。

### 6.3 本地优先的选区菜单

选区菜单遵循“本地优先”原则：基础动作必须离线可用、响应稳定、不会因为网络状态影响阅读体验。外部能力可以作为显式入口，但不能成为基础链路依赖。

推荐一级动作：

| 动作 | 说明 |
|---|---|
| 复制 | 本地剪贴板 |
| 划线/高亮 | 本地笔记高亮 |
| 笔记 | 打开本地笔记编辑 |
| 字典 | 优先本地词典或已内置词典能力 |
| 搜索 | 书内搜索 |
| 分享 | 系统分享 sheet |

可选二级动作：

| 动作 | 说明 |
|---|---|
| 外部处理 | 通过系统文本处理能力交给用户选择的本机应用 |
| 网页搜索 | 仅作为显式外部跳转，不作为默认能力 |

菜单形态：

- 使用轻量浮岛，不优先做重毛玻璃。
- 背景采用半透明实色 + 细边框 + 阴影。
- 根据可用空间避让，位置优先级：选区下方 → 选区上方 → 横屏或大屏右侧。
- 横向空间不足时分页或二级更多，不压缩文字到不可读。
- 出现/消失动画使用 fade + scale，建议 120-150ms，避免影响拖拽响应。
- 一级菜单项使用 Material Icon + 短文字，图标用于提升扫读速度，文字用于无障碍和歧义消解。
- 菜单项提供无障碍标签和清晰 focus 顺序。

动画打断规则：

- 把手拖拽开始时，立即 cancel 菜单动画并隐藏菜单。
- 菜单 fade out 过程中，如果用户长按选中新词，立即 cancel fade out，重置到新锚点后执行 fade in。
- 菜单 fade in 过程中，如果页面切换、选区清除或自动滚动开始，立即 cancel fade in 并隐藏。
- 动画状态不参与业务状态判断，菜单是否可见以最新选区状态为准。

不推荐第一阶段做：

- 高斯模糊毛玻璃。
- 把需要网络或长耗时的能力放进一级菜单。
- 菜单项复杂动态重排。

### 6.4 词典集成设计

词典能力按本地优先分层：

| 层级 | 能力 | 说明 |
|---|---|---|
| L1 | 内置/本地词典弹窗 | 优先路径，直接在阅读器内显示释义 |
| L2 | 已安装本机词典应用 | 通过系统 Intent 或应用内适配打开，用户明确选择 |
| L3 | 复制到剪贴板 | 无词典能力时的稳定 fallback |
| L4 | 外部网页查询 | 可选、显式跳转，不进入默认链路 |

查询流程：

1. 选区文本规范化：trim、限制最大长度、过滤过长段落。
2. 选区文本超过 100 字符时，不展示词典入口，仅保留复制/外部处理等通用动作。
3. 如果文本是单词或短词组，优先展示词典入口。
4. 如果文本是多句或段落，默认不展示词典入口，避免误触。
5. 有本地词典结果时，在阅读器内浮层显示。
6. 无本地结果时，提供“复制”或“外部处理”。
7. 查询超过 3 秒无响应时视为超时，显示轻量失败态，不阻塞阅读器菜单。

实现建议：

- 与现有字典模块保持单向依赖：选区菜单发起查询请求，字典模块返回本地结果。
- 不在选区模块内直接读词典数据库，避免职责扩散。
- 外部文本处理可参考 Android `ACTION_PROCESS_TEXT` 思路，但必须作为可选路径。

### 6.5 深色模式与色彩 token

把手、高亮、菜单不能只套用同一透明度。深色背景下低 alpha 高亮可能不可见，浅色背景下高 alpha 又会脏。

建议建立 token：

| Token | 日间 | 夜间/深色 | 护眼 |
|---|---|---|---|
| selectionFill | accent 20%-24% | accent 28%-34% | accent 22%-28% |
| selectionHandle | accent solid | 高亮 accent 或浅色 accent | 护眼适配 accent |
| noteHighlight | yellow/amber low alpha | amber desaturated | warm low alpha |
| overlapFill | 独立交叠色 | 独立交叠色 | 独立交叠色 |
| menuBg | surface 96%-98% | surface 88%-94% | warm surface |

验收：

- 深色模式下文字不发灰。
- 选区覆盖笔记后仍能区分当前选区和已有笔记。
- 高亮颜色不能只靠颜色差异表达状态，必要时用边界、透明度、层级辅助。

### 6.6 无障碍要求

基础要求：

- 菜单项必须有可读 label。
- 把手触摸区域满足 44-48dp 目标。
- 把手需要可被 TalkBack 描述为“选区起点/选区终点”。
- 选区创建、清除、复制成功、笔记保存成功等状态应提供轻量反馈。
- 不依赖颜色作为唯一状态表达。

反馈策略：

| 场景 | 反馈 |
|---|---|
| 复制成功 | Toast/Snackbar 短提示 + `HapticFeedbackConstants.CLOCK_TICK` |
| 笔记保存成功 | Toast/Snackbar 短提示，随后清除临时选区 |
| 选区过长 | Toast/Snackbar 轻提示，不打断当前拖拽 |
| 选区清除 | 不弹强提示；TalkBack 模式下可播报状态变化 |
| 外部处理返回 | 默认清除选区，不恢复旧菜单 |

键盘/手柄支持作为低优先级增强：

- 方向键移动焦点到菜单项。
- 返回键优先清除选区。
- 后续可增加键盘微调选区起止位置。

---

## 7. P4：连续滚动模式边缘扩选

### 7.1 设计目标

当用户拖拽选区把手到屏幕顶部或底部边缘时，连续滚动模式应自动滚动正文，并持续扩展选区。

仅建议先支持：

- 连续滚动模式。
- 当前书籍/当前章节已有分页模型能覆盖的范围。
- 本地字符 offset 选区，不跨网络、不跨异步内容加载边界。

### 7.2 Controller 边界

新增概念：`SelectionAutoScrollController`。

职责：

- 监听拖拽点是否进入边缘区域。
- 基于距离边缘的深度计算滚动速度。
- 使用 `Choreographer` 或帧回调驱动滚动。
- 每次滚动后重新命中拖拽点对应字符。
- 拖拽结束、取消、离开边缘区域时停止。
- 提供 `cancel()`：立即停止帧回调，清理速度、边缘方向、最后拖拽点等临时状态。

不放入 `CanvasTouchHandler` 的原因：

- `CanvasTouchHandler` 应只负责事件识别。
- 自动滚动是持续状态机，和页面 delegate、View invalidation、选区命中都有关系。
- 放在触摸处理器里会让手势层耦合滚动和选区模型。

### 7.3 ScrollPageDelegate API

当前连续滚动内部有滚动偏移能力，但需要暴露受控接口。

建议接口语义：

- 输入：本帧 deltaY。
- 输出：是否实际滚动、是否发生页面回收、当前页面/偏移是否变化。
- 限制：只允许在 UI 线程调用。
- 保护：fling 期间进入选区拖拽时先 cancel fling。

### 7.4 自动滚动期间的菜单行为

自动滚动期间不显示选区菜单，避免菜单跟随页面持续跳动。

规则：

| 场景 | 菜单行为 |
|---|---|
| 把手拖拽开始 | 隐藏菜单 |
| 进入边缘自动滚动 | 保持隐藏 |
| 自动滚动停止但手指仍按住 | 保持隐藏 |
| 拖拽松手且选区有效 | 根据最新选区重新计算锚点并显示菜单 |
| 拖拽取消或选区为空 | 不显示菜单 |

验收：

- 自动滚动期间菜单不闪烁、不追随抖动。
- 松手后菜单位置基于最新可见选区计算。
- 页面回收后不使用旧菜单锚点。

### 7.5 边缘区域和速度曲线

建议：

| 区域 | 行为 |
|---|---|
| 顶部 0%-10% | 向上滚动 |
| 底部 90%-100% | 向下滚动 |
| 中间区域 | 停止自动滚动 |

速度：

- 靠近边缘速度更快。
- 最小速度要能稳定推进一行。
- 最大速度不要超过用户可读范围，避免选区跳跃。
- 可使用线性或 ease-in 曲线，先不要做复杂物理模拟。

### 7.6 跨页/跨章限制

不能假设“绝对 Offset 会自然跨页连选”。需要确认选区模型是否支持：

- 当前页之外的字符 offset。
- 多页 selection geometry。
- 页面回收后的 range 保持。
- 跨章 content 加载。

建议 MVP：

1. 先支持连续滚动模式下的同章节跨页选区。
2. 再支持相邻章节自动加载后的跨章选区。
3. 跨章选区需要单独的数据结构和菜单动作校验。

---

## 8. P5：选区与笔记高亮混色

### 8.1 不直接全局 MULTIPLY

直接给 `selectionPaint` 设置 `PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)` 风险较高：

- 可能影响正文抗锯齿观感。
- 可能受硬件加速和离屏 recorder 影响。
- 夜间模式、护眼模式、自定义背景下颜色不可控。
- 与已有色温滤镜叠加后难以预测。

### 8.2 推荐策略

优先级从高到低：

1. **调色策略**  
   笔记高亮使用更低 alpha，选区使用稳定 accent 低 alpha。
2. **交叠区域单独颜色**  
   检测选区和笔记交叠 rect，交叠区域使用专门 token。
3. **隔离图层混合**  
   如果必须 multiply，只在 `saveLayer` 内作用于高亮层，不影响正文、把手和色温滤镜。

### 8.3 测试矩阵

至少覆盖：

- 日间浅色纸张背景。
- 夜间背景。
- 护眼背景。
- 高亮笔记 + 当前选区交叠。
- 查找高亮 + 当前选区交叠。
- 色温滤镜开启/关闭。

---

## 9. 架构边界建议

### 9.1 目标边界

```
CanvasTouchHandler
    只识别手势：长按、拖拽、松手、速度

ReaderCanvasView
    编排状态：选区模型、菜单、动画、invalidate

CanvasTextSelection
    维护选区状态：range、anchor、命中、把手几何

SelectionGeometryCache
    由 ReaderPageRenderer 持有，@MainThread，缓存几何：rect/path/handle/menu anchor

ReaderPageRenderer
    绘制：正文 overlay、高亮、把手、查找结果；管理选区几何缓存

SelectionAutoScrollController
    @MainThread，持续边缘滚动：帧回调、delegate 调用、重新命中

ScrollPageDelegate
    提供受控滚动能力：滚动、回收、状态反馈
```

### 9.2 状态归属

| 状态 | 归属 |
|---|---|
| `SelectionRange` | `CanvasTextSelection` |
| `AnchorId` / active anchor | `CanvasTextSelection` |
| 把手 rect | `CanvasTextSelection` 或 `SelectionGeometryCache` |
| 把手 scale/alpha | `ReaderCanvasView` View 状态 |
| 选区 path | `ReaderPageRenderer` 持有的 `SelectionGeometryCache` |
| 菜单可见性 | `ReaderCanvasView` / 菜单控制器 |
| 自动滚动帧循环 | `SelectionAutoScrollController` |
| 页面偏移 | `ScrollPageDelegate` |

### 9.3 线程模型

当前版本全部选区交互、几何缓存和自动滚动都限定在主线程。

要求：

- `SelectionGeometryCache` 标记为 `@MainThread`，不做内部锁。
- `SelectionAutoScrollController` 标记为 `@MainThread`，所有帧回调、delegate 调用、命中重算都在主线程。
- `ReaderPageRenderer` 访问缓存时不跨线程。
- 如果未来需要后台预计算 selection geometry，必须单独设计不可变输入快照、结果回传和 generation 校验，不能直接复用当前主线程 cache。

### 9.4 事件通信机制

不引入 EventBus。选区链路保持直接、可追踪：

| 方向 | 机制 | 说明 |
|---|---|---|
| `CanvasTouchHandler` → `ReaderCanvasView` | 现有 callbacks | 上报长按、把手拖拽、拖拽结束、速度状态 |
| `ReaderCanvasView` → `CanvasTextSelection` | 直接调用 | 更新 range、active anchor、命中结果 |
| `ReaderCanvasView` → `SelectionAutoScrollController` | 直接调用 | 拖拽开始/移动/结束时启动或停止自动滚动 |
| `SelectionAutoScrollController` → `ScrollPageDelegate` | 受控接口 | 每帧请求滚动，获取页面回收结果 |
| `ReaderCanvasView` → `ReaderPageRenderer` | 绘制参数传递 | renderer 内部命中或重建几何缓存 |
| 选区菜单 → 笔记/字典模块 | 明确 use case / callback | 只传选区文本和 range，不暴露选区内部状态 |

### 9.5 生命周期管理

选区状态跟随 `ReaderCanvasView` 生命周期，不进入持久化层。

要求：

- `ReaderCanvasView` detach 时清除选区、停止自动滚动、取消动画。
- 页面切换或章节切换时清除选区和菜单。
- 阅读设置导致 reflow 时清除选区并失效几何缓存。
- 进入后台时可清除菜单；是否保留选区由产品策略决定，默认清除。
- 自动滚动 controller 必须在拖拽结束、取消、View detach 时停止帧回调。
- 外部文本处理返回后默认清除选区，不恢复旧菜单。
- 页面回收时清除对应 pageKey 的几何缓存。

---

## 10. 分阶段实施计划

### Phase -1：基线测量

任务：

- 建立选区拖拽性能基线。
- 记录当前分配、帧耗时、Path.Op 模式表现。
- 记录手势冲突和边界拖拽问题。
- 建立真机兼容设备清单。
- 增加大字号、大行距、多行选区、放大镜、菜单同时存在的内存压力基线。
- 增加低内存设备或低端机拖拽稳定性基线。

验收：

- 有可复测的性能数据。
- 有可复现的手势和边界问题清单。
- 后续优化可以和基线对比。
- 有大字号 32sp、行距 2.5x、20+ 行选区的内存峰值记录。

### Phase 0：行为正确性

任务：

- 修正坐标到字符索引 clamp。
- 修正行间和边缘 y 命中。
- 修正 x 在行首左侧返回行尾的问题。
- 修正 fast drag 基于 AnchorId 推断方向的问题。
- 修正多行 `isPointInSelection` 包围盒误判。
- 明确空选区松手策略。
- 明确选区最大长度限制和超限后的禁止扩展行为。
- 明确超限时 `moveHandle` 返回当前 range，把手冻结在上限位置。
- 明确点击空白、双击空白、返回键、翻页后的选区取消规则。
- 明确把手拖拽期间的手势优先级。
- 明确复制成功、笔记保存成功、选区过长等轻量反馈。

验收：

- 单行、多行、跨行拖拽稳定。
- 把手交叉后样式和吸附方向正确。
- 空选区不展示可执行菜单。
- 超过最大选区长度后只能缩小，不能继续扩展。
- 翻页、返回、外部点击不会留下半残选区状态。

### Phase 1：低分配绘制

任务：

- 绘制用 `Paint` 成员复用。
- `Path` / `RectF` 成员复用。
- 选区 rect 不创建临时 list。
- 根据基线决定是否把热点循环改为 index-based。
- 查找高亮和笔记高亮减少临时对象。
- 提供 `Path.Op` 和逐 rect 绘制回退开关。
- `Path.op` 返回 false 或检测到绘制异常时自动切换到逐 rect 回退。

验收：

- 拖拽选区时 Allocation Tracker 中主绘制链路接近零分配。
- 没有因复用对象导致的残留状态错误。

### Phase 2：几何缓存

任务：

- Phase 2 前确认或补齐 `SelectionLayoutVersionProvider` / `SelectionStyleVersionProvider`。
- 引入 `SelectionGeometryCache`。
- 限制缓存容量，默认最多 5-10 个 pageKey。
- 定义完整 cache key。
- 接入 layout/style/page 失效。
- Path 只在 range 或 layout 改变时重算。
- 页面回收时清除对应 pageKey 的缓存。
- 当前版本标记为 `@MainThread`，不做后台预计算。

验收：

- 手指微动但字符索引未变时，不重算 Path。
- 字号、边距、翻页、章节切换后不出现旧高亮错位。
- 页面回收、reflow、深色模式切换后不复用旧几何。

### Phase 3：UI 体验

任务：

- active handle scale/alpha。
- 菜单轻量浮岛。
- 菜单项使用 Material Icon + 短文字。
- 菜单按下方、上方、横屏右侧的优先级避让选区和屏幕边缘。
- 菜单 fade + scale 动效，120-150ms。
- 菜单动画支持 cancel：拖拽、选区清除、自动滚动、页面切换时立即中断。
- 把手 spring 参数和 active 放大参数。
- active handle 重叠时绘制在 inactive handle 上层。
- 放大镜位置 clamp，顶部阈值为 `magnifierHeight + 24dp`。
- 深色模式 token。
- 无障碍 label、触控热区、TalkBack 验证。
- 本地优先词典入口，超过 100 字符不展示词典入口，查询 3 秒超时。

验收：

- 拖拽状态清晰。
- 菜单不遮挡把手。
- 小屏和大字体下菜单不挤压错位。
- 深色模式下选区和笔记交叠仍可读。
- TalkBack 可以理解菜单项和选区状态。
- 复制成功和笔记保存成功有轻量反馈。

### Phase 4：边缘自动滚动

任务：

- 新建 `SelectionAutoScrollController`。
- 提供 `cancel()`，立即停止帧回调并清理临时状态。
- `ScrollPageDelegate` 提供受控滚动 API。
- 拖拽进入顶部/底部 10% 区域开始滚动。
- 自动滚动期间隐藏菜单，松手后基于最新选区重新显示。
- 页面回收后重新命中字符并更新 range。

验收：

- 连续滚动模式可跨页扩选。
- 离开边缘区域立即停止。
- 松手、取消、页面切换时不残留帧回调。
- 自动滚动期间菜单不闪烁、不跟随抖动。

### Phase 5：混色实验

任务：

- 先用调色策略解决选区和笔记叠色。
- 如效果不足，再实验隔离图层 multiply。
- 建立夜间/护眼/查找高亮测试矩阵。

验收：

- 文字不发灰。
- 不同背景下选区和笔记都可辨认。
- 不影响色温滤镜。

---

## 11. 测试计划

### 11.1 单元测试

重点覆盖：

- `pixelToChar` 行首左侧、行尾右侧、行间、页面上下边缘。
- `moveHandle` 交叉前后 range 与 visual role。
- fast drag 吸附方向。
- 空选区策略。
- 多行 `isPointInSelection`。

### 11.2 手势测试

场景：

- 长按选中一个词。
- 左把手拖过右把手。
- 右把手拖过左把手。
- 快速拖动和慢速精调切换。
- 拖到屏幕顶部/底部。
- 连续滚动模式边缘扩选。
- 选区拖拽期间尝试翻页。
- 选区拖拽期间触发连续滚动 fling。
- 选区存在时点击空白、双击空白、按返回键。
- 选区菜单打开时点击菜单外区域。
- 选区拖拽中下拉通知栏后取消手势。
- 选区拖拽中应用进入后台再返回。
- 选区菜单打开时收到返回键事件。
- 菜单 fade in / fade out 过程中开始新的拖拽。

### 11.3 视觉回归

截图覆盖：

- 浅色背景。
- 夜间模式。
- 护眼背景。
- 大字号。
- 大行距。
- 多行选区。
- 选区覆盖笔记高亮。
- 菜单贴近顶部/底部/左右边缘。
- 选区恰好在页面第一行时的菜单避让。
- 选区恰好在页面最后一行时的菜单避让。
- 横屏模式下的菜单位置。
- 分屏/多窗口模式下窗口尺寸变化后的选区位置。
- 深色模式 + 当前选区。
- 深色模式 + 笔记高亮 + 当前选区交叠。
- 深色模式 + 查找高亮 + 当前选区交叠。
- 高对比度/大字体设置。

### 11.4 性能验证

工具：

- Android Studio Profiler。
- Frame timing。
- Allocation tracking。
- 手动长选区拖拽压力测试。

指标：

- 拖拽时无明显掉帧。
- 主绘制路径不持续分配。
- 页面回收后无旧 Path 复用。
- 自动滚动停止后无残留 callback。
- 大字号 32sp + 行距 2.5x + 20+ 行选区下无异常内存峰值。
- 低端设备上拖拽不出现持续卡顿。

### 11.5 无障碍验证

覆盖：

- TalkBack 能读出菜单动作。
- TalkBack 能区分选区起点和终点。
- 菜单项 focus 顺序符合视觉顺序。
- 返回键优先清除选区。
- 大字体下菜单不遮挡把手，不出现文字截断。
- 选区状态不只依赖颜色表达。

### 11.6 集成测试

覆盖：

- 选区 → 复制 → 粘贴。
- 选区 → 高亮 → 保存 → 重新打开书籍后查看。
- 选区 → 笔记 → 保存 → 笔记列表/正文标记同步。
- 选区 → 本地词典查询 → 返回阅读器。
- 无本地词典 → fallback 到复制/外部处理。
- 本地词典返回空结果 → 显示空态，不阻塞菜单。
- 本地词典查询超过 3 秒 → 显示超时态，菜单保持可操作。
- 选区 → 外部文本处理 → 取消/返回后阅读器状态正确。
- 翻页后选区按生命周期规则清除。

### 11.7 动态主题测试

覆盖：

- 选区存在时切换深色模式。
- 菜单打开时切换深色模式。
- 放大镜显示时切换深色模式。
- 选区覆盖笔记时切换护眼/夜间主题。
- 动态主题切换后 `styleVersion` 递增并失效旧几何。

---

## 12. 风险与回滚

| 风险 | 影响 | 应对 |
|---|---|---|
| 几何缓存 key 不完整 | 高亮错位 | 先不开启缓存，完成失效测试后再接入 |
| 边缘自动滚动抢手势 | 影响正常翻页/滚动 | 仅在把手拖拽中启用，结束立即停 |
| 复用对象残留状态 | 绘制异常 | 每次使用前 reset/set，增加 debug 断言 |
| multiply 混色不可控 | 文字发灰或颜色脏 | 默认不用 multiply，放到实验阶段 |
| 动效过度 | 阅读干扰 | 动效短、轻、只在交互中出现 |
| 跨章选区复杂度高 | 范围、菜单动作不稳定 | MVP 只做同章节跨页，跨章另设阶段 |
| Path.Op 设备兼容问题 | 圆角融合异常或闪烁 | 保留逐 rect 绘制回退开关 |
| 深色模式 token 不完整 | 高亮不可读或文字发灰 | Phase 3 前补齐色彩矩阵 |
| 外部文本处理返回异常 | 菜单状态残留 | 外部跳转前保存轻量状态，返回后按生命周期规则恢复或清除 |
| TalkBack 操作不可达 | 无障碍体验失败 | Phase 3 加入 TalkBack 验收 |
| layout/style version 缺失 | 缓存无法可靠失效 | Phase 2 前先建立 version provider |
| 超长选区导致卡顿 | 绘制、复制、笔记处理成本过高 | Phase 0 增加最大选区长度限制 |
| 自动滚动期间菜单抖动 | UI 干扰拖拽 | 自动滚动期间隐藏菜单，松手后重新计算 |
| 后台预计算误用主线程缓存 | 线程安全问题 | 当前 cache 标记 `@MainThread`，后台预计算另设方案 |
| 菜单动画竞态 | 菜单停在旧位置或旧透明度 | 拖拽、选区清除、页面切换时 cancel 动画并以最新状态重建 |
| 几何缓存无上限 | 连续滚动长时间选择导致内存增长 | 默认限制 5-10 个 pageKey，页面回收时清理 |
| 词典查询阻塞 | 菜单等待过久 | 本地查询 3 秒超时，显示失败态并保持菜单可操作 |
| 分屏/多窗口尺寸变化 | 选区和菜单错位 | 窗口尺寸变化递增 layoutVersion 并清除旧菜单锚点 |

---

## 13. 最终建议

这次优化应从“终极重构”调整为“可验证的连续交付”：

| 优先级 | 阶段 | 内容 |
|---|---|---|
| P-1 | Phase -1 | 建立性能、行为、设备兼容、内存压力、大字号基线 |
| P0 | Phase 0 + Phase 1 | 正确性 + 低分配绘制，增加选区长度限制 |
| P1 | Phase 2 | 几何缓存，先确认 layout/style version 机制 |
| P2 | Phase 3 | 把手动效参数、菜单图标、词典入口、无障碍、深色模式 |
| P3 | Phase 4 | 连续滚动边缘扩选，自动滚动期间隐藏菜单 |
| P4 | Phase 5 | 混色实验和高级视觉效果 |

执行建议：

1. 先测基线，再优化，不做无指标的性能重构。
2. Phase 0 + Phase 1 合并实施，优先解决用户能感知的拖拽正确性和明显分配问题。
3. Phase 0 增加选区长度限制，避免超长选区拖垮绘制和菜单动作。
4. Phase 2 前确认或补齐 layout/style version provider，否则不启用几何缓存。
5. `SelectionGeometryCache` 由 `ReaderPageRenderer` 持有，标记 `@MainThread`，限制 5-10 个 pageKey，避免 View 层缓存状态扩散。
6. `Path.Op` 默认启用但必须有自动回退，保证设备兼容。
7. 选区菜单坚持本地优先：复制、笔记、高亮、书内搜索、本地词典是主路径，外部处理是显式补充。
8. Phase 3 补齐菜单图标、把手动效参数、动画取消、放大镜边界阈值、深色模式、无障碍和手势冲突验收。
9. Phase 4 自动滚动期间隐藏菜单，松手后基于最新选区重新计算位置。
10. 边缘扩选只在连续滚动模式先做同章节 MVP，跨章选区另设阶段。

基础选区是阅读器的核心交互，应保持本地、快速、可预测。菜单可以扩展，但基础动作必须优先依赖本地能力，不能让外部服务成为阅读流程的前置条件。

---

## 14. 官方参考资料

本方案修订参考以下官方资料：

- Android Developers - Optimize a custom view：`onDraw()` 是高收益优化点，动画运行期间应避免分配对象。  
  https://developer.android.com/develop/ui/views/layout/custom-views/optimizing-view
- Android Developers - Slow rendering：Android UI 渲染包含 UI 线程 `View#draw` 记录阶段和 RenderThread `DrawFrame` 阶段。  
  https://developer.android.com/topic/performance/vitals/render
- Android Developers - Inspect GPU rendering speed：Profile GPU Rendering 用柱状图展示每帧渲染耗时，可用于定位渲染瓶颈。  
  https://developer.android.com/topic/performance/rendering/inspect-gpu-rendering
- Android Developers - Analyze with Profile GPU Rendering：解释渲染管线各阶段耗时，适合建立优化前后基线。  
  https://developer.android.com/topic/performance/rendering/profile-gpu
- Android Developers - Manage touch events in a ViewGroup：`requestDisallowInterceptTouchEvent()` 可用于请求父容器不要拦截触摸事件。  
  https://developer.android.com/develop/ui/views/touch-and-input/gestures/viewgroup
- Android Developers - Intent `ACTION_PROCESS_TEXT`：系统文本处理 Intent，可作为外部文本处理的显式补充入口。  
  https://developer.android.com/reference/android/content/Intent#ACTION_PROCESS_TEXT
- Android Developers - Hardware acceleration：硬件加速会影响 2D 绘制管线和资源成本，复杂混合应谨慎验证。  
  https://developer.android.com/develop/ui/views/graphics/hardware-accel
- Android Developers - Drag and scale：自定义 View 拖拽手势可通过 `onTouchEvent()` 处理。  
  https://developer.android.com/develop/ui/views/touch-and-input/gestures/scale
- Android Developers - `Path.op`：官方 Path 布尔运算 API，返回值可作为失败回退依据。  
  https://developer.android.com/reference/android/graphics/Path#op(android.graphics.Path,%20android.graphics.Path.Op)
- Android Developers - `Animator.cancel()`：动画取消会通知 cancel/end 监听，菜单动画打断需按最新状态重建。  
  https://developer.android.com/reference/android/animation/Animator#cancel()
- Android Developers - `HapticFeedbackConstants`：系统触觉反馈常量，适合表达复制成功等轻量操作反馈。  
  https://developer.android.com/reference/android/view/HapticFeedbackConstants
- Android Developers - Multi-window support：窗口尺寸可能动态变化，选区几何和菜单锚点必须随窗口更新失效。  
  https://developer.android.com/develop/ui/views/layout/support-multi-window-mode
