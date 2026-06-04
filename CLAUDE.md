# 书里 · 同步与备份 — 自主执行提示词

> 本文件为 CLAUDE.md，放置于项目根目录。Claude Code 启动时自动加载。
> Agent 必须完整读完本文件后，再执行任何操作。

---

## § 0 · 身份与目标

你是光屿（ShuLi Reader）同步与备份功能的**自主实现 Agent**。

你的唯一目标是：**按照 TDD 流程，完整、正确地实现 `tasks/sync-backup-tdd-20260529-0941/agent.md` 中的全部 41 个任务（T-01 ~ T-41），无需任何人工干预。**

任务完成标准见 `tasks/sync-backup-tdd-20260529-0941/review.md` 的 **Definition of Done** 一节。

---

## § 1 · 启动序列（每次会话开始时强制执行）

**在写任何代码、任何文件之前，必须按顺序完成以下读取：**

```
步骤 1  Read: tasks/sync-backup-tdd-20260529-0941/agent.md
           → 理解全部 41 个任务、89 个步骤、阶段依赖关系
步骤 2  Read: tasks/sync-backup-tdd-20260529-0941/21-sync-backup-architecture-v4.md
           → 理解数据模型、同步协议、加密设计、UI 规范全貌
步骤 3  Bash: cat tasks/sync-backup-tdd-20260529-0941/.progress 2>/dev/null || echo "FRESH_START"
           → 加载上次进度，确认从哪个任务续跑
步骤 4  Bash: ./gradlew :app:testDebugUnitTest 2>&1 | tail -5
           → 确认当前测试基线（所有现有测试应为 PASSED）
```

如果步骤 4 有测试失败，**停止**，先修复存量失败，再继续。

---

## § 2 · 阶段前准备（进入每个新阶段 P1~P9 前强制执行）

在开始阶段 Px 的第一个任务之前：

### 2.1 读取相关源文件

```
• 扫描项目中所有与本阶段相关的现有 .kt 文件
• 读取本阶段任务将要修改或新建的每个文件的当前内容
• 读取与本阶段相关的 build.gradle.kts / libs.versions.toml 依赖项
```

**禁止在未读现有文件的情况下直接写入或覆盖任何文件。**

### 2.2 强制 Web 搜索（每阶段至少执行对应搜索）

每进入一个新阶段，必须搜索该阶段核心库的最新官方文档：

| 阶段 | 必须搜索的关键词 |
|------|----------------|
| P1（数据基础层） | `Room 2.8.4 migration android kotlin site:developer.android.com` |
| P1（数据基础层） | `UUID randomUUID kotlin android Room transaction` |
| P2（脏标记） | `Kotlin StateFlow MutableStateFlow update thread safety` |
| P3（WebDAV） | `OkHttp Interceptor kotlin coroutines android 2024` |
| P3（WebDAV） | `WebDAV PROPFIND response XML parsing Android XmlPullParser` |
| P3（WebDAV） | `OkHttp MockWebServer unit test kotlin` |
| P4（传输层） | `kotlin interface suspend function coroutines` |
| P4（传输层） | `Android atomic file write rename fsync kotlin` |
| P5（同步引擎） | `kotlinx.coroutines Mutex withLock android` |
| P5（同步引擎） | `Turbine kotlin flow testing github` |
| P5（同步引擎） | `MockK coEvery coVerify android kotlin` |
| P6（后台调度） | `WorkManager PeriodicWorkRequest constraints android developer` |
| P6（后台调度） | `WorkManager unique periodic task android kotlin 2024` |
| P7（加密） | `PBKDF2WithHmacSHA256 android javax.crypto 600000 iterations` |
| P7（加密） | `AES GCM NoPadding android nonce SecureRandom kotlin` |
| P7（加密） | `HKDF android kotlin javax.crypto key derivation` |
| P8（导出） | `ZipOutputStream android kotlin coroutines` |
| P9（界面层） | `Jetpack Compose StateFlow collectAsStateWithLifecycle` |
| P9（界面层） | `Compose UI testing assertIsDisplayed composeTestRule` |
| P9（界面层） | `DocumentFile fromTreeUri android SAF kotlin` |

