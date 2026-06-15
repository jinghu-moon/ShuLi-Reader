# 32 - 项目文件树结构重构任务清单

> 对应方案文档：`docs/31-project-structure-refactor-plan.md`  
> 辅助脚本目录：`scripts/`  
> 范围：`app/src/main/java/com/shuli/reader`、`app/src/test`、`app/src/androidTest`  
> 原则：小批次、可验证、先审计后移动；文件名、包名、类名允许调整，但必须同步 import 与测试。

---

## 0. 执行约定

### 状态标记

- `[ ]` 未开始
- `[-]` 进行中
- `[x]` 已完成
- `[!]` 阻塞或需人工决策

### 高风险操作

以下任务实际执行前必须单独确认：

- 删除文件或目录。
- 执行 `scripts/refactor-p05-delete.ps1`，因为脚本会使用 `git rm`、`git tag`，编译失败时可能触发 `git reset --hard`。
- 使用 `scripts/_convert-to-bom.ps1` 改写脚本编码。
- 大批量移动或批量替换 import。

本清单不要求执行 `git commit`、`git push` 或创建分支。若后续采用分支/提交策略，应由执行人另行确认。

### 默认验证命令

每个代码移动批次至少运行：

```powershell
./gradlew.bat :app:compileDebugKotlin
./gradlew.bat :app:compileDebugUnitTestKotlin
./gradlew.bat :app:compileDebugAndroidTestKotlin
```

只改文档时不需要运行 Gradle。

---

## 1. 脚本能力清单

| 脚本 | 用途 | 覆盖阶段 | 注意事项 |
|---|---|---|---|
| `scripts/refactor-p0-audit.ps1` | 生成重构前依赖与基线报告 | P0 | 可通过 `-OutputDir` 指定输出目录 |
| `scripts/refactor-p05-delete.ps1` | 按 9 项候选清理旧 quicksettings 死代码 | P0.5 | 高风险；先 `-DryRun`，实际执行前确认 |
| `scripts/_validate-syntax.ps1` | 校验 P0/P0.5 脚本 PowerShell 语法 | 脚本维护 | 不改业务代码 |
| `scripts/_check-encoding.ps1` | 检查 P0/P0.5 脚本编码字节 | 脚本维护 | 不改业务代码 |
| `scripts/_convert-to-bom.ps1` | 将重构脚本转为 UTF-8 BOM | 脚本维护 | 会改写脚本文件；仅在 PowerShell 5.1 解析中文失败时使用 |
| `scripts/i18n_migrate.py` | i18n 迁移辅助 | 无关 | 不纳入本轮文件树重构 |
| `scripts/setup-signing.ps1` | 签名配置辅助 | 无关 | 不纳入本轮文件树重构 |

脚本覆盖边界：

- P0 与 P0.5 有专用脚本。
- P1-P8 当前没有专用迁移脚本，优先使用 IDE Move/Rename Refactor。
- 若为 P1-P8 新增脚本，应先复用 `docs/31-project-structure-refactor-plan.md` 中的 UTF-8 无 BOM写入模板，并补充 dry-run。

---

## 2. 阶段总览

| 阶段 | 目标 | 前置依赖 | 脚本支持 | 完成判定 |
|---|---|---|---|---|
| P0 | 建立审计基线 | 无 | `refactor-p0-audit.ps1` | 6 份审计报告已生成并人工审阅 |
| P0.5 | 删除旧 quicksettings 与完成态 flag | P0 | `refactor-p05-delete.ps1` | 旧入口、旧文件、`SETTINGS_PANEL_V5_ENABLED` 清零 |
| P1.1 | 移动 reader state / intent | P0.5 | 无 | state/intent 进入 `screen/`，编译通过 |
| P1.2 | 移动 ReaderScreen、overlay、effect | P1.1 | 无 | `overlays/`、`effects/` 不再作为目标包 |
| P2 | 移动 reader session 文件 | P1.1 | 无 | session 协调类进入 `session/` |
| P3 | 收敛 reader settings 逻辑 | P1.1 | 无 | settings 逻辑集中，只有 `panel/` 独立 |
| P1.3 | 最后移动 ReaderViewModel | P2 + P3 | 无 | ViewModel import 清理并低于阈值 |
| P4 | 正式化 quicksettings v5 | P3 | 无 | `v5` 目录和 `*V5` 类名消失 |
| P5 | 收敛业务 UI 到 feature | P4 | 无 | `ui/` 只保留基础 UI |
| P5.5 | 补 core/data 职责说明 | P5 可并行 | 无 | `core/data/README.md` 创建 |
| P6 | `core/canvasrecorder` 改为 `core/recorder` | P5 或独立批 | 无 | recorder 包名与 import 全部更新 |
| P7 | 重组 `core/reader` engine | P6 | 无 | 根目录文件归类到 engine/text/model/session 等 |
| P8 | 归并 sync 包与 `core/sync` | P5 | 无 | 同步域 UI/领域边界明确 |

