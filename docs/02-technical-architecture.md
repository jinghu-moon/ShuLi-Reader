# 02 - 技术架构

## 架构概览

采用 Clean Architecture 分层架构，关注点分离，便于测试和维护。

```
┌─────────────────────────────────────────────────────────┐
│                      UI Layer (Compose)                  │
│  书架页 │ 阅读器页 │ 设置页 │ 搜索页 │ 统计页            │
├─────────────────────────────────────────────────────────┤
│                    ViewModel Layer                       │
│  BookshelfVM │ ReaderVM │ SettingsVM │ SyncVM            │
├─────────────────────────────────────────────────────────┤
│                   Domain Layer (Use Cases)               │
│  阅读引擎 │ 文件解析 │ 同步管理 │ 主题引擎 │ 搜索引擎     │
├─────────────────────────────────────────────────────────┤
│                    Data Layer                            │
│  Room DB │ SharedPreferences │ 文件系统 │ WebDAV         │
└─────────────────────────────────────────────────────────┘
```

## 各层职责

### UI Layer

- Compose 声明式 UI
- 响应 ViewModel 状态变化
- 处理用户交互事件

### ViewModel Layer

- 管理 UI 状态
- 协调 Use Cases
- 处理生命周期

### Domain Layer

- 业务逻辑封装
- Use Cases 组合
- 领域模型定义

### Data Layer

- 数据持久化
- 文件系统操作
- 网络同步

## 核心模块

### 1. 阅读引擎模块

```
阅读引擎
├── 分页器（Paginator）
│   ├── 文本分页算法
│   ├── 虚拟化加载
│   └── 缓存策略
├── 渲染器（Renderer）
│   ├── Canvas 文本绘制
│   ├── 翻页动画
│   └── 手势处理
└── 解析器（Parser）
    ├── TXT 解析
    ├── EPUB 解析
    └── 编码检测
```

### 2. 数据存储模块

```
数据存储
├── Room Database
│   ├── 书籍信息表
│   ├── 书签表
│   ├── 笔记表
│   └── 阅读进度表
├── SharedPreferences
│   ├── 应用设置
│   └── 主题配置
└── 文件系统
    ├── 书籍文件
    └── 缓存文件
```

### 3. 同步模块

```
同步模块
├── WebDAV 客户端
│   ├── 文件上传
│   ├── 文件下载
│   └── 冲突处理
└── 同步管理器
    ├── 增量同步
    ├── 冲突解决
    └── 离线队列
```

## 数据流

```
用户操作 → UI 事件 → ViewModel → Use Case → Repository → 数据源
    ↑                                                        │
    └────────────────── 状态更新 ←───────────────────────────┘
```

## 依赖注入

使用 Koin 进行依赖注入：

```kotlin
val appModule = module {
    // Repository
    single<BookRepository> { BookRepositoryImpl(get(), get()) }
    single<ReadingProgressRepository> { ReadingProgressRepositoryImpl(get()) }
    
    // Use Cases
    factory { GetBooksUseCase(get()) }
    factory { ParseFileUseCase(get()) }
    factory { SyncProgressUseCase(get()) }
    
    // ViewModel
    viewModel { BookshelfViewModel(get(), get()) }
    viewModel { ReaderViewModel(get(), get(), get()) }
}
```

## 错误处理

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}
```

## 线程模型

| 操作 | 线程 |
|------|------|
| UI 渲染 | Main |
| 文件解析 | IO |
| 数据库操作 | IO |
| 网络同步 | IO |
| 分页计算 | Default |
