# 同步与备份架构设计 v4

本方案为 ShuLi Reader 设计完整的数据同步与备份体系，支持云端（WebDAV）和本地双通道同步、端到端加密、ZIP 导出/恢复，覆盖阅读进度、书签、笔记、阅读设置和书籍文件。

---

## V4 变更说明（相较 V3）

本版本基于架构审查意见进行了以下修订：

**安全**
- **PBKDF2 迭代次数从 ≥310,000 提升至 ≥600,000**（符合 OWASP 2023 最新推荐）
- 新增 `crypto.json` 完整性保护机制，防止 KDF 参数降级攻击
- 明确 ChaCha20-Poly1305 在 Android 上的兼容性约束及推荐替代方案

**数据模型**
- **统一 manifest 分片结构**（消除 §4.7 与 §5.2 之间的矛盾，books 列表全部移到 `books/{bookKey}/meta.json`）
- **明确 bookKey 生成时机**：在 `importBook()` 事务内立即生成，不延迟到首次同步
- **修正 tombstone 清理策略**：改为基于设备全量同步确认，而非固定 30 天时间窗口
- 新增 config 配置的 key-level merge 逻辑，防止多设备分别修改不同配置项时静默丢失

**性能**
- **改进 `fastHash` 采样策略**：从"头 + 尾"双点改为"头 + 中间 + 尾"三点采样，提升对中文小说的辨别力
- 新增 manifest 写入串行化约束，防止并发同步时写丢失
- 明确 `SyncTaskEntity.payloadJson` 仅适用于小数据（< 64KB），大文件不进数据库
- 补充大文件加密临时磁盘占用上限要求

**功能**
- 明确 `SyncOrchestrator.BOTH` 的失败语义：两端独立上报，互不阻断
- 新增设备管理 UI（§14.12）：查看已同步设备、最后同步时间、移除设备

**UI/UX**
- 云端同步卡片重设计：拆分为摘要视图（默认）+ 设置子页（点击展开）
- 新增导出/同步两套密码的视觉区隔规范
- 修正后台通知刷新策略：仅在状态机切换时更新，不跟随每本书更新
- 冲突弹窗新增设备标识 fallback（设备名缺失时显示"其他设备"）
- 导出大小预估改为异步计算
- 本地同步路径改用用户友好格式
- 限流状态 UI 新增可操作引导
- 取消同步按钮补充说明文案

---

# 第一部分：背景与战略

## 第一章 当前项目现状

### 1.1 已有基础

- **网络栈**：项目已有 OkHttp 依赖。
- **序列化**：已有 kotlinx.serialization JSON。
- **配置存储**：已有 DataStore，并已存在 `webdav_url / webdav_user / webdav_password / sync_method`。
- **设置 UI**：已有 WebDAV URL、用户名、密码输入框。
- **同步雏形**：已有 `core/sync/WebDavClient.kt` 与 `WebDavSyncManager.kt`。
- **现有 WebDAV 能力**：`PROPFIND / GET / PUT` + Basic Auth + progress JSON。

### 1.2 当前不足

- **协议能力不足**：缺少 `MKCOL / DELETE / HEAD / ETag / If-Match / If-None-Match / Depth 分页处理`。
- **认证不足**：只支持 Basic Auth，不支持 Digest。
- **文件同步缺失**：当前只同步 progress，不同步 TXT/EPUB 文件、书签、笔记、配置。
- **XML 解析缺失**：`PROPFIND` 返回 XML 目前未解析为资源列表。
- **错误处理不足**：未区分 401/403/404/409/412/423/429/503。
- **请求限流缺失**：坚果云免费版 600 请求 / 30 分钟，当前无节流。
- **凭据安全不足**：密码明文 DataStore 存储。
- **端到端加密缺失**：当前只依赖 HTTPS，云端服务商仍可看到书名、进度、书签、笔记、配置。
- **后台同步缺失**：没有 WorkManager 周期同步与网络约束。

### 1.3 明确非目标（本版本不做）

以下能力在当前版本明确不做，避免需求膨胀和团队误解：

- **全量实时双向合并**：不做类似 Google Docs 的实时协同编辑；同步为"拉取-合并-推送"模式。
- **多人协作**：同一本书的书签/笔记仅支持单人多设备同步，不支持多人同时编辑同一本书。
- **跨设备书籍正文自动覆盖**：书籍文件同步默认关闭，仅在用户手动开启时触发；不会自动用远端书籍覆盖本地。
- **ZIP 日常同步**：ZIP 仅用于手动完整备份/迁移，不作为日常同步格式。
- **服务端锁管理界面**：不提供 WebDAV 锁的 UI 管理；冲突通过 latest-wins + 用户提示解决。
- **离线冲突历史回溯**：不保留完整的冲突解决历史日志；仅保留最近一次同步状态。
- **自动删除远端旧目录**：目录迁移后旧目录保留，需用户手动确认清理。

---

## 第二章 外部资料结论

### 2.1 dav4jvm

`dav4jvm` 是 DAVx⁵ 团队维护的 JVM/Kotlin WebDAV/CalDAV/CardDAV 库。

官方 README 要点：

- 当前有两个包：
  - `at.bitfire.dav4jvm.okhttp`：当前稳定包，OkHttp，JVM only。
  - `at.bitfire.dav4jvm.ktor`：新包，KMP 方向。
- Android 自带 XPP，通常无需额外 XML Pull Parser。
- 官方示例支持 `BasicDigestAuthHandler`，可配置 Basic/Digest。
- 支持 propfind、put、get 等高级封装。

**判断**：协议完整度高，但引入 JitPack 依赖和较多抽象。若目标是"轻量"，不应第一阶段直接引入。

### 2.2 sardine-android / sardine-next

- API 更高层，适合快速完成列目录/下载/上传。
- 维护活跃度和未来方向弱于 dav4jvm。

**判断**：不作为首选。

### 2.3 纯 OkHttp 手写

项目当前已经走这条路。对于阅读器场景，需要：

- `PROPFIND`：列远端目录与读取元数据。
- `MKCOL`：创建 `.shuli/`、`books/`、`state/`、`config/`。
- `GET`：下载书籍或 JSON。
- `PUT`：上传书籍或 JSON。
- `HEAD`：低成本探测 ETag/Last-Modified/Content-Length。
- `DELETE`：可选，用于远端清理。

**判断**：最符合"高性能、稳定、低延迟、轻量"。第一阶段推荐继续强化手写 OkHttp WebDAV，而不是引入 dav4jvm。

### 2.4 legado（阅读 App）WebDAV 实现参考

legado 是主流 Android 阅读 App，已支持坚果云 WebDAV 同步。分析其 `refer/legado-with-MD3-main` 项目源码：

**架构分层**：

- `WebDav.kt`：底层 WebDAV 协议实现（OkHttp + Jsoup XML 解析）
- `Authorization.kt`：认证封装（仅 Basic Auth）
- `AppWebDav.kt`：业务层，处理进度同步逻辑
- `Backup.kt`：备份逻辑，ZIP 打包
- `RemoteBookRepository.kt`：远程书籍管理

**关键实现细节**：

| 方面 | legado 做法 | 评价 |
|------|------------|------|
| HTTP 客户端 | OkHttp，通过 `newBuilder()` 创建专用实例 | ✅ 可借鉴 |
| XML 解析 | Jsoup（HTML 解析器强行解析 XML） | ⚠️ 不规范，我们用 XML Pull Parser |
| 认证 | 仅 Basic Auth，通过 Interceptor 注入 | ⚠️ 我们支持 Basic + Digest |
| 进度文件命名 | `{书名}_{作者}.json` | ❌ 泄露隐私，我们用 hash |
| 目录结构 | 平铺：`bookProgress/` + `books/` | ✅ 简单直接 |
| 冲突解决 | 时间戳比较 + 章节索引/位置比较 | ✅ 可参考进度比较逻辑 |
| 限流 | 无特殊处理 | ❌ 我们有请求预算 + 退避 |
| E2EE | 无 | ❌ 我们有完整 E2EE |
| 备份格式 | ZIP 整包，每天最多一次 | ✅ 简单但不适合频繁同步 |

**坚果云特殊处理**：

```kotlin
// 检测是否坚果云
val isJianGuoYun = rootWebDavUrl.startsWith("https://dav.jianguoyun.com/dav/")

// 401 错误时检查 Basic Auth 支持
if (response.code == 401) {
    val headers = response.headers("WWW-Authenticate")
    val supportBasicAuth = headers.any { it.startsWith("Basic", ignoreCase = true) }
    if (headers.isNotEmpty() && !supportBasicAuth) {
        AppLog.put("服务器不支持BasicAuth认证")
    }
}
```

**我们的方案优势**：

1. **manifest-first**：减少 PROPFIND 请求，适应坚果云限流
2. **bookKey hash**：不泄露书名、作者等隐私信息
3. **传输层抽象**：统一云端/本地同步
4. **完整 E2EE**：保护云端数据隐私
5. **限流策略**：请求预算 + 指数退避，避免被封禁
6. **冲突解决更灵活**：提供三选项（跳转/保留/暂不处理）

**可借鉴的点**：

1. **进度比较逻辑**：当时间戳相同时，比较章节索引和位置取较新进度
2. **URL 协议转换**：`davs://` → `https://`，兼容用户输入习惯
3. **错误信息解析**：解析 WebDAV XML 错误响应中的 `s:exception` 和 `s:message`
4. **设备名区分**：备份文件名包含设备名，便于多设备识别

### 2.5 RFC 4918 要点

- WebDAV 使用 XML 请求/响应，必须正确处理命名空间。
- `PROPFIND` 是核心目录发现能力。
- 条件请求与锁相关状态码需要正确处理：`412 Precondition Failed`、`423 Locked`。
- `ETag` 和 `Last-Modified` 是缓存和冲突判断基础。

### 2.6 WorkManager 要点

Android 官方推荐 WorkManager 用于可靠后台任务：

- 支持网络、电量等约束。
- 支持一次性和周期任务。
- 支持指数退避重试。
- 任务持久化，可跨重启恢复。

**判断**：手动同步 + 即时小同步用协程；周期全量/增量同步用 WorkManager。

### 2.7 坚果云限制

官方说明：

- 免费版：600 请求 / 30 分钟。
- 付费版：1500 请求 / 30 分钟。
- 单次 `PROPFIND` 文件/文件夹数量：750 项。
- WebDAV 上传大小默认 500MB。

**设计约束**：必须减少请求数，批量 JSON、缓存 manifest、单线程/低并发、指数退避。

### 2.8 WebDAV 服务商对接指南

所有主流 WebDAV 服务商都支持标准协议，对接方式一致，仅地址和限制不同。

**坚果云**：

```text
WebDAV 地址：https://dav.jianguoyun.com/dav/
认证方式：Basic Auth（用户名 + 应用密码）
获取应用密码：坚果云 → 设置 → 安全选项 → 第三方应用管理 → 添加应用
限制：
  - 免费版：600 请求 / 30 分钟
  - 付费版：1500 请求 / 30 分钟
  - 单次 PROPFIND 最多 750 项
  - 单文件上传上限：500MB
```

**InfiniCLOUD (TeraCloud)**：

```text
WebDAV 地址：https://xxx.teracloud.jp/dav/
认证方式：Basic Auth（用户名 + 应用密码）
获取应用密码：InfiniCLOUD → 设置 → Apps → WebDAV → 启用并生成密码
限制：
  - 免费版：20GB 存储，无明确请求频率限制
  - 上传大小：无明确限制
  - 支持 Digest Auth（可选）
```

**Nextcloud / ownCloud**：

```text
WebDAV 地址：https://your-server/remote.php/dav/files/{username}/
认证方式：Basic Auth 或 Digest Auth
获取应用密码：设置 → 安全 → 创建新应用密码
限制：取决于服务器配置
```

**通用 WebDAV 服务器**：

```text
地址：由用户提供
认证：Basic Auth 或 Digest Auth
限制：取决于服务器配置
```

**客户端适配策略**：

```kotlin
// 服务商预设（简化用户配置）
enum class WebDavProvider(
    val displayName: String,
    val baseUrl: String,
    val authType: AuthType,
    val rateLimit: Int,        // 请求/30分钟
    val maxFileSize: Long      // 单文件上限
) {
    JIANGUOYUN(
        displayName = "坚果云",
        baseUrl = "https://dav.jianguoyun.com/dav/",
        authType = AuthType.BASIC,
        rateLimit = 600,
        maxFileSize = 500 * 1024 * 1024
    ),
    TERACLOUD(
        displayName = "InfiniCLOUD",
        baseUrl = "https://xxx.teracloud.jp/dav/",
        authType = AuthType.BASIC,
        rateLimit = 1000,  // 保守估计
        maxFileSize = Long.MAX_VALUE
    ),
    NEXTCLOUD(
        displayName = "Nextcloud",
        baseUrl = "",  // 用户自定义
        authType = AuthType.BASIC_OR_DIGEST,
        rateLimit = 1000,
        maxFileSize = Long.MAX_VALUE
    ),
    CUSTOM(
        displayName = "自定义",
        baseUrl = "",  // 用户自定义
        authType = AuthType.BASIC_OR_DIGEST,
        rateLimit = 600,  // 默认保守
        maxFileSize = 500 * 1024 * 1024
    )
}

enum class AuthType {
    BASIC,
    DIGEST,
    BASIC_OR_DIGEST
}
```

**认证适配**：

```kotlin
// OkHttp 认证拦截器
fun createAuthInterceptor(
    username: String,
    password: String,
    authType: AuthType
): Interceptor {
    return when (authType) {
        AuthType.BASIC -> BasicAuthInterceptor(username, password)
        AuthType.DIGEST -> DigestAuthInterceptor(username, password)
        AuthType.BASIC_OR_DIGEST -> {
            // 先尝试 Basic，401 时切换 Digest
            AdaptiveAuthInterceptor(username, password)
        }
    }
}
```

**测试连接流程**：

```text
1. 用户选择服务商（或自定义）
2. 输入用户名和应用密码
3. 客户端发送 PROPFIND / 请求
4. 成功：显示目录结构和可用空间
5. 失败：明确提示错误原因
   - 401：用户名或密码错误
   - 403：没有 WebDAV 权限
   - 404：地址错误
   - 网络错误：检查网络连接
```

---

## 第三章 推荐总体路线

### 3.1 结论

采用两阶段路线：