---

## 3. P0 审计任务

目标：建立迁移基线，避免移动文件后遗漏 DI、导航、测试和跨域模型调用点。

- [ ] **T-P0-01** 校验重构脚本语法
  - 命令：`.\scripts\_validate-syntax.ps1`
  - 通过标准：`refactor-p0-audit.ps1`、`refactor-p05-delete.ps1` 均为 `SYNTAX OK`。

- [ ] **T-P0-02** 检查重构脚本编码
  - 命令：`.\scripts\_check-encoding.ps1`
  - 通过标准：确认当前 PowerShell 环境可以正确解析脚本；如需转换 BOM，先单独确认。

- [ ] **T-P0-03** 运行 P0 审计脚本
  - 命令：`.\scripts\refactor-p0-audit.ps1`
  - 输出目录：`docs/refactor-audit/`

- [ ] **T-P0-04** 审阅 `01-shuliappcontainer-imports.txt`
  - 重点：手写 DI 容器引用的类，后续移动必须同步 `core/ShuLiAppContainer.kt`。

- [ ] **T-P0-05** 审阅 `02-mainactivity-imports.txt`
  - 重点：顶层入口引用和 `MainActivity.kt` import。

- [ ] **T-P0-06** 审阅 `03-navigation-entrypoints.txt`
  - 重点：`NavHost`、`NavController`、`composable(...)`、`navigate(...)` 调用点。

- [ ] **T-P0-07** 审阅 `04-readingstatus-callers.txt`
  - 结论：`ReadingStatus` 第一阶段保留，后期如迁入 `core/data/model`，以该清单为修改范围。

- [ ] **T-P0-08** 审阅 `05-test-directory-layout.txt`
  - 结论：标记哪些测试目录镜像 main，哪些测试只需改 import。

- [ ] **T-P0-09** 审阅 `06-baseline-counts.txt`
  - 记录基线：`feature/reader` 根目录 `.kt` 数量、`ReaderViewModel.kt` import 行数、`v5` 命中数。

- [ ] **T-P0-10** 输出 P0 决策记录
  - 建议位置：`docs/refactor-audit/00-review-notes.md`
  - 内容：审计结论、已知风险、P0.5 是否可启动。

---

## 4. P0.5 死代码清理任务

目标：在大规模移动前减少旧 quicksettings 分支和重复 UI。

高风险说明：本阶段涉及删除文件和脚本内置 git 操作，实际执行前必须确认。

- [ ] **T-P05-01** 运行 P0.5 dry-run
  - 命令：`.\scripts\refactor-p05-delete.ps1 -DryRun`
  - 通过标准：9 个候选项风险已看清，无未知高引用项。

- [ ] **T-P05-02** 确认删除策略
  - 必须确认：不再保留旧设置面板回退路径。
  - 必须确认：`ReaderOverlayPanels.kt` 只保留新版设置面板入口。

- [ ] **T-P05-03** 删除 `QuickSettingsSheet.kt`
  - 候选项：脚本 item 1
  - 验证：`QuickSettingsSheet` 不再被 main/test/androidTest 引用。

- [ ] **T-P05-04** 删除旧 `HeaderFooterCustomizationPanel.kt`
  - 候选项：脚本 item 2
  - 验证：无旧 quicksettings 引用残留。

- [ ] **T-P05-05** 删除旧 `InteractionPanel.kt`
  - 候选项：脚本 item 3

- [ ] **T-P05-06** 删除旧 `LayoutPanel.kt`
  - 候选项：脚本 item 4

- [ ] **T-P05-07** 删除旧 `SettingsPanel.kt`
  - 候选项：脚本 item 5
  - 注意：不要误删 `SettingsPanelV5.kt`，它属于 P4 正式化。

- [ ] **T-P05-08** 处理 `SharedComponents.kt`
  - 候选项：脚本 item 6
  - 注意：脚本标记为人工判断；若 v5 仍引用其中函数，应先迁出共享函数再删除。

