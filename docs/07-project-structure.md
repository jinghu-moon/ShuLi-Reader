# 07 - 项目结构

## 目录结构

```
ShuLi-Reader/
├── app/                              # 应用模块
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/shuli/reader/
│   │   │   │   ├── ShuLiApplication.kt
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── di/                        # 依赖注入
│   │   │   │   │   ├── AppModule.kt
│   │   │   │   │   ├── DatabaseModule.kt
│   │   │   │   │   └── NetworkModule.kt
│   │   │   │   ├── navigation/                # 导航
│   │   │   │   │   └── NavGraph.kt
│   │   │   │   └── ui/                        # UI 层
│   │   │   │       ├── theme/                 # 主题
│   │   │   │       │   ├── Color.kt
│   │   │   │       │   ├── Shape.kt
│   │   │   │       │   ├── Theme.kt
│   │   │   │       │   └── Type.kt
│   │   │   │       ├── components/            # 通用组件
│   │   │   │       │   ├── ReaderButton.kt
│   │   │   │       │   ├── ReaderCard.kt
│   │   │   │       │   ├── ReaderDialog.kt
│   │   │   │       │   ├── ReaderSlider.kt
│   │   │   │       │   └── ...
│   │   │   │       ├── bookshelf/             # 书架页面
│   │   │   │       │   ├── BookshelfScreen.kt
│   │   │   │       │   ├── BookshelfViewModel.kt
│   │   │   │       │   └── components/
│   │   │   │       ├── reader/                # 阅读器页面
│   │   │   │       │   ├── ReaderScreen.kt
│   │   │   │       │   ├── ReaderViewModel.kt
│   │   │   │       │   └── components/
│   │   │   │       ├── settings/              # 设置页面
│   │   │   │       │   ├── SettingsScreen.kt
│   │   │   │       │   └── SettingsViewModel.kt
│   │   │   │       ├── search/                # 搜索页面
│   │   │   │       │   ├── SearchScreen.kt
│   │   │   │       │   └── SearchViewModel.kt
│   │   │   │       └── statistics/            # 统计页面
│   │   │   │           ├── StatisticsScreen.kt
│   │   │   │           └── StatisticsViewModel.kt
│   │   │   ├── res/
│   │   │   │   ├── font/                      # 字体文件
│   │   │   │   │   ├── lxgw_wenkai.ttf
│   │   │   │   │   └── lxgw_wenkai_bold.ttf
│   │   │   │   ├── drawable/
│   │   │   │   ├── values/
│   │   │   │   └── xml/
│   │   │   └── AndroidManifest.xml
│   │   └── test/                              # 单元测试
│   └── build.gradle.kts
├── core/                             # 核心模块
│   ├── parser/                       # 文件解析
│   │   ├── src/main/java/com/shuli/reader/core/parser/
│   │   │   ├── TxtParser.kt
│   │   │   ├── EpubParser.kt
│   │   │   ├── CharsetDetector.kt
│   │   │   └── model/
│   │   │       ├── BookContent.kt
│   │   │       └── Chapter.kt
│   │   └── build.gradle.kts
│   ├── engine/                       # 阅读引擎
│   │   ├── src/main/java/com/shuli/reader/core/engine/
│   │   │   ├── paginator/
│   │   │   │   ├── Paginator.kt
│   │   │   │   ├── VirtualPaginator.kt
│   │   │   │   └── model/
│   │   │   │       ├── Page.kt
│   │   │   │       └── TextLine.kt
│   │   │   ├── renderer/
│   │   │   │   ├── TextRenderer.kt
│   │   │   │   ├── PageRenderer.kt
│   │   │   │   └── SimulationFlipRenderer.kt
│   │   │   ├── animation/
│   │   │   │   ├── AnimationController.kt
│   │   │   │   └── PageFlipAnimation.kt
│   │   │   └── gesture/
│   │   │       └── GestureHandler.kt
│   │   └── build.gradle.kts
│   ├── database/                     # 数据库
│   │   ├── src/main/java/com/shuli/reader/core/database/
│   │   │   ├── ShuLiDatabase.kt
│   │   │   ├── dao/
│   │   │   │   ├── BookDao.kt
│   │   │   │   ├── BookmarkDao.kt
│   │   │   │   ├── NoteDao.kt
│   │   │   │   └── ReadingProgressDao.kt
│   │   │   ├── entity/
│   │   │   │   ├── BookEntity.kt
│   │   │   │   ├── BookmarkEntity.kt
│   │   │   │   ├── NoteEntity.kt
│   │   │   │   └── ReadingProgressEntity.kt
│   │   │   └── converter/
│   │   │       └── Converters.kt
│   │   └── build.gradle.kts
│   ├── sync/                         # 同步模块
│   │   ├── src/main/java/com/shuli/reader/core/sync/
│   │   │   ├── WebDavClient.kt
│   │   │   ├── SyncManager.kt
│   │   │   └── model/
│   │   │       └── WebDavFile.kt
│   │   └── build.gradle.kts
│   └── theme/                        # 主题系统
│       ├── src/main/java/com/shuli/reader/core/theme/
│       │   ├── ThemeManager.kt
│       │   ├── model/
│       │   │   └── ReaderTheme.kt
│       │   └── builtin/
│       │       ├── LightTheme.kt
│       │       ├── DarkTheme.kt
│       │       └── PaperTheme.kt
│       └── build.gradle.kts
├── data/                             # 数据层
│   ├── src/main/java/com/shuli/reader/data/
│   │   ├── repository/
│   │   │   ├── BookRepository.kt
│   │   │   ├── BookmarkRepository.kt
│   │   │   ├── ReadingProgressRepository.kt
│   │   │   └── ThemeRepository.kt
│   │   └── datasource/
│   │       ├── local/
│   │       │   ├── BookLocalDataSource.kt
│   │       │   └── PreferencesDataSource.kt
│   │       └── remote/
│   │           └── WebDavDataSource.kt
│   └── build.gradle.kts
├── domain/                           # 领域层
│   ├── src/main/java/com/shuli/reader/domain/
│   │   ├── model/
│   │   │   ├── Book.kt
│   │   │   ├── Bookmark.kt
│   │   │   ├── Note.kt
│   │   │   └── ReadingProgress.kt
│   │   ├── repository/
│   │   │   └── Repository.kt
│   │   └── usecase/
│   │       ├── GetBooksUseCase.kt
│   │       ├── ParseFileUseCase.kt
│   │       ├── SyncProgressUseCase.kt
│   │       └── SearchBooksUseCase.kt
│   └── build.gradle.kts
├── common/                           # 公共模块
│   ├── src/main/java/com/shuli/reader/common/
│   │   ├── util/
│   │   │   ├── FileUtil.kt
│   │   │   ├── DateUtil.kt
│   │   │   └── StringUtil.kt
│   │   ├── extension/
│   │   │   ├── ContextExt.kt
│   │   │   └── ModifierExt.kt
│   │   └── constant/
│   │       └── AppConstants.kt
│   └── build.gradle.kts
├── docs/                             # 设计文档
│   ├── 00-project-overview.md
│   ├── 01-requirements-analysis.md
│   ├── 02-technical-architecture.md
│   ├── 03-component-selection.md
│   ├── 04-ui-design-system.md
│   ├── 05-core-module-design.md
│   ├── 06-performance-optimization.md
│   └── 07-project-structure.md
├── build.gradle.kts                  # 根构建文件
├── settings.gradle.kts               # 设置文件
└── gradle.properties                 # Gradle 配置
```

## 模块依赖关系

```
app
├── core:parser
├── core:engine
├── core:database
├── core:sync
├── core:theme
├── data
├── domain
└── common

core:parser
└── common

core:engine
└── common

core:database
└── common

core:sync
└── common

core:theme
└── common

data
├── core:database
├── core:sync
└── domain

domain
└── common
```

## 包名规范

- 应用层：`com.shuli.reader`
- 核心模块：`com.shuli.reader.core.*`
- 数据层：`com.shuli.reader.data`
- 领域层：`com.shuli.reader.domain`
- 公共模块：`com.shuli.reader.common`

## 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 包名 | 全小写 | `com.shuli.reader.core.parser` |
| 类名 | 大驼峰 | `TxtParser` |
| 方法名 | 小驼峰 | `parseFile()` |
| 常量 | 全大写下划线 | `MAX_CACHE_SIZE` |
| 资源文件 | 全小写下划线 | `ic_book.xml` |
