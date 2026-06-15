# 31 - 项目文件树结构重构方案

> 编写时间：2026-06-15  
> 范围：`app/src/main/java/com/shuli/reader`  
> 前提：项目处于鼓励重构阶段，允许包名、文件名、类名调整；调整后必须同步修改所有 import 与调用点。  
> 核心原则：SRP / KISS / DRY / YAGNI，优先消除职责混杂和历史命名负担。

---

## 0. 结论

当前顶层结构 `core / feature / sync / ui` 可以保留，真正需要重构的是第二层边界：

- `feature/reader` 是最大复杂度中心，应优先拆分。
- `ui/` 中存在具体业务页面，应逐步迁回所属 feature 或 sync。
- `core/reader` 与 `core/canvasrecorder` 边界可以更清晰，但不应早于 `feature/reader`。
- `core/sync` 与顶层 `sync/` 并存，是同步域的遗留边界；第一阶段只标记与审计，后续合并到 `sync/network/webdav` 或废弃。
- 旧 quicksettings 仍被 `QuickSettingsSheet.kt` 引用，但其入口受 `SETTINGS_PANEL_V5_ENABLED` 控制；若确认默认启用 v5 且不再需要回退，应作为 P0.5 死代码直接删除。
- `ReaderSessionState.kt` 当前只命中自身，疑似死代码，应在 P0.5 纳入删除评估。
- 当前树中未发现 `HeaderFooterWireframe.kt`，清理任务中不再列为待删除项。

推荐路线：

1. **P0：建立命名与移动规则**
2. **P0.5：删除旧 quicksettings 与已完成 feature flag**
3. **P1：分批拆 `feature/reader/screen`，ReaderViewModel 最后移动**
4. **P2：拆 `feature/reader/session`**
5. **P3：收敛 `feature/reader/settings` 逻辑，不过度细分**
6. **P4：迁移 quicksettings v5 到 reader settings panel，并去掉 V5 命名**
7. **P5：收敛跨 feature 的 `ui/` 业务页面到 `feature/*`，不放入 `sync/ui`**
8. **P5.5：补 `core/data` 职责说明**
9. **P6：重命名 `core/canvasrecorder` 为 `core/recorder`**
10. **P7：重组 `core/reader` 引擎结构**
11. **P8：压缩 `sync` 过细包结构**

---

## 1. 当前结构诊断

### 1.1 顶层结构

```text
com/shuli/reader/
├── core/
├── feature/
├── sync/
├── ui/
├── MainActivity.kt
└── ShuLiApplication.kt
```

顶层结构是合理的：`core` 放平台内核与领域基础设施，`feature` 放业务功能，`sync` 放同步域，`ui` 放跨业务通用 UI。

问题在于：当前 `ui` 中放了业务页面，`feature/reader` 内部过重，`core` 的 reader 引擎、通用 recorder、文本工具边界不够明确。

### 1.2 `feature/reader` 问题

当前 `feature/reader` 根目录混合了：

- 屏幕入口：`ReaderScreen.kt`
- 状态与意图：`ReaderUiState.kt`、`ReaderIntent.kt`
- ViewModel：`ReaderViewModel.kt`
- 会话管理：`BookSessionManager.kt`
- 分页协调：`ChapterPaginationCoordinator.kt`
- 导航协调：`ReaderNavigationCoordinator.kt`
- 设置管理：`ReaderSettingsManager.kt`、`ReaderPreferenceMonitor.kt`
- 搜索与书签：`ReaderSearchManager.kt`、`BookmarkNotesManager.kt`
- 渲染编排：`render/`
- 浮层：`overlays/`
- 快捷设置 UI：`component/quicksettings/` 与 `component/quicksettings/v5/`

这是典型的 feature 内部职责平铺。后续继续做阅读器功能时，所有改动都会向根目录聚集。

### 1.3 `component/quicksettings` 问题

当前旧版 quicksettings 仍被引用：

```text
feature/reader/component/QuickSettingsSheet.kt
├── imports quicksettings.InteractionPanel
├── imports quicksettings.LayoutPanel
├── imports quicksettings.StylePanel
└── imports quicksettings.SettingsPanel
```

旧版文件仍在编译链路中，因此**不能只删除文件而不修改入口**：

```text
feature/reader/component/quicksettings/
├── HeaderFooterCustomizationPanel.kt
├── InteractionPanel.kt
├── LayoutPanel.kt
├── SettingsPanel.kt
├── SharedComponents.kt
└── StylePanel.kt
```

但 v5 已经形成新体系：

```text
feature/reader/component/quicksettings/v5/
├── controls/
├── tabs/
├── SettingsPanelV5.kt
├── SettingsPanelV5Modal.kt
├── SettingsCardV5.kt
├── SettingRow.kt
├── SlotMatrix.kt
└── ...
```

建议在 P0.5 中直接完成三件事：

1. 删除旧入口 `QuickSettingsSheet.kt`。
2. 删除旧版 `component/quicksettings/` 根目录文件。
3. 删除 `ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED` 及 `ReaderOverlayPanels.kt` 中的旧版分支，只保留 v5 分支。

这样避免新增 `legacyquicksettings` 目录，也避免长期维护两套设置面板。

### 1.4 `ui/` 问题

当前 `ui/` 下有多组业务页面：

```text
ui/
├── settings/sync/
├── settings/crypto/
├── devices/
├── log/
├── conflict/
├── export/
├── testing/
└── theme/
```

其中只有 `theme/`、`testing/` 明确是跨 feature 基础设施。其它目录更像具体业务 UI：

- `ui/settings/sync` 属于设置 feature 的同步设置页
- `ui/settings/crypto` 属于设置 feature 的加密设置页
- `ui/conflict` 属于 sync 冲突解决 UI
- `ui/devices` 属于 sync 设备管理 UI
- `ui/log` 属于 sync 日志 UI
- `ui/export` 属于 bookshelf / backup 导出 UI

同时，`feature/sync` 当前不存在。把同步 UI 迁出 `ui/` 不是简单移动目录，还需要新建 sync feature 的入口、导航接入点和 ViewModel 归属规则。

### 1.5 `core` 问题

当前 `core` 混合了多个层级：

```text
core/
├── canvasrecorder/
├── reader/
│   ├── animation/
│   ├── cache/
│   ├── canvas/
│   ├── layout/
│   ├── model/
│   └── text/
├── text/
├── data/
├── database/
├── repository/
├── parser/
├── font/
├── theme/
├── tts/
└── ...
```

主要问题：

