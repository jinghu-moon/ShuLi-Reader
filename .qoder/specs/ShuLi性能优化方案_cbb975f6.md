# ShuLi-Reader 性能优化方案

## Context

ShuLi-Reader 是一款面向小说重度用户的沉浸式本地阅读器，核心支持 100MB+ 大文件流畅阅读。项目已完成渲染引擎重构（CanvasRecorder 缓存方案，内存 -99%）和分页器优化（区间模型，零分配），架构设计优秀。但在 UI 层细粒度控制、数据流效率、EPUB 解析和启动路径上仍存在可观的优化空间。项目处于快速开发期，允许破坏性改动和重构。

---

## Task 1: 渲染路径热点优化 (P0 - 最高优先级)

**目标**: 消除翻页和绘制过程中的无效开销

### 1.1 AndroidView update 块拆分重构

**文件**: `app/src/main/java/com/shuli/reader/feature/reader/ReaderScreen.kt`

**问题**: `update = { view -> ... }` 中 20+ setter 在任意 uiState 字段变化时全量执行。翻页时仅 pageIndex 改变，但 `setThemeColors()`、`setFontFamily()`、`setPageDelegate()` 等全部重新调用。

**方案**: 
- 将 update 内的操作按变化频率拆分为 **高频**（pageIndex → setPage）和 **低频**（theme/font/delegate）两组
- 低频操作通过 `LaunchedEffect` + `derivedStateOf` 监听，仅在对应参数实际变化时触发
- 高频操作保留在 update 块中，但添加条件守卫

**预期收益**: 翻页时减少 15-20 个无效 setter 调用，消除每帧的 ResourcesCompat.getFont() 和中间对象分配

### 1.2 两端对齐绘制优化 (JNI 调用减半)

**文件**: `app/src/main/java/com/shuli/reader/core/reader/ReaderPageRenderer.kt`

**问题**: 两端对齐时逐字符调用 `canvas.drawText(char.toString(), x, y, paint)`，每字符产生：1 次 String 分配 + 1 次 JNI drawText 调用。一页 ~2000 字 = 4000 次 JNI 调用。

**方案**:
- 预计算每字符的 x 坐标存入 `FloatArray`
- 使用 `canvas.drawText(content, startOffset, endOffset, x, y, paint)` 配合 `Paint.setLetterSpacing()` 或分段绘制（相邻字符间距相同时合并为一次 drawText 调用）
- 利用已有的 `TextLine.charWidths: FloatArray` 数据

**预期收益**: JNI 调用 2N → ~N/5（合并相邻等距字符），GC 压力 -40%

### 1.3 Paint 对象复用

**文件**: `ReaderPageRenderer.kt` 的 `drawBattery()` 方法

**问题**: 每帧创建 3 个 Paint 对象（电池框、电池头、电量填充）

**方案**: 提升为类级别的 `companion object` 或实例成员，复用 Paint 配置

**预期收益**: 消除每帧 3 个对象分配

---

## Task 2: 数据流架构重构 (P0)

**目标**: 消除参数变化导致的不必要 reflow 和重组

### 2.1 ViewModel Flow 分组

**文件**: `app/src/main/java/com/shuli/reader/feature/reader/ReaderViewModel.kt`

**问题**: 30+ preference flows 通过单一 `combine` 合并，导致任何参数变化（如亮度调节）都触发整章 reflow（重新分页）。

**方案**: 按影响范围分为三组独立的 combine：
```
Group A (layoutFlows): fontSize, lineSpacing, paragraphSpacing, indent, margin → 触发 reflow
Group B (visualFlows): fontFamily, fontWeight, theme, textColor → 仅触发重绘 (invalidate)
Group C (behaviorFlows): brightness, keepScreenOn, volume → 仅更新窗口属性
```
每组独立 `collectLatest`，互不干扰。

**预期收益**: 亮度/音量调节不再触发 reflow（从 ~500ms 耗时降为 <1ms），字体切换仅重绘不重分页

### 2.2 ReaderUiState 拆分

**文件**: `ReaderViewModel.kt` + `ReaderScreen.kt`