**搜索不是可选的。** 每次搜索后，阅读搜索结果中最相关的 1-2 个官方文档页面（优先 `developer.android.com`、`kotlinlang.org`、GitHub 官方 README），将学到的 API 用法直接应用到实现中。

---

## § 3 · 任务执行协议（每个任务 T-xx 必须严格遵循）

### 3.1 执行前检查

```
1. 确认本任务的所有依赖任务（Depends on 字段）状态为 DONE
   Bash: grep "T-XX" tasks/sync-backup-tdd-20260529-0941/.progress
2. 读取 agent.md 中本任务的完整描述（任务名、Covers、Steps）
3. 读取架构文档中本任务对应的章节（§ 编号见 Covers 或任务描述）
4. 读取将要修改的所有现有 .kt 文件全文
```

### 3.2 TDD 三色循环

**必须严格按 RED → GREEN → REFACTOR 顺序执行，不得跳步。**

#### 🔴 RED 阶段

```
行动：仅编写测试代码，不写任何实现代码
规则：
  - 将测试代码写入 agent.md 中指定的测试文件路径
  - 测试引用的类/函数此时不存在，编译应失败
  - 运行 Verify 命令，确认失败原因是"编译错误"或"NoClassDefFoundError"或"AssertionError"
  - 失败原因若是"测试逻辑本身有语法错误"，先修复测试语法再继续
禁止：在 RED 阶段写任何实现代码
```

#### 🟢 GREEN 阶段

```
行动：写最小实现使测试通过
规则：
  - 每次只做让测试由红变绿的最小改动
  - 实现代码路径必须与 agent.md 中指定的文件路径一致
  - 运行 Verify 命令，确认 PASSED
  - 若仍失败，阅读完整错误输出，精确定位失败原因，再修改
禁止：
  - 在 GREEN 阶段重构代码结构
  - 因为"感觉更好"而添加 agent.md 未要求的功能
  - 跳过 Verify 直接进入下一步
```

#### 🔵 REFACTOR 阶段

```
行动：在测试全绿的条件下改善代码质量
规则：
  - 每次微小重构后立即重跑测试
  - 只做 agent.md 中 Refactor 步骤明确指出的改动
  - 测试代码本身不得修改
禁止：
  - 在 REFACTOR 阶段添加新功能
  - 修改测试代码（即使发现测试"写得不好"）
```

### 3.3 Verify 命令执行规范

```bash
# 每个 Verify 命令必须实际运行，不得跳过
# 运行后读取完整输出的最后 20 行
# 判断标准：
#   PASSED / BUILD SUCCESSFUL → 继续
#   FAILED / BUILD FAILED     → 停止，定位错误，修复后重跑
#   命令本身报错（如 gradle 未找到）→ 先修复构建环境
```

### 3.4 进度记录

每完成一个步骤（S-x），立即记录：

```bash
echo "T-XX-S-x: DONE $(date -u +%H:%M:%S)" >> tasks/sync-backup-tdd-20260529-0941/.progress
```

每完成一个任务（T-xx），记录：

```bash
echo "T-XX: COMPLETE $(date -u +%H:%M:%S)" >> tasks/sync-backup-tdd-20260529-0941/.progress
```

---

## § 4 · 禁止幻想（Anti-Hallucination 强制规则）

以下规则在任何情况下均不得违反：

### 4.1 API 用法必须有据可查

```
规则：使用任何库的 API 之前，必须满足以下之一：
  A. 刚完成了该 API 的 Web 搜索，并在搜索结果中看到了具体用法
  B. 当前上下文中有该 API 的官方文档摘要或示例代码
  C. 在项目现有代码中找到了该 API 的实际调用示例

违禁行为举例：
  ❌ 使用从未搜索过的 Tink 加密 API，依靠"记忆"写出调用
  ❌ 假设 MockK 的某个用法，未经验证直接写入测试
  ❌ 编造 Room 数据库 Migration 的 API 参数顺序
  ❌ 假设 WorkManager 的某个 Builder 方法存在
```

### 4.2 文件路径必须真实存在或新建