```text
Phase 1：最小可用同步闭环（收窄范围）
纯 OkHttp WebDAV Client
+ kotlinx.serialization JSON
+ Room 本地状态
+ DataStore/Keystore 凭据
+ Manifest-first 同步
+ 进度/书签/笔记同步（核心闭环）

Phase 1.5：扩展同步能力
+ 小说文件同步（高级选项，默认关闭）
+ 配置备份同步
+ WorkManager 后台同步

Phase 2：端到端加密闭环（必做）
AES-256-GCM / ChaCha20-Poly1305
+ 用户同步密码派生主密钥
+ 加密 manifest/state/bookmarks/notes/config/books
+ 密钥版本与加密元数据

不立即引入 dav4jvm。

### 3.2 Phase 1 最小可用闭环定义

Phase 1 的目标是验证"同步真的能稳定工作"，而不是一次性交付完整产品。

**Phase 1 必须包含：**
1. 连接测试（testConnection）
2. 远端目录初始化（ensureRemoteDirs）
3. manifest 读写
4. 阅读进度同步（state/{bookKey}.json）
5. 书签同步（bookmarks/{bookKey}.json）
6. 笔记同步（notes/{bookKey}.json）
7. 基础冲突解决（latest-wins + UUID merge）

**Phase 1 不包含（推迟到 1.5 或 2）：**
- 小说文件同步（Phase 1.5）
- 配置备份同步（Phase 1.5）
- WorkManager 后台同步（Phase 1.5）
- 端到端加密（Phase 2）

**理由**：进度/书签/笔记是用户最常感知的同步需求，且数据量小、冲突简单，适合作为最小闭环验证。书籍文件同步涉及大文件、限流、冲突复杂度，应在其稳定后再扩展。

### 3.3 端到端加密定位

端到端加密不是可有可无的高级选项，而是 **WebDAV 同步功能的正式安全目标**。

**发布门槛（硬性要求）：**

- **未启用 E2EE 时**：仅允许内部测试或过渡版本；对外正式版默认必须启用，或至少强制二次确认"我了解云端可被服务商读取"。
- **正式对用户开放文件/笔记/配置同步时**：应默认启用或强提示启用 E2EE。
- **E2EE 验收通过前**：不视为 WebDAV 同步功能正式发布。

**开发节奏：**

- **Phase 1**：先完成稳定的同步协议、manifest、冲突解决。此阶段可作为内部测试 / 明文过渡版本。
- **Phase 2**：实现 E2EE 后，才视为正式完成 WebDAV 同步能力。

### 3.4 原因

- **轻量**：不新增 WebDAV 大依赖，不引入 JitPack 风险。
- **性能好**：直接流式上传/下载，避免 XML/对象层额外开销。
- **控制力强**：可针对坚果云限流、低请求数、断点策略做优化。
- **项目贴合**：现有代码已经有 OkHttp WebDAV 雏形，继续扩展成本低。
- **安全可演进**：先稳定数据模型，再对 payload 层统一加密，避免协议层和加密层同时复杂化。
- **可升级**：若后续遇到 Digest/兼容性问题，再替换为 dav4jvm Transport 层。

---

# 第二部分：核心架构设计

## 第四章 远端目录结构

### 4.1 Phase 1 明文过渡结构

```text
/ShuLiReader/
  manifest.json          ← 包含 schemaVersion
  device/
    {deviceId}.json
  books/
    {bookKey}/
      book.txt / book.epub
      meta.json
  state/
    {bookKey}.json
  bookmarks/
    {bookKey}.json
  notes/
    {bookKey}.json
  config/
    preferences.json
    reader_presets.json
  backups/
    shuli-backup-YYYY-MM-DD.zip
```

### 4.2 Phase 2 正式加密结构

```text
/ShuLiReaderEncrypted/
  crypto.json
  manifest.enc           ← 包含 schemaVersion
  device/
    {deviceId}.enc
  books/
    {bookKey}/
      book.enc
      meta.enc
  state/
    {bookKey}.enc
  bookmarks/
    {bookKey}.enc
  notes/
    {bookKey}.enc
  config/
    preferences.enc
    reader_presets.enc
  backups/
    shuli-backup-YYYY-MM-DD.zip.enc
```

### 4.3 远端目录版本与迁移策略

**schemaVersion 位置**：放在根 `manifest.json` / `manifest.enc` 中，作为远端目录结构的版本标识。

```json
{
  "schemaVersion": 1,
  "updatedAt": 1710000000000,
  "books": [...]
}
```

**格式升级流程**（每次远端格式变更必须遵循）：

1. **读取旧格式**：客户端检测到 `schemaVersion` 低于当前版本时，按旧格式解析。
2. **转换为新格式**：在内存中将数据转换为新格式。
3. **上传新格式**：用新格式覆盖远端文件。
4. **更新 schemaVersion**：写入新版本号。

**旧目录不自动删除**：

- 迁移后旧目录（如 `/ShuLiReader/`）保留，不自动清理。
- 提供"清理旧目录"按钮，需用户二次确认。
- 至少保留一个迁移确认步骤，避免误删。

**明文 → 加密迁移流程**：

1. 用户点击"迁移到加密同步空间"。
2. 拉取 `/ShuLiReader/` 全部明文数据。
3. 本地加密后上传到 `/ShuLiReaderEncrypted/`。
4. UI 显示迁移进度和结果。
5. 迁移成功后提示用户可选择删除明文目录。

### 4.4 文件命名策略

- 明文过渡期：保留 `book.txt / book.epub` 扩展名，便于调试和手动恢复。
- 加密正式期：统一 `book.enc`，避免从扩展名泄露书籍类型。
- `bookKey` 本身可继续使用 hash，避免暴露书名。
- 加密模式下远端不应出现明文书名、作者、章节名、笔记文本。

### 4.5 bookKey 规则

不要用 `bookId` 作为远端文件名，因为不同设备 Room 自增 ID 不一致。

**推荐：随机 UUID**

```text
bookKey = UUID.randomUUID().toString()
// 例如: "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

**生成时机（重要）**：

`bookKey` 必须在 `BookRepository.importBook()` 的写入事务内立即生成，与 Room bookId 同步写入数据库，而不是延迟到首次同步时生成。

```kotlin
// 正确做法：importBook 事务内一并生成
suspend fun importBook(file: File): BookEntity {
    val bookKey = UUID.randomUUID().toString()  // ← 立即生成
    val entity = BookEntity(
        title = parseTitle(file),
        filePath = file.absolutePath,
        bookKey = bookKey,         // ← 与 Room 行同时落盘
        // ...
    )
    return bookDao.insert(entity)
}

// 错误做法：在第一次同步时才生成
// ❌ 这会导致两台设备导入同一本书后各自生成不同的 bookKey
//    manifest 层面永远无法识别"这是同一本书"
```

**为什么要在 importBook 时生成**：
- 若推迟到同步时生成，设备 A 和设备 B 独立导入同一本书会产生两个不同的 bookKey
- manifest diff 会把同一本书视为两本不同的书，造成双份同步和重复
- 在 importBook 时生成可以保证同一设备上的 bookKey 是稳定且提前已知的

**跨设备同一本书的辅助识别（可选）**：

若将来需要在 manifest 层识别"不同 bookKey 但实为同一本书"，可在 `meta.json` 中增加 `contentFingerprint` 字段：

```kotlin
val contentFingerprint = sha256(title + author + fileSize.toString())
// 仅作辅助比对，不替换 bookKey 作为主键
```

**设计理由**：
- 不可逆：无法从 bookKey 推断书名、作者等信息
- 防撞库：热门书籍（如《三体》）不会被枚举
- 跨设备唯一：UUID 碰撞概率可忽略
- 隐私保护：即使 manifest 泄露，也无法得知书库内容

**本地映射表**：

```text
本地 Room bookId ↔ remote bookKey 映射
存储在本地数据库，不上传到远端
```

**兼容旧方案**：

如果已有数据使用 hash(bookKey)，可保留旧格式，新书使用 UUID。迁移时在 manifest 中标记 `bookKeyType: "hash" | "uuid"`。

### 4.6 localDeviceId 设计

**定义**：每个设备安装 App 后生成的随机 UUID，用于标识数据来源。

```kotlin
// 首次启动时生成，存储在 DataStore
val localDeviceId: String = UUID.randomUUID().toString()
// 例如: "f47ac10b-58cc-4372-a567-0e02b2c3d479"
```

**用途**：

1. **mergeSource 追踪**：书签/笔记合并时记录来自哪个设备
2. **冲突解决**：时间戳相同时，可辅助判断数据来源
3. **日志排障**：SyncOperationLog 中记录 deviceId，便于定位多设备问题
4. **设备管理**：`device/{deviceId}.json` 中记录设备信息（型号、App 版本、最后同步时间）

**存储位置**：

| 位置 | 是否存储 | 说明 |
|------|---------|------|
| 本地 DataStore | ✅ | 主存储，App 卸载前不变 |
| 远端 `device/{deviceId}.json` | ✅ | 设备信息同步到云端 |
| 远端 `manifest.json` | ❌ | 不直接存储，通过 `updatedBy` 间接引用 |
| 远端 `meta.json` | ✅ | `updatedBy` 字段记录最后修改设备 |

**关键约束**：

- `localDeviceId` **不同步到其他设备**，每设备独立生成
- 远端 `device/{deviceId}.json` 仅用于设备列表展示，不用于本地身份识别
- App 卸载重装后 `localDeviceId` 会变，这是可接受的（旧设备记录保留，新设备生成新 ID）

### 4.7 manifest 分片策略（避免热点）

**问题**：单一 manifest 会被所有设备频繁读写，导致 ETag 冲突和写放大。

**解决方案**：manifest 拆分为轻量索引 + 局部状态

```text
/ShuLiReader/
  manifest.json              ← 轻量全局索引（只存版本号）
  books/
    {bookKey}/
      meta.json              ← 书籍元数据（局部）
      state.json             ← 阅读进度（局部）
      bookmarks.json         ← 书签（局部）
      notes.json             ← 笔记（局部）
  config/
    preferences.json
  device/
    {deviceId}.json
```

**manifest.json（轻量索引，唯一权威结构）**：

```json
{
  "schemaVersion": 2,
  "updatedAt": 1710000000000,
  "updatedBy": "device-uuid-xxx",
  "version": 42,
  "bookCount": 156
}
```

manifest 不再包含 books 数组。所有书籍详情全部存储在各自的 `books/{bookKey}/meta.json` 文件中。

**books/{bookKey}/meta.json（书籍局部状态，唯一来源）**：

```json
{
  "bookKey": "a1b2c3d4-...",
  "title": "...",
  "author": "...",
  "fileName": "book.txt",
  "fileType": "TXT",
  "fileSize": 1234567,
  "fileHash": "sha256...",
  "version": 12,
  "updatedAt": 1710000000000,
  "updatedBy": "device-uuid-xxx",
  "deleted": false
}
```

> ⚠️ **与旧版本的重要区别**：第 5.2 节的 manifest 示例曾包含完整 `books[]` 数组，V4 起已废弃该字段。`books[]` 不再出现在 manifest 中，相关实现必须统一读取 `books/{bookKey}/meta.json`。

**manifest 分片策略优势**：
- 冲突粒度小：每本书独立，修改一本书不影响其他书的 ETag
- 上传量小：只上传变更的书，不重写整个 manifest
- 适合 WebDAV：减少 PROPFIND 和 PUT 次数
- 适合 Syncthing：文件级同步，冲突检测更精确
- 适合未来 KMP：数据结构更模块化

**manifest 职责（收窄，最终确认）**：
- ✅ schemaVersion：版本标识
- ✅ bookCount：书籍数量（UI 展示用）
- ✅ version：全局逻辑版本号
- ✅ updatedAt / updatedBy：最近一次写入信息
- ❌ books 列表：已移到 `books/{bookKey}/meta.json`
- ❌ 每本书的任何字段：已移到局部文件

---

## 第五章 同步数据模型

### 5.1 明文逻辑模型

同步逻辑仍使用 JSON DTO，便于开发、调试和冲突合并。

区别是：

```text
Phase 1：JSON DTO 直接 PUT 到 WebDAV
Phase 2：JSON DTO → serialize → encrypt → PUT .enc
```

也就是说，E2EE 不改变业务数据模型，只改变远端持久化格式。

---

### 5.2 manifest.json / manifest.enc

远端总索引，减少 `PROPFIND` 次数。V4 起 manifest 仅保留轻量全局字段，books 详情全部移至 `books/{bookKey}/meta.json`（见 §4.7）。

明文逻辑结构：

```json
{
  "schemaVersion": 2,
  "updatedAt": 1710000000000,
  "updatedBy": "device-uuid-xxx",
  "version": 42,
  "bookCount": 156
}
```

加密模式下：

```text
manifest.enc = encrypt(json(manifest))
```

注意：

- 加密模式下 `manifest.enc` 不含书名，因为书名已移至各自的 `books/{bookKey}/meta.enc`。
- 远端路径不应包含书名。
- `manifest.enc` 仍需整体加密，因为 bookCount、更新时间等元数据本身也属于隐私。
- 书籍列表的增删改通过修改对应的 `books/{bookKey}/meta.json` 完成，不再直接修改 manifest。manifest 的 version 和 bookCount 在每次同步结束时统一更新一次。

---

### 5.3 state/{bookKey}.json / state/{bookKey}.enc

阅读进度。兼容当前 byte-stream 重构。

明文逻辑结构：

```json
{
  "schemaVersion": 2,
  "bookKey": "...",
  "fileType": "TXT",
  "byteOffset": 123456,
  "chapterIndex": 12,
  "chapterPos": 345,
  "chapterTitle": "第十二章",
  "readingProgress": 0.45,
  "updatedAt": 1710000000000,
  "deviceId": "android-xxx"
}
```

规则：

- TXT：`byteOffset` 是权威。
- EPUB：`chapterIndex + chapterPos` 保留为权威。
- `chapterTitle` 只作 UI 缓存。
- 加密模式下：`state/{bookKey}.enc = encrypt(json(state))`。

---

### 5.4 bookmarks/{bookKey}.json / bookmarks/{bookKey}.enc

```json
{
  "schemaVersion": 1,
  "bookKey": "...",
  "items": [
    {
      "id": "uuid",
      "byteOffset": 123456,
      "chapterIndex": 12,
      "chapterPos": 345,
      "selectedText": "...",
      "createdAt": 1710000000000,
      "updatedAt": 1710000000000,
      "deleted": false
    }
  ]
}
```

加密模式下：

```text
bookmarks/{bookKey}.enc = encrypt(json(bookmarks))
```

---

### 5.5 notes/{bookKey}.json / notes/{bookKey}.enc

```json
{
  "schemaVersion": 1,
  "bookKey": "...",
  "items": [
    {
      "id": "uuid",
      "byteStart": 123456,
      "byteEnd": 123789,
      "chapterIndex": 12,
      "chapterStartPos": 345,
      "chapterEndPos": 678,
      "content": "批注内容",
      "color": "#FFE082",
      "createdAt": 1710000000000,
      "updatedAt": 1710000000000,
      "deleted": false
    }
  ]
}
```

加密模式下：

```text
notes/{bookKey}.enc = encrypt(json(notes))
```

---

### 5.6 config/preferences.json / config/preferences.enc

**全量备份用户可迁移配置**，不备份账号密码、绝对路径、设备相关项。

```json
{
  "schemaVersion": 1,
  "updatedAt": 1710000000000,

  "appearance": {
    "language": "zh-CN",
    "themeMode": "system",
    "appFont": "harmony"
  },

  "reader": {
    "fontSize": 16.0,
    "lineSpacing": 1.5,
    "paragraphSpacing": 1.0,
    "indent": 2.0,
    "defaultPageAnim": "overlay",
    "pageTurnDir": "horizontal",
    "fullScreen": false,
    "keepScreenOn": false,
    "brightness": -1.0,
    "marginHorizontal": 24.0,
    "marginVertical": 48.0,
    "readingFont": "harmony",
    "letterSpacing": 0.0,
    "fontWeight": "normal",
    "textAlign": "left",
    "chineseConvert": "none",
    "useZhLayout": false,
    "usePanguSpacing": false
  },

  "headerFooter": {
    "headerVisibility": "hide_when_status_bar",
    "headerLeft": "chapter_title",
    "headerCenter": "none",
    "headerRight": "none",
    "footerVisibility": "always_show",
    "footerLeft": "progress",
    "footerCenter": "page_number",
    "footerRight": "time",
    "headerFooterAlpha": 0.4,
    "showProgress": true
  },

  "titleStyle": {
    "titleAlign": "center",
    "titleSizeOffset": 4,
    "titleMarginTop": 9.0,
    "titleMarginBottom": 60.0
  },

  "pageTurn": {
    "volumeKeyTurnPage": false,
    "edgeTurnPage": true
  },

  "library": {
    "duplicateCheckEnabled": true,
    "importCopyFile": true,
    "unifiedCoverPalette": "auto",
    "viewMode": "GRID"
  },

  "readingStats": {
    "readingTimeEnabled": true,
    "readingDailyTarget": 30
  },

  "tts": {
    "ttsSpeed": 1.0,
    "ttsPitch": 1.0,
    "ttsAutoPage": false,
    "ttsHighlightSentence": false
  },

  "advanced": {
    "gpuAcceleration": true,
    "loggingEnabled": false
  }
}
```