- [ ] **T-P05-09** 删除旧 `StylePanel.kt`
  - 候选项：脚本 item 7

- [ ] **T-P05-10** 删除 `ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED`
  - 候选项：脚本 item 8
  - 同步修改：`ReaderOverlayPanels.kt` 删除旧分支。
  - 同步修改：`ReaderFeatureFlagsTest.kt` 删除该 flag 的断言，保留其它 flag 测试。

- [ ] **T-P05-11** 删除或保留决策 `ReaderSessionState.kt`
  - 候选项：脚本 item 9
  - 删除条件：`rg "ReaderSessionState"` 只命中自身，且会话状态已有替代承载。
  - 若不删：在文档中说明保留原因和后续归属。

- [ ] **T-P05-12** 运行 P0.5 实际脚本
  - 命令：`.\scripts\refactor-p05-delete.ps1`
  - 可续跑：`.\scripts\refactor-p05-delete.ps1 -StartFrom <first-failed-id>`
  - 注意：执行前确认脚本的 git 操作风险。

- [ ] **T-P05-13** P0.5 后验收
  - 命令：
    ```powershell
    rg -n "SETTINGS_PANEL_V5_ENABLED|QuickSettingsSheet|\b\w+V5\b" app/src/main/java app/src/test app/src/androidTest
    ./gradlew.bat :app:compileDebugKotlin
    ./gradlew.bat :app:compileDebugAndroidTestKotlin
    ```
  - 通过标准：`SETTINGS_PANEL_V5_ENABLED` 与 `QuickSettingsSheet` 0 命中；`*V5` 仅剩 P4 待处理项。

---

## 5. P1 reader screen 拆分任务

### P1.1 state / intent

- [ ] **T-P11-01** 创建 `feature/reader/screen/`
- [ ] **T-P11-02** 移动 `ReaderUiState.kt` 到 `screen/`
- [ ] **T-P11-03** 移动 `ReaderIntent.kt` 到 `screen/`
- [ ] **T-P11-04** 移动 `ReaderPageState.kt` 到 `screen/`
- [ ] **T-P11-05** 移动 `ReaderBookmarkState.kt` 到 `screen/`
- [ ] **T-P11-06** 移动 `ReaderOverlayState.kt` 到 `screen/`
- [ ] **T-P11-07** 移动 `ReaderSearchState.kt` 到 `screen/`
- [ ] **T-P11-08** 更新 main/test/androidTest import
  - 已知测试：`app/src/test/java/com/shuli/reader/feature/reader/ReaderIntentTest.kt`
- [ ] **T-P11-09** 编译验证
  - 命令：
    ```powershell
    ./gradlew.bat :app:compileDebugKotlin
    ./gradlew.bat :app:compileDebugUnitTestKotlin
    ```
- [ ] **T-P11-10** 记录实际耗时
  - 用途：作为 P1-P4 工作量估算校准点。

### P1.2 ReaderScreen / overlay / effect

- [ ] **T-P12-01** 移动 `ReaderScreen.kt` 到 `feature/reader/screen/`
- [ ] **T-P12-02** 移动 `ReaderTopBar.kt` 到 `feature/reader/screen/`
- [ ] **T-P12-03** 移动 `ReaderBottomBar.kt` 到 `feature/reader/screen/`
- [ ] **T-P12-04** 移动 `ReaderOverlayPanels.kt` 到 `feature/reader/screen/`
- [ ] **T-P12-05** 移动 `ReaderCanvasEffects.kt` 到 `feature/reader/render/`
- [ ] **T-P12-06** 删除空的 `feature/reader/overlays/` 目录
- [ ] **T-P12-07** 删除空的 `feature/reader/effects/` 目录
- [ ] **T-P12-08** 检查导航和入口引用
  - 重点：`MainActivity.kt`、导航注册点、reader screen 创建位置。
- [ ] **T-P12-09** 编译验证
  - 命令：
    ```powershell
    ./gradlew.bat :app:compileDebugKotlin
    ./gradlew.bat :app:compileDebugAndroidTestKotlin
    ```

### P1.3 ReaderViewModel 最后移动

> 前置依赖：P2 + P3 完成。