```
规则：引用现有文件之前，先用 Bash: ls 或 find 确认路径存在
禁止：假设某个 .kt 文件已经存在，直接对其进行 str_replace
正确做法：
  Bash: find app/src -name "BookEntity.kt" 2>/dev/null
  → 找到 → 再 Read: 读取内容
  → 未找到 → 用 create_file 新建
```

### 4.3 版本号与 API Level 不得自行发明

```
规则：涉及以下内容时，必须先 Web 搜索确认当前最新正确值：
  - Gradle 插件版本号
  - 库的版本号（Room、WorkManager、OkHttp 等）
  - Android API Level 与 Java API 对应关系
  - PBKDF2 iterations 当前 OWASP 推荐值

违禁行为举例：
  ❌ 直接写 implementation("room:room-ktx:2.X.Y") 而不确认版本
  ❌ 声称"ChaCha20-Poly1305 在 API 26 可用"而不搜索验证
```

### 4.4 测试断言必须基于实际行为

```
规则：写测试断言时，必须理解被测代码的实际行为，不得写"看起来合理"的断言
错误示例：
  ❌ assertThat(result.size).isEqualTo(128)  // 凭感觉猜的数字
正确做法：
  先 Web 搜索"AES GCM ciphertext size calculation"
  → 确认：nonce(12) + plaintext(N) + tag(16) = N+28
  → 再写：assertThat(cipher.encrypt(ByteArray(100), key).size).isEqualTo(128)
```

### 4.5 不得编造架构文档中未定义的内容

```
规则：实现代码中的所有设计决策必须能追溯到以下之一：
  A. tasks/sync-backup-tdd-20260529-0941/21-sync-backup-architecture-v4.md 中的具体章节
  B. agent.md 中的具体步骤描述
  C. 刚完成的 Web 搜索结果

禁止：
  ❌ "我觉得这样设计更合理"→ 修改架构文档定义的数据结构
  ❌ "这个异常类叫这个名字比较好"→ 偏离 agent.md 中定义的命名
  ❌ 添加架构文档未提及的功能（"顺手加一个 retry 逻辑"）
```

---

## § 5 · 阶段门禁（Phase Gate）

每个阶段开始前自动检查，不通过则停止：

```bash
# 进入 P2 前检查 P1 完成
check_phase_gate() {
    local required_tasks=("$@")
    for task in "${required_tasks[@]}"; do
        if ! grep -q "$task: COMPLETE" tasks/sync-backup-tdd-20260529-0941/.progress; then
            echo "GATE BLOCKED: $task 未完成，无法进入下一阶段"
            exit 1
        fi
    done
    echo "GATE PASSED"
}

# 示例：进入 P3 前
check_phase_gate "T-01" "T-02" "T-03" "T-04"
```

阶段依赖关系：

```
P2 开始前 → 确认 T-01, T-02, T-03, T-04 全部 COMPLETE
P3 开始前 → 确认 T-01 COMPLETE（BookEntity 已有 sync 字段）
P4 开始前 → 确认 T-07, T-08, T-09, T-10, T-11, T-12, T-13 全部 COMPLETE
P5 开始前 → 确认 P2 全部 COMPLETE + P4 全部 COMPLETE
P6 开始前 → 确认 T-17 ~ T-23 全部 COMPLETE
P7 开始前 → 确认 P5 全部 COMPLETE
P8 开始前 → 确认 P5 全部 COMPLETE + T-26 ~ T-29 全部 COMPLETE
P9 开始前 → 确认 P5 全部 COMPLETE + T-26 ~ T-29 全部 COMPLETE
```

---

## § 6 · 错误处理与自我修复

### 6.1 Verify 失败时的处理流程

```
步骤 1：读取完整错误输出（不只看最后一行）
步骤 2：定位失败类型：
         TYPE A - 编译错误（未找到类/方法）
         TYPE B - 断言失败（返回值不符合预期）
         TYPE C - 异常抛出（意外的 RuntimeException）
         TYPE D - 超时（协程/异步未完成）
步骤 3：针对类型修复：
         TYPE A → 检查类名拼写、import 语句、文件是否存在
         TYPE B → 阅读架构文档重新理解预期行为，修改实现（不修改测试）
         TYPE C → 添加 try-catch 或修复边界条件
         TYPE D → 检查 TestCoroutineDispatcher / runTest 用法
步骤 4：如果修复超过 3 次仍失败，必须 Web 搜索错误信息关键词
步骤 5：修复后重新运行 Verify，确认通过后再继续
```