**不同步的配置（明确排除）**：

| 排除项 | 原因 |
|-------|------|
| `syncMethod` | 同步方式属于设备本地决策 |
| `webdavUrl` | 凭据，不同步 |
| `webdavUser` | 凭据，不同步 |
| `webdavPassword` | 凭据，不同步 |

### 5.7 config/reader_presets.json / config/reader_presets.enc

阅读器预设（多套排版方案）同步：

```json
{
  "schemaVersion": 1,
  "updatedAt": 1710000000000,
  "presets": [
    {
      "id": 1,
      "name": "日间舒适",
      "createdAt": 1710000000000,
      "config": {
        "fontSize": 18.0,
        "lineSpacing": 1.8,
        "paragraphSpacing": 1.2,
        "indent": 2.0,
        "pageAnimType": "HORIZONTAL",
        "backgroundColor": "PAPER",
        "marginHorizontal": 24.0,
        "marginVertical": 48.0,
        "brightness": -1.0,
        "readingFont": "harmony",
        "optimizeRender": true,
        "letterSpacing": 0.0,
        "fontWeight": "NORMAL",
        "textAlign": "LEFT",
        "chineseConvert": "NONE",
        "useZhLayout": false,
        "usePanguSpacing": false,
        "keepScreenOn": false,
        "volumeKeyTurnPage": false,
        "edgeTurnPage": true,
        "ttsSpeed": 1.0,
        "ttsPitch": 1.0,
        "titleStyle": {
          "align": "CENTER",
          "sizeOffsetSp": 4,
          "marginTopDp": 9.0,
          "marginBottomDp": 60.0
        },
        "header": {
          "visibility": "HIDE_WHEN_STATUS_BAR",
          "left": "CHAPTER_TITLE",
          "center": "NONE",
          "right": "NONE"
        },
        "footer": {
          "visibility": "ALWAYS_SHOW",
          "left": "PROGRESS",
          "center": "PAGE_NUMBER",
          "right": "TIME"
        },
        "headerFooterAlpha": 0.4,
        "showProgress": true
      }
    },
    {
      "id": 2,
      "name": "夜间护眼",
      "createdAt": 1710000000000,
      "config": { "..." }
    }
  ]
}
```

**设计要点**：

- 预设是完整的 `ReaderPreferences` 快照，包含所有阅读器配置
- 预设 ID 仅本地使用，同步时以 `name` 为标识
- 同名预设：远端时间戳最新 wins
- 删除预设：tombstone 标记，30 天后清理

加密模式下：

```text
config/preferences.enc = encrypt(json(preferences))
```

---

## 第六章 同步策略

### 6.1 同步状态机（SyncStateMachine）

同步过程需要一个明确的状态机，用于协调 UI 展示、Worker 调度和通知栏提示。

**状态枚举**：

```kotlin
enum class SyncState {
    IDLE,            // 空闲，无同步任务
    SCANNING,        // 扫描本地变更 / 拉取远端 manifest
    DOWNLOADING,     // 下载远端数据
    MERGING,         // 合并冲突
    UPLOADING,       // 上传本地变更
    SUCCESS,         // 同步成功完成
    FAILED,          // 同步失败（含具体错误）
    RATE_LIMITED,    // 被限流（429/503），等待退避
    WAITING_RETRY,   // 等待重试（网络断开等）
    CRYPTO_LOCKED    // E2EE 模式下密钥未解锁
}
```

**状态转换图**：

```text
IDLE ──→ SCANNING ──→ DOWNLOADING ──→ MERGING ──→ UPLOADING ──→ SUCCESS
  ↑         │              │             │            │            │
  │         ▼              ▼             ▼            ▼            │
  │      FAILED         FAILED        FAILED       FAILED         │
  │         │              │             │            │            │
  │         ▼              ▼             ▼            ▼            │
  │    RATE_LIMITED    RATE_LIMITED  RATE_LIMITED RATE_LIMITED    │
  │         │              │             │            │            │
  │         ▼              ▼             ▼            ▼            │
  └── WAITING_RETRY ←─────┴─────────────┴────────────┘            │
                                                                  │
  CRYPTO_LOCKED（任意状态下检测到密钥未解锁时跳转）                    │
                                                                  │
  └───────────────────────────────────────────────────────────────┘
```

**状态机职责**：

1. **UI 层**：观察 `SyncState` Flow，展示对应的状态指示器（进度条、文字、颜色）
2. **Worker 层**：根据状态决定是否可启动新同步（`IDLE` / `SUCCESS` / `FAILED` 时可启动）
3. **通知栏**：`SCANNING` / `DOWNLOADING` / `UPLOADING` 时显示进度通知
4. **限流处理**：`RATE_LIMITED` 时记录 `retryAfter`，定时转为 `WAITING_RETRY`

```kotlin
class SyncStateMachine {
    private val _state = MutableStateFlow(SyncState.IDLE)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private var retryAfter: Instant? = null

    fun transition(newState: SyncState) {
        val current = _state.value
        require(isValidTransition(current, newState)) {
            "Invalid transition: $current → $newState"
        }
        _state.value = newState
    }

    fun canStartSync(): Boolean {
        return _state.value in listOf(SyncState.IDLE, SyncState.SUCCESS, SyncState.FAILED)
    }
}
```

### 6.2 变更检测策略（增量同步核心）

同步的关键不是"怎么传"，而是"传什么"。必须精确识别变更数据，避免全量扫描和重复传输。

**三层脏标记机制**：

```text
┌─────────────────────────────────────────────────────────────┐
│                    变更检测三层架构                           │
├─────────────────────────────────────────────────────────────┤
│  第一层：内存脏标记（设置类）                                  │
│    UserPreferences 修改 → dirtyKeys += key                  │
│    持久化到 DataStore 后写入 pendingSyncKeys                 │
├─────────────────────────────────────────────────────────────┤
│  第二层：Room 脏标记（数据类）                                │
│    书签/笔记/进度修改 → isDirty = true                       │
│    同步成功 → isDirty = false, syncedVersion = version       │
├─────────────────────────────────────────────────────────────┤
│  第三层：Hash 免检（静态文件）                                │
│    书籍/字体文件 → fastHash 相同 → 跳过                      │
│    不读取文件内容，不做传输                                   │
└─────────────────────────────────────────────────────────────┘
```

**第一层：内存脏标记（设置类配置）**

适用于 `config/preferences.json` 中的配置项：

```kotlin
class PreferencesDirtyTracker {
    // 内存中记录被修改的 key
    private val _dirtyKeys = MutableStateFlow<Set<String>>(emptySet())
    val dirtyKeys: StateFlow<Set<String>> = _dirtyKeys.asStateFlow()

    // 用户修改设置时调用
    fun markDirty(key: String) {
        _dirtyKeys.update { it + key }
    }

    // 同步成功后清除
    fun clearDirty() {
        _dirtyKeys.update { emptySet() }
    }

    // 是否有脏数据
    fun hasDirty(): Boolean = _dirtyKeys.value.isNotEmpty()
}
```

**行为**：
- 用户修改字号 → `dirtyKeys += "default_font_size"`
- 用户修改主题 → `dirtyKeys += "theme_mode"`
- 同步时检测 `hasDirty()` → 只有脏了才上传 `preferences.json`
- 同步成功 → `clearDirty()`

**优化效果**：
- 用户只改了字号 → 只上传 `preferences.json`（~1KB）
- 用户未改设置 → 跳过配置同步（0 请求）

**第二层：Room 脏标记（数据类实体）**

适用于书签、笔记、阅读进度：

```kotlin
// 书签表新增字段
@Entity
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: Long,
    val byteOffset: Long,
    // ... 其他字段 ...

    // 同步相关字段
    val isDirty: Boolean = false,        // 本地修改后置 true
    val syncedVersion: Int = 0,          // 上次同步成功的版本号
    val version: Int = 1,                // 当前版本号，每次修改递增
    val updatedAt: Long = System.currentTimeMillis(),
    val remoteBookKey: String? = null    // 远端 bookKey 映射
)
```

**行为**：
- 用户添加书签 → `isDirty = true, version = max(local, remote) + 1`
- 用户修改书签 → `isDirty = true, version++`
- 用户删除书签 → `isDirty = true, deleted = true`（tombstone）
- 同步时查询 `WHERE isDirty = true` → 只处理脏数据
- 同步成功 → `isDirty = false, syncedVersion = version`

**同样的逻辑适用于**：
- `NoteEntity`（笔记）
- `ReadingProgressEntity`（阅读进度）
- `ReaderPresetEntity`（阅读预设）

**第三层：Hash 免检（静态文件）**

适用于书籍文件、用户自定义字体：

```kotlin
// 书籍表
@Entity
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val title: String,
    val filePath: String,
    val fileType: String,  // TXT, EPUB
    val fileSize: Long,
    val fastHash: String,   // 文件头部 + 尾部 + 大小的快速 hash
    val fullHash: String?,  // 完整 SHA-256（后台计算，可为空）
    // ...
)
```

**同步时判断逻辑**：

```kotlin
fun shouldSyncBookFile(local: BookEntity, remote: MetaJson): Boolean {
    // 1. 远端不存在 → 需要上传
    if (remote == null) return true

    // 2. fastHash 相同 → 跳过（99% 的情况）
    if (local.fastHash == remote.fileHash) return false

    // 3. fastHash 不同 → 可能是不同版本，需要用户确认
    //    提示："远端有一本同名但内容不同的书，如何处理？"
    return false // 不自动覆盖，等用户决策
}
```

**fastHash 计算**（三点采样，提升对中文小说的辨别力）：

```kotlin
fun computeFastHash(file: File): String {
    val buffer = ByteArray(4096)  // 每段 4KB
    val digest = MessageDigest.getInstance("SHA-256")
    val fileSize = file.length()

    // 第一段：头部 4KB（版权声明等固定内容仍需纳入）
    file.inputStream().use { fis ->
        val headRead = fis.read(buffer)
        digest.update(buffer, 0, headRead)
    }

    // 第二段：中间偏移 1/3 处 4KB
    // 目的：绕过头尾固定区域，大幅提升中文小说辨别力
    if (fileSize > 8192) {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(fileSize / 3)
            val midRead = raf.read(buffer)
            digest.update(buffer, 0, midRead)
        }
    }

    // 第三段：尾部 4KB
    if (fileSize > 4096) {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(maxOf(0, fileSize - 4096))
            val tailRead = raf.read(buffer)
            digest.update(buffer, 0, tailRead)
        }
    }

    // 加入文件大小（防止不同大小的文件产生相同 hash）
    digest.update(ByteBuffer.allocate(8).putLong(fileSize).array())

    return digest.digest().toHexString()
}
```

**为什么使用三点采样而非头 + 尾**：

中文网文存在大量固定模板：头部是版权声明/网站来源，尾部是"全文完"/出版信息。双点采样对这类文件的辨别力退化为主要依赖文件大小，碰撞率显著高于预期。三点采样中间偏移 1/3 处的内容通常是正文章节，能有效区分内容相近但章节不同的书籍版本。

**优化效果**：
- 100 本书，用户只新增 1 本 → 只上传 1 个文件
- 未修改的 99 本 → fastHash 相同，0 请求

**字体文件同理**：

```kotlin
// 字体同步时
fun shouldSyncFont(localFont: FontFile, remoteMeta: FontMeta): Boolean {
    if (remoteMeta == null) return true
    if (localFont.fastHash == remoteMeta.fileHash) return false
    return false // 不自动覆盖
}
```

### 6.3 同步时的完整变更检测流程

```text
SyncEngine.sync(transport)
│
├─ 1. 拉取远端 manifest
│
├─ 2. 配置变更检测
│     └─ hasDirty("preferences")? → YES → 上传 preferences.json
│
├─ 3. 预设变更检测
│     └─ hasDirty("reader_presets")? → YES → 上传 reader_presets.json
│
├─ 4. 进度变更检测
│     └─ SELECT * FROM ReadingProgress WHERE isDirty = true
│     └─ 逐本上传 state/{bookKey}.json
│
├─ 5. 书签变更检测
│     └─ SELECT * FROM Bookmark WHERE isDirty = true
│     └─ 按 bookKey 分组，上传 bookmarks/{bookKey}.json
│
├─ 6. 笔记变更检测
│     └─ SELECT * FROM Note WHERE isDirty = true
│     └─ 按 bookKey 分组，上传 notes/{bookKey}.json
│
├─ 7. 书籍文件变更检测（若开启）
│     └─ 比较 fastHash → 只上传新增/变更的书籍
│
├─ 8. 下载远端变更
│     └─ 比较 version → 只下载 version 更高的远端数据
│
└─ 9. 清除脏标记
      └─ isDirty = false, syncedVersion = version
```

**请求数估算（典型场景）**：

| 场景 | 请求数 | 说明 |
|------|--------|------|
| 只改了字号 | 2 | GET manifest + PUT preferences |
| 读了 3 本书 | 8 | GET manifest + PUT 3×state + PUT 3×manifest + PUT manifest |
| 加了 5 个书签（同一本书） | 4 | GET manifest + PUT bookmarks/{bookKey} + PUT manifest |
| 新增 1 本书 | 6 | GET manifest + PUT meta + PUT book file + PUT state + PUT manifest |
| 什么都不改 | 1 | GET manifest（确认无变更） |

### 6.4 同步引擎（统一，不区分云端/本地）

所有同步操作通过 `SyncEngine` 执行，传入不同的 `SyncTransport`：

```kotlin
class SyncEngine(
    private val manifestManager: ManifestManager,
    private val conflictResolver: ConflictResolver,
    private val cryptoManager: SyncCryptoManager?
) {
    suspend fun sync(transport: SyncTransport, options: SyncOptions) {
        // 1. 确保远端目录存在
        transport.ensureDirectories()

        // 2. 拉取远端 manifest
        val remoteManifest = transport.readManifest()

        // 3. 解密（若启用 E2EE）
        val decryptedManifest = remoteManifest?.let { cryptoManager?.decrypt(it) }

        // 4. diff 本地/远端
        val diff = manifestManager.diff(localManifest, decryptedManifest)

        // 5. 上传脏数据
        diff.toUpload.forEach { transport.write(it.path, it.data) }

        // 6. 下载远端独有数据
        diff.toDownload.forEach { transport.write(it.localPath, transport.read(it.path)) }

        // 7. 更新 manifest
        transport.writeManifest(updatedManifest)
    }
}
```

### 6.5 手动同步

用户点击"立即同步"（云端或本地）：