- `canvasrecorder` 是通用绘制录制工具，不应和 reader 语义绑定。
- `core/text` 与 `core/reader/text` 容易混淆。前者应是通用文本处理，后者应是阅读排版文本算法。
- `core/theme` 与 `ui/theme` 并存。应明确：core 只存主题数据/偏好，ui 负责 Material 映射。
- `data / database / repository` 平铺，长期会让数据层入口不清。
- `core/sync/WebDavClient.kt`、`core/sync/WebDavSyncManager.kt` 与顶层 `sync/network/webdav`、`sync/transport` 并存，是旧 WebDAV 同步层与新同步子系统的边界问题。
- `core/reading/ReadingStatus.kt` 虽然目录很薄，但当前被 bookshelf、reader、stats、repository 多处使用，不作为第一阶段死代码处理。执行前运行以下命令建立调用点清单，作为后期迁入 `core/data/model` 的附件：

```powershell
rg -l "ReadingStatus" app/src/main/java | Tee-Object docs/31-readingstatus-callers.txt
```

`core/sync` 当前引用点包括 `core/ShuLiAppContainer.kt`、`ui/settings/sync/CloudSyncSettingsViewModel.kt`、`core/sync` 单测和 WebDAV androidTest。后续迁移必须同步这些入口。

---

## 2. 命名与移动规则

### 2.1 包名规则

包路径必须反映职责，不保留历史路径：

```text
screen      页面入口、ViewModel、UiState、Intent
session     阅读会话、章节打开、分页协调、导航协调
settings    阅读设置逻辑与设置域模型
panel       设置面板 UI
render      ReaderCanvas 的 snapshot/diff/orchestrator
engine      core reader 引擎
recorder    通用 CanvasRecorder 封装
```

### 2.2 文件命名规则

允许重命名文件和类，但必须同步 import。命名偏向“职责名”，减少历史版本号。

建议规则：

| 类型 | 命名 |
|---|---|
| Compose 页面 | `*Screen.kt` |
| Compose 面板 | `*Panel.kt` |
| Compose 弹窗 | `*Dialog.kt` / `*Modal.kt` |
| Compose 控件 | `*Control.kt` 或稳定品牌前缀 `Ink*` |
| 状态 | `*UiState.kt` |
| 意图 | `*Intent.kt` |
| 协调器 | `*Coordinator.kt` |
| 管理器 | `*Manager.kt` |
| 映射器 | `*Mapper.kt` |
| 工厂 | `*Factory.kt` |
| 快照 | `*Snapshot.kt` |

### 2.3 版本号规则

重构后不建议保留 `V5` 在正式目录中。`V5` 是迁移阶段信息，不是业务职责。

推荐：

| 当前文件 | 目标文件 |
|---|---|
| `SettingsPanelV5.kt` | `ReaderSettingsPanel.kt` |
| `SettingsPanelV5Modal.kt` | `ReaderSettingsModal.kt` |
| `SettingsCardV5.kt` | `ReaderSettingsCard.kt` |
| `SettingsPeekContent.kt` | `ReaderSettingsPeek.kt` |
| `SettingRow.kt` | `ReaderSettingRow.kt` |

若实际执行前被要求临时保留旧版，才使用 `legacy` 包表达历史状态；默认路线仍是 P0.5 直接删除旧 quicksettings。

---

## 3. 目标结构

### 3.1 顶层目标

```text
com/shuli/reader/
├── core/
├── feature/
├── sync/
├── ui/
├── MainActivity.kt
└── ShuLiApplication.kt
```

顶层不变，降低一次性迁移成本。

### 3.2 `feature/reader` 目标结构

```text
feature/reader/
├── screen/
│   ├── ReaderScreen.kt
│   ├── ReaderViewModel.kt
│   ├── ReaderUiState.kt
│   ├── ReaderIntent.kt
│   ├── ReaderPageState.kt
│   ├── ReaderBookmarkState.kt
│   ├── ReaderOverlayState.kt
│   ├── ReaderSearchState.kt
│   ├── ReaderTopBar.kt
│   ├── ReaderBottomBar.kt
│   └── ReaderOverlayPanels.kt
├── session/
│   ├── BookSessionManager.kt
│   ├── ChapterPaginationCoordinator.kt
│   ├── ReaderNavigationCoordinator.kt
│   ├── ReaderProgressResolver.kt
│   ├── ReaderSearchManager.kt
│   ├── BookmarkNotesManager.kt
│   └── ReaderPresetManager.kt
├── settings/
│   ├── ReaderSettingsManager.kt
│   ├── ReaderSettingsResolver.kt
│   ├── ReaderPreferenceMonitor.kt
│   ├── ReaderSettingRegistry.kt
│   ├── ReaderFeatureFlags.kt
│   ├── SettingsTab.kt
│   ├── PresetSnapshot.kt
│   ├── ReaderPrefsLayers.kt
│   ├── GestureConfig.kt
│   ├── HapticFeedbackHelper.kt
│   ├── EyeCareTimer.kt
│   ├── BlueLightPreset.kt
│   └── panel/
│       ├── ReaderSettingsPanel.kt
│       ├── ReaderSettingsModal.kt
│       ├── ReaderSettingsCard.kt
│       ├── ReaderSettingRow.kt
│       ├── ReaderSettingsPeek.kt
│       ├── GestureZoneEditorOverlay.kt
│       ├── SlotMatrix.kt
│       ├── ThemeSwatchRow.kt
│       ├── VisualMarginControl.kt
│       ├── controls/
│       └── tabs/
├── render/
│   └── ReaderCanvasEffects.kt
└── component/
    ├── directory/
    ├── PickerSheet.kt
    └── VerticalBrightnessSlider.kt
```

说明：

- `screen` 只放页面入口、ViewModel、UI state、intent。
- `ReaderTopBar.kt`、`ReaderBottomBar.kt`、`ReaderOverlayPanels.kt` 是 ReaderScreen 的 UI 组成部分，第一阶段直接归入 `screen`，不再保留独立 `overlays` 包。
- `session` 只放阅读会话和业务流程协调。
- `settings` 统一 reader 设置域。除 `panel/` 外不继续拆 `manager/registry/preset/interaction`，避免 2-3 个文件一个包的过度分层。
- `render` 保持现状，因为它已经是清晰的渲染 snapshot/diff 编排边界。
- `ReaderCanvasEffects.kt` 本质是渲染副作用，直接归入 `render`。
- 旧 quicksettings 不再保留 legacy 目录。P0.5 直接删除旧入口、旧 UI 文件和已完成的 v5 feature flag。

### 3.3 `core` 目标结构

```text
core/
├── reader/
│   ├── engine/
│   │   ├── ReaderCanvasView.kt
│   │   ├── ReaderPageRenderer.kt
│   │   ├── Paginator.kt
│   │   ├── RenderApplierTarget.kt
│   │   ├── animation/
│   │   ├── cache/
│   │   ├── input/
│   │   └── selection/
│   ├── model/
│   ├── layout/
│   └── text/
├── recorder/
│   ├── CanvasRecorder.kt
│   ├── CanvasRecorderFactory.kt
│   ├── CanvasRecorderExtensions.kt
│   ├── CanvasRecorderLocked.kt
│   ├── impl/
│   ├── pools/
│   └── internal/
├── text/
├── data/
│   ├── preferences/
│   ├── database/
│   └── repository/
├── reading/
├── sync/                # 过渡期保留，后续并入顶层 sync 或删除
├── parser/
├── font/
├── i18n/
├── tts/
├── cover/
├── performance/
└── util/
```

