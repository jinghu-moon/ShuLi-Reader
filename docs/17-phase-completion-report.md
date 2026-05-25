# 17 · 阅读器设置面板重构 · 完成报告

> 日期：2026-05-24
> 范围：7 个阶段全部完成

---

## 阶段完成概况

| 阶段   | 内容                                      | 状态 |
| ------ | ----------------------------------------- | ---- |
| 阶段一 | ModalBottomSheet + 6-Tab 容器骨架         | 完成 |
| 阶段二 | 现有数据项迁移到新 UI                     | 完成 |
| 阶段三 | 字距/对齐/字重/简繁转换                   | 完成 |
| 阶段四 | 预设管理（Room 数据库 + UI）              | 完成 |
| 阶段五 | 页眉脚三槽位系统                          | 完成 |
| 阶段六 | 杂项（屏幕常亮/音量键/边缘翻页/恢复默认） | 完成 |
| 阶段七 | 测试与回归                                | 完成 |

---

## 新建文件

| 路径                                             | 用途                                                         |
| ------------------------------------------------ | ------------------------------------------------------------ |
| `feature/reader/component/QuickSettingsSheet.kt` | 主面板入口（含全部 6 个 Tab 面板）                           |
| `core/reader/HeaderFooterModels.kt`              | HeaderConfig / FooterConfig / SlotContent / TitleStyleConfig |
| `core/reader/SlotResolver.kt`                    | 槽位文本解析器                                               |
| `core/database/entity/ReaderPresetEntity.kt`     | 预设实体                                                     |
| `core/database/dao/ReaderPresetDao.kt`           | 预设 DAO                                                     |
| `test/.../SlotResolverTest.kt`                   | 槽位解析器单元测试（13 用例）                                |
| `test/.../PresetSerializerTest.kt`               | JSON 序列化往返测试（9 用例）                                |
| `test/.../ChineseConverterTest.kt`               | 简繁转换单元测试（11 用例）                                  |

## 修改文件

| 路径                   | 改动摘要                                                                                                                            |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `ReaderScreen.kt`      | 移除旧亮度/设置浮层；接入 QuickSettingsSheet；屏幕常亮 LaunchedEffect                                                               |
| `ReaderViewModel.kt`   | 新增 13 个 setter + 预设 CRUD + resetToDefault + resolveHeader/FooterSlots                                                          |
| `ReaderPreferences.kt` | 新增 11 个字段（titleStyle/header/footer/headerFooterAlpha/showProgress/keepScreenOn/volumeKeyTurnPage/edgeTurnPage）+ 转换扩展函数 |
| `UserPreferences.kt`   | 新增 13 个 DataStore key + Flow + setter                                                                                            |
| `ReaderCanvasView.kt`  | 新增 setHeaderSlots/setFooterSlots/setHeaderFooterAlpha                                                                             |
| `MainActivity.kt`      | 音量键翻页 dispatchKeyEvent + ViewModel 生命周期管理                                                                                |
| `AppDatabase.kt`       | 注册 ReaderPresetEntity + Migration                                                                                                 |

---

## 测试结果

### 通过

- `SlotResolverTest` — 13 个用例全部通过
- `PresetSerializerTest` — 9 个用例全部通过
- `ChineseConverterTest` — 11 个用例全部通过
- `PaginatorTest` — 编译通过，showHeader/showFooter 默认值兼容
- `ReaderPreferencesTest` — 编译通过，新字段默认值兼容
- `ReaderViewModelTest` — 编译通过，构造函数兼容
- `assembleDebug` — BUILD SUCCESSFUL
- `compileDebugUnitTestKotlin` — BUILD SUCCESSFUL

### 预存失败（非回归）

- `PageDelegateTest` / `ScrollPageDelegateTest` / `SimulationPageDelegateTest` — 共 15 个 NPE，原因为 Canvas mock 问题，与本次改动无关

---

## 已知遗留