- [ ] **T-P13-01** 移动 `ReaderViewModel.kt` 到 `feature/reader/screen/`
- [ ] **T-P13-02** 清理 `ReaderViewModel.kt` 未使用 import
- [ ] **T-P13-03** 确认 import 行数 `< 70`
- [ ] **T-P13-04** 更新 `ReaderViewModelTest` 和相关 reader 测试 import
- [ ] **T-P13-05** 全量编译验证
  - 命令：
    ```powershell
    ./gradlew.bat :app:compileDebugKotlin
    ./gradlew.bat :app:compileDebugUnitTestKotlin
    ./gradlew.bat :app:compileDebugAndroidTestKotlin
    ```

---

## 6. P2 reader session 任务

目标：把阅读会话、分页协调、导航协调等流程对象从 `feature/reader` 根目录移出。

- [ ] **T-P2-01** 创建 `feature/reader/session/`
- [ ] **T-P2-02** 移动 `BookSessionManager.kt`
- [ ] **T-P2-03** 移动 `ChapterPaginationCoordinator.kt`
- [ ] **T-P2-04** 移动 `ReaderNavigationCoordinator.kt`
- [ ] **T-P2-05** 移动 `ReaderProgressResolver.kt`
- [ ] **T-P2-06** 移动 `ReaderSearchManager.kt`
- [ ] **T-P2-07** 移动 `BookmarkNotesManager.kt`
- [ ] **T-P2-08** 移动 `ReaderPresetManager.kt`
- [ ] **T-P2-09** 移动 `FontImportManager.kt`
- [ ] **T-P2-10** 更新 `ReaderViewModel.kt` import，但暂不移动 ViewModel
- [ ] **T-P2-11** 更新对应单测 import
- [ ] **T-P2-12** 编译验证
  - 命令：
    ```powershell
    ./gradlew.bat :app:compileDebugKotlin
    ./gradlew.bat :app:compileDebugUnitTestKotlin
    ```

---

## 7. P3 reader settings 任务

目标：settings 逻辑集中在 `feature/reader/settings/`，仅 UI 面板独立到 `panel/`。

- [ ] **T-P3-01** 移动 `ReaderSettingsManager.kt` 到 `feature/reader/settings/`
- [ ] **T-P3-02** 移动 `ReaderSettingsResolver.kt` 到 `feature/reader/settings/`
- [ ] **T-P3-03** 移动 `ReaderPreferenceMonitor.kt` 到 `feature/reader/settings/`
- [ ] **T-P3-04** 保持 `ReaderSettingRegistry.kt` 在 `feature/reader/settings/`
- [ ] **T-P3-05** 保持 `SettingsTab.kt` 在 `feature/reader/settings/`
- [ ] **T-P3-06** 保持 `PresetSnapshot.kt` 与 `ReaderPrefsLayers.kt` 在 `feature/reader/settings/`
- [ ] **T-P3-07** 保持 `GestureConfig.kt`、`HapticFeedbackHelper.kt`、`EyeCareTimer.kt`、`BlueLightPreset.kt` 在 `feature/reader/settings/`
- [ ] **T-P3-08** 若 P0.5 未删除 `ReaderSessionState.kt`，重新评估归属或删除
- [ ] **T-P3-09** 更新 reader settings 相关测试 import
- [ ] **T-P3-10** 编译验证
  - 命令：
    ```powershell
    ./gradlew.bat :app:compileDebugKotlin
    ./gradlew.bat :app:compileDebugUnitTestKotlin
    ```

---

## 8. P4 quicksettings v5 正式化任务

目标：将 `component/quicksettings/v5` 上移为正式 reader settings panel，并去除迁移版本号命名。