说明：

- `core/recorder` 是通用绘制录制基础设施，不应叫 `canvasrecorder`。
- `core/reader/engine` 放真正的阅读引擎运行时。
- `PageBitmapCache` 这类阅读器专用渲染缓存并入 `core/reader/engine/cache`，不再单独使用 `canvas` 包，避免和 `core/recorder` 混淆。
- `core/text` 保留通用文本处理，例如繁简转换、空行清理、盘古空格。
- `core/reader/text` 保留阅读排版算法，避免和通用文本处理混在一起。
- `core/data` 的 database/repository/preferences 归并放到后期。第一阶段只新增职责说明，避免 Room entity/dao 和 migration 影响面过早扩大。
- `core/reading/ReadingStatus.kt` 当前是跨 bookshelf、reader、stats、repository 的阅读状态模型，第一阶段保留；后期若数据层重组，可评估迁入 `core/data/model`。
- `core/sync` 是旧 WebDAV 同步层过渡包。目标是把仍有效的协议能力迁到 `sync/network/webdav` / `sync/network/transport`，或在 `CloudSyncSettingsViewModel` 等调用点改用新同步子系统后删除。

### 3.4 `sync` 目标结构

```text
sync/
├── engine/
│   ├── conflict/
│   ├── dirty/
│   ├── hash/
│   ├── manifest/
│   └── state/
├── network/
│   ├── webdav/
│   ├── transport/
│   └── throttle/
├── crypto/
├── device/
├── backup/
│   ├── export/
│   └── import/
└── worker/
    └── notification/
```

说明：

- 当前 sync 子包数量较多，但多数职责合理。合并目标不是减少文件，而是把“协议/引擎/任务/UI”分清。
- `sync/export` 建议改名为 `sync/backup`，因为它同时包含备份导入导出，不只是 export。
- `sync` 保持纯同步域，不承载 Compose UI。冲突解决、设备管理、同步日志、同步设置等 UI 放到 `feature/sync` 或 `feature/settings/sync`。

### 3.4.1 `feature/sync` 目标结构

当前 `feature/` 下只有 `bookshelf / reader / settings / stats`，`feature/sync` 尚不存在。因此 P5 不是单纯移动 UI 文件，还需要新建 feature 入口。

```text
feature/sync/
├── SyncFeatureEntry.kt
├── conflict/
├── devices/
├── log/
└── settings/
```

说明：

- `feature/sync` 承载同步相关 UI 和 ViewModel。
- `sync` 提供纯 Kotlin 领域能力和 worker，不反向依赖 Compose。
- 设置页中的同步入口可从 `feature/settings` 导航到 `feature/sync/settings`，而不是把 UI 放进 `sync`。
- 第一批迁移时创建 `SyncFeatureEntry.kt` 作为导航/入口 stub，并同步检查 `MainActivity.kt` 或现有导航注册点。

### 3.5 `ui` 目标结构

```text
ui/
├── theme/
├── testing/
└── component/
```

说明：

- `ui` 只保留跨 feature 的基础 UI。
- 业务 UI 不留在 `ui` 根下。
- `ui/theme` 是 Material/Compose 主题入口。
- `core/theme` 后续只保留非 Compose 的主题数据模型或并入 `core/data/preferences`。

---

## 4. 分阶段迁移计划

### P0 - 建立迁移纪律

目标：确保重构可控。

规则：

- 每个批次完成后运行 `./gradlew.bat :app:compileDebugKotlin`。
- 涉及测试源码时运行 `./gradlew.bat :app:compileDebugUnitTestKotlin`。
- 涉及 androidTest 时运行 `./gradlew.bat :app:compileDebugAndroidTestKotlin`。
- 文件移动必须同步 package 声明和 import。
- 每个批次完成后检查 `core/ShuLiAppContainer.kt`、`MainActivity.kt` 和现有导航入口，确保手写 DI 与导航 import 同步。
- 不保留 facade、deprecated wrapper、兼容层，除非确实存在多阶段迁移需要。
- 死代码优先删除，不新增 legacy 目录。

工具：

```powershell
# 在 P0 阶段一次性运行，生成 6 份基线报告，作为整个重构的附件。
.\scripts\refactor-p0-audit.ps1
# 输出目录：docs/refactor-audit/
```

产出报告（人工审阅后归档）：