```text
1. SyncEngine.sync(transport, options)
2. transport 自动处理：
   - WebDavTransport: testConnection + HTTP PUT/GET
   - LocalTransport: 文件系统 I/O
3. 显示同步结果
```

**书籍文件同步是高级选项**：

- **默认行为**：同步阅读进度、书签、笔记、配置；书籍文件不同步。
- **用户手动开启后**：书籍文件才参与同步。
- **理由**：书籍文件是空间、冲突复杂度的主要放大器。对云端，还增加限流风险。

### 6.6 阅读页退出时低延迟同步进度

```text
ReaderViewModel.onCleared / 退出阅读页
→ enqueue progress sync
→ debounce 2-5s
→ serialize state JSON
→ encrypt if E2EE enabled
→ SyncEngine.syncSingle(transport, "state/{bookKey}.json", data)
```

原则：

- 只同步一个小文件。
- 不触发 manifest 全量扫描。
- 失败进入本地 pending 队列。
- 云端和本地使用相同逻辑，仅 transport 不同。

### 6.7 启动后轻量同步

App 启动后延迟 5-10 秒：

```text
SyncEngine.syncLite(transport)
→ 仅拉取 manifest + 最近阅读书籍的 state
→ 不下载大文件，不扫全目录
```

### 6.8 WorkManager 周期同步

**云端周期同步**：

约束：
- NetworkType.CONNECTED
- 大文件上传可选 `UNMETERED`
- 指数退避
- 唯一任务名：`webdav_periodic_sync`

频率建议：
- 默认关闭
- 用户开启后 6-12 小时一次
- 坚果云用户不要低于 6 小时，避免频率限制

**本地周期同步**（可选）：

约束：
- 无网络约束（本地 I/O）
- 唯一任务名：`local_periodic_sync`

频率建议：
- 默认关闭
- 用户开启后 1-6 小时一次
- 适合 Syncthing/NAS 用户，保证数据及时写入同步目录

---

## 第七章 冲突解决

### 7.1 logicalVersion 机制

**问题**：纯依赖时间戳判断冲突存在风险——设备时钟不准、时区差异、NTP 跳变都可能导致误判。

**解决方案**：在时间戳基础上增加 `logicalVersion`（逻辑版本号），每次修改递增。

**优先级规则**：

```text
1. logicalVersion 高的 wins（最优先）
2. logicalVersion 相同时，比较 updatedAt
3. updatedAt 也相同时，使用二级比较（进度内容等）
```

**实现**：

```kotlin
fun resolveByVersion(local: BookState, remote: BookState): BookState {
    // 1. 逻辑版本比较
    if (remote.version > local.version) return remote
    if (local.version > remote.version) return local

    // 2. 版本相同，比较时间戳
    if (remote.updatedAt > local.updatedAt) return remote
    if (local.updatedAt > remote.updatedAt) return local

    // 3. 时间戳也相同，使用内容比较
    return resolveByContent(local, remote)
}
```

**版本递增规则**：

- 每次本地修改：`version = max(localVersion, remoteVersion) + 1`
- 合并冲突后：`version = max(localVersion, remoteVersion) + 1`
- 上传成功后：本地记录服务器返回的版本号

**优势**：

- 避免时钟不准导致的误判
- 版本号单调递增，比较结果确定性高
- 与 `updatedAt` 配合，形成双重保障
- 便于调试：日志中可直接看到版本号变化

### 7.2 时间戳权威性

**核心原则**：以服务器返回的 `Last-Modified` 或 `ETag` 作为权威时间戳，不依赖设备本地时钟。

**原因**：
- 用户设备时钟可能不准（手动调整、时区错误、电池耗尽后重置）。
- WebDAV 服务器时间通常更可靠。
- 本地 `updatedAt` 仅用于本地排序和 pending 队列，不作为跨设备权威。

**实现**：
- 上传时：本地写入 `updatedAt`，服务器返回 `Last-Modified` 后更新本地记录。
- 下载时：以服务器 `Last-Modified` 为准，与本地 `updatedAt` 比较决定是否采用。

### 7.3 进度

默认：服务器时间戳最新 wins。

**冲突判断**：
- 若远端进度比本地新，且差距 > 5%：弹提示，提供三个选项：
  1. "跳转到云端进度"（采用远端）
  2. "保留本地进度"（覆盖远端）
  3. "暂不处理"（跳过本次，下次同步再判断）
- 若差距 ≤ 5%：自动采用最新，不弹提示。

**时间戳相同时的二级比较**（参考 legado）：

当服务器时间戳与本地 `updatedAt` 相同时，使用进度内容比较：

```kotlin
fun resolveProgressConflict(local: BookState, remote: BookState): BookState {
    // 1. 时间戳比较
    if (remote.updatedAt > local.updatedAt) return remote
    if (local.updatedAt > remote.updatedAt) return local

    // 2. 时间戳相同，比较进度内容
    return when {
        // TXT：比较 byteOffset
        local.fileType == "TXT" -> {
            if (remote.byteOffset > local.byteOffset) remote else local
        }
        // EPUB：比较 chapterIndex，相同时比较 chapterPos
        local.fileType == "EPUB" -> {
            when {
                remote.chapterIndex > local.chapterIndex -> remote
                remote.chapterIndex < local.chapterIndex -> local
                remote.chapterPos > local.chapterPos -> remote
                else -> local
            }
        }
        else -> remote // 默认采用远端
    }
}
```

**设计理由**：
- 避免时间戳精度不足导致的误判
- 取"更靠前的阅读位置"作为最新进度
- 参考 legado 的 `durChapterIndex + durChapterPos` 比较逻辑

**E2EE 下冲突解决发生在**：

```text
download .enc
→ decrypt
→ parse JSON
→ resolve conflict
→ encrypt merged JSON
→ upload
```

### 7.4 书签/笔记

使用 item-level UUID + tombstone：

- 同一 `id`：服务器时间戳最新 wins。
- 删除不是物理删除，而是 `deleted=true`。
- tombstone 清理使用设备确认机制，而非固定时间窗口（见下方）。

**tombstone 清理策略（V4 修订）**：

旧策略（"30 天后自动清理"）存在多设备安全盲点：若设备 B 离线超过 30 天，设备 A 已清理 tombstone，B 上线后合并时会误判"B 有这条书签，A 没有"→重新上传，使被删内容复活。

V4 改为基于**设备全量同步确认**的清理策略：

```kotlin
// tombstone 的清理条件：
// 远端 device/ 目录下的所有设备，其 lastSyncAt 都晚于 tombstone.deletedAt
fun canCompactTombstone(tombstone: BookmarkEntity, devices: List<DeviceInfo>): Boolean {
    if (devices.isEmpty()) return false
    return devices.all { device ->
        device.lastSyncAt > tombstone.deletedAt
    }
}
```

**行为说明**：
- 客户端在每次成功同步后，更新远端 `device/{deviceId}.json` 的 `lastSyncAt`
- compact 只在全量同步时触发，逐条检查 tombstone 是否满足清理条件
- 设备 B 长期离线：tombstone 不会被清理，B 上线后正常同步删除状态
- 设备 B 永久离线（如丢失）：提供"移除设备"操作（删除对应 `device/{deviceId}.json`），之后 tombstone 清理不再等待该设备

**合并来源标记**（便于排错）：

书签/笔记合并后，在本地记录中保留 `mergeSource` 字段：

```json
{
  "id": "uuid",
  "byteOffset": 123456,
  "content": "...",
  "mergeSource": "remote",
  "mergeAt": 1710000000000
}
```

- `mergeSource` 可选值：`local`（本地胜出）、`remote`（远端胜出）、`merged`（双方合并）。
- 此字段仅用于排错和日志，不影响业务逻辑。
- 同步日志中记录："书签 {id} 从 {mergeSource} 合并，原因：{reason}"。

### 7.5 配置

分类同步：

- 账号/密码/设备 ID：永不同步。
- 主题/语言：可选同步，默认开启。
- 阅读排版配置：使用 key-level merge（见下方）。

**阅读排版配置的 key-level merge（V4 新增）**：

"远端最新 wins"会导致设备 A 改字号、设备 B 改主题时，其中一个修改被静默丢弃。V4 改为 key-level merge，利用 `PreferencesDirtyTracker` 的脏 key 粒度进行合并：

```kotlin
fun mergePreferences(
    local: UserPreferences,
    remote: UserPreferences,
    localDirtyKeys: Set<String>
): UserPreferences {
    // 合并规则：
    // - local 中标记为 dirty 的 key → 使用 local 值（本地改过）
    // - 其余 key → 使用 remote 值（远端可能更新过，或与 local 相同）
    return UserPreferences(
        fontSize = if ("fontSize" in localDirtyKeys) local.fontSize else remote.fontSize,
        themeMode = if ("themeMode" in localDirtyKeys) local.themeMode else remote.themeMode,
        lineSpacing = if ("lineSpacing" in localDirtyKeys) local.lineSpacing else remote.lineSpacing,
        // ... 其余所有字段同理
    )
}
```

**设计理由**：
- 设备 A 改字号 + 设备 B 改主题 → 两者都能保留，不会互相覆盖
- `localDirtyKeys` 在每次同步结束后清空，下次再改才重新标记
- 与 §6.2 第一层脏标记机制完全兼容，无需新增数据结构
- 远端 `preferences.json` 的整体时间戳不再作为"谁胜出"的唯一依据

### 7.6 小说文件

不自动覆盖不同 hash 的同名书。

策略：

```text
bookKey 相同 + hash 相同：同一本书
bookKey 相同 + hash 不同：冲突，生成副本或询问
title 相同 + hash 不同：视为不同版本
```

加密模式下仍用解密后的 `meta.enc` 和 hash 判断。

---

## 第八章 推送格式与压缩策略

### 8.1 常规同步格式

```text
常规同步 ≠ 压缩包
常规同步 = manifest + 分散 JSON / ENC + 原始书籍文件 / 加密书籍文件
```

### 8.2 Phase 1

```text
小数据：JSON 单文件
小说文件：原始 TXT/EPUB 文件
```

### 8.3 Phase 2

```text
小数据：.enc 单文件
小说文件：book.enc
```

### 8.4 为什么常规同步不用 ZIP

- **低延迟差**：每次只改进度，却要重新打包上传。
- **冲突难处理**：两个设备同时改不同书签，ZIP 只能整包覆盖。
- **增量同步差**：WebDAV 不支持 ZIP 内部增量。
- **内存/IO 更重**：Android 上打包大书库成本高。
- **失败恢复差**：上传到一半失败，整包无效。
- **不利于频繁进度同步**：虽然请求数少，但每次数据量大。

### 8.5 ZIP 的适用场景

ZIP 仅用于手动完整备份/迁移：

```text
/ShuLiReader/backups/shuli-backup-2026-05-28.zip
/ShuLiReaderEncrypted/backups/shuli-backup-2026-05-28.zip.enc
```

不作为日常同步格式。

---

# 第三部分：功能扩展

## 第九章 本地同步与导出设计

### 9.1 两种模式

| 模式 | 格式 | 场景 | 触发方式 |
|------|------|------|---------|
| **同步模式** | 分散 JSON/ENC 文件 | 日常多设备同步、NAS/Syncthing 用户 | 自动/手动 |
| **导出模式** | ZIP / ZIP.enc | 冷备份、换手机迁移、分享给他人 | 手动 |

两种模式共享数据模型和加密逻辑，区别在于存储格式和使用场景。

---

### 9.2 同步模式：传输层抽象

本地同步与云端同步共享**完全相同的同步引擎**，仅传输层不同：

```text
                    ┌─────────────────────────────────────┐
                    │           同步引擎（共享）            │
                    │  ┌─────────────────────────────────┐ │
                    │  │ 变更检测（时间戳对比）            │ │
                    │  │ 冲突解决（latest-wins / merge）  │ │
                    │  │ manifest diff                   │ │
                    │  │ 数据序列化/反序列化              │ │
                    │  │ 加密/解密（E2EE 模式）           │ │
                    │  └─────────────────────────────────┘ │
                    └───────────────┬─────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼                               ▼
          ┌─────────────────┐             ┌─────────────────┐
          │ WebDavTransport │             │ LocalTransport  │
          │  (HTTP/WebDAV)  │             │ (文件系统 I/O)   │
          └────────┬────────┘             └────────┬────────┘
                   ▼                               ▼
            远端 WebDAV 服务器              本地同步目录
```

### 9.3 传输层接口

```kotlin
interface SyncTransport {
    suspend fun read(path: String): ByteArray?
    suspend fun write(path: String, data: ByteArray)
    suspend fun delete(path: String)
    suspend fun list(path: String): List<ResourceInfo>
    suspend fun exists(path: String): Boolean
    suspend fun getMetadata(path: String): ResourceMetadata?  // ETag, Last-Modified
}

// 云端实现
class WebDavTransport(
    private val client: WebDavClient,
    private val cryptoManager: SyncCryptoManager?
) : SyncTransport { ... }

// 本地实现
class LocalFileTransport(
    private val rootDir: File,
    private val cryptoManager: SyncCryptoManager?
) : SyncTransport { ... }
```

### 9.4 本地同步目录结构

与云端**完全一致**：

**明文模式**：

```text
{用户选择的目录}/ShuLiReader/
  manifest.json          ← 包含 schemaVersion
  device/
    {deviceId}.json
  books/
    {bookKey}/
      book.txt / book.epub
      meta.json
  state/
    {bookKey}.json
  bookmarks/
    {bookKey}.json
  notes/
    {bookKey}.json
  config/
    preferences.json
    reader_presets.json
```

**加密模式**：

```text
{用户选择的目录}/ShuLiReaderEncrypted/
  crypto.json
  manifest.enc
  device/
    {deviceId}.enc
  books/
    {bookKey}/
      book.enc
      meta.enc
  state/
    {bookKey}.enc
  bookmarks/
    {bookKey}.enc
  notes/
    {bookKey}.enc
  config/
    preferences.enc
    reader_presets.enc
```

### 9.5 本地同步的原子写入策略

**问题**：本地同步目录可能被 Syncthing、坚果云客户端等第三方同步工具监控。如果写入过程中断（进程被杀、断电），会导致文件损坏，进而触发同步工具上传损坏的文件到其他设备。

**解决方案**：原子写入（Atomic Write）

```kotlin
class AtomicFileWriter {
    /**
     * 原子写入：写入临时文件 → fsync → 重命名
     * 保证文件要么完整写入，要么保持原样，不会出现半写状态
     */
    suspend fun writeAtomic(targetFile: File, data: ByteArray) {
        val tempFile = File(targetFile.parent, ".tmp-${UUID.randomUUID()}")

        try {
            // 1. 写入临时文件
            tempFile.outputStream().use { os ->
                os.write(data)
                os.flush()
                os.fd.sync()  // fsync: 确保数据写入磁盘
            }

            // 2. 原子重命名（同一文件系统内是原子操作）
            tempFile.renameTo(targetFile)
                .takeIf { it }
                ?: throw IOException("Rename failed: ${tempFile.name} → ${targetFile.name}")
        } finally {
            // 3. 清理临时文件（若重命名失败）
            if (tempFile.exists()) tempFile.delete()
        }
    }
}
```

**适用范围**：

- 所有本地同步写入：`manifest.json`、`state/*.json`、`bookmarks/*.json`、`notes/*.json`、`config/*.json`
- 云端同步不适用（WebDAV PUT 本身是整体上传，服务端处理）

**Syncthing 兼容性**：

