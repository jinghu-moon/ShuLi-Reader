# 14 - Release 质量清单

> 本文档记录 ShuLi-Reader 当前 Release 候选前的可验证状态、发布前检查项、隐私权限说明、性能基线入口和已知限制。它与 `docs/13-implementation-task-list.md` 配套使用，只记录稳定事实，不替代设计文档。

## 一、构建状态

### 1.1 已验证命令

当前仓库使用 Gradle Wrapper 构建。Release 前至少执行：

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:compileDebugAndroidTestKotlin
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:assembleRelease
./gradlew.bat :benchmark:assembleDebug
```

### 1.2 Release 构建配置

- `app/build.gradle.kts` 已开启 `release.isMinifyEnabled = true` 与 `release.isShrinkResources = true`。
- Release 使用 `getDefaultProguardFile("proguard-android-optimize.txt")` 与 `app/proguard-rules.pro`。
- `app/proguard-rules.pro` 已保留 Room 实体与 Kotlin Serialization serializer。
- ABI split 已开启，覆盖 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`，并生成 universal APK。
- `debug` 使用 `.debug` applicationId 后缀，Release 使用正式 `com.shuli.reader`。

## 二、权限与隐私

### 2.1 当前 Manifest 权限

| 权限 | 用途 | 说明 |
|------|------|------|
| `android.permission.INTERNET` | WebDAV 进度同步 | 普通权限，安装时自动授予，仅在同步功能调用网络时使用 |

### 2.2 文件访问

- 书籍导入使用 Android Storage Access Framework：`OpenMultipleDocuments` 与 `OpenDocumentTree`。
- 不在启动时请求外部存储权限。
- 本地书籍默认复制到应用私有目录，删除 App 后由系统清理。
- 用户选择不复制时，读取依赖用户授权的 URI 或文件可访问性。

### 2.3 WebDAV 数据

- WebDAV 同步当前只定义进度同步对象 `BookProgress`，默认不上传书籍原文。
- 同步数据包含书籍 ID、章节索引、章节内字符偏移、阅读进度、更新时间与设备标识。
- 密码存储在正式设置入口落地前必须使用 Android Keystore 或等价加密方案，不得明文写入 DataStore。

### 2.4 TTS 与阅读统计

- TTS 使用系统 `TextToSpeech`，不上传正文到第三方服务。
- 阅读统计用于本地阅读时长、进度与会话聚合。
- 在用户启用同步前，统计和进度数据仅保存在本机数据库。

## 三、性能基线入口

当前已具备以下代码级性能采样能力：

- `FrameTimeRecorder`：记录帧间隔、总帧数、掉帧数、最大帧耗时与平均帧耗时。
- `StartupTrace`：记录启动或进入阅读链路的阶段耗时。
- `RuntimeMemorySampler`：采样 JVM 当前已用内存与最大内存。
- `ReaderCanvasView.releaseBitmaps()`：在页面替换、尺寸变化和 View detach 时释放位图缓冲。

Release 前需要补充真机或模拟器上的人工/自动记录：

| 场景 | 目标 |
|------|------|
| 书架点击进入阅读首屏 | 主流设备 < 500ms |
| 连续翻页 | 稳定 60fps，单帧 < 16.6ms |
| 100MB+ TXT 阅读 30 分钟 | 无持续内存增长，常规阅读 < 150MB |
| 书架 1000 本搜索/筛选 | 不阻塞主线程 |

## 四、配色与主题发布检查

- 非阅读界面使用 MoTu App Material 3 token。
- 阅读界面使用独立 `ReaderColorScheme`，Canvas 通过 `ReaderColorScheme.toCanvasThemeColors()` 接收轻量绘制色值。
- 默认阅读主题为 `PAPER`，背景 `#EAE5DC`，正文 `#2C231A`。
- 旧 Geist 命名与蓝色强调色不得回流到生产代码。
- OLED 只作为独立阅读主题，不作为默认夜间主题。

## 五、已知限制

- TTS 当前完成核心控制器和 Android `TextToSpeech` 适配，阅读页自动翻页、当前句高亮和后台真机行为仍需 UI/设备验收。
- WebDAV 当前完成最小协议客户端和进度冲突管理，账号设置界面与加密凭据持久化仍需接入。
- 截图回归、TalkBack 全流程和连接设备 UI 测试需要在具备设备环境时执行。
- Macrobenchmark 模块已建立，冷启动与热启动脚本已定义；首屏阅读和连续翻页仍需设备/数据 fixture 补全。

## 六、发布前最终门禁

- 单元测试全绿。
- Debug 与 Release 构建通过。
- AndroidTest 编译通过。
- benchmark 模块编译通过。
- Manifest 权限与实际入口一致。
- 无生产 UI 硬编码中文字符串，文案集中在 `AppStrings`。
- 无中文命名的生产函数、变量、类或文件。
- 无 `TODO`、`FIXME` 或过期占位注释残留在生产代码。
