# 划词查词 Demo

以 `docs/prototypes/dict-popup-prototype.html` 为视觉参考，用**真实 MDX/MDD 词典数据**驱动的可交互 Demo。
浏览器里测试查词效果：点击划线词 → 浮动菜单 → 查词 → 底部弹窗显示真实释义。

## 启动

```bash
# 默认用 mdict/test-dict/ 下的《漢語大詞典》
./gradlew :mdict:runDemo

# 或指定任意 MDX（同目录同名 .mdd 会自动加载）
./gradlew :mdict:runDemo --args="D:/path/to/your.mdx"
```

启动后浏览器打开 **http://localhost:8765**，Ctrl+C 停止。

## 功能

- **划词查词**：点击正文里的虚线词（霁色/踟蹰/迤逦…）→ 浮动菜单 → 「查词」
- **词头可编辑**：弹窗顶部输入框可改词，回车连续查（踟蹰 → 踯躅 → …）
- **真实释义渲染**：释义用 iframe + 词典自带 CSS 渲染（最接近 WebView 路径的效果）
- **词典内跳转**：释义里的 `entry://` 链接点击触发新查词
- **未找到联想**：查不到时用前缀匹配给「你是不是要找」
- **亮/暗主题**、底部弹窗拖拽调高度

## 实现

| 端点 | 作用 |
|---|---|
| `/` | Demo 页面（原型 MoTu 风格 UI） |
| `/info` | 词典名 + 词条数 |
| `/lookup?word=X` | 真实查词，返回释义 HTML（资源引用已重写到 `/res`，`entry://` 重写为父页面查词） |
| `/res?path=X` | 词典资源（CSS/JS/图片），优先词典目录文件，否则从 MDD 取 |

服务端：`src/main/kotlin/.../demo/DictDemoServer.kt`（JDK 自带 HttpServer，零新依赖）。
页面：`src/main/resources/demo/index.html`。

> Demo 仅用于本地测试解析库效果，不属于 Android app 的一部分。真实词典文件（`test-dict/`）已 gitignore。