- Syncthing 监听文件系统 inotify 事件
- 原子重命名会触发 `MOVED_TO` 事件（而非 `MODIFY`）
- Syncthing 会将重命名视为新文件，触发完整同步
- 这是预期行为：保证同步到其他设备的文件是完整的

**清理策略**：

- App 启动时清理 `.tmp-*` 文件（上次同步中断留下的临时文件）
- 临时文件超过 1 小时自动清理

### 9.6 本地同步目录选择

**Android 存储策略**：

1. **SAF（Storage Access Framework）**（推荐）：
   - 使用 `ACTION_OPEN_DOCUMENT_TREE` 让用户选择目录
   - 优点：无需 `WRITE_EXTERNAL_STORAGE` 权限，Android 10+ 兼容好
   - 缺点：性能略低于直接文件访问

2. **应用内部存储**（默认兜底）：
   - 路径：`/data/data/com.shuli.reader/sync/`
   - 优点：无需权限，应用独占访问
   - 缺点：卸载时丢失，其他应用无法访问

3. **共享存储（兼容旧版本）**：
   - 路径：`/sdcard/Documents/ShuLiReader/`
   - 需要：`WRITE_EXTERNAL_STORAGE`（Android 10 以下）

**推荐策略**：
- 首次使用时引导用户选择同步目录
- 默认提供"应用内部存储"选项（无需配置）
- 提供"选择其他位置"按钮（SAF）

### 9.7 本地同步的典型使用场景

**场景 1：单设备本地同步**
- 用户不配置 WebDAV，仅使用本地同步
- 同步目录在应用内部存储
- 保证数据不丢，支持回滚

**场景 2：NAS/网络存储同步**
- 用户选择 NAS 挂载目录（如 `/mnt/nas/ShuLiReader/`）
- 通过 NAS 自带的同步功能实现多设备同步
- 相当于"自带云端"的 WebDAV

**场景 3：Syncthing/坚果云客户端同步**
- 用户选择 Syncthing 同步目录
- 通过第三方同步工具实现跨设备同步
- 无需配置 WebDAV，利用现有同步工具

**场景 4：SD 卡备份**
- 用户选择 SD 卡目录
- 作为冷备份，不参与实时同步
- 换手机时直接插卡恢复

### 9.8 本地同步与云端同步的统一调度

```kotlin
class SyncOrchestrator(
    private val webDavTransport: WebDavTransport?,
    private val localTransport: LocalFileTransport?,
    private val syncEngine: SyncEngine
) {
    suspend fun sync(target: SyncTarget): SyncOrchestratorResult {
        return when (target) {
            SyncTarget.CLOUD -> {
                val result = runCatching { webDavTransport?.let { syncEngine.sync(it) } }
                SyncOrchestratorResult(cloudResult = result, localResult = null)
            }
            SyncTarget.LOCAL -> {
                val result = runCatching { localTransport?.let { syncEngine.sync(it) } }
                SyncOrchestratorResult(cloudResult = null, localResult = result)
            }
            SyncTarget.BOTH -> {
                // 先本地后云端；两端相互独立，任意一端失败不阻断另一端
                val localResult = runCatching { localTransport?.let { syncEngine.sync(it) } }
                val cloudResult = runCatching { webDavTransport?.let { syncEngine.sync(it) } }
                SyncOrchestratorResult(cloudResult = cloudResult, localResult = localResult)
            }
        }
    }
}

data class SyncOrchestratorResult(
    val cloudResult: Result<Unit>?,   // null = 该端未配置
    val localResult: Result<Unit>?    // null = 该端未配置
) {
    val allSucceeded get() = (cloudResult?.isSuccess != false) && (localResult?.isSuccess != false)
}
```

**BOTH 模式的失败语义（V4 明确）**：
- 本地同步成功、云端同步失败 → 两者结果分别上报，不回滚本地同步
- 云端同步成功、本地同步失败 → 两者结果分别上报，不回滚云端同步
- 两者相互独立：各自的数据已同步到各自的存储，错误单独展示在 UI 状态卡片上
- 设置页的两个状态卡片分别显示各自的最近同步结果，不合并为单一状态

**执行顺序**：先本地后云端，因为本地 I/O 更快，若用户立即关闭 app，优先保证本地数据落盘。

```kotlin
enum class SyncTarget {
    CLOUD,   // 仅云端
    LOCAL,   // 仅本地
    BOTH     // 先本地后云端，两端独立
}
```

### 9.9 导出模式：ZIP 打包

ZIP 导出是独立于同步的功能，用于冷备份、迁移、分享。

**适用场景**：

- 换手机时一次性完整迁移
- 导出到 SD 卡作为冷备份
- 分享书库给他人（用独立密码保护）
- 升级前的完整快照

**ZIP 内部结构**：

```text
shuli-export-2026-05-28.zip
├── manifest.json
├── books.json
├── states/
│   ├── {bookKey1}.json
│   └── {bookKey2}.json
├── bookmarks/
│   ├── {bookKey1}.json
│   └── {bookKey2}.json
├── notes/
│   ├── {bookKey1}.json
│   └── {bookKey2}.json
├── config/
│   └── preferences.json
└── books/                    ← 可选
    ├── {bookKey1}/book.txt
    └── {bookKey2}/book.epub
```

加密模式：

```text
shuli-export-2026-05-28.zip.enc
└── (同上，所有内容加密)
```

**导出选项**：

| 选项 | 默认 | 说明 |
|------|------|------|
| 阅读进度 | ✅ 必选 | states/ |
| 书签 | ✅ 开启 | bookmarks/ |
| 笔记 | ✅ 开启 | notes/ |
| 配置 | ✅ 开启 | config/ |
| 书籍文件 | ❌ 关闭 | books/（增大文件体积） |
| 加密 | ❌ 关闭 | 启用后生成 .zip.enc |

**加密密码独立管理**：

导出加密密码与同步加密密码**完全独立**：

| 密码类型 | 用途 | 设置方式 | 遗失后果 |
|---------|------|---------|---------|
| 同步加密密码 | 云端/本地同步的 E2EE | 设置页面配置 | 无法解密同步空间，需重置 |
| 导出加密密码 | ZIP 导出加密 | 导出时临时输入 | 无法解密该 ZIP 文件 |

**设计理由**：
- 导出文件可能分享给他人，用独立密码更灵活
- 导出密码不落盘，每次导出时临时输入
- 导入时输入密码验证，错误提示明确

**导出流程**：

```text
1. 用户点击"导出备份"
2. 选择导出内容（进度/书签/笔记/配置/书籍文件）
3. 选择是否加密
   - 若加密：输入导出密码（临时，不保存）
4. 选择保存位置（SAF 或分享）
5. 读取本地数据，打包为 ZIP（或加密为 .zip.enc）
6. 保存到目标位置
7. 显示导出结果（文件大小、路径）
8. 可选：分享给其他应用
```

**导入流程**：

```text
1. 用户点击"从文件恢复"或打开 .zip/.zip.enc 文件
2. 读取 manifest，显示导出信息（时间、设备、书籍数量、是否加密）
3. 若加密：输入密码验证（与同步密码无关）
4. 选择导入策略：
   - 完全覆盖（清空本地，以导入数据为准）
   - 智能合并（保留本地较新的数据）
   - 仅导入本地不存在的数据
5. 解压到临时目录
6. 执行导入（复用 SyncEngine 的数据导入逻辑）
7. 清理临时目录
8. 显示导入结果
```

**与同步模式的关系**：

- 导入 ZIP 后，数据进入本地同步目录
- 后续可通过同步模式继续增量同步
- ZIP 是"快照"，同步是"持续"

### 9.10 同步状态统一展示

设置页面展示同步和导出功能：

```text
┌─────────────────────────────────────────────┐
│ 同步                                         │
├─────────────────────────────────────────────┤
│ 云端（WebDAV）                               │
│   状态：已同步 · 2 分钟前                    │
│   目标：坚果云                               │
│   [立即同步] [设置]                          │
├─────────────────────────────────────────────┤
│ 本地                                         │
│   状态：已同步 · 5 分钟前                    │
│   目标：/sdcard/Documents/ShuLiReader/       │
│   [立即同步] [更改目录]                      │
├─────────────────────────────────────────────┤
│ 导出与恢复                                   │
│   [导出备份文件]  [从文件恢复]               │
│   最近导出：2026-05-28 · 12.3MB             │
└─────────────────────────────────────────────┘
```

---

## 第十章 端到端加密设计（Phase 2 必做）

### 10.1 目标

E2EE 需要保护：

- 书名、作者、文件名。
- 小说正文。
- 阅读进度。
- 书签文本。
- 笔记内容。
- 阅读偏好配置。
- manifest 书库列表。

E2EE 不保护：

- WebDAV 服务商可见的文件大小大致范围。
- 同步时间。
- 目录层级。
- `bookKey` 数量。
- 请求来源 IP。

### 10.2 加密算法

主选：

```text
AES-256-GCM
```

原因：

- Android Keystore / Java Crypto 支持成熟，所有 API 版本均有硬件加速。
- AEAD 同时提供加密和完整性认证。
- 不需要额外 HMAC。

备选：

```text
ChaCha20-Poly1305
```

**Android 兼容性约束（重要）**：

ChaCha20-Poly1305 通过 `Cipher.getInstance("ChaCha20-Poly1305")` 是 Java 11 JEP 329 引入的，但 Android 的 Conscrypt provider 对其支持因版本而异：

| 方案 | minSdk 要求 | 说明 |
|------|------------|------|
| 标准 JCA `Cipher.getInstance("ChaCha20-Poly1305")` | API 28+（建议实测验证） | 部分机型 Conscrypt 未暴露该算法 |
| Google Tink 库 | API 21+ | 统一封装了两种算法，推荐用于跨平台场景 |
| 纯 AES-256-GCM | API 21+ | 所有版本均稳定，无兼容风险 |

**V4 决策**：

- Phase 2 默认实现 AES-256-GCM。
- 若引入 Google Tink，可同时支持 ChaCha20-Poly1305 作为低端设备的备选（Tink 处理兼容性）。
- 不推荐直接调用 JCA ChaCha20-Poly1305，除非明确 minSdk ≥ 28 且在目标设备上测试通过。
- `keyVersion` 字段保留，未来可平滑迁移到任何算法。

### 10.3 密钥派生

用户设置"同步加密密码"：

```text
syncPassword
+ random salt（16 字节，SecureRandom）
+ PBKDF2WithHmacSHA256
+ iterations >= 600,000       ← V4 从 310,000 提升至 600,000
→ masterKey 256-bit
```

**为什么提升至 600,000**：OWASP 2023 将 PBKDF2-HMAC-SHA256 的推荐迭代次数从 310,000 更新为 600,000，以对抗现代 GPU（如 RTX 4090）的暴力破解能力。同步加密密码作为 E2EE 的唯一入口，必须符合最新安全基准。

P2 可升级 Argon2id / scrypt，但会引入额外依赖。第一版 E2EE 推荐 PBKDF2，轻量且平台内置。`iterations` 字段存入 `crypto.json`，未来可无缝提升。

### 10.4 crypto.json

远端加密元数据，不包含明文书籍信息。

```json
{
  "schemaVersion": 1,
  "kdf": "PBKDF2WithHmacSHA256",
  "iterations": 600000,
  "salt": "base64...",
  "cipher": "AES-256-GCM",
  "keyVersion": 1,
  "createdAt": 1710000000000,
  "integrity": "base64..."
}
```

**crypto.json 完整性保护（V4 新增）**：

`crypto.json` 明文存储于远端，若攻击者可修改（如将 `iterations` 改为 1），可实施 KDF 参数降级攻击，大幅降低密码暴力破解成本。

V4 采用两层防护：

**第一层：本地缓存校验**

```kotlin
// 首次成功验证后，将 crypto.json 的 SHA-256 存储到本地 DataStore
suspend fun verifyAndCacheCryptoJson(remote: CryptoMetadata) {
    val localCachedHash = dataStore.getCryptoJsonHash()
    val remoteHash = sha256(json(remote))

    if (localCachedHash != null && localCachedHash != remoteHash) {
        // 内容被修改，向用户告警
        throw CryptoConfigTamperedException("crypto.json 已被修改，可能遭到攻击")
    }

    // 首次或校验通过：更新本地缓存
    dataStore.setCryptoJsonHash(remoteHash)
}
```

**第二层：integrity 字段（HMAC 签名）**

`crypto.json` 中的 `integrity` 字段存储由 masterKey 派生子密钥计算的 HMAC-SHA256：

```kotlin
fun computeIntegrity(meta: CryptoMetadata, masterKey: ByteArray): String {
    val hkdfKey = hkdf(masterKey, info = "crypto-integrity")
    val payload = "${meta.kdf}:${meta.iterations}:${meta.salt}:${meta.cipher}:${meta.keyVersion}"
    return hmacSha256(hkdfKey, payload.toByteArray()).toBase64()
}

fun verifyCryptoIntegrity(meta: CryptoMetadata, masterKey: ByteArray): Boolean {
    val expected = computeIntegrity(meta, masterKey)
    return MessageDigest.isEqual(expected.toByteArray(), meta.integrity.toByteArray())
}
```

**设计理由**：
- 攻击者无法在不知道 masterKey（同步密码）的情况下伪造 `integrity` 字段
- 即使 `crypto.json` 被替换，解密时 integrity 校验失败会立即阻止继续操作
- HMAC 使用 HKDF 派生的子密钥，与数据加密密钥隔离

### 10.5 单文件加密格式

每个 `.enc` 文件结构：

```text
SHULIENC1 magic       9 bytes
keyVersion           4 bytes
nonceLength          1 byte
nonce                12 bytes
ciphertext           N bytes
gcmTag               16 bytes（Java GCM 通常附在 ciphertext 尾部）
```

逻辑封装：

```kotlin
EncryptedPayload(
    magic = "SHULIENC1",
    keyVersion = 1,
    nonce = random(12),
    ciphertext = AES_GCM_Encrypt(plaintext, aad)
)
```

### 10.6 AAD 设计

使用 AAD 防止文件被挪位置后仍能通过认证：

```text
AAD = remotePath + ":" + schemaVersion + ":" + bookKey
```

示例：

```text
state/{bookKey}.enc:2:{bookKey}
```

### 10.7 大文件加密

小说文件可能很大，不能整本读入内存。

推荐分片流式加密：

```text
book.enc
  header
  chunkSize
  chunkCount
  chunk[0].nonce + chunk[0].ciphertext
  chunk[1].nonce + chunk[1].ciphertext
  ...
```

默认 chunk：

```text
1MB 或 4MB
```

每个 chunk 独立 AES-GCM：

```text
nonce = random 12 bytes
AAD = bookKey + chunkIndex + totalSize + fileHash
```

优点：

- 内存稳定。
- 上传前可边读边加密到临时文件。
- 下载可边解密边写入临时文件。
- 单 chunk 损坏可定位。

### 10.8 加密写入流程

小 JSON：

```text
json DTO
→ UTF-8 bytes
→ encryptBytes()
→ PUT *.enc
```

大文件：

```text
source book file
→ encryptToTempFile()
→ PUT temp encrypted file streaming
→ delete temp
```

下载：

```text
GET *.enc
→ decrypt to memory / temp file
→ import / merge
```

### 10.9 密码错误处理

如果用户输入错误同步密码：

- `crypto.json` 可正常读取。
- 解密 `manifest.enc` 会 GCM tag 校验失败。
- UI 显示："同步密码错误或远端数据损坏"。

### 10.10 密码遗失

