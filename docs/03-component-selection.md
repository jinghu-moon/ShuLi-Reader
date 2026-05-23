# 03 - 组件选型

## 选型原则

遵循奥卡姆剃刀原则：
- 能用系统 API 就不引入第三方库
- 功能单一、体积小、无传递依赖优先
- 只在确实需要时才增加复杂度

## 架构组件

| 组件 | 选择 | 体积 | 理由 |
|------|------|------|------|
| DI 框架 | Koin | ~300KB | 无需注解处理器，编译快，API 简洁 |
| 数据库 | Room | ~1.2MB | 无替代品，FTS4 全文搜索是刚需 |
| 配置存储 | SharedPreferences | 0KB | KV 存储足够，DataStore 过重 |
| 导航 | Compose Navigation | ~200KB | 官方方案，无额外依赖 |
| 异步 | Coroutines + Flow | 0KB | Kotlin 标准 |

## 文件解析

| 组件 | 选择 | 体积 | 理由 |
|------|------|------|------|
| 编码检测 | juniversalchardet | ~60KB | Mozilla 引擎，轻量准确 |
| EPUB 解压 | java.util.zip | 0KB | 系统内置，零依赖 |
| XML 解析 | XmlPullParser | 0KB | 系统内置，比 DOM 省内存 |
| HTML 解析 | Jsoup | ~400KB | CSS 选择器 + DOM 操作，EPUB 渲染刚需 |
| CSS 解析 | 自研子集解析器 | 0KB | EPUB 仅需 CSS 子集 |

## 渲染层

| 组件 | 选择 | 体积 | 理由 |
|------|------|------|------|
| 文本渲染 | Canvas 自绘 | 0KB | 虚拟化分页必须精确控制字符位置 |
| 翻页动画 | Compose Animation | 0KB | 仿真翻页用 Canvas + 贝塞尔曲线自绘 |
| 手势处理 | Compose PointerInput | 0KB | 声明式，无额外依赖 |
| 图片加载 | Coil | ~200KB | Kotlin 优先，Compose 原生支持 |

## 网络同步

| 组件 | 选择 | 体积 | 理由 |
|------|------|------|------|
| HTTP | OkHttp | ~400KB | WebDAV 需要底层 HTTP 控制 |
| WebDAV | 自研 | 0KB | 基于 OkHttp，仅实现 PROPFIND/GET/PUT |
| JSON | kotlinx.serialization | ~300KB | Kotlin 原生，编译时生成 |

## 工具库

| 组件 | 选择 | 体积 | 理由 |
|------|------|------|------|
| 文件流 | java.io | 0KB | 本项目无复杂 IO 场景 |
| 日志 | android.util.Log | 0KB | 仅 debug 用，Release 静默 |
| 日期 | java.time | 0KB | API 26+ 原生支持 |

## 测试

| 组件 | 选择 | 理由 |
|------|------|------|
| 单元测试 | JUnit 4 | Android 标准 |
| Mock 框架 | MockK | Kotlin 原生，协程支持好 |

## 最终依赖清单

```kotlin
dependencies {
    // 架构
    implementation("io.insert-koin:koin-androidx-compose:3.5.6")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // 文件解析
    implementation("com.github.nicholasgasior:juniversalchardet:2.4.0")
    implementation("org.jsoup:jsoup:1.17.2")
    
    // 网络同步
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // 图片
    implementation("io.coil-kt:coil-compose:2.6.0")
    
    // 测试
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("junit:junit:4.13.2")
}
```

## 依赖统计

| 指标 | 数值 |
|------|------|
| 第三方依赖数 | 8 个 |
| 依赖总大小 | ~3.1MB |
| 自研代码量 | ~1050 行 |
| 注解处理器 | 0 个 |