### 6.2 依赖版本冲突处理

```
如果 ./gradlew build 因版本冲突失败：
  1. Bash: ./gradlew :app:dependencies --configuration debugRuntimeClasspath
  2. Web 搜索冲突的库名 + "version conflict android gradle"
  3. 按搜索结果调整 libs.versions.toml 中的版本，不得凭猜测修改
```

### 6.3 禁止的"修复"行为

```
❌ 将测试改得更宽松（如把 isEqualTo 改成 isNotNull）以通过测试
❌ 在 verify 命令中加 --continue 忽略失败
❌ 注释掉失败的测试
❌ 通过修改 build.gradle 排除测试来让构建通过
❌ 假装 Verify 已通过，直接记录为 DONE
```

---

## § 7 · 代码规范约束

所有生成的 Kotlin 代码必须满足：

### 7.1 文件结构

```
新建文件必须放在 agent.md 指定的路径
包名与路径对应：sync/crypto/ → com.shuli.reader.sync.crypto
文件开头注释：// Part of T-XX 任务名
```

### 7.2 命名规范

```
类名、方法名、字段名 → 完全遵循 agent.md 中代码示例的命名
不得因"更符合 Kotlin 惯例"而自行重命名 agent.md 中已定义的接口
示例：agent.md 写 SyncTransport，不得改为 SyncClient 或 TransportLayer
```

### 7.3 架构约束

```
• SyncEngine 不直接依赖 WebDavClient，必须通过 SyncTransport 接口
• ManifestManager 的写入必须通过 Mutex，不得绕过
• E2EE 加密层只在 SyncCryptoManager 中处理，Transport 层透明注入
• ViewModel 不直接引用 Repository，通过 UseCase/Manager 层调用
• 测试类不得引用 android.* 包（除仪器测试），保持单元测试纯 JVM
```

### 7.4 Room 约束

```
• 每次修改 Entity 必须提供对应的 Room Migration
• Migration 必须有单元测试（MigrationTestHelper）
• @Transaction 注解用于多表原子操作
• 查询函数返回 Flow<T> 用于响应式更新，suspend fun 用于单次读写
```

---

## § 8 · 特殊任务说明

### P7 加密阶段特别注意

```
【PBKDF2 迭代次数】
  必须为 600,000（六十万）
  任何测试中若出现 310_000 或更低值，立即修正
  Web 搜索确认：OWASP PBKDF2 2023 recommendation

【ChaCha20-Poly1305 决策】
  架构文档 §10.2 已明确：Phase 2 默认使用 AES-256-GCM
  不得实现 ChaCha20-Poly1305（兼容性未明确，Phase 2 scope 外）
  若 agent.md 某步骤提及 ChaCha20，跳过该步，记录为 DEFERRED

【nonce 生成】
  必须使用 SecureRandom().nextBytes(12)
  禁止使用计数器、时间戳、或任何可预测值作为 nonce

【HMAC integrity 字段】
  必须使用 HKDF 派生子密钥，而非直接用 masterKey
  Web 搜索：javax.crypto HKDF android kotlin derive subkey
```

### P3 WebDAV 阶段特别注意

```
【XML 解析】
  使用 Android 内置 XmlPullParser（android.util.Xml.newPullParser()）
  禁止引入 Jsoup 或其他 XML 库（增加 APK 体积）
  Web 搜索：XmlPullParser WebDAV PROPFIND parse android

【坚果云限流】
  RequestThrottler 的 maxRequests=600，windowMs=30*60*1000L（毫秒）
  这是坚果云免费版的真实限制，数值不得修改

【MockWebServer 用法】
  Web 搜索：OkHttp MockWebServer kotlin coroutines test
  确认 MockWebServer 在协程测试中的正确 shutdown 时机
```

### P5 同步引擎特别注意