必须明确提示：

```text
同步加密密码无法找回。忘记后无法解密云端备份。
```

### 10.11 密钥轮换

P2 可先不做自动轮换，但格式保留 `keyVersion`。

后续支持：

```text
输入旧密码 + 新密码
→ 解密所有远端数据
→ 用新 keyVersion 重新加密上传
```

---

# 第四部分：横切关注点

## 第十一章 安全设计

### 11.1 本地凭据存储

当前 DataStore 明文存 `webdav_password`，需要改造。

推荐：

- URL、用户名仍放 DataStore。
- WebDAV 密码/应用密码放 Android Keystore 加密后的本地文件。
- 同步加密密码不直接落盘。
- 可选：保存由 Keystore 包裹的派生 key，需用户授权或生物识别后解锁。

更稳方向：

```text
Keystore AES key
+ app private file encrypted blob
+ DataStore 只存引用/标记
```

### 11.2 凭据分类管理

**WebDAV 账号密码**、**同步加密密码**、**导出加密密码** 必须分开管理：

| 凭据类型 | 存储位置 | 丢失后果 | 恢复方式 |
|---------|---------|---------|---------|
| WebDAV URL | DataStore | 需重新配置 | 用户重新输入 |
| WebDAV 用户名 | DataStore | 需重新配置 | 用户重新输入 |
| WebDAV 密码/应用密码 | Keystore 加密文件 | 需重新配置 | 用户重新输入 |
| 同步加密密码 | 不落盘（仅内存/Keystore 派生 key） | 无法解密同步空间 | 不可恢复，需重置 |
| 导出加密密码 | 不落盘（每次导出临时输入） | 无法解密该 ZIP 文件 | 不可恢复 |

### 11.3 同步加密密码遗失策略

**核心原则**：同步加密密码丢失后不可恢复，但应提供"重置并重新建立新空间"的流程。

**密码遗失提示**（必须在设置界面明确展示）：

```
⚠️ 同步加密密码无法找回
忘记密码后，云端已加密的数据将无法解密。
如需继续使用加密同步，请重置并建立新的加密同步空间。
```

**重置流程**：

1. 用户点击"重置加密同步空间"。
2. 二次确认："此操作将创建新的加密空间，旧空间数据无法恢复。确认继续？"
3. 生成新的 `crypto.json`（新 salt、新 keyVersion）。
4. 创建新的 `/ShuLiReaderEncrypted/` 目录（或清理旧目录）。
5. 本地数据重新加密上传到新空间。
6. 旧空间数据保留，用户可手动清理。

**设计理由**：
- 避免用户以为"忘了密码还能客服找回"。
- 重置流程保证用户可以继续使用同步功能，只是丢失历史云端数据。

### 11.4 传输安全

- 默认只允许 HTTPS。
- HTTP 需要用户显式确认"不安全连接"。
- 日志绝不打印 password、Authorization、完整 URL query。
- E2EE 模式下即使 HTTPS 被服务端终止，服务端也只能看到密文。

### 11.5 端到端加密安全边界

E2EE 必须覆盖：

- manifest
- state
- bookmarks
- notes
- config
- books
- backups

E2EE 不覆盖：

- 请求时间
- 文件大小近似
- 文件数量
- WebDAV 根目录名
- 设备 IP

### 11.6 加密模式兼容策略

- Phase 1 明文目录：`/ShuLiReader/`
- Phase 2 加密目录：`/ShuLiReaderEncrypted/`
- 不建议在同一目录混合 `.json` 和 `.enc`。
- 可提供"一键迁移到加密同步空间"：
  1. 拉取明文数据。
  2. 本地加密。
  3. 上传到 `/ShuLiReaderEncrypted/`。
  4. 用户确认后可删除明文目录。

---

## 第十二章 性能设计

### 12.1 请求数控制

- 优先读写 `manifest`，避免每次 `PROPFIND` 全目录。
- 每本书的进度/书签/笔记分别一个 JSON/ENC，避免单条批注一个请求。
- 新增/删除后才更新 manifest。
- `PROPFIND Depth=1` 只用于首次初始化或 manifest 缺失修复。
- 单线程同步或最多 2 并发。
- E2EE 不改变请求数，只增加本地 CPU 成本。

**manifest 写入串行化（V4 新增）**：

并发同步时（最多 2 并发），两个任务可能同时读取 manifest、分别修改、分别写回，造成写丢失（后写覆盖前写）。V4 要求 manifest 的读-改-写必须串行执行：

```kotlin
// manifest 写入使用 Mutex 保护，防止并发写丢失
private val manifestWriteMutex = Mutex()

suspend fun updateManifest(update: (SyncManifest) -> SyncManifest) {
    manifestWriteMutex.withLock {
        val current = transport.readManifest()
        val updated = update(current)
        transport.writeManifest(updated, ifMatch = current.etag)  // 乐观锁
    }
}

// WebDAV 层面额外使用 If-Match ETag 做服务端校验
// 若服务端返回 412，说明有并发写入，重新拉取后再试
```

**并发上限建议**：
- manifest 写入：必须串行（Mutex）
- 书籍文件上传（books/{bookKey}/book.enc）：可 2 并发
- 小 JSON 上传（state/bookmarks/notes）：可 2 并发，但单 bookKey 同类文件串行

### 12.2 文件上传/下载

- 使用 OkHttp streaming RequestBody / ResponseBody，不把整本书读入内存。
- 明文模式：原文件 streaming PUT/GET。
- 加密模式：先加密到 app cache 临时文件，再 streaming PUT；下载先 streaming 到临时文件，再解密导入。
- 书籍文件只在：
  - 本地新增且远端缺失。
  - 用户明确开启"同步书籍文件"。
  - fastHash/fullHash 不一致且用户确认覆盖。
- 进度/书签/笔记默认自动同步。
- 大文件上传前检查服务端限制；坚果云默认 500MB，应提示用户。

**大文件加密的磁盘临时占用（V4 新增说明）**：

加密模式下，大文件的处理路径为：原文件（N MB）→ 加密临时文件（N MB+）→ 上传后删除临时文件。因此在上传期间，app cache 目录会同时存在原文件和加密临时文件，**峰值磁盘占用约为文件大小的 2 倍**。

验收要求：
- 内存峰值 < 16MB（通过 chunk 流式加密保证）
- 临时文件磁盘占用 ≤ 原文件大小 × 1.1（加密后体积略增，不超过 10%）
- 同步结束后 cache 目录自动清理所有 `.enc.tmp` 文件
- 若磁盘空间不足（cache 剩余 < 文件大小 × 1.5），提前告知用户并中止，不允许中途失败留下残破临时文件

**未来优化方向（Phase 3）**：考虑实现"边加密边上传"（`EncryptedFileStream` 直接驱动 OkHttp `RequestBody`），消除临时文件的磁盘占用。当前 Phase 2 先以临时文件方案实现，稳定后再优化。

### 12.3 加密性能预算

目标：

- 小 JSON 加密 < 5ms。
- 5MB TXT 加密 < 300ms。
- 100MB EPUB/TXT 加密走后台，不阻塞 UI。
- 大文件加密临时文件内存峰值 < 16MB。

### 12.4 本地 dirty 队列

新增本地同步状态表：

```text
SyncTaskEntity:
  id
  type: PROGRESS / BOOKMARKS / NOTES / CONFIG / BOOK_FILE
  bookKey
  localPath
  remotePath
  payloadJson          ← 仅用于小数据（< 64KB），大文件不在此存内容
  encryptedPayloadPath ← 大文件加密后的本地临时路径（替代 payloadJson）
  retryCount
  nextRetryAt
  createdAt
  updatedAt
  requiresEncryption
```

**`payloadJson` 字段约束**：

- 仅用于进度/书签/笔记/配置等小 JSON（< 64KB）
- 书籍文件（BOOK_FILE 类型）不存内容：`payloadJson = null`，仅存 `localPath`
- 若未来某个 JSON 超过 64KB，应改为写入临时文件并用 `encryptedPayloadPath` 引用，不得直接存入数据库列
- 超过限制的数据会导致 Room 数据库体积膨胀，并在序列化时产生额外内存压力

推荐直接 Room，避免进程死亡丢任务。

### 12.5 限流策略

- 每次同步设置请求预算，例如 100 次。
- 遇到 429/503：暂停同步，指数退避，记录 `retryAfter`。
- 坚果云免费版按 600/30min 设计，推荐保守：最多 100 请求/10min。

---

## 第十三章 可观测性与排障

同步功能上线后，最常见的问题不是协议本身，而是"用户说不同步"。必须提供清晰的状态可见性。

### 13.1 同步状态 UI 展示项

在设置页面的"同步状态"区域展示以下信息：

| 状态项 | 说明 | 示例 |
|-------|------|------|
| 最近一次同步时间 | 上次成功或失败的时间 | "2 分钟前" / "从未同步" |
| 最近一次同步结果 | 成功/失败及原因 | "成功" / "失败：网络超时" |
| 当前请求计数 | 本次同步周期已用请求数 | "12 / 100 次" |
| 剩余重试时间 | 遇到 429/503 后的退避时间 | "5 分钟后重试" |
| 远端目录状态 | 是否已初始化 | "已初始化" / "未初始化，需手动同步" |
| 当前空间类型 | 明文/加密 | "明文空间" / "加密空间" |
| 同步加密密码状态 | 是否已设置/解锁 | "已设置" / "未设置" / "已锁定" |

### 13.2 同步操作日志（SyncOperationLog）

每次同步操作记录详细日志，用于排障和性能分析。

**日志结构**：

```kotlin
@Serializable
data class SyncOperationLog(
    val timestamp: Long,           // 操作时间
    val deviceId: String,          // 本地设备 ID（用于多设备问题定位）
    val transportType: String,     // "WEBDAV" | "LOCAL"
    val bookKey: String?,          // 操作的书籍（可选）
    val action: SyncAction,        // 操作类型
    val requestsUsed: Int,         // 本次操作消耗的请求数（云端）
    val duration: Long,            // 耗时（ms）
    val result: SyncResult,        // 结果
    val conflictReason: String?,   // 冲突原因（若有）
    val mergeSource: String?,      // 合并来源：local/remote/merged
    val error: String?,            // 错误信息
    val bytesTransferred: Long     // 传输字节数
)

enum class SyncAction {
    SYNC_FULL,           // 全量同步
    SYNC_PROGRESS,       // 仅同步进度
    SYNC_BOOKMARKS,      // 同步书签
    SYNC_NOTES,          // 同步笔记
    SYNC_CONFIG,         // 同步配置
    SYNC_BOOK_FILE,      // 同步书籍文件
    UPLOAD_MANIFEST,     // 上传 manifest
    DOWNLOAD_MANIFEST,   // 下载 manifest
    TEST_CONNECTION      // 测试连接
}

enum class SyncResult {
    SUCCESS,
    FAILED,
    RATE_LIMITED,
    CONFLICT_RESOLVED,
    SKIPPED,             // 跳过（如密钥未解锁）
    PARTIAL              // 部分成功
}
```

**存储策略**：

- Room 表 `SyncOperationLogEntity`，保留最近 200 条
- 超过 200 条自动清理最旧记录
- 提供"导出日志"功能（生成文本文件，便于反馈问题）

**日志查询 UI**：

```text
设置 → 同步 → 查看同步日志

┌─────────────────────────────────────────────┐
│ 2026-05-29 10:30  全量同步  成功  1.2s      │
│   设备: Pixel 7  请求: 12  传输: 2.3KB      │
├─────────────────────────────────────────────┤
│ 2026-05-29 10:25  进度同步  成功  0.3s      │
│   书籍: a1b2c3d4  请求: 2  传输: 128B       │
├─────────────────────────────────────────────┤
│ 2026-05-29 09:15  全量同步  失败  5.1s      │
│   错误: RATE_LIMITED  重试: 10:45           │
└─────────────────────────────────────────────┘
```

### 13.3 同步日志（本地）

本地保留最近 50 条同步日志，用于排错：

```json
{
  "timestamp": 1710000000000,
  "action": "SYNC_PROGRESS",
  "bookKey": "...",
  "result": "SUCCESS",
  "duration": 1234,
  "requestsUsed": 3,
  "error": null
}
```

**错误类型枚举**：

- `AUTH_FAILED`：认证失败
- `NETWORK_ERROR`：网络不可用
- `RATE_LIMITED`：被限流（429/503）
- `SERVER_ERROR`：服务端错误
- `CONFLICT`：数据冲突
- `CRYPTO_ERROR`：加密/解密失败
- `UNKNOWN`：未知错误

### 13.4 用户可见的错误提示

- 认证失败："WebDAV 账号或密码错误，请检查设置。"
- 网络错误："网络不可用，请检查网络连接。"
- 限流："同步请求过于频繁，请稍后再试。"
- 加密密码错误："同步加密密码错误，无法解密云端数据。"
- 目录未初始化："远端同步目录未初始化，点击'立即同步'自动创建。"

---

## 第十四章 UI 设计

### 14.1 设置页面结构

同步相关设置整合到现有设置页面，作为独立分组：

```text
设置
├── 外观设置
├── 阅读器显示偏好
├── 书库与导入设置
├── 阅读统计
├── 朗读设置
├── ───────────────────
├── 📁 同步与备份          ← 新增分组
│   ├── 云端同步 (WebDAV)
│   ├── 本地同步
│   ├── 导出与恢复
│   └── 同步日志
├── ───────────────────
├── 高级设置
└── 关于与版权
```

### 14.2 云端同步卡片（V4 重设计：摘要 + 设置子页）

原设计将服务商、账号、连接状态、同步内容、加密状态、上次同步全部置于单张卡片，条目超过 8 个，信息密度过高。V4 拆分为**摘要卡**（默认）和**设置子页**（点击进入）：

**摘要卡（设置页内嵌显示）**：

```text
┌─────────────────────────────────────────────────────────────┐
│ ☁️ 云端同步 (WebDAV)                              [开关 ▶] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ● 已连接 · 坚果云 · 加密已启用                             │
│  上次同步: 2 分钟前 · 成功 · 请求 12/100                   │
│                                                             │
│  [立即同步]                            [设置 ▸]            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**云端同步设置子页（点击[设置]进入）**：

```text
┌─────────────────────────────────────────────────────────────┐
│ ← 云端同步设置                                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  服务商     坚果云                        [更改 ▸]          │
│  账号       user@example.com              [更改 ▸]          │
│  连接状态   ● 已连接                      [测试连接]        │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  同步内容                                                   │
│    ☑ 阅读进度    ☑ 书签    ☑ 笔记    ☑ 设置    ☐ 书籍文件  │
│  💡 书籍文件同步默认关闭，开启后大文件将消耗更多请求配额     │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  仅 Wi-Fi 上传大文件           [开关 ▶]                     │
│  后台自动同步（6-12 小时）      [开关 ▶]                     │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  端到端加密  🔒 已启用 (v2)              [管理加密 ▸]        │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  [查看同步日志 ▸]       [已同步设备 ▸]                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**交互说明**：

