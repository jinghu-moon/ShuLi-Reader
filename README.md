# ShuLi-Reader (书笠阅读器)

ShuLi-Reader 是一款基于现代 Android 技术栈（Jetpack Compose、Material 3、Coroutines、Flows、Room）打造的超轻量、高性能本地 TXT/EPUB 电子书阅读器。项目致力于提供丝滑的阅读体验与零卡顿的图书库管理。

## 🌟 核心功能亮点

*   **⚡ 极速零阻塞导入**：
    *   重构了 TXT 和 EPUB 文件的元数据（Metadata）与封面抽取管线。
    *   针对 TXT 文本，使用基于轻量正则的快速首行/前30行标题智能提取算法；针对 EPUB 文本，采用 “Zero-IO” 级别的 Zip 目录直接解析技术获取图书元信息，全程运行在 `Dispatchers.IO`，彻底杜绝主线程阻塞。
*   **📂 文件夹批量导入**：
    *   支持选择文件夹并自动扫描其下的所有 TXT 与 EPUB 书籍，供用户便捷地进行多选或一键批量导入。
*   **🔍 智能去重与自动定位高亮**：
    *   基于物理存储路径进行精确的 $O(1)$ 重复书籍拦截，抛出自定义 `BookAlreadyExistsException`。
    *   在触发重复导入时，系统会自动在后台以异步非阻塞形式弹窗提示。
    *   同时，书架主页的网格/列表视图将**自动滚动定位**至已有书籍封面，并叠加渲染一层精致的 **M3 主题圆角发光描边遮罩层**（3dp 粗细的 Primary 边框 + 25% 不透明度的容器背景）高亮闪烁 2.5 秒，帮助用户一目了然地定位图书。
*   **🌀 协程高并发分流**：
    *   优化了 UI 层 `Events` 事件流的串行挂起问题，将阻塞性的 `showSnackbar` 气泡弹出放入独立的并发协程，使滚动和高亮定位能够在同一微秒同步并发执行。
*   **🎨 现代响应式设计**：
    *   完美适配并支持“网格（Grid）”与“列表（List）”两种书籍排版模式的无缝切换。
    *   支持书架快速文本过滤，并带有匹配文字高亮着色效果。

## 🛠️ 技术选型

*   **UI 框架**：Jetpack Compose (Material 3) 响应式自适应布局
*   **异步流与并发**：Kotlin Coroutines (协程) & SharedFlow / StateFlow (状态流与事件流)
*   **持久化数据库**：Room Database (支持底层高效 SQL 路径查询)
*   **图片处理**：Coil (极速图像渲染)
*   **构建配置**：Gradle Kotlin DSL (`.gradle.kts`)

## 📦 快速上手

1.  克隆本仓库：
    ```bash
    git clone https://github.com/jinghu-moon/ShuLi-Reader.git
    ```
2.  使用 Android Studio 导入项目。
3.  连接真机或启动模拟器，执行以下命令编译并部署调试包：
    ```bash
    ./gradlew installDebug
    ```

## 📄 许可证

项目采用 [AGPL-3.0](LICENSE) 许可证进行开源。