- [ ] **T-P4-01** 创建 `feature/reader/settings/panel/`
- [ ] **T-P4-02** 创建 `feature/reader/settings/panel/controls/`
- [ ] **T-P4-03** 创建 `feature/reader/settings/panel/tabs/`
- [ ] **T-P4-04** `SettingsPanelV5.kt` -> `ReaderSettingsPanel.kt`
- [ ] **T-P4-05** `SettingsPanelV5Modal.kt` -> `ReaderSettingsModal.kt`
- [ ] **T-P4-06** `SettingsCardV5.kt` -> `ReaderSettingsCard.kt`
- [ ] **T-P4-07** `SettingsPeekContent.kt` -> `ReaderSettingsPeek.kt`
- [ ] **T-P4-08** `SettingRow.kt` -> `ReaderSettingRow.kt`
- [ ] **T-P4-09** 移动 `GestureZoneEditorOverlay.kt`
- [ ] **T-P4-10** 移动 `SlotMatrix.kt`
- [ ] **T-P4-11** 移动 `ThemeSwatchRow.kt`
- [ ] **T-P4-12** 移动 `VisualMarginControl.kt`
- [ ] **T-P4-13** 移动 `CustomThemeDialog.kt`
- [ ] **T-P4-14** 移动 `controls/*`
- [ ] **T-P4-15** 移动 `tabs/*`
- [ ] **T-P4-16** 更新所有 import 和文件内 package
- [ ] **T-P4-17** 删除空的 `component/quicksettings/v5/`
- [ ] **T-P4-18** 验收 `V5` 命名
  - 命令：
    ```powershell
    rg -n "\b\w+V5\b|SETTINGS_PANEL_V5_ENABLED|QuickSettingsSheet|component\.quicksettings" app/src/main/java app/src/test app/src/androidTest
    ```
  - 通过标准：正式源码无 `*V5` 类名/文件名；旧 quicksettings 0 命中。
- [ ] **T-P4-19** 编译验证
  - 命令：
    ```powershell
    ./gradlew.bat :app:compileDebugKotlin
    ./gradlew.bat :app:compileDebugAndroidTestKotlin
    ```

---

## 9. P5 ui 业务页面收敛任务

目标：`ui/` 只保留跨 feature 基础 UI，业务 UI 回到对应 feature。

- [ ] **T-P5-01** 创建 `feature/sync/SyncFeatureEntry.kt`
- [ ] **T-P5-02** 设计并接入 sync feature 导航入口
  - 检查：`MainActivity.kt`、现有导航注册点、设置页入口。
- [ ] **T-P5-03** 迁移 `ui/settings/sync/` 到 `feature/sync/settings/`
- [ ] **T-P5-04** 迁移 `ui/settings/crypto/` 到 `feature/settings/crypto/`
- [ ] **T-P5-05** 迁移 `ui/conflict/` 到 `feature/sync/conflict/`
- [ ] **T-P5-06** 迁移 `ui/devices/` 到 `feature/sync/devices/`
- [ ] **T-P5-07** 迁移 `ui/log/` 到 `feature/sync/log/`
- [ ] **T-P5-08** 迁移 `ui/export/` 到 `feature/bookshelf/export/ui/`
- [ ] **T-P5-09** 确认 ViewModel 随 Screen 一起移动
  - 覆盖：`ConflictDialogViewModel`、`DeviceManagementViewModel`、`SyncLogViewModel`、`SyncSummaryViewModel`、`CloudSyncSettingsViewModel`、`EncryptionManagementViewModel`。
- [ ] **T-P5-10** 保留 `ui/theme/` 与 `ui/testing/`
- [ ] **T-P5-11** 若有通用 UI，统一放入 `ui/component/`
- [ ] **T-P5-12** 更新对应测试包路径和 import
- [ ] **T-P5-13** 编译验证
  - 命令：
    ```powershell
    ./gradlew.bat :app:compileDebugKotlin
    ./gradlew.bat :app:compileDebugAndroidTestKotlin
    ```

---

## 10. P5.5 core/data 职责说明任务

目标：先明确数据层边界，不提前移动 Room / DAO / Repository。

- [ ] **T-P55-01** 创建 `app/src/main/java/com/shuli/reader/core/data/README.md`
- [ ] **T-P55-02** 说明 `preferences/` 职责
- [ ] **T-P55-03** 说明 `database/` 职责
- [ ] **T-P55-04** 说明 `repository/` 职责
- [ ] **T-P55-05** 标注当前过渡结构：`core/database`、`core/repository` 尚未移动
- [ ] **T-P55-06** 文档检查，无需 Gradle 编译

---

## 11. P6 core recorder 重命名任务

目标：`core/canvasrecorder` 改为通用 `core/recorder`，脱离 reader 语义。

- [ ] **T-P6-01** 创建 `core/recorder/`
- [ ] **T-P6-02** 移动 `core/canvasrecorder/*` 到 `core/recorder/`
- [ ] **T-P6-03** 包名改为 `com.shuli.reader.core.recorder`
- [ ] **T-P6-04** `CanvasRecorderImpl.kt` 评估重命名为 `CanvasRecorderBitmapImpl.kt`
- [ ] **T-P6-05** 保持 `CanvasRecorderApi23Impl.kt`
- [ ] **T-P6-06** 保持 `CanvasRecorderApi29Impl.kt`
- [ ] **T-P6-07** 更新 reader render 链路 import
- [ ] **T-P6-08** 更新 CanvasRecorder 相关测试 import
- [ ] **T-P6-09** 验证旧包 0 命中
  - 命令：`rg -n "core\.canvasrecorder|canvasrecorder" app/src/main/java app/src/test app/src/androidTest`