| 元素 | 交互 | 说明 |
|------|------|------|
| 开关（摘要卡） | 点击切换 | 关闭后停止所有云端同步，已同步数据保留 |
| 服务商 | 点击展开选择器 | 坚果云/InfiniCLOUD/Nextcloud/自定义 |
| 账号 | 点击弹窗编辑 | 用户名输入框 |
| 测试连接 | 点击执行 | 发送 PROPFIND /，显示结果 |
| 同步内容 | 复选框 | 书籍文件默认关闭，其余默认开启 |
| 管理加密 | 点击跳转 | 进入加密管理页（§14.7） |
| 立即同步（摘要卡） | 点击触发 | 手动同步，按钮变为进度指示器 |
| 设置 ▸ | 点击跳转 | 进入云端同步设置子页 |
| 已同步设备 ▸ | 点击跳转 | 进入设备管理页（§14.12） |
└─────────────────────────────────────────────────────────────┘
```

### 14.3 本地同步卡片

```text
┌─────────────────────────────────────────────────────────────┐
│ 📂 本地同步                                      [开关 ▶] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  同步目录   Documents / ShuLiReader             [更改目录 ▸]│
│             内部存储                                         │
│                                                             │
│  连接状态   ● 目录可访问                  [测试访问]        │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  同步内容                                                   │
│    ☑ 阅读进度    ☑ 书签    ☑ 笔记    ☑ 设置    ☐ 书籍文件  │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  加密状态   🔓 未加密                     [启用加密 ▸]      │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  上次同步   5 分钟前 · 成功                                  │
│                                                             │
│  [立即同步]                                                  │
│                                                             │
│  💡 配合 Syncthing/坚果云客户端可实现跨设备同步               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**同步目录路径的友好显示（V4 新增）**：

不直接显示 Android 原始路径（如 `/storage/emulated/0/Documents/ShuLiReader/`），改为用户友好格式：

```kotlin
fun formatSyncPath(uri: Uri): String {
    // 使用 DocumentFile 获取友好名称
    val docFile = DocumentFile.fromTreeUri(context, uri)
    // 例：将 primary:Documents/ShuLiReader 转换为 Documents / ShuLiReader
    val displayPath = docFile?.uri?.lastPathSegment
        ?.replace(":", " / ")
        ?.replace("/", " / ")
        ?: uri.toString()

    val storageLabel = if (uri.toString().contains("primary")) "内部存储" else "外部存储"
    return "$displayPath\n$storageLabel"
}
```

**交互说明**：

| 元素 | 交互 | 说明 |
|------|------|------|
| 同步目录 | 显示友好路径 | 使用 DocumentFile.fromTreeUri 转换 |
| 更改目录 | SAF `ACTION_OPEN_DOCUMENT_TREE` | 用户选择新目录 |
| 测试访问 | 点击执行 | 尝试读写目录，显示结果 |
| 启用加密 | 点击进入加密设置 | 与云端加密独立 |

### 14.4 导出与恢复卡片

```text
┌─────────────────────────────────────────────────────────────┐
│ 💾 导出与恢复                                                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  [导出备份文件]                      [从文件恢复]            │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  最近导出                                                     │
│    2026-05-28 12:30 · shuli-backup-2026-05-28.zip · 12.3MB  │
│    [分享 ▸]                                                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 14.5 导出选项弹窗

```text
┌─────────────────────────────────────────────────────────────┐
│ 导出备份                                                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  导出内容                                                     │
│    ☑ 阅读进度 (必选)                                         │
│    ☑ 书签                                                    │
│    ☑ 笔记                                                    │
│    ☑ 阅读设置                                                │
│    ☑ 阅读预设                                                │
│    ☐ 书籍文件                                                │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 🔐 导出加密（可选）                                   │   │
│  │    此密码仅用于当前导出文件，与同步加密密码无关        │   │
│  │    ☐ 加密导出文件                                    │   │
│  │    密码 ________________  (至少 8 位)                │   │
│  │    确认 ________________                             │   │
│  │    ⚠️ 忘记后无法恢复，请妥善保管                      │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  预估大小   计算中...                                        │
│             （异步计算，请稍候）                              │
│                                                             │
│           [取消]                    [导出]                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**导出密码的视觉区隔（V4 新增）**：

导出密码区块使用独立的浅色背景（`--color-background-warning` 调至 10% 透明度），与页面主背景区分，并配有明确的标题和说明文字，强调"与同步密码无关"，消除用户混淆。

**预估大小异步计算（V4 新增）**：

弹窗打开时不阻塞主线程，而是：

```kotlin
// 弹窗打开后立即发起异步估算
viewModel.estimateExportSize(selectedOptions).collect { state ->
    when (state) {
        is Calculating -> showLoadingIndicator("计算中...")
        is Calculated -> showSize(state.bytes)
        is Error -> showSize("无法估算")
    }
}
```

估算完成前显示"计算中..."，完成后更新为具体大小（如"约 12.5 MB"）。若用户勾选"书籍文件"，触发重新估算。

### 14.6 恢复选项弹窗

```text
┌─────────────────────────────────────────────────────────────┐
│ 从文件恢复                                                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  备份信息                                                     │
│    文件名    shuli-backup-2026-05-28.zip                     │
│    导出时间  2026-05-28 12:30                                │
│    设备      Pixel 7                                         │
│    书籍数量  156 本                                           │
│    加密状态  🔒 已加密                                        │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  解密密码 ________________                                   │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  导入策略                                                     │
│    ○ 完全覆盖 (清空本地，以导入数据为准)                      │
│    ● 智能合并 (保留本地较新的数据)                            │
│    ○ 仅导入本地不存在的数据                                  │
│                                                             │
│  ⚠️ 完全覆盖将删除本地所有书签、笔记、进度                    │
│                                                             │
│           [取消]                    [恢复]                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 14.7 加密管理页面

```text
┌─────────────────────────────────────────────────────────────┐
│ ← 端到端加密                                                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  当前状态   🔒 已启用 (密钥版本 v2)                          │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  同步加密密码                                                 │
│    状态       已设置                                          │
│    [验证密码]  [修改密码]                                     │
│                                                             │
│  ⚠️ 忘记同步加密密码后，云端已加密数据将无法解密。             │
│     如需继续使用，请重置并建立新的加密空间。                   │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  迁移                                                         │
│    [迁移到加密空间]  (将明文数据加密后上传到新目录)            │
│                                                             │
│  ─────────────────────────────────────────────────────────  │
│                                                             │
│  危险操作                                                     │
│    [重置加密空间]  (创建新空间，旧空间数据无法恢复)            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 14.8 同步日志页面

```text
┌─────────────────────────────────────────────────────────────┐
│ ← 同步日志                                     [导出日志]   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  筛选  [全部 ▾]  [云端]  [本地]  [失败]                     │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  今天                                                        │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ 10:30  全量同步  ☁️ 坚果云  ✅ 成功  1.2s              ││
│  │        设备: Pixel 7  请求: 12  传输: 2.3KB            ││
│  ├─────────────────────────────────────────────────────────┤│
│  │ 10:25  进度同步  📂 本地  ✅ 成功  0.3s                ││
│  │        书籍: 斗破苍穹  传输: 128B                       ││
│  ├─────────────────────────────────────────────────────────┤│
│  │ 09:15  全量同步  ☁️ 坚果云  ❌ 失败  5.1s              ││
│  │        错误: RATE_LIMITED  重试: 10:45                  ││
│  └─────────────────────────────────────────────────────────┘│
│                                                             │
│  昨天                                                        │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ 22:00  全量同步  ☁️ 坚果云  ✅ 成功  2.1s              ││
│  │        请求: 8  传输: 1.1KB                             ││
│  └─────────────────────────────────────────────────────────┘
│                                                             │
│                    [加载更多]                                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 14.9 冲突解决弹窗

```text
┌─────────────────────────────────────────────────────────────┐
│ ⚠️ 进度冲突                                                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  《斗破苍穹》在多设备上有不同进度：                           │
│                                                             │
│  ┌─────────────────────┐  ┌─────────────────────┐          │
│  │ 📱 本机              │  │ ☁️ 其他设备          │          │
│  │ （Pixel 7）          │  │ （小米 14）           │          │
│  │                     │  │                     │          │
│  │ 第 12 章 · 45%      │  │ 第 15 章 · 62%      │          │
│  │ 今天 10:30          │  │ 今天 09:15          │          │
│  └─────────────────────┘  └─────────────────────┘          │
│                                                             │
│  差距: 3 章 (17%)                                            │
│                                                             │
│           [保留本地]  [跳转到云端进度]  [暂不处理]            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**设备标识的 fallback 策略（V4 新增）**：

冲突弹窗从 `device/{deviceId}.json` 读取设备名，但该字段可能缺失（App 重装、旧版本迁移等情况）：

```kotlin
fun getDeviceDisplayName(deviceInfo: DeviceInfo?): String {
    return when {
        deviceInfo == null -> "其他设备"          // 设备信息读取失败
        deviceInfo.model.isNotBlank() -> deviceInfo.model  // 正常情况
        else -> "其他设备（${deviceInfo.deviceId.take(6)}）" // model 字段为空
    }
}
```

设备信息 `device/{deviceId}.json` 必须包含 `Build.MODEL` 和 `appVersion`：

```json
{
  "deviceId": "f47ac10b-...",
  "model": "Pixel 7",
  "manufacturer": "Google",
  "appVersion": "1.2.3",
  "lastSyncAt": 1710000000000
}
```

**交互说明**：

| 选项 | 行为 |
|------|------|
| 保留本地 | 本地进度覆盖云端，下次同步生效 |
| 跳转到云端进度 | 采用云端进度，阅读器跳转到对应位置 |
| 暂不处理 | 跳过本次，下次同步再判断 |

### 14.10 同步中状态指示

**设置页面**：

### 14.10 同步中状态指示

**设置页面**：

```text
┌─────────────────────────────────────────────────────────────┐
│ ☁️ 云端同步 (WebDAV)                              [开关 ▶] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  同步中...  ████████░░░░  上传阶段                           │
│  正在上传书签与笔记...                                       │
│                                                             │
│  [取消同步]                                                  │
│  取消不会丢失已完成的部分，下次同步时继续                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**通知栏**（后台同步时，仅在状态机切换时更新）：

通知只在 SCANNING → DOWNLOADING → MERGING → UPLOADING → SUCCESS/FAILED 切换时刷新，不跟随每本书的个别进度更新。

```text
┌─────────────────────────────────────────────────────────────┐
│ 🔄 ShuLi Reader 同步中                                      │
│ 正在上传书签与笔记...                                        │
│                              [取消]                         │
└─────────────────────────────────────────────────────────────┘
```

状态文案对应关系：

| SyncState | 通知文案 |
|-----------|---------|
| SCANNING | 正在扫描变更... |
| DOWNLOADING | 正在下载云端数据... |
| MERGING | 正在合并数据... |
| UPLOADING | 正在上传书签与笔记... |
| SUCCESS | 同步完成 |
| FAILED | 同步失败 |
| RATE_LIMITED | 请求频繁，稍后继续 |

进度数字（如"已处理 3/156 本"）只在设置页的内嵌进度指示中显示，不进通知栏。

**同步完成通知**：

```text
┌─────────────────────────────────────────────────────────────┐
│ ✅ 同步完成                                                  │
│ 已同步 5 处书签、2 处笔记 · 1.2s                             │
│                              [查看]                         │
└─────────────────────────────────────────────────────────────┘
```

**同步失败通知**：

```text
┌─────────────────────────────────────────────────────────────┐
│ ❌ 同步失败                                                  │
│ 网络不可用 · 将在网络恢复后重试                               │
│                              [重试]                         │
└─────────────────────────────────────────────────────────────┘
```

### 14.11 错误状态 UI

**连接失败**：

```text
┌─────────────────────────────────────────────────────────────┐
│ ☁️ 云端同步 (WebDAV)                              [开关 ▶] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ⚠️ 连接失败                                                │
│  用户名或密码错误，请检查设置。                               │
│                                                             │
│  [重新测试连接]  [修改设置 ▸]                                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**限流中（V4 更新，增加可操作引导）**：

```text
┌─────────────────────────────────────────────────────────────┐
│ ☁️ 云端同步 (WebDAV)                              [开关 ▶] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ⏳ 请求过于频繁，将在 5 分钟后自动继续                      │
│  坚果云免费版：600 次请求 / 30 分钟                          │
│                                                             │
│  上次同步: 2 分钟前 · 成功                                   │
│                                                             │
│  了解坚果云请求限制 ↗   升级付费版可获得更多配额 ↗           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

"了解坚果云请求限制"链接跳转到 app 内 WebDAV 服务商说明页（提前内置）；"升级付费版"链接仅在检测到用户为免费版时显示，跳转到坚果云官网。

**密码未设置**：

```text
┌─────────────────────────────────────────────────────────────┐
│ ☁️ 云端同步 (WebDAV)                              [开关 ▶] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  🔐 加密同步空间已启用，但密码未验证                          │
│  请输入同步加密密码以继续。                                   │
│                                                             │
│  密码 ________________                                       │
│                                                             │
│  [验证]  [忘记密码？重置加密空间 ▸]                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 14.12 已同步设备管理页（V4 新增）

入口：云端同步设置子页 → [已同步设备 ▸]

```text
┌─────────────────────────────────────────────────────────────┐
│ ← 已同步设备                                                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  以下设备曾同步过本账号的数据：                               │
│                                                             │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ 📱 Pixel 7                               [本机]        ││
│  │    最后同步: 刚刚  · 应用版本: 1.2.3                   ││
│  └─────────────────────────────────────────────────────────┘│
│                                                             │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ 📱 小米 14                                              ││
│  │    最后同步: 3 天前  · 应用版本: 1.2.1                  ││
│  │                                           [移除 ▸]     ││
│  └─────────────────────────────────────────────────────────┘│
│                                                             │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ 📱 其他设备                                             ││
│  │    最后同步: 30 天前  · 应用版本: 1.0.0                 ││
│  │                                           [移除 ▸]     ││
│  └─────────────────────────────────────────────────────────┘│
│                                                             │
│  💡 移除设备后，该设备不再参与 tombstone 清理等待            │
│     下次该设备同步时会重新注册                               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**移除设备确认弹窗**：

```text
┌─────────────────────────────────────────────────────────────┐
│ 移除设备                                                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  确认移除「小米 14」？                                       │
│                                                             │
│  • 该设备的同步记录将从云端删除                              │
│  • 该设备下次同步时会重新注册                                │
│  • 已同步的书签、笔记数据不受影响                            │
│                                                             │
│                    [取消]          [确认移除]                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**实现说明**：

```kotlin
// 读取所有设备信息：PROPFIND device/ 目录
suspend fun listDevices(): List<DeviceInfo> {
    return transport.listDirectory("device/")
        .map { path -> transport.readJson<DeviceInfo>(path) }
        .sortedByDescending { it.lastSyncAt }
}

// 移除设备：删除对应的 device/{deviceId}.json
suspend fun removeDevice(deviceId: String) {
    transport.delete("device/$deviceId.json")
    // 移除后，tombstone compact 不再等待该设备
}
```

---



## 第十五章 库选型最终建议

### 15.1 第一阶段：继续强化纯 OkHttp

保留并重构现有：

- `WebDavClient`
- `WebDavSyncManager`

新增：

- `DavResource`
- `WebDavXmlParser`
- `WebDavRepository`
- `SyncManifest`
- `SyncTaskEntity`
- `BookSyncMapper`
- `WebDavSyncWorker`

### 15.2 第二阶段：加密层必做

新增：

- `SyncCryptoManager`
- `EncryptedPayload`
- `CryptoMetadata`
- `EncryptedFileStream`
- `SyncPasswordViewModel`
- `SyncPasswordDialog`
- `CryptoKeyRepository`

### 15.3 第三阶段：按需引入 dav4jvm

