# 16 - Macrobenchmark 与可访问性执行指南

本文档指导第三档"性能 + 真机验收"专项的本地执行流程。所有命令在 `ShuLi-Reader/` 根目录下运行。

## 一、前置条件

### 1.1 设备要求

- **Android 13+** 物理设备或 API 33+ 模拟器（与 `targetSdk = 35` / `minSdk = 31` 范围一致）
- 设备已开启 **USB 调试**
- 已通过 `adb devices` 确认设备连接
- **关闭** 系统级"开发者选项 → 动画缩放"为非 0 值（否则触发 `PageDelegateFactory` 自动降级，影响 60fps 验收）
- 推荐至少 **4 GB RAM**，避免 OOM 干扰内存指标
- 屏幕保持常亮（`adb shell svc power stayon true`）

### 1.2 构建产物

```powershell
./gradlew.bat :app:assembleDebug
./gradlew.bat :benchmark:assembleDebug
```

确认产物：
- `app/build/outputs/apk/debug/app-debug.apk`
- `benchmark/build/outputs/apk/androidTest/debug/benchmark-debug-androidTest.apk`

### 1.3 fixture 准备

100MB TXT fixture 生成器位于 :app androidTest 源集，按需在设备 `externalCacheDir` 写入：

```powershell
./gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.shuli.reader.benchmark.FixtureGenerator
```

完成后设备文件位置：

```
/sdcard/Android/data/com.shuli.reader.debug/cache/test_100mb.txt
```

fixture 已存在且尺寸 95-105MB 时会跳过重新生成。

## 二、Macrobenchmark 场景命令

### 2.1 全部场景

```powershell
./gradlew.bat :benchmark:connectedDebugAndroidTest
```

### 2.2 单场景

| 场景 | 测试方法 | 主要指标 | 任务编号 |
|------|---------|---------|---------|
| 冷启动 | `StartupMacrobenchmark.coldStartup` | `StartupTimingMetric` | T12.1 |
| 热启动 | `StartupMacrobenchmark.warmStartup` | `StartupTimingMetric` | T12.1 |
| 温启动 | `StartupMacrobenchmark.hotStartup` | `StartupTimingMetric` | T12.1 |
| 进入阅读首屏 | `ReaderPerformanceBenchmark.firstFrameAfterColdStart` | `StartupTimingMetric` + `FrameTimingMetric` | T12.1 |
| 连续翻页 50×2 | `ReaderPerformanceBenchmark.continuousPaging` | `FrameTimingMetric` | T12.2 |
| 滚动目录 | `ReaderPerformanceBenchmark.directoryScroll` | `FrameTimingMetric` | T0.5 |
| 100MB 元数据导入 | `ReaderPerformanceBenchmark.largeFileImport` | `StartupTimingMetric` + `FrameTimingMetric` | T12.3 |

单测命令示例：

```powershell
./gradlew.bat :benchmark:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.shuli.reader.benchmark.ReaderPerformanceBenchmark#continuousPaging
```

## 三、结果定位

Macrobenchmark 结果输出在两个位置：

```
benchmark/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/<DeviceID>/
  ├─ *.perfetto-trace          # Perfetto 跟踪，可在 https://ui.perfetto.dev 加载
  └─ *.json                    # 指标 JSON（含 P50/P95/P99 时序）
```

```
build/reports/androidTests/connected/debug/index.html   # JUnit 风格 HTML 报告
```

## 四、P0 性能门禁判断

将基准 JSON 中的指标对照下表：

| 门禁 | 指标位置 | 判定 |
|------|---------|------|
| 首屏 < 500ms | `firstFrameAfterColdStart` 的 `timeToInitialDisplayMs` P95 | < 500 |
| 翻页 60fps | `continuousPaging` 的 `frameDurationCpuMs` P99 | < 16.6 |
| 目录滚动顺滑 | `directoryScroll` 的 `frameOverrunMs` P95 | < 0 |
| 100MB 元数据 | `largeFileImport` 的 `timeToInitialDisplayMs` P95 | < 8000（业务可接受） |
| 内存 < 150MB | Profiler 录屏（非 Macrobenchmark 范畴） | 见 §5 |