- [ ] **T-P6-10** 编译与测试验证
  - 命令：
    ```powershell
    ./gradlew.bat :app:compileDebugKotlin
    ./gradlew.bat :app:testDebugUnitTest --tests "*reader*" --tests "*CanvasRecorder*" --tests "*Recorder*"
    ./gradlew.bat :app:compileDebugAndroidTestKotlin
    ```

---

## 12. P7 core/reader engine 重组任务

目标：清理 `core/reader` 根目录，把引擎、文本、模型、输入、选区、缓存边界分清。

- [ ] **T-P7-01** 创建 `core/reader/engine/`
- [ ] **T-P7-02** 创建 `core/reader/engine/cache/`
- [ ] **T-P7-03** 创建 `core/reader/engine/input/`
- [ ] **T-P7-04** 创建 `core/reader/engine/selection/`
- [ ] **T-P7-05** 移动 `ReaderCanvasView.kt`
- [ ] **T-P7-06** 移动 `ReaderPageRenderer.kt`
- [ ] **T-P7-07** 移动 `Paginator.kt`
- [ ] **T-P7-08** 移动 `RenderApplierTarget.kt`
- [ ] **T-P7-09** 移动 `CanvasVisualParamsManager.kt`
- [ ] **T-P7-10** 移动 `PageRenderContext.kt`
- [ ] **T-P7-11** 移动 `RenderContext.kt`
- [ ] **T-P7-12** 移动 `SlotResolver.kt`
- [ ] **T-P7-13** 移动 `PaginationStrategy.kt`
- [ ] **T-P7-14** 移动 `VerticalPaginationStrategy.kt`
- [ ] **T-P7-15** 移动 `TouchZone.kt` 到 `engine/input/`
- [ ] **T-P7-16** 移动 `animation/` 到 `engine/animation/`
- [ ] **T-P7-17** 移动 `cache/` 到 `engine/cache/`
- [ ] **T-P7-18** 移动 `canvas/PageBitmapCache.kt` 到 `engine/cache/`
- [ ] **T-P7-19** 移动 `canvas/CanvasTouchHandler.kt` 到 `engine/input/`
- [ ] **T-P7-20** 移动 `canvas/CanvasTextSelection.kt` 到 `engine/selection/`
- [ ] **T-P7-21** 移动文本测量和断词文件到 `core/reader/text/`
  - 覆盖：`TextMeasurer.kt`、`SimpleTextMeasurer.kt`、`AndroidTextMeasurer.kt`、`WidthWindow.kt`、`HyphenationEngine.kt`、`BionicSegments.kt`。
- [ ] **T-P7-22** 移动 `HeaderFooterModels.kt` 到 `core/reader/model/`
- [ ] **T-P7-23** 评估并移动 `ChapterProvider.kt` 到 `feature/reader/session/`
- [ ] **T-P7-24** 评估并移动 `ReadingStateManager.kt` 到 `feature/reader/session/`
- [ ] **T-P7-25** 删除或替代 `Placeholder.kt`
- [ ] **T-P7-26** 明确保留 `core/reader/layout/`
- [ ] **T-P7-27** 更新所有 import 和测试包路径
- [ ] **T-P7-28** 编译验证
  - 命令：
    ```powershell
    ./gradlew.bat :app:compileDebugKotlin
    ./gradlew.bat :app:compileDebugUnitTestKotlin
    ./gradlew.bat :app:compileDebugAndroidTestKotlin
    ```

---

## 13. P8 sync 包归并任务

目标：同步域保持纯 Kotlin 领域/协议/worker，Compose UI 已由 P5 迁到 feature。