| 报告 | 用途 |
|---|---|
| `01-shuliappcontainer-imports.txt` | 手写 DI 容器引用清单，移动被注入类时必须同步 |
| `02-mainactivity-imports.txt` | 顶层入口引用清单 |
| `03-navigation-entrypoints.txt` | 导航注册点（composable("route") / navigate( 等） |
| `04-readingstatus-callers.txt` | §1.5 标记的跨域模型调用点，后期迁移用 |
| `05-test-directory-layout.txt` | 测试目录镜像度，判断移动 vs 只改 import |
| `06-baseline-counts.txt` | §9 量化标准的基线值，重构后对比 |

批次开始前先做测试引用审计，输出作为该批次的移动清单附件：

```powershell
rg -l "\b(目标类名1|目标类名2)\b" app/src/test app/src/androidTest
rg -n "import com\.shuli\.reader\.(旧包路径)" app/src/test app/src/androidTest
```

当前 P1.1 预查结果：

```text
ReaderIntent -> app/src/test/java/com/shuli/reader/feature/reader/ReaderIntentTest.kt
ReaderUiState / ReaderPageState / ReaderBookmarkState / ReaderOverlayState / ReaderSearchState -> 未发现直接测试引用
```

### P0.5 - 清理旧 quicksettings 死代码

目标：在大规模移动前先减少历史分支和重复 UI。

前提：

- `ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED` 默认启用。
- 项目不再需要旧设置面板回退路径。
- `ReaderOverlayPanels.kt` 可以直接保留 v5 设置面板分支。
- 删除文件属于高风险操作，实际执行前按项目规则单独确认；本阶段只定义候选清单。

工具：

```powershell
# 先 dry-run 看全部影响
.\scripts\refactor-p05-delete.ps1 -DryRun

# 确认无遗漏后实际执行
.\scripts\refactor-p05-delete.ps1
```

脚本行为：

- 每项开始前打 `refactor-p05-item{N}-{timestamp}` tag 作为单项回滚点。
- 预检查引用计数，超出预期时人工确认。
- 删除后跑 `./gradlew :app:compileDebugKotlin`，失败自动 `git reset --hard` 到本项 tag。
- 全部通过 → 打 `refactor-p05-done-{date}` tag；任一失败 → 中止并保留所有 tag 供诊断。
- 支持 `-StartFrom N` 续跑。

删除：

| 当前文件 / 目录 | 动作 | 测试同步 |
|---|---|---|
| `feature/reader/component/QuickSettingsSheet.kt` | 删除 | 删除或更新引用旧入口的 UI 测试 |
| `feature/reader/component/quicksettings/HeaderFooterCustomizationPanel.kt` | 删除 | 无独立测试则无需移动 |
| `feature/reader/component/quicksettings/InteractionPanel.kt` | 删除 | 无独立测试则无需移动 |
| `feature/reader/component/quicksettings/LayoutPanel.kt` | 删除 | 无独立测试则无需移动 |
| `feature/reader/component/quicksettings/SettingsPanel.kt` | 删除 | 无独立测试则无需移动 |
| `feature/reader/component/quicksettings/SharedComponents.kt` | 删除 | 无独立测试则无需移动 |
| `feature/reader/component/quicksettings/StylePanel.kt` | 删除 | 无独立测试则无需移动 |
| `ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED` | 删除字段 | 更新引用该 flag 的测试 |
| `ReaderOverlayPanels.kt` 旧版分支 | 删除分支 | 更新 UI 测试快照/断言 |
| `feature/reader/settings/ReaderSessionState.kt` | 同时满足以下两条才删除：(1) `rg "ReaderSessionState" app/src/main app/src/test app/src/androidTest` 只命中自身；(2) `rg "sessionState|SessionState" app/src/main/java/com/shuli/reader/feature/reader/` 显示会话状态已由 `BookSessionManager` / `ReaderUiState` 承载 | 无直接测试；删除前复查 ViewModel 会话状态是否已有替代 |

已知测试影响：

- `app/src/test/java/com/shuli/reader/feature/reader/settings/ReaderFeatureFlagsTest.kt` 当前覆盖 `SETTINGS_PANEL_V5_ENABLED` 可切换行为；删除该 flag 后应同步删除对应断言，保留其它有效 feature flag 测试。

验证：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:compileDebugAndroidTestKotlin
```

### P1 - 拆 `feature/reader/screen` 轻依赖文件

原则：`ReaderViewModel.kt` 最后移动。它依赖 session/settings/render/screen 等多个方向，如果第一批移动，会制造大量临时 import churn。

#### P1.1 - 移动 state / intent

移动：

| 当前文件 | 目标文件 | 对应测试 |
|---|---|---|
| `feature/reader/ReaderUiState.kt` | `feature/reader/screen/ReaderUiState.kt` | 当前未发现直接测试引用；执行前复查 |
| `feature/reader/ReaderIntent.kt` | `feature/reader/screen/ReaderIntent.kt` | `ReaderIntentTest.kt` |
| `feature/reader/ReaderPageState.kt` | `feature/reader/screen/ReaderPageState.kt` | 当前未发现直接测试引用；执行前复查 |
| `feature/reader/ReaderBookmarkState.kt` | `feature/reader/screen/ReaderBookmarkState.kt` | 当前未发现直接测试引用；执行前复查 |
| `feature/reader/ReaderOverlayState.kt` | `feature/reader/screen/ReaderOverlayState.kt` | 当前未发现直接测试引用；执行前复查 |
| `feature/reader/ReaderSearchState.kt` | `feature/reader/screen/ReaderSearchState.kt` | 当前未发现直接测试引用；执行前复查 |

包名：

```kotlin
package com.shuli.reader.feature.reader.screen
```

验证：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:compileDebugUnitTestKotlin
```

#### P1.2 - 移动 screen 入口

| 当前文件 | 目标文件 | 对应测试 |
|---|---|---|
| `feature/reader/ReaderScreen.kt` | `feature/reader/screen/ReaderScreen.kt` | Reader screen / overlay 相关 Compose 测试 |
| `feature/reader/overlays/ReaderTopBar.kt` | `feature/reader/screen/ReaderTopBar.kt` | Reader screen / overlay 相关 Compose 测试 |
| `feature/reader/overlays/ReaderBottomBar.kt` | `feature/reader/screen/ReaderBottomBar.kt` | Reader screen / overlay 相关 Compose 测试 |
| `feature/reader/overlays/ReaderOverlayPanels.kt` | `feature/reader/screen/ReaderOverlayPanels.kt` | P0.5 后仅保留新版设置面板入口；同步 overlay 测试 |
| `feature/reader/effects/ReaderCanvasEffects.kt` | `feature/reader/render/ReaderCanvasEffects.kt` | Reader render / canvas effect 相关测试 |

完成后删除空的 `feature/reader/overlays/` 与 `feature/reader/effects/` 目录：

```powershell
# 确认目录已空（应只剩 $null 或空输出）
Get-ChildItem app/src/main/java/com/shuli/reader/feature/reader/overlays -Recurse
Get-ChildItem app/src/main/java/com/shuli/reader/feature/reader/effects -Recurse

# 空则删除
Remove-Item app/src/main/java/com/shuli/reader/feature/reader/overlays -Recurse
Remove-Item app/src/main/java/com/shuli/reader/feature/reader/effects -Recurse
```

验证：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:compileDebugAndroidTestKotlin
```

#### P1.3 - 最后移动 ViewModel

`ReaderViewModel.kt` 等 P2/P3 完成后再移动：

| 当前文件 | 目标文件 | 对应测试 |
|---|---|---|
| `feature/reader/ReaderViewModel.kt` | `feature/reader/screen/ReaderViewModel.kt` | ReaderViewModel / ReaderIntent / reader settings 相关单测 |

验证：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:compileDebugUnitTestKotlin
./gradlew.bat :app:compileDebugAndroidTestKotlin
```

### P2 - 拆 `feature/reader/session`

移动：

| 当前文件 | 目标文件 | 对应测试 |
|---|---|---|
| `feature/reader/BookSessionManager.kt` | `feature/reader/session/BookSessionManager.kt` | Reader session / progress 相关单测 |
| `feature/reader/ChapterPaginationCoordinator.kt` | `feature/reader/session/ChapterPaginationCoordinator.kt` | Pagination / reader render 相关单测 |
| `feature/reader/ReaderNavigationCoordinator.kt` | `feature/reader/session/ReaderNavigationCoordinator.kt` | Reader navigation 相关单测 |
| `feature/reader/ReaderProgressResolver.kt` | `feature/reader/session/ReaderProgressResolver.kt` | Progress resolver 相关单测 |
| `feature/reader/ReaderSearchManager.kt` | `feature/reader/session/ReaderSearchManager.kt` | Search manager 相关单测 |
| `feature/reader/BookmarkNotesManager.kt` | `feature/reader/session/BookmarkNotesManager.kt` | Bookmark / note 相关单测 |
| `feature/reader/ReaderPresetManager.kt` | `feature/reader/session/ReaderPresetManager.kt` | Preset 相关单测 |
| `feature/reader/FontImportManager.kt` | `feature/reader/session/FontImportManager.kt` | Font import 相关单测 |

包名：

```kotlin
package com.shuli.reader.feature.reader.session
```

注意：

- `ReaderViewModel` 会大量引用这些类，移动后先修当前 ViewModel imports；ViewModel 自身仍等 P1.3 最后移动。
- 这批只移动文件，不拆类内部逻辑。

验证：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:compileDebugUnitTestKotlin
```

### P3 - 整理 `feature/reader/settings`

移动：

| 当前文件 | 目标文件 | 对应测试 |
|---|---|---|
| `feature/reader/ReaderSettingsManager.kt` | `feature/reader/settings/ReaderSettingsManager.kt` | Reader settings manager 相关单测 |
| `feature/reader/ReaderSettingsResolver.kt` | `feature/reader/settings/ReaderSettingsResolver.kt` | Reader settings resolver 相关单测 |
| `feature/reader/ReaderPreferenceMonitor.kt` | `feature/reader/settings/ReaderPreferenceMonitor.kt` | Preference monitor 相关单测 |
| `feature/reader/settings/ReaderSettingRegistry.kt` | 保持 | RegistryArchitectureTest 等 |
| `feature/reader/settings/ReaderFeatureFlags.kt` | 保持；若 P0.5 后文件为空则删除 | ReaderFeatureFlagsTest / MigrationTest / DegradationStrategyTest |
| `feature/reader/settings/SettingsTab.kt` | 保持 | 搜索 `SettingsTab` import 并同步 |
| `feature/reader/settings/PresetSnapshot.kt` | 保持 | PresetSnapshotTest |
| `feature/reader/settings/ReaderPrefsLayers.kt` | 保持 | ReaderPrefsLayersTest |
| `feature/reader/settings/GestureConfig.kt` | 保持 | Gesture config 相关测试 |
| `feature/reader/settings/HapticFeedbackHelper.kt` | 保持 | 搜索 import 并同步 |
| `feature/reader/settings/EyeCareTimer.kt` | 保持 | 搜索 import 并同步 |
| `feature/reader/settings/BlueLightPreset.kt` | 保持 | 搜索 import 并同步 |
| `feature/reader/settings/ReaderSessionState.kt` | 若 P0.5 未删除则重新评估；默认不保留 | 无直接测试 |

包名按目录同步。除 `panel/` 外，本阶段不再拆 `manager/registry/preset/interaction`，避免细碎包结构增加 import churn。

验证：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:compileDebugUnitTestKotlin
```

### P4 - 迁移 quicksettings v5，并统一正式文件名

移动并重命名：

| 当前文件 | 目标文件 | 对应测试 |
|---|---|---|
| `component/quicksettings/v5/SettingsPanelV5.kt` | `settings/panel/ReaderSettingsPanel.kt` | QuickSettingsPanelTest / Compose 面板测试 |
| `component/quicksettings/v5/SettingsPanelV5Modal.kt` | `settings/panel/ReaderSettingsModal.kt` | QuickSettingsPanelTest / modal 测试 |
| `component/quicksettings/v5/SettingsCardV5.kt` | `settings/panel/ReaderSettingsCard.kt` | 搜索 `SettingsCardV5` import 并同步 |
| `component/quicksettings/v5/SettingsPeekContent.kt` | `settings/panel/ReaderSettingsPeek.kt` | 搜索 `SettingsPeekContent` import 并同步 |
| `component/quicksettings/v5/SettingRow.kt` | `settings/panel/ReaderSettingRow.kt` | 搜索 `SettingRow` import 并同步 |
| `component/quicksettings/v5/GestureZoneEditorOverlay.kt` | `settings/panel/GestureZoneEditorOverlay.kt` | 触控区域 UI 测试 |
| `component/quicksettings/v5/SlotMatrix.kt` | `settings/panel/SlotMatrix.kt` | SlotMatrixTest |
| `component/quicksettings/v5/ThemeSwatchRow.kt` | `settings/panel/ThemeSwatchRow.kt` | 搜索 import 并同步 |
| `component/quicksettings/v5/VisualMarginControl.kt` | `settings/panel/VisualMarginControl.kt` | 搜索 import 并同步 |
| `component/quicksettings/v5/CustomThemeDialog.kt` | `settings/panel/CustomThemeDialog.kt` | 自定义主题相关 UI 测试 |
| `component/quicksettings/v5/controls/*` | `settings/panel/controls/*` | InkControlsTest |
| `component/quicksettings/v5/tabs/*` | `settings/panel/tabs/*` | QuickSettingsPanelTest |

包名：

```kotlin
package com.shuli.reader.feature.reader.settings.panel
package com.shuli.reader.feature.reader.settings.panel.controls
package com.shuli.reader.feature.reader.settings.panel.tabs
```

旧 quicksettings 已在 P0.5 删除，不再迁移到 legacy 目录。

额外清理：

```powershell
rg -n "\b\w+V5\b|SETTINGS_PANEL_V5_ENABLED|QuickSettingsSheet|component\.quicksettings" app/src/main/java app/src/test app/src/androidTest
```

要求：

- `V5` 不应出现在正式类名、文件名、包名中。
- `SETTINGS_PANEL_V5_ENABLED` 0 命中。
- `QuickSettingsSheet` 0 命中。

验证：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:compileDebugAndroidTestKotlin
```

### P5 - 收敛跨 feature 的 `ui/`

前提：`feature/sync` 当前不存在，需要先创建 `feature/sync/SyncFeatureEntry.kt`，再迁移同步相关 UI 和 ViewModel。

建议移动：

| 当前目录 | 目标目录 | 对应测试 |
|---|---|---|
| `ui/settings/sync/` | `feature/sync/settings/` | SyncSummaryViewModelTest / CloudSyncSettingsViewModelTest |
| `ui/settings/crypto/` | `feature/settings/crypto/` | EncryptionManagementViewModelTest |
| `ui/conflict/` | `feature/sync/conflict/` | ConflictDialogViewModelTest |
| `ui/devices/` | `feature/sync/devices/` | DeviceManagementViewModelTest |
| `ui/log/` | `feature/sync/log/` | SyncLogViewModelTest |
| `ui/export/` | `feature/bookshelf/export/ui/` | Export UI tests |

路径决策说明：

- `ui/settings/sync/` 固定迁到 `feature/sync/settings/`，**不**放 `feature/settings/sync/`。理由：`CloudSyncSettingsViewModel` 与 `ConflictDialogViewModel`、`DeviceManagementViewModel` 共享 sync 域状态与 DI 依赖，集中放 `feature/sync/` 更易协作；`feature/settings/` 只做导航入口，不承载 sync 业务 ViewModel。
- `ui/export/` 固定迁到 `feature/bookshelf/export/ui/`，**不**放 `feature/sync/backup`。理由：当前导出 UI 的调用方是 bookshelf 的备份/还原入口，与 sync 的 `sync/backup/`（协议层）没有直接依赖；放到 bookshelf feature 内避免跨 feature 反向依赖。

保留：

```text
ui/theme/
ui/testing/
```

如果新增通用组件，再建：

```text
ui/component/
```

注意：

- 不把 Compose UI 放进 `sync/ui`。`sync` 保持纯领域、协议、worker。
- 如果设置页只是入口，具体同步设置 UI 可以归 `feature/sync/settings`，由 `feature/settings` 导航过去。
- `ConflictDialogViewModel`、`DeviceManagementViewModel`、`SyncLogViewModel`、`SyncSummaryViewModel`、`CloudSyncSettingsViewModel`、`EncryptionManagementViewModel` 等 ViewModel 必须随对应 Screen 一起移动，不留在 `ui/`。
- 迁移后检查 `MainActivity.kt`、设置页导航、手写 DI 容器和测试包路径。

验证：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:compileDebugAndroidTestKotlin
```

### P5.5 - 明确 `core/data` 职责边界

第一阶段不移动 Room / Repository / Preferences 文件，避免牵动 migration、DAO、entity 和数据库测试。

但第一阶段结束前必须新增职责说明文件：

```text
core/data/README.md
```

内容约定：

```text
core/data/
├── preferences/  # DataStore / 用户偏好模型，例如 UserPreferences、ReaderPreferences
├── database/     # Room database / dao / entity / migration
└── repository/   # 跨数据源聚合查询、读写门面
```

如果暂时不移动现有 `core/database`、`core/repository`，README 应明确“目标结构”和“当前过渡结构”，防止新增代码继续随手落到错误目录。

### P6 - 重命名 `core/canvasrecorder` 为 `core/recorder`

移动并改包名：

```text
core/canvasrecorder/ -> core/recorder/
```

包名：

```kotlin
package com.shuli.reader.core.recorder
```

建议顺便整理实现类：

```text
core/recorder/
├── CanvasRecorder.kt
├── CanvasRecorderFactory.kt
├── CanvasRecorderExtensions.kt
├── CanvasRecorderLocked.kt
├── BaseCanvasRecorder.kt
├── impl/
│   ├── CanvasRecorderApi23Impl.kt
│   ├── CanvasRecorderApi29Impl.kt
│   └── CanvasRecorderBitmapImpl.kt
├── pools/
└── internal/
```

文件名建议：

| 当前文件 | 目标文件 | 对应测试 |
|---|---|---|
| `CanvasRecorderImpl.kt` | `CanvasRecorderBitmapImpl.kt` | Reader render / CanvasRecorder 相关测试 |
| `CanvasRecorderApi23Impl.kt` | 保持 | Reader render / CanvasRecorder 相关测试 |
| `CanvasRecorderApi29Impl.kt` | 保持 | Reader render / CanvasRecorder 相关测试 |

验证：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:testDebugUnitTest --tests "*reader*" --tests "*CanvasRecorder*" --tests "*Recorder*"
./gradlew.bat :app:compileDebugAndroidTestKotlin
```

### P7 - `core/reader` 引擎结构整理

移动：

| 当前路径 | 目标路径 | 对应测试 |
|---|---|---|
| `core/reader/ReaderCanvasView.kt` | `core/reader/engine/ReaderCanvasView.kt` | Reader render / UI 相关测试 |
| `core/reader/ReaderPageRenderer.kt` | `core/reader/engine/ReaderPageRenderer.kt` | VerticalRenderTest / render 测试 |
| `core/reader/Paginator.kt` | `core/reader/engine/Paginator.kt` | Paginator / VerticalPaginationStrategy 测试 |
| `core/reader/RenderApplierTarget.kt` | `core/reader/engine/RenderApplierTarget.kt` | ReaderCanvasStateApplierTest / OrchestratorTest |
| `core/reader/CanvasVisualParamsManager.kt` | `core/reader/engine/CanvasVisualParamsManager.kt` | ReaderCanvas / render 相关测试 |
| `core/reader/PageRenderContext.kt` | `core/reader/engine/PageRenderContext.kt` | render 相关测试 |
| `core/reader/RenderContext.kt` | `core/reader/engine/RenderContext.kt` | render 相关测试 |
| `core/reader/SlotResolver.kt` | `core/reader/engine/SlotResolver.kt` | header/footer slot 相关测试 |
| `core/reader/PaginationStrategy.kt` | `core/reader/engine/PaginationStrategy.kt` | pagination 相关测试 |
| `core/reader/VerticalPaginationStrategy.kt` | `core/reader/engine/VerticalPaginationStrategy.kt` | VerticalPaginationStrategy 测试 |
| `core/reader/TouchZone.kt` | `core/reader/engine/input/TouchZone.kt` | gesture / touch 相关测试 |
| `core/reader/animation/` | `core/reader/engine/animation/` | PageDelegate / animation 相关测试 |
| `core/reader/cache/` | `core/reader/engine/cache/` | cache / page count 相关测试 |
| `core/reader/canvas/PageBitmapCache.kt` | `core/reader/engine/cache/PageBitmapCache.kt` | render cache 相关测试 |
| `core/reader/canvas/CanvasTouchHandler.kt` | `core/reader/engine/input/CanvasTouchHandler.kt` | gesture / touch 相关测试 |
| `core/reader/canvas/CanvasTextSelection.kt` | `core/reader/engine/selection/CanvasTextSelection.kt` | selection 相关测试 |
| `core/reader/TextMeasurer.kt` | `core/reader/text/TextMeasurer.kt` | text layout 相关测试 |
| `core/reader/SimpleTextMeasurer.kt` | `core/reader/text/SimpleTextMeasurer.kt` | text layout 相关测试 |
| `core/reader/AndroidTextMeasurer.kt` | `core/reader/text/AndroidTextMeasurer.kt` | text layout 相关测试 |
| `core/reader/WidthWindow.kt` | `core/reader/text/WidthWindow.kt` | text layout 相关测试 |
| `core/reader/HyphenationEngine.kt` | `core/reader/text/HyphenationEngine.kt` | hyphenation 相关测试 |
| `core/reader/BionicSegments.kt` | `core/reader/text/BionicSegments.kt` | bionic reading 相关测试 |
| `core/reader/HeaderFooterModels.kt` | `core/reader/model/HeaderFooterModels.kt` | header/footer model 相关测试 |
| `core/reader/ChapterProvider.kt` | 优先迁到 `feature/reader/session/ChapterProvider.kt` | ChapterProviderTest；迁移后测试包同步 |
| `core/reader/ReadingStateManager.kt` | 优先迁到 `feature/reader/session/ReadingStateManager.kt` | ReadingStateManagerTest；迁移后测试包同步 |
| `core/reader/Placeholder.kt` | 若仍只含包说明则删除 | 无 |
| `core/reader/layout/` | 保持 | layout 相关测试 |
| `core/reader/model/` | 保持 | model 相关测试 |
| `core/reader/text/` | 保持并接收文本测量/断词相关文件 | text layout 相关测试 |

注意：

- 这是影响面较大的包迁移，必须单独执行。
- 不建议和 `core/recorder` 同一批做。
- 不再创建 `core/reader/engine/canvas` 包。缓存、输入、选区分别落入 `cache/input/selection`，避免和 `core/recorder` 混淆。
- `core/reader/layout/` 明确保持。`ReaderLayoutInput` 与 `ReaderTextMeasurerFactory` 是排版输入与测量装配，`model/` 则放 `TextPage`、`TextChapter` 等排版输出；输入/输出分包比合并到 `model` 更清晰。
- `ChapterProvider`、`ReadingStateManager` 当前更像 reader feature 的会话流程对象，而不是可复用 core 引擎。迁移前需确认没有非 reader feature 依赖；若新增了跨 feature 依赖，再改为 `core/reader/engine/session`。

验证：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:compileDebugUnitTestKotlin
./gradlew.bat :app:compileDebugAndroidTestKotlin
```

### P8 - `sync` 包归并

迁移目标：

| 当前目录 | 目标目录 | 对应测试 |
|---|---|---|
| `sync/conflict/` | `sync/engine/conflict/` | conflict resolver 相关测试 |
| `sync/dirty/` | `sync/engine/dirty/` | dirty tracker 相关测试 |
| `sync/hash/` | `sync/engine/hash/` | hash 相关测试 |
| `sync/manifest/` | `sync/engine/manifest/` | manifest manager 相关测试 |
| `sync/state/` | `sync/engine/state/` | state machine 相关测试 |
| `sync/transport/` | `sync/network/transport/` | transport 相关测试 |
| `sync/throttle/` | `sync/network/throttle/` | throttle 相关测试 |
| `sync/export/` | `sync/backup/` | backup import/export 相关测试 |
| `sync/notification/` | `sync/worker/notification/` | notification 相关测试 |
| `core/sync/WebDavClient.kt` | 迁入 `sync/network/webdav/`，或由 `SyncWebDavClient` 替代后删除 | WebDavClientTest / WebDavIntegrationTest / CloudSyncSettingsViewModelTest |
| `core/sync/WebDavSyncManager.kt` | 迁入 `sync/engine/`，或由新同步 engine 替代后删除 | WebDavSyncManagerTest / ShuLiAppContainer import |

保留：

```text
sync/engine/
sync/network/webdav/
sync/crypto/
sync/device/
sync/worker/
```

同步相关 Compose UI 不进入 `sync`，按 P5 迁移到 `feature/sync/*` 或 `feature/settings/*`。

`core/sync` 处理原则：

- 不在 P0-P4 提前迁移，避免同时牵动 UI、DI、WebDAV 测试和新同步子系统。
- P8 执行前先选择一种路线：复用旧实现并改包，或切到 `sync/network/webdav/SyncWebDavClient` 后删除旧实现。
- 任一路线都必须同步 `core/ShuLiAppContainer.kt`、`feature/sync/settings` 或 `feature/settings/sync` 下的 ViewModel，以及 `app/src/test/java/com/shuli/reader/core/sync`、`app/src/androidTest/java/com/shuli/reader/core/sync` 测试路径。

验证：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:compileDebugUnitTestKotlin
```

---

## 5. 推荐批次顺序

| 批次 | 内容 | 前置依赖 | 风险 | 收益 |
|---|---|---|---:|---:|
| P0 | 建立规则，仅文档 | 无 | 低 | 中 |
| P0.5 | 删除旧 quicksettings + 已完成 feature flag | P0 | 低 | 高 |
| P1.1 | `feature/reader/screen` state / intent | P0.5 | 中 | 高 |
| P1.2 | `ReaderScreen` + overlay/effect 归并 | P1.1 | 中 | 高 |
| P2 | `feature/reader/session` | P1.1 | 中 | 高 |
| P3 | `feature/reader/settings` 逻辑收敛 | P1.1 | 中 | 高 |
| P1.3 | 移动 `ReaderViewModel` | P2 + P3 | 中 | 高 |
| P4 | quicksettings v5 正式化，删除 V5 命名 | P3 | 中 | 高 |
| P5 | 业务 UI 从 `ui/` 迁回 `feature/*` | P4 | 中 | 中 |
| P5.5 | 新增 `core/data` 职责说明 | P5 可并行 | 低 | 中 |
| P6 | `core/canvasrecorder` -> `core/recorder` | P5 或独立分支 | 中 | 中 |
| P7 | `core/reader` engine 重组 | P6 | 高 | 中 |
| P8 | `sync` 包归并 + `core/sync` 处理 | P5 | 中 | 中 |

建议 P0.5-P4 作为第一阶段完成。第一阶段结束后，`feature/reader` 的职责边界会明显清晰，旧设置面板历史包袱也被移除。

粗略工作量估算：

| 范围 | 估算 |
|---|---:|
| P0 / P0.5 | 1-2 天 |
| P1（3 个子批） | 2-3 天 |
| P2 | 2 天 |
| P3 | 2 天 |
| P4 | 2 天 |
| P5-P8 | 4-6 天 |
| **总计** | **约 3-4 周，含编译与测试修复** |

节奏校准：

- P1.1 作为"节奏校准批"：6 个文件移动 + 对应 import 修复 + 编译验证，应在 **0.5 天**内完成。
- 若 P1.1 实际耗时超出预期，按比例修订后续估算，而不是压缩后续批次质量。
- 修订后的估算应写回本节，并在 commit message 中标注"基于 P1.1 实际耗时修订"。

---

## 6. Import 与文件名修改策略

### 6.1 推荐操作方式

优先使用 IDE 的 Move/Rename refactor，或使用可重复脚本批量替换。每批控制在一个职责范围内。

每批流程：

1. 移动文件到目标目录。
2. 修改 package 声明。
3. 全局替换 import。
4. 同步移动或更新测试文件。
5. 运行 compile。
6. 修复遗漏 import。
7. 运行相关测试编译。

### 6.1.1 PowerShell 小批量移动模板

优先使用 Android Studio / IntelliJ 的 Move/Rename Refactor。只有在批次很小、路径明确、且已建立回滚点时，才使用脚本。脚本默认先 `-WhatIf` 预览，确认后再去掉 `-WhatIf`。

> **编码约定**：项目源码使用 **UTF-8 无 BOM、CRLF 换行**。PowerShell 的 `Set-Content` 默认写 UTF-16 LE（PS 5.1）或 UTF-8 BOM（PS 7），都会破坏源码编码。脚本统一使用 `[System.IO.File]::WriteAllText(path, content, UTF8Encoding($false))` 写回文件。

```powershell
# 项目源码约定：UTF-8 无 BOM，CRLF 换行
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

$moves = @(
    @{
        From = "app/src/main/java/com/shuli/reader/feature/reader/ReaderIntent.kt"
        To = "app/src/main/java/com/shuli/reader/feature/reader/screen/ReaderIntent.kt"
        OldPackage = "package com.shuli.reader.feature.reader"
        NewPackage = "package com.shuli.reader.feature.reader.screen"
        OldImport = "com.shuli.reader.feature.reader.ReaderIntent"
        NewImport = "com.shuli.reader.feature.reader.screen.ReaderIntent"
    }
)

foreach ($move in $moves) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Path $move.To) | Out-Null
    Move-Item -LiteralPath $move.From -Destination $move.To -WhatIf
}

# 确认 Move-Item 预览无误后去掉 -WhatIf，再执行 package/import 替换。
foreach ($move in $moves) {
    $content = (Get-Content -LiteralPath $move.To -Raw -Encoding UTF8) `
        -replace [regex]::Escape($move.OldPackage), $move.NewPackage
    [System.IO.File]::WriteAllText($move.To, $content, $utf8NoBom)

    rg -l ([regex]::Escape($move.OldImport)) "app/src/main/java" "app/src/test" "app/src/androidTest" |
        ForEach-Object {
            $c = (Get-Content -LiteralPath $_ -Raw -Encoding UTF8) `
                -replace [regex]::Escape($move.OldImport), $move.NewImport
            [System.IO.File]::WriteAllText($_, $c, $utf8NoBom)
        }
}

./gradlew.bat :app:compileDebugKotlin
```

脚本只处理一对一移动和 import 替换。涉及类名重命名、文件拆分、删除文件时，使用 IDE refactor 或手工小步执行。

### 6.1.2 测试文件同步策略

每个源码移动批次必须同步处理：

```text
app/src/test/java/com/shuli/reader/...
app/src/androidTest/java/com/shuli/reader/...
```

规则：

- 被测类移动后，测试文件的 package/import 必须同步更新。
- 如果测试目录镜像 main 目录结构，应同步移动测试文件。
- 如果测试只通过 public API 访问被测类，可只改 import，不强制移动文件。
- 如果删除旧 UI，相关 Compose UI 测试必须删除旧入口断言或改为 v5 入口断言。
- 每批移动表必须包含“对应测试”列；没有直接测试时写“搜索 import 并同步”。

### 6.2 禁止事项

- 禁止保留同名新旧类并长期共存。
- 禁止为了“兼容”保留空 wrapper。
- 禁止一次同时移动 `feature/reader`、`core`、`sync` 三大域。
- 禁止只改目录不改包名。
- 禁止文件名仍带历史版本号但放在正式目录中。
- 禁止 main 源码已迁移但测试仍引用旧 package。

### 6.3 可接受的短期例外

以下可以短期存在，但必须在同一阶段后续任务中删除：

- 临时 TODO 注释
- 迁移中的旧 package import

---

## 7. 回滚与失败处理

每个批次开始前建立明确回滚点：

```powershell
$tag = "refactor-p{n}-start-$(Get-Date -Format yyyyMMdd)"
git tag $tag
```

示例：`refactor-p1-start-20260615`。同一批次若因失败需要重做，使用新日期重新 tag，保留历史痕迹。

失败处理：

- 编译失败优先修 import/package。
- 同一批次编译失败超过 15 分钟仍无法定位，停止继续移动文件。
- 需要回滚时，使用批次开始前的 tag 作为回滚点。
- `git reset --hard refactor-p{n}-start` 属于破坏性操作，执行前必须显式确认。
- 禁止半完成提交：不能“移了 10 个文件还有 5 个没移”就提交。
- 每个提交必须是可独立编译的逻辑单元。

更保守的替代策略：

```powershell
git switch -c refactor/p{n}
```

在独立分支完成一个批次，编译通过后再合并。这样失败时可直接丢弃分支，避免污染主工作流。

---

## 8. 验证矩阵

| 改动类型 | 必跑命令 |
|---|---|
| 只移动 main Kotlin 文件 | `./gradlew.bat :app:compileDebugKotlin` |
| 影响 unit test import | `./gradlew.bat :app:compileDebugUnitTestKotlin` |
| 影响 androidTest / Compose 测试 | `./gradlew.bat :app:compileDebugAndroidTestKotlin` |
| 影响 render / reader 行为 | `./gradlew.bat :app:testDebugUnitTest --tests "*reader*"` |
| 影响 sync | `./gradlew.bat :app:testDebugUnitTest --tests "*sync*"` |
| 影响数据库 entity/dao | `./gradlew.bat :app:compileDebugKotlin` + migration tests |

每个 P0.5-P8 批次默认至少运行：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:compileDebugUnitTestKotlin
./gradlew.bat :app:compileDebugAndroidTestKotlin
```

Windows 下不要并行运行多个 Gradle compile 任务清理同一 `app/build/tmp/kotlin-classes/debug`，否则可能遇到文件占用导致删除失败。

---

## 9. 第一阶段完成标准

第一阶段建议覆盖 P0.5-P4。

完成后应满足：

- `feature/reader` 根目录只剩少量 package 或入口，不再平铺大量 manager/coordinator。
- 新设置面板不再位于 `component/quicksettings/v5`。
- 旧 quicksettings 不存在。
- 正式文件名不带 `V5`。
- `ReaderScreen.kt`、`ReaderViewModel.kt` imports 指向 `screen/session/settings` 新包。
- 所有 compile 任务通过。

量化标准：

```powershell
Get-ChildItem "app/src/main/java/com/shuli/reader/feature/reader" -File -Filter "*.kt"
```

- `feature/reader/` 根目录 `.kt` 文件数 < 5。
- `ReaderViewModel.kt` import 行数 < 70；P1.3 移动时必须顺手清理未使用 import。
- `component/quicksettings/v5/` 目录不存在。
- `component/quicksettings/` 旧目录不存在。
- 全项目正式源码无 `V5` 后缀类名/文件名。
- `SETTINGS_PANEL_V5_ENABLED` 0 命中。
- `QuickSettingsSheet` 0 命中。
- `./gradlew.bat :app:testDebugUnitTest` 全绿，或仅允许已记录的预存失败。
- `./gradlew.bat :app:compileDebugAndroidTestKotlin` 通过。

检查命令：

```powershell
rg -n "SETTINGS_PANEL_V5_ENABLED|QuickSettingsSheet|\b\w+V5\b" app/src/main/java app/src/test app/src/androidTest
```

---

## 10. 长期方向

长期目标不是“目录越多越好”，而是每个目录有明确变更原因：

- 阅读器 UI 改动主要落在 `feature/reader/screen`、`settings/panel`。
- 阅读流程改动主要落在 `feature/reader/session`。
- ReaderCanvas 渲染改动主要落在 `feature/reader/render` 与 `core/reader/engine`。
- 通用绘制缓存改动主要落在 `core/recorder`。
- 同步协议改动主要落在 `sync/network`。
- 同步状态机改动主要落在 `sync/engine`。
- 业务页面不再散落到 `ui/` 根目录。

如果每次新增功能都能自然落入一个目录，且不需要先理解无关功能，重构目标即达成。