**问题**: `ReaderUiState` 含 20+ 字段，单字段变化触发整个 Compose 子树重组

**方案**: 
- 拆分为 `PageState`（当前页/章节数据）、`LayoutState`（排版参数）、`VisualState`（主题/字体）、`BehaviorState`（亮度/屏幕常亮）
- ReaderScreen 按需订阅各子 state，缩小重组范围

**预期收益**: 翻页时仅 PageState 变化 → 只有页面显示区域重组，工具栏/页眉/进度条不受影响

### 2.3 BookshelfViewModel combine 优化

**文件**: `app/src/main/java/com/shuli/reader/feature/bookshelf/BookshelfViewModel.kt`

**问题**: 13 个 Flow combine，搜索/排序/筛选每次变化都重建整个列表状态

**方案**: 将搜索、排序、筛选各自独立为 StateFlow，使用 `flatMapLatest` 链式组合，减少不必要的重新计算

---

## Task 3: EPUB 解析优化 (P1)

**目标**: 减少 EPUB 打开和章节加载耗时

### 3.1 JSoup 替代方案

**文件**: `app/src/main/java/com/shuli/reader/core/parser/EpubParser.kt`

**问题**: 
- `extractChapterTitle()` 对每章 HTML 调用完整 `Jsoup.parse()`，50-100KB HTML 耗时 30-100ms
- 100+ 章节全书索引耗时 500ms-2s
- nav.xhtml 重复解析（parseMetadata 和 parseChapterIndex 各解析一次）

**方案**:
1. **标题提取**: 改用正则或轻量级 SAX 解析，仅提取 `<h1>`-`<h6>` 或 `<title>` 标签
2. **nav.xhtml 缓存**: 首次解析后缓存结果，全局共享
3. **ZipEntry 目录缓存**: 构建 `Map<path, ZipEntry>` 避免每章重复查询 ZIP 目录

**预期收益**: 全书章节索引从 500ms-2s 降至 100-300ms

### 3.2 OPF 解析去重

**问题**: `parseMetadata()` 和 `parseChapterIndex()` 都调用 `findOpfPath()` 重复读取

**方案**: 统一解析入口，首次解析后缓存 OPF DOM 结构

---

## Task 4: 数据库与缓存优化 (P1)

### 4.1 添加缺失索引

**文件**: `app/src/main/java/com/shuli/reader/core/database/`

**问题**: `books.filePath` 缺少唯一索引，书籍查重时触发全表扫描

**方案**: 添加 `@Index(value = ["filePath"], unique = true)` 到 BookEntity

### 4.2 缓存 LRU 改为字节计量

**文件**: `app/src/main/java/com/shuli/reader/core/reader/CacheManager.kt`

**问题**: 当前 LRU 基于条目数（4-16 章节），但章节大小差异巨大（1KB 短章 vs 100KB 长章），可能导致大章节挤占所有缓存槽位

**方案**: 改为基于字节大小的 LRU，`maxSize = Runtime.maxMemory() * 0.05`，每个 TextChapter 报告实际内存占用

### 4.3 相邻章节预加载

**问题**: 当前仅缓存已访问章节，快速翻到新章节时有 200-500ms 等待

**方案**: 
- 翻页到最后 2 页时，后台预加载下一章节（解析 + 分页）
- 维持 current ± 1 章节始终在缓存中
- 使用低优先级协程避免影响当前渲染

---

## Task 5: 启动性能优化 (P1)

### 5.1 数据库异步初始化

**文件**: `app/src/main/java/com/shuli/reader/core/ShuLiAppContainer.kt`

**问题**: Database + DataStore 首次访问在主线程同步等待 ~200-250ms

**方案**: 
- Application.onCreate() 中立即在 IO 线程触发 database 和 DataStore 初始化
- 使用 `CompletableDeferred` 暴露初始化完成信号
- UI 层首次使用数据时 await（通常此时已初始化完成）

**预期收益**: 冷启动减少 100-150ms 主线程阻塞

### 5.2 迁移链优化

**问题**: 13 个 Room migration，首次升级逐个执行