触发条件：

- 大量用户反馈 Digest 认证失败。
- 遇到多个服务商 XML/PROPFIND 兼容性坑。
- 后续计划 KMP。

如果引入：优先 `at.bitfire.dav4jvm.okhttp`。

不推荐当前立即用 sardine。

---

## 第十六章 实施步骤

### 16.1 Phase 1：最小可用同步闭环（协议层 + 进度/书签/笔记）

**协议层强化**：
1. `WebDavClient` 改为 suspend API。
2. 加 OkHttp 超时、连接池、禁止自动重定向或安全处理重定向。
3. 新增 `HEAD / MKCOL / DELETE`。
4. `PUT` 支持 String、ByteArray、File streaming 三种 body。
5. `GET` 支持 String 与 File streaming 下载。
6. 新增 `DavResource` 与 XML Pull Parser 解析 `PROPFIND`。
7. 错误类型化：Auth、NotFound、Conflict、RateLimited、Locked、ServerUnavailable。
8. 错误响应解析：提取 WebDAV XML 错误中的 `s:exception` 和 `s:message`（参考 legado）。
9. URL 协议转换：支持用户输入 `davs://` → 自动转为 `https://`（参考 legado）。
10. 明确 `Content-Type`：JSON 为 `application/json; charset=utf-8`，二进制为 `application/octet-stream`。

**远端目录与 manifest**：
11. `ensureRemoteRoot()` 创建 `/ShuLiReader/` 子目录。
12. `manifest.json` 数据模型（含 `schemaVersion`）。
13. 本地书籍 → `bookKey` 映射。
14. 上传/下载 manifest。
15. 无 manifest 时使用 `PROPFIND Depth=1` 修复。

**进度/书签/笔记同步**：
16. `BookProgress` 改为 v2：加入 `bookKey`、`byteOffset`、兼容 EPUB 字段。
17. 书签/笔记 JSON 模型加入 UUID、tombstone、`mergeSource`。
18. ReaderViewModel 保存进度后 enqueue 小 JSON 上传。
19. 冲突解决：进度 latest-wins + 大差距三选项提示 + 时间戳相同时二级比较（参考 legado）；书签/笔记 item-level merge。
20. 同步状态 UI：最近同步时间、结果、请求计数。

**变更检测（增量同步）**：
21. Room 实体（Bookmark/Note/ReadingProgress/ReaderPreset）新增字段：`isDirty`、`version`、`syncedVersion`。
22. `PreferencesDirtyTracker`：内存记录脏 key，同步后清除。
23. `fastHash` 计算：书籍/字体文件头部+尾部+大小的轻量 hash，用于免检跳过。
24. 同步时 `WHERE isDirty = true` 只处理脏数据，避免全量扫描。

### 16.2 Phase 1.5：扩展同步能力 + 本地备份

**云端同步扩展**：
1. 设置项：是否同步书籍文件（默认关闭）。
2. 上传本地缺失远端的 TXT/EPUB。
3. 下载远端缺失本地的 TXT/EPUB。
4. 大文件提示与坚果云 500MB 限制提示。
5. 下载完成后走现有 `BookRepository.importBook()`。
6. 导出可迁移 UserPreferences。
7. 导出 ReaderPresetEntity。
8. 不导出 WebDAV 凭据、文件绝对路径、设备 ID。
9. 配置冲突使用服务器时间戳最新 wins。

**后台同步**：
10. 引入 WorkManager 依赖。
11. `SyncWorker`（统一，通过参数区分云端/本地）。
12. 手动同步使用 OneTimeWorkRequest。
13. 周期同步使用 PeriodicWorkRequest，默认关闭。
14. 云端：网络约束 + 指数退避；本地：无网络约束。

**本地同步**：
15. 实现 `SyncTransport` 接口。
16. 实现 `LocalFileTransport`：文件系统 I/O。
17. 本地同步目录选择 UI（SAF + 内部存储默认）。
18. 本地同步状态展示。
19. 统一 `SyncOrchestrator` 调度云端/本地/两者。

**ZIP 导出/导入（非同步，仅分享）**：
20. ZIP 打包导出功能（复用 SyncEngine 数据）。
21. ZIP 解压导入功能（复用 SyncEngine 数据）。
22. SAF 导出到用户选择的位置。

### 16.3 Phase 2：端到端加密（必做）

**E2EE（云端和本地统一）**：
1. 新增同步加密密码设置 UI。
2. 新增 `crypto.json`。
3. PBKDF2 派生 masterKey。
4. AES-256-GCM 加密/解密小 JSON。
5. manifest/state/bookmarks/notes/config 全部切换到 `.enc`。
6. 大文件分片加密 `book.enc`。
7. 密码错误和数据损坏错误处理。
8. 加密目录 `/ShuLiReaderEncrypted/` 初始化（云端和本地同步创建）。
9. 明文目录迁移到加密目录。
10. E2EE 验收通过后，正式开放完整同步功能。
11. E2EE 模式下 Worker 需要先检查密钥是否已解锁；未解锁则跳过并提示用户。

**加密 ZIP 导出**：
12. 加密 ZIP 导出：`.zip.enc` 格式。
13. 导出密码输入/验证。
14. 加密 ZIP 导入恢复流程。

### 16.4 Phase 2.5：设置 UI 完善

1. "测试连接"按钮（云端）。
2. "立即同步"按钮（云端/本地/两者）。
3. "同步书籍文件"开关（默认关闭）。
4. "仅 Wi-Fi 上传大文件"开关（云端）。
5. "后台自动同步"开关（云端/本地）。
6. "启用端到端加密"入口。
7. "同步加密密码"设置/验证弹窗。
8. "迁移到加密同步空间"按钮（云端和本地）。
9. "重置加密同步空间"按钮。
10. 云端同步状态：最近同步时间、失败原因、重试时间。
11. 本地同步状态：最近同步时间、同步目录路径。
12. 同步日志查看入口。
13. 常用服务商快捷说明：坚果云、InfiniCLOUD、Nextcloud。

---

## 第十七章 具体代码影响范围

### 17.1 已有文件

- `core/sync/WebDavClient.kt`：重构为完整协议客户端。
- `core/sync/WebDavSyncManager.kt`：从 progress-only 扩展为 orchestrator 或拆分。
- `core/ShuLiAppContainer.kt`：注入 WebDavRepository / CryptoManager / Worker factory 所需依赖。
- `core/data/UserPreferences.kt`：新增同步开关、根目录、仅 Wi-Fi、自动同步、E2EE 状态等配置。
- `feature/settings/SettingsViewModel.kt`：新增测试连接、立即同步、加密密码状态。
- `feature/settings/SettingsScreen.kt`：新增 UI 控件。
- `BookRepository.kt`：提供 bookKey、导出/导入文件同步入口。
- `BookmarkDao.kt` / `NoteDao.kt`：需要导出/导入 API。
- `ReaderViewModel.kt`：退出阅读页/保存进度时 enqueue sync。

### 17.2 新增文件

**同步核心**：
- `core/sync/SyncEngine.kt`：统一同步引擎
- `core/sync/SyncTransport.kt`：传输层接口
- `core/sync/SyncOrchestrator.kt`：云端/本地/两者的调度器
- `core/sync/model/SyncManifest.kt`
- `core/sync/model/BookSyncState.kt`
- `core/sync/model/BookmarkSyncDto.kt`
- `core/sync/model/NoteSyncDto.kt`
- `core/sync/model/ConfigSyncDto.kt`
- `core/sync/model/CryptoMetadata.kt`
- `core/sync/model/EncryptedPayload.kt`
- `core/sync/model/SyncOptions.kt`：同步选项（是否含书籍文件等）
- `core/sync/SyncConflictResolver.kt`
- `core/sync/SyncRateLimiter.kt`
- `core/sync/SyncCryptoManager.kt`
- `core/sync/EncryptedFileStream.kt`
- `core/sync/CryptoKeyRepository.kt`
- `core/sync/PreferencesDirtyTracker.kt`：设置脏标记追踪
- `core/sync/FastHashCalculator.kt`：书籍/字体快速 hash 计算
- `core/database/entity/SyncTaskEntity.kt`
- `core/database/dao/SyncTaskDao.kt`

**传输层实现**：
- `core/sync/transport/WebDavTransport.kt`：WebDAV 传输实现
- `core/sync/transport/LocalFileTransport.kt`：本地文件传输实现
- `core/sync/WebDavXmlParser.kt`：WebDAV XML 解析
- `core/sync/WebDavClient.kt`：重构为完整协议客户端

**Worker**：
- `core/sync/worker/SyncWorker.kt`：统一 Worker（通过参数区分云端/本地）
- `core/sync/worker/LocalSyncWorker.kt`：本地同步 Worker（无网络约束）

**ZIP 导出/导入**：
- `core/backup/ZipExporter.kt`：ZIP 打包导出
- `core/backup/ZipImporter.kt`：ZIP 解压导入
- `core/backup/model/ExportOptions.kt`：导出选项

**UI**：
- `feature/settings/SyncSettingsViewModel.kt`：同步设置 ViewModel
- `feature/settings/SyncStatusCard.kt`：云端/本地同步状态卡片
- `feature/settings/ExportImportScreen.kt`：ZIP 导出/导入页面
- `feature/settings/SyncLogScreen.kt`：同步日志页面
- `feature/settings/CryptoManageScreen.kt`：加密管理页面
- `feature/settings/component/ConflictResolveDialog.kt`：冲突解决弹窗
- `feature/settings/component/ExportOptionsDialog.kt`：导出选项弹窗
- `feature/settings/component/RestoreOptionsDialog.kt`：恢复选项弹窗
- `feature/settings/component/SyncProgressIndicator.kt`：同步进度指示器

---

## 第十八章 验收标准

### 18.1 连接与兼容性

- 坚果云可测试连接、创建目录、上传/下载 JSON。
- InfiniCLOUD 可测试连接、上传/下载 JSON。
- Nextcloud 可测试连接、上传/下载 JSON。
- 错误密码显示明确认证失败。
- 目录不存在自动 MKCOL。
- 明文目录和加密目录可区分识别。

### 18.2 性能

- 上传阅读进度 JSON/ENC < 500ms（网络正常）。
- 手动同步仅进度/书签/笔记：请求数 ≤ 10 + 变更书籍数 × 3。
- 100 本书 manifest diff < 1s（不含网络）。
- 大文件上传/下载内存峰值不随文件大小线性增长。
- 小 JSON 加密/解密 < 5ms。
- 大文件加密/解密不阻塞 UI。

### 18.3 稳定性

- 网络断开不崩溃，任务进入 pending。
- 429/503 指数退避。
- 进程被杀后 pending 任务不丢。
- 同一本书多设备进度冲突可解决。
- 不因坚果云 600/30min 触发频繁封禁。
- E2EE 密码错误不会破坏本地数据。
- `.enc` 文件损坏能明确报错并跳过。

### 18.4 安全

- WebDAV 密码不再明文存 DataStore。
- 同步加密密码不明文落盘。
- 日志不包含 Authorization、password、syncPassword。
- 默认 HTTPS。
- E2EE 覆盖 manifest、books、state、bookmarks、notes、config、backups。
- 加密模式远端不出现明文书名、作者、笔记、书签、配置。
- 忘记同步加密密码无法恢复，UI 必须明确提示。

### 18.5 本地同步

- 本地同步目录结构与云端完全一致。
- 本地同步使用相同的 manifest 和时间戳机制。
- 本地同步支持增量同步（非全量打包）。
- 本地同步支持 E2EE（与云端共享加密逻辑）。
- ZIP 导出/导入功能正常工作。

---

## 第十九章 关键决策

- **第一阶段库选型**：纯 OkHttp 手写，不引入 dav4jvm。
- **Phase 1 范围收窄**：最小可用闭环 = 连接测试 + 目录初始化 + manifest + 进度/书签/笔记同步；书籍文件和配置备份推迟到 Phase 1.5。
- **双模式设计**：同步模式（增量，分散文件）+ 导出模式（全量，ZIP）；两种模式共享数据模型和加密逻辑。
- **密码独立管理**：同步加密密码（持久，设置页面配置）与导出加密密码（临时，每次导出输入）完全独立。
- **传输层抽象**：`SyncTransport` 接口统一云端（WebDAV）和本地（文件系统），同步引擎完全复用。
- **本地同步定位**：与云端同步对等，不是"备份"功能；支持增量同步、冲突解决、E2EE。
- **本地同步目录结构**：与云端完全一致（manifest + 分散 JSON/ENC），便于理解和调试。
- **ZIP 导出定位**：冷备份、迁移、分享；一次性打包，非增量；用户可选择导出内容和是否加密。
- **远端同步核心**：manifest-first，减少 PROPFIND。
- **端到端加密**：必须实现；未启用 E2EE 时仅允许内部测试或过渡版，正式版默认必须启用或强制二次确认。
- **加密算法**：AES-256-GCM；PBKDF2WithHmacSHA256 派生密钥；保留 keyVersion。
- **进度模型**：TXT 用 byteOffset，EPUB 用 chapterIndex/chapterPos。
- **书籍文件同步**：明确高级选项，默认关闭；仅在用户手动开启时才同步。
- **冲突策略**：进度 latest-wins + 大差距三选项提示（跳转/保留/暂不处理）+ 时间戳相同时二级进度比较；书签/笔记 UUID merge + mergeSource 标记。
- **时间戳权威**：云端以服务器 Last-Modified 为准；本地以文件系统时间戳为准。
- **URL 兼容**：支持 `davs://` → `https://` 自动转换，兼容用户输入习惯（参考 legado）。
- **凭据分离管理**：WebDAV 密码与同步加密密码分开存储和管理。
- **密码遗失策略**：同步加密密码不可恢复，提供"重置并重新建立新空间"流程。
- **目录版本化**：manifest 包含 schemaVersion；格式升级走"读旧→转新→上传"；旧目录不自动删除。
- **后台同步**：WorkManager 可选，默认先手动同步；云端和本地可独立配置。
- **可观测性**：同步状态 UI 必须展示云端和本地的最近同步时间、结果、目录路径。
- **服务商优化**：坚果云限流优先级最高。
- **同步状态机**：`SyncState` 枚举管理 UI/Worker/通知协调，状态包括 IDLE、SCANNING、DOWNLOADING、MERGING、UPLOADING、SUCCESS、FAILED、RATE_LIMITED、WAITING_RETRY、CRYPTO_LOCKED。
- **操作日志**：`SyncOperationLog` 记录每次同步的设备、操作类型、耗时、请求消耗、冲突原因、合并来源，保留最近 200 条，支持导出。
- **localDeviceId**：每设备独立生成的随机 UUID，用于 mergeSource 追踪、冲突排障、设备管理；不同步到其他设备，App 卸载重装后会变。
- **logicalVersion**：单调递增的逻辑版本号，优先级高于时间戳；解决设备时钟不准、时区差异导致的误判问题。
- **原子写入**：本地同步使用 write-to-tmp → fsync → rename 策略，防止 Syncthing 等工具同步损坏文件。
- **增量变更检测**：三层脏标记机制——内存脏标记（设置类）、Room isDirty 字段（数据类）、fastHash 免检（静态文件）；同步只处理脏数据，避免全量扫描和重复传输。
- **UI 设计**：同步设置整合到设置页面独立分组；云端/本地各一个状态卡片；导出/恢复通过弹窗交互；冲突解决弹窗提供三选项；同步中状态同时展示在设置页面和通知栏。
