# P0 审计结论与决策记录

> 生成时间：2026-06-15

## 审计结论

### 1. ShuLiAppContainer 依赖（报告 01）

- 13 条 import + 6 条内联全限定名
- **风险点**：`core/sync/` 下的 `WebDavSyncManager`、`WebDavClient` 等在容器中通过内联全限定名引用
- **结论**：P1-P8 移动文件时必须同步更新此容器

### 2. MainActivity 依赖（报告 02）

- 16 条 import
- 引用了 4 个 Screen（Bookshelf、Reader、Settings、Stats）+ 4 个 ViewModel
- **结论**：移动 Screen/ViewModel 时必须同步更新

### 3. 导航机制（报告 03）

- 项目未使用 Jetpack Navigation Compose
- 使用 sealed class `ActiveScreen` + `when` 表达式实现手动导航
- **结论**：移动 Screen 文件只需同步 `MainActivity.kt` import，无 NavGraph 注册点

### 4. ReadingStatus 跨域使用（报告 04）

- 20 个文件引用，跨越 bookshelf/reader/stats/repository 4 个域
- **结论**：第一阶段保留不动，后期迁入 `core/data/model`

### 5. 测试目录镜像度（报告 05）

- `app/src/test/`：测试目录与 main 镜像度高，`feature/reader/`、`sync/`、`ui/` 均有对应测试
- `app/src/androidTest/`：`core/canvasrecorder/`、`feature/reader/component/quicksettings/v5/` 有对应测试
- **结论**：P1-P8 移动时测试文件需同步移动或更新 import

### 6. 基线计数（报告 06）

| 指标 | 当前值 | 目标值 | 状态 |
|------|--------|--------|------|
| feature/reader/ 根目录 .kt 文件数 | 19 | < 5 | ❌ |
| ReaderViewModel.kt import 行数 | 57 | < 70 | ✅ |
| component/quicksettings/v5/ 存在 | True | False | ❌ |
| component/quicksettings/ 旧文件数 | 6 | 0 | ❌ |
| SETTINGS_PANEL_V5_ENABLED 命中(main) | 2 | 0 | ❌ |
| QuickSettingsSheet 命中(main) | 3 | 0 | ❌ |

## 已知风险

1. **core/sync 内联引用**：`ShuLiAppContainer.kt` 中 `core/sync/` 的类通过全限定名引用，移动时容易遗漏
2. **androidTest 中的 quicksettings 测试**：`QuickSettingsPanelTest.kt`、`FontPreviewRowTest.kt`、`SlotMatrixTest.kt`、`InkControlsTest.kt` 需在 P0.5/P4 同步更新
3. **ReaderFeatureFlagsTest.kt**：包含 `SETTINGS_PANEL_V5_ENABLED` 断言，P0.5 需同步删除

## P0.5 启动决策

**结论：可以启动 P0.5**

理由：
- 旧 quicksettings 仅被 `ReaderOverlayPanels.kt` 的 `if (SETTINGS_PANEL_V5_ENABLED)` 分支引用
- 删除旧分支后，旧文件引用归零
- `ReaderSessionState.kt` 无外部引用，可安全删除
- 测试影响可控（仅需更新 `ReaderFeatureFlagsTest.kt` 和 androidTest 快照）

**下一步**：执行 `.\scripts\refactor-p05-delete.ps1 -DryRun` 查看详细影响
