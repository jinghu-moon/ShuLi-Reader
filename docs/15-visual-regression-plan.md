# 15 - 视觉回归与截图采集方案

> 本方案用于补齐 T0.4/T13 的截图回归入口。当前仓库已具备 Compose UI Test 编译基础和稳定 `UiTestTags`，截图采集仍需在具备模拟器或真机环境时执行，不在无设备环境下伪造验收结果。

## 一、目标

- 固定关键页面的视觉基线，防止配色、排版、动效状态和可访问性改动产生不可见回归。
- 覆盖 `docs/14-color-scheme.md` 定义的同源双体系：非阅读界面暖白 Material 3 token、阅读界面纸感棕 ReaderColorScheme。
- 截图只覆盖稳定页面状态，不依赖真实网络、时间、随机数据或设备私有字体。

## 二、测试标签规范

稳定入口统一维护在 `app/src/main/java/com/shuli/reader/ui/testing/UiTestTags.kt`。

当前核心标签：

- `BOOKSHELF_SCREEN`
- `BOOKSHELF_SEARCH_BUTTON`
- `BOOKSHELF_SORT_BUTTON`
- `BOOKSHELF_VIEW_MODE_BUTTON`
- `BOOKSHELF_MORE_BUTTON`
- `BOOKSHELF_IMPORT_FAB`
- `READER_SCREEN`
- `READER_CANVAS`
- `READER_BACK_BUTTON`
- `SETTINGS_SCREEN`
- `SETTINGS_BACK_BUTTON`

约束：

- 只给根页面、导航按钮、主要操作按钮和 Canvas 这类稳定交互目标加 tag。
- 普通标题、正文、列表文本不加 tag，优先用语义描述或资源文案查询。
- tag 值使用英文 snake_case，保持跨语言 UI 文案切换不受影响。

## 三、截图矩阵

第一批稳定基线：

| 场景 | 主题 | 视口 | 验证点 |
|------|------|------|------|
| 书架空态 | 亮色 | 手机竖屏 | 暖白背景、导入 FAB、顶部操作栏 |
| 书架列表 | 亮色 | 手机竖屏 | 默认封面、进度、筛选和排序入口 |
| 设置页 | 亮色 | 手机竖屏 | 分组密度、色块预览、触控目标 |
| 设置页 | 暗色 | 手机竖屏 | 暗色 MoTu token、对比度 |
| 阅读页 | 纸感 | 手机竖屏 | 纸感背景、深棕正文、页眉页脚 |
| 阅读页 | 清爽 | 手机竖屏 | 非纯白正文背景 |
| 阅读页 | 夜间 | 手机竖屏 | 非纯黑背景、非纯白正文 |
| 阅读页 | OLED | 手机竖屏 | OLED 仅作为显式阅读主题 |
| 阅读页工具栏 | 纸感 | 手机竖屏 | 半透明浮层与正文不冲突 |
| 阅读目录/快速设置 | 纸感 | 手机竖屏 | 浮层层级与 ReaderColorScheme |

第二批设备补充：

- 手机横屏。
- 7-8 英寸平板竖屏。
- 10-11 英寸平板横屏。
- 系统字体 1.3x。
- 深色系统主题跟随模式。

## 四、执行入口

本地编译门禁：

```powershell
./gradlew.bat :app:compileDebugAndroidTestKotlin
```

具备设备后执行：

```powershell
./gradlew.bat :app:connectedDebugAndroidTest --stacktrace
```

截图产物建议输出到：

```text
app/build/outputs/visual-regression/<device>/<theme>/<case>.png
```

基线产物建议由人工确认后存放到：

```text
docs/visual-baseline/<device>/<theme>/<case>.png
```

## 五、判定规则

- 文字不得越界、重叠或被工具栏遮挡。
- 阅读正文和背景对比度不低于 4.5:1。
- 控件触控区域不小于 48dp。
- 阅读页默认背景不得为纯白，夜间默认背景不得为纯黑。
- 视觉差异需先判断是否符合 `docs/14-color-scheme.md`，再更新基线。

## 六、当前状态

- 已完成：Compose UI Test 依赖、最小 tag 测试、核心页面和关键操作 `UiTestTags`。
- 已完成：颜色 token 单元测试覆盖 MoTu App token、ReaderColorScheme、Canvas 桥接和纯黑白约束。
- 待设备环境补证：真实截图采集、TalkBack 全流程、平板/横屏视觉基线。