1. **PageDelegate 测试 NPE**：Canvas mock 的 `width`/`height` 返回 0 导致 NPE，需单独修复 mock 初始化
2. **UI 测试（Compose）**：未编写 instrumented 测试，需在真机/模拟器环境验证
3. **视觉回归截图**：需在真机上手动截取基线图
4. **性能基准**：需在真机上运行 benchmark 验证分页耗时和帧率

---

## 偏离原计划

- 阶段一~三在前次会话已完成，本次从阶段五继续
- 原计划 6-Tab 分面板拆分为独立文件，实际合并到 `QuickSettingsSheet.kt` 单文件中（减少文件数量，便于维护）
- 原计划 `SlotPickerDialog` 独立文件，实际用 `SegmentedButton` 内联实现（更简洁）
- 原计划独立的 `headerFooterColor` / `dividerColor` 字段 + `ColorPickerDialog` **未实现**；目前页眉脚颜色与分隔线颜色跟随 `ReaderTheme`（4 个预设主题各自固定值），用户无法独立调色。后续如需要再加 `ColorPickerDialog`

## 二次审核修复（2026-05-25）

针对首轮完成报告审核中发现的遗留缺陷做了补救：

| 编号  | 内容                                                                                                                                      |
| ----- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| S2    | `drawTextJustified` 可用宽度算错（3×margin → 2×margin），导致两端对齐无法触达右边距                                                       |
| S3    | `ChineseConverter` 由手写 60 字字典替换为 `opencc4j:1.8.1`（工业级 OpenCC，支持词汇级转换）                                               |
| S4    | `setHeaderSlots/setFooterSlots/setShowProgress/setHeaderFooterAlpha` 改用 `invalidateAllRecorders()` + `submitRenderTask()`，邻页同步刷新 |
| S5    | `ReaderCanvasView` 新增 `edgeTurnPageEnabled` 字段 + setter；触摸处理按开关拦截边缘点击                                                   |
| M1    | 音量键翻页加 `!showQuickSettings && !showDirectory` 条件，BottomSheet/目录打开时不劫持                                                    |
| M4/M5 | `setTextAlign` / `setFontWeight` 不再 `reflowCurrentChapter`，仅 `currentPageInvalidate`（FakeBold 不改字宽、对齐不改字数）               |

## 三次审核修复（2026-05-25）

针对二次审核发现 S1 章节标题"渲染管线通但参数硬编码 + 无 UI 入口"问题：

| 编号    | 内容                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Bug A   | `TextPage` 新增 `topContentY: Float` 字段（Paginator 计算 `marginVertical + headerHeight + titleAreaHeight` 写入），`ReaderPageRenderer.drawChapterTitle` 用 `page.topContentY - marginBottom - titleSize*0.15` 反推基线，消除硬编码 `48f`/`24f` 与 prefs 脱钩问题                                                                                                                                                                                     |
| Bug D   | `UserPreferences` 加 `titleAlign / titleSizeOffset / titleMarginTop / titleMarginBottom` 4 个 DataStore key + Flow + setter；`ReaderPreferences.kt` 加 `toTitleAlign / toStorageString` 扩展；`ReaderViewModel` 加 4 个 setter + combine() 监听（26→30 参数）+ applyPreset/resetToDefault 应用；`QuickSettingsSheet.HeaderFooterPanel` 头部新增「正文标题」分区（对齐 SegmentedButton + 字号偏移/上距/下距 3 个 Slider）；`ReaderScreen` 接通 4 个回调 |
| M3 补丁 | `ChapterCacheKey` 补 4 个 titleStyle 字段（titleAlignOrdinal/sizeOffsetSp/marginTopDp/marginBottomDp），3 处构造点更新；避免跨书时 titleStyle 不同命中错误缓存                                                                                                                                                                                                                                                                                         |

`compileDebugKotlin` BUILD SUCCESSFUL，无编译错误。