**方案**: 合并相邻无数据变更的迁移为复合迁移；对于新安装用户，使用 `createFromAsset()` 直接提供最终 schema

---

## Task 6: 构建与编译优化 (P2)

### 6.1 ProGuard 规则补完

**文件**: `app/proguard-rules.pro`

**问题**: 缺少 Coil、OkHttp、Zstd-JNI 的 keep 规则，Release 包可能反射失败

**方案**: 添加必要的 `-keep` 规则

### 6.2 Compose 编译器报告

**方案**: 启用 Compose compiler metrics（`-Pandroidx.compose.compiler.reports=true`），分析 Stability 报告，标记不稳定类添加 `@Stable`/`@Immutable` 注解

### 6.3 ABI 分割优化

**当前**: 已有 per-ABI split，良好。可进一步考虑移除 `armeabi-v7a`（minSdk 31 = Android 12，几乎全是 arm64 设备）

---

## Task 7: 代码级微优化 (P2)

### 7.1 高亮循环 measureText 去重

**问题**: 高亮渲染时对同一行文本重复调用 `paint.measureText()`

**方案**: 利用已有的 `TextLine.charWidths` 累加计算高亮区域宽度，无需重新测量

### 7.2 themeColors 缓存

**问题**: `themeColors` 派生属性每次访问重新计算

**方案**: 使用 `lazy` 或在 theme 变化时重算并缓存

### 7.3 GlobalScope 替换

**文件**: `BookRepository.kt`

**问题**: 后台索引使用 `GlobalScope`，不受生命周期控制，存在泄漏风险

**方案**: 替换为 Application 级别的 `applicationScope` 或 `ProcessLifecycleOwner.lifecycleScope`

### 7.4 书签/笔记分页加载

**问题**: 一次性加载全部书签/笔记，数据量大时列表卡顿

**方案**: 使用 Room 的 `PagingSource` + Compose `LazyPagingItems`

---

## 优先级总结

| 优先级 | Task | 预期收益 | 工作量 |
|--------|------|---------|--------|
| **P0** | Task 1.1 AndroidView update 拆分 | 翻页减少 15-20 无效调用 | 4-6h |
| **P0** | Task 2.1 Flow 分组 | 非排版操作不触发 reflow | 8-12h |
| **P0** | Task 2.2 UiState 拆分 | 缩小重组范围 50%+ | 8-12h |
| **P0** | Task 1.2 两端对齐优化 | JNI 调用 -80%, GC -40% | 12-16h |
| **P1** | Task 3.1 EPUB JSoup 替代 | 章节索引 -60% 耗时 | 6-8h |
| **P1** | Task 4.1 数据库索引 | 书籍查重从全表扫描→索引命中 | 1h |
| **P1** | Task 4.3 相邻章节预加载 | 章节切换零等待 | 4-6h |
| **P1** | Task 5.1 异步初始化 | 冷启动 -100-150ms | 4-6h |
| **P2** | Task 6.1-6.3 构建优化 | 包大小优化、Release 稳定性 | 2-4h |
| **P2** | Task 7.1-7.4 微优化 | 细节打磨 | 4-8h |

---

## 验证方案

### 性能基准测试
```bash
# 运行现有 macrobenchmark
./gradlew.bat :benchmark:connectedDebugAndroidTest

# 关注指标:
# - coldStartup: 目标 < 1800ms
# - continuousPaging (50次翻页): P99 帧时间 < 16.7ms
# - 100MB 文件导入: < 30s
```

### Compose 稳定性检查
```bash
# 生成 Compose compiler metrics
./gradlew.bat :app:assembleRelease -Pandroidx.compose.compiler.reports=true
# 检查 app/build/compose_metrics/ 下的 stability 报告
```

### 内存验证
- Android Studio Memory Profiler 监控阅读 10 分钟内存趋势
- 验证无持续增长（GC 后恢复基线）
- 目标: 阅读态 PSS < 150MB

### 帧率验证
- `adb shell dumpsys gfxinfo com.shuli.reader` 获取帧时间分布
- 翻页动画 P99 < 16ms（60fps）
- 连续快速翻页 50 次无掉帧