## 五、内存验收（非 Macrobenchmark）

100MB 长时间阅读内存增长需要单独录屏：

1. Android Studio Profiler → Memory → 选 `com.shuli.reader.debug`
2. 跑 `largeFileImport` 完成导入
3. 手动点击书架的 `test_100mb` 进入阅读
4. 启动后台 timer，30 分钟内每 5 分钟翻页约 20 次
5. 检查内存趋势：稳态在 100–150MB 之间应平稳，**不可见连续上升**
6. 离开阅读页（返回书架）后内存应回落 30MB 以上

## 六、可访问性 / TalkBack 验收

### 6.1 启用 TalkBack

```
设置 → 辅助功能 → TalkBack → 开启
```

或通过 ADB：

```powershell
adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
adb shell settings put secure accessibility_enabled 1
```

### 6.2 验收清单

| 流程 | 期望 TalkBack 朗读 |
|------|------------------|
| 导入图书按钮 | "导入图书"/"Import book" |
| 进入阅读页 | "书里阅读器" + 章节标题 |
| 底部 5 个按钮焦点 | 依次朗读：上一章 / 下一章 / 目录 / 亮度调节 / 阅读器显示偏好 |
| 翻页 | 朗读新页首段（依赖 Canvas 语义） |
| 打开设置 | 设置项标题正确朗读 |

ReaderScreen 底部按钮的 `contentDescription` 已在 T13.4 中补齐（`AppStrings.previousChapter/nextChapter/directoryTab/brightness/readerPreferences`）。

### 6.3 字体缩放验收

```powershell
adb shell settings put system font_scale 1.30
```

启动 App 后逐屏检查文字溢出/工具栏遮挡。验毕恢复：

```powershell
adb shell settings put system font_scale 1.0
```

## 七、StrictMode 主线程检测

`ShuLiApplication` 在 `BuildConfig.DEBUG` 时已自动启用 StrictMode。验收方式：

```powershell
adb logcat -s StrictMode
```

执行书架打开、搜索、滚动、进入阅读等操作。如果出现 `StrictModeDiskReadViolation` / `StrictModeNetworkViolation` 在主线程，则违反 T12.4 验收，需将相关 IO 迁至 `Dispatchers.IO`。

## 八、WebDAV 真实环境（可选）

第三档真机验收清单中"两端 WebDAV 进度同步"需要外部服务：

1. 启动本地 WebDAV 测试服务（推荐 Nextcloud Docker）：
   ```bash
   docker run -d -p 8080:80 nextcloud:latest
   ```
2. 在 App 设置 → 同步 → 输入 `http://<HOST_IP>:8080/remote.php/dav/files/admin/`、用户名、密码
3. 设备 A 阅读到任一进度，设备 B 拉取后预期定位到相同章节

## 九、故障排查

| 现象 | 可能原因 | 处理 |
|------|---------|------|
| `Benchmark cannot find test_100mb.txt` | fixture 未生成 | 重新跑 §1.3 |
| `firstFrameAfterColdStart` P95 > 1500ms | 跑在低端设备 / 模拟器 | 切换主流真机 / 关闭后台进程 |
| `FrameTimingMetric` 抖动剧烈 | 系统动画缩放未关 / 后台应用占用 GPU | 检查 §1.1 |
| `pm clear` 在 Android 14 失败 | OEM 权限限制 | 改用 `am force-stop` + 数据手动清理 |
| TalkBack 朗读为"按钮按钮按钮" | IconButton 内 Icon `contentDescription = null` | 见 T13.4 审计清单 |

---

## 输出对照

完成本指南各步骤后，应汇集以下产物作为第三档验收材料：

- `benchmark/build/outputs/connected_android_test_additional_output/.../perfetto-trace`（每场景一份）
- `benchmark/build/outputs/connected_android_test_additional_output/.../metrics.json`（每场景一份）
- Profiler 录屏的截图或 hprof（内存验收）
- TalkBack 流程演示录屏
- StrictMode logcat 抓取（无主线程违规）

材料齐全即可宣告第三档完成。