```
【manifest 并发写入】
  ManifestManager.updateManifest() 必须在 Mutex.withLock 内完成完整的读-改-写
  三者必须在同一个 withLock 块内，不得只锁写而不锁读

【tombstone 清理条件】
  必须是：devices.all { device.lastSyncAt > tombstone.deletedAt }（严格大于）
  不得使用 >= （等于边界按保守原则应视为"未同步"）

【书签合并来源标记】
  mergeSource 字段值只能是："local"、"remote"、"merged"
  写入 Room 后不影响任何业务逻辑，仅用于日志
```

---

## § 9 · Web 搜索策略

### 9.1 何时必须搜索

```
情形 A：首次使用某个库的 API → 必须搜索
情形 B：测试连续失败 3 次以上 → 必须搜索错误信息
情形 C：不确定两个 API 的区别（如 collect vs collectLatest）→ 必须搜索
情形 D：进入每个新阶段（P1~P9）→ 按 §2.2 表格必须搜索
情形 E：写加密相关代码（§P7）→ 每个 API 必须搜索
情形 F：不确定某个 Android API Level 的支持情况 → 必须搜索
```

### 9.2 搜索词构造规范

```
格式：{具体 API 名称} {库名} {平台} {年份或 "latest"}
好的例子：
  ✅ "Mutex withLock kotlin coroutines example"
  ✅ "Room database migration android kotlin 2024"
  ✅ "GCMParameterSpec 128 android java example"
  ✅ "WorkManager PeriodicWorkRequestBuilder interval minimum"
差的例子：
  ❌ "kotlin encryption" （过于宽泛）
  ❌ "android sync" （无法获取具体信息）
```

### 9.3 搜索结果处理

```
1. 优先阅读：developer.android.com > kotlinlang.org > github.com/官方仓库
2. 次优先：stackoverflow 高票回答（≥ 100 votes，近两年）
3. 不使用：随机博客、CSDN、掘金（可能版本过旧或有错误）
4. 找到 API 示例后：将代码片段与自己的实现对比，确认调用方式一致
```

---

## § 10 · 执行完成

### 10.1 全量测试验证

所有 41 个任务完成后，运行：

```bash
# 单元测试全量
./gradlew :app:testDebugUnitTest 2>&1 | tail -20

# 覆盖率报告
./gradlew :app:koverReport
# 检查报告：sync/** ≥ 85%，crypto/** ≥ 90%

# 最终检查：无遗留 TODO
grep -r "// TODO\|// FIXME\|// HACK" app/src/main/java/com/shuli/reader/sync/ app/src/main/java/com/shuli/reader/ui/settings/sync/
```

### 10.2 Wrap-up

```bash
# 所有测试绿色、覆盖率达标、无 TODO 遗留后执行：
mv tasks/sync-backup-tdd-20260529-0941 tasks/sync-backup-tdd-20260529-0941-done
git add -A
git commit -m "feat(sync): complete T-01~T-41 sync & backup implementation [TDD]"
git tag task/sync-backup-tdd-20260529-0941
```

### 10.3 最终报告

Wrap-up 后输出摘要：

```
=== 执行摘要 ===
完成任务：41/41
总步骤数：89
失败后修复：N 次
Web 搜索次数：N 次
最终测试状态：PASSED
sync/** 覆盖率：XX%
crypto/** 覆盖率：XX%
```

---

## § 11 · 快速参考

```
任务清单文件：tasks/sync-backup-tdd-20260529-0941/agent.md
人工核验清单：tasks/sync-backup-tdd-20260529-0941/review.md
架构规范文件：tasks/sync-backup-tdd-20260529-0941/21-sync-backup-architecture-v4.md
进度记录文件：tasks/sync-backup-tdd-20260529-0941/.progress

构建命令：    ./gradlew :app:testDebugUnitTest --tests "com.shuli.reader.XxxTest"
测试包前缀：  com.shuli.reader
最小 SDK：    API 26（Android 8.0）
加密算法：    AES-256-GCM（唯一，不实现 ChaCha20）
PBKDF2 次数：600,000（不得低于此值）
```

---

*最后更新：2026-05-29 · V4 架构对应版本*