- [ ] **T-P8-01** 移动 `sync/conflict/` 到 `sync/engine/conflict/`
- [ ] **T-P8-02** 移动 `sync/dirty/` 到 `sync/engine/dirty/`
- [ ] **T-P8-03** 移动 `sync/hash/` 到 `sync/engine/hash/`
- [ ] **T-P8-04** 移动 `sync/manifest/` 到 `sync/engine/manifest/`
- [ ] **T-P8-05** 移动 `sync/state/` 到 `sync/engine/state/`
- [ ] **T-P8-06** 移动 `sync/transport/` 到 `sync/network/transport/`
- [ ] **T-P8-07** 移动 `sync/throttle/` 到 `sync/network/throttle/`
- [ ] **T-P8-08** 移动 `sync/export/` 到 `sync/backup/`
- [ ] **T-P8-09** 移动 `sync/notification/` 到 `sync/worker/notification/`
- [ ] **T-P8-10** 决策 `core/sync/WebDavClient.kt`
  - 路线 A：迁入 `sync/network/webdav/`。
  - 路线 B：切到 `SyncWebDavClient` 后删除旧实现。
- [ ] **T-P8-11** 决策 `core/sync/WebDavSyncManager.kt`
  - 路线 A：迁入 `sync/engine/`。
  - 路线 B：由新同步 engine 替代后删除。
- [ ] **T-P8-12** 同步更新 `ShuLiAppContainer.kt`
- [ ] **T-P8-13** 同步更新 `feature/sync/settings` 或 `feature/settings/sync` 下的 ViewModel
- [ ] **T-P8-14** 迁移或更新 `app/src/test/java/com/shuli/reader/core/sync`
- [ ] **T-P8-15** 迁移或更新 `app/src/androidTest/java/com/shuli/reader/core/sync`
- [ ] **T-P8-16** 验证 Compose UI 未进入 `sync/`
  - 命令：`rg -n "androidx\.compose|@Composable" app/src/main/java/com/shuli/reader/sync`
- [ ] **T-P8-17** 编译与同步测试验证
  - 命令：
    ```powershell
    ./gradlew.bat :app:compileDebugKotlin
    ./gradlew.bat :app:compileDebugUnitTestKotlin
    ./gradlew.bat :app:testDebugUnitTest --tests "*sync*"
    ```

---

## 14. 第一阶段验收清单

第一阶段范围：P0.5-P4。

- [ ] **T-AC1-01** `feature/reader/` 根目录 `.kt` 文件数 `< 5`
  - 命令：`Get-ChildItem "app/src/main/java/com/shuli/reader/feature/reader" -File -Filter "*.kt"`

- [ ] **T-AC1-02** `ReaderViewModel.kt` import 行数 `< 70`

- [ ] **T-AC1-03** `component/quicksettings/v5/` 目录不存在

- [ ] **T-AC1-04** 旧 `component/quicksettings/` 目录不存在或无旧 `.kt` 文件

- [ ] **T-AC1-05** `SETTINGS_PANEL_V5_ENABLED` 0 命中

- [ ] **T-AC1-06** `QuickSettingsSheet` 0 命中

- [ ] **T-AC1-07** 正式源码无 `*V5` 类名/文件名
  - 命令：
    ```powershell
    rg -n "SETTINGS_PANEL_V5_ENABLED|QuickSettingsSheet|\b\w+V5\b" app/src/main/java app/src/test app/src/androidTest
    ```

- [ ] **T-AC1-08** 编译与测试通过
  - 命令：
    ```powershell
    ./gradlew.bat :app:compileDebugKotlin
    ./gradlew.bat :app:testDebugUnitTest
    ./gradlew.bat :app:compileDebugAndroidTestKotlin
    ```
  - 允许例外：仅允许已记录的预存失败。

---

## 15. 最终验收清单

- [ ] **T-ACF-01** `ui/` 只保留 `theme/`、`testing/`、可选 `component/`
- [ ] **T-ACF-02** `sync/` 不包含 Compose UI
- [ ] **T-ACF-03** `core/canvasrecorder` 不存在
- [ ] **T-ACF-04** `core/recorder` 包名和 import 全部生效
- [ ] **T-ACF-05** `core/reader` 根目录不再平铺大量引擎文件
- [ ] **T-ACF-06** `core/sync` 已决策：迁移到新 sync 子系统或保留原因已记录
- [ ] **T-ACF-07** `core/data/README.md` 已说明过渡结构
- [ ] **T-ACF-08** main/test/androidTest package 与 import 一致
- [ ] **T-ACF-09** 全量编译通过
  - 命令：
    ```powershell
    ./gradlew.bat :app:compileDebugKotlin
    ./gradlew.bat :app:compileDebugUnitTestKotlin
    ./gradlew.bat :app:compileDebugAndroidTestKotlin
    ```

