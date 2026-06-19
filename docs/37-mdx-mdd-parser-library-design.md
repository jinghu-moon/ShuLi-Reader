# 37 - MDX/MDD 词典解析库设计方案（纯 Kotlin · 高性能 · 轻量）

> 编写时间：2026-06-18
> 状态：设计完成，待实施
> 定位：本文档是 [`37-dictionary-lookup-design.md`](./37-dictionary-lookup-design.md) 的**前置依赖**。
> 37 号文档负责「查词功能」的上层（选区触发、查词弹窗、词库管理、释义渲染）；
> 本文档负责其中 MDX/MDD 格式的**底层解析引擎**，即 37 号文档 4 章、7.2 节、12 章所引用的 `MdxParser` 的完整实现规格。
> 参考实现（已下载至 `refer/dict/`）：
>
>   - `writemdict-master/fileformat.md` — MDX/MDD v2.0 二进制格式逆向规范（**格式圣经**）
>   - `js-mdict-master/`（terasum/js-mdict）— TypeScript 实现，**Kotlin 重写主蓝图**
>   - `mdict-java-master/`（KnIfER/mdict-java）— Java 实现，**JVM 平台习语参考**（核心解析为 Apache 2.0）
>   - `mdict-js-master/`（fengdh/mdict-js）— 极简纯 JS 实现 + minilzo JS 移植参考
> 原则：纯 Kotlin · 零外部解析依赖 · 协议干净（仅参考 MIT/Apache 部分）· 按需解块 · 内存可控

---

## 1. 目标与非目标

### 1.1 目标

| 目标 | 度量 |
|---|---|
| **格式完整兼容** | 覆盖 MDX/MDD v1.2 与 v2.0、四种编码、三种压缩、key-info 加密（见 §3 兼容性矩阵） |
| **高性能** | 单词典精确查询 < 10ms（索引已加载）；首次索引加载 < 300ms（7 万词条级） |
| **轻量** | 零外部 MDX 解析依赖；纯 Kotlin + JDK 自带 `Inflater`/`Charset`；索引常驻内存单词典 < 3MB |
| **协议干净** | 仅参考 MIT（mdict-js）、Apache 2.0（mdict-java 核心包）实现；本库随项目 AGPL-3.0-or-later 发布 |
| **Android 适配** | 全部 IO 走 `suspend` + `Dispatchers.IO`；大文件随机访问不 OOM |

### 1.2 非目标（明确不做）

| 不做 | 原因 |
|---|---|
| **Salsa20 注册码加密**（`Encrypted` 低位 = 1，record 区加密的商业 DRM 词典） | 需用户专属注册码/密钥，无密钥则**任何阅读器都无法解密**（欧路、GoldenDict 同样解不了）。社区流通词库 99% 不含此加密 |
| MDX **写入/构建** | 本项目只读词典，不生产词典 |
| 全文检索（FTS）/ 倒排索引 | 由 37 号文档的上层查询引擎按需实现，不属于解析库职责 |
| 模糊/通配符匹配 | 同上，属上层 `DictLookupEngine` 职责（37 号文档 §11） |

> **关于「完全兼容」的诚实边界**：本库目标是「完全兼容**社区共享的、未 DRM 加密的** MDX/MDD 词库」。真正的 100%（含注册码加密）在技术上不可达，这是格式本身的限制，须在产品层做期望管理。

---

## 2. 格式总览

> 完整规范见 `refer/dict/writemdict-master/fileformat.md`。下面是落地视角的精炼。

MDX 与 MDD 共享**完全相同**的三段结构，仅在「编码 / 输出类型」上分流：

```
┌─────────────────┐
│  Header Section │  4B 长度 + UTF-16LE XML + 4B adler32
├─────────────────┤
│ Keyword Section │  5 个计数字段 + key_index（压缩+可加密）+ N 个 key_block（压缩）
├─────────────────┤
│ Record Section  │  4 个计数字段 + record_info 表 + N 个 record_block（压缩）
└─────────────────┘
```

**查词的本质**：keyword → 在 key_index 中二分定位所在 key_block → 解压该 block 取出 `keyword → record_offset` → 在 record_info 中定位 offset 所属 record_block → 解压该 block 按 `[start,end)` 切出释义。

**MDX vs MDD 的唯一差异**：

| 维度 | MDX | MDD |
|---|---|---|
| Header XML 根标签 | `<Dictionary>` | `<Library_Data>` |
| keyword 含义 | 词头（按 `Encoding`） | 资源路径（如 `\img\a.png`，**强制 UTF-16LE**） |
| record 含义 | 释义（按 `Encoding` decode 为文本） | 二进制资源（原样 `ByteArray` 返回） |
| 路径归一化 | 无 | 查询时 `/` → `\`，并确保以 `\` 开头 |

→ 解析层 95% 代码复用，MDD 只是「编码固定 UTF-16LE + 输出 ByteArray 不 decode」的特例。

---

## 3. 兼容性矩阵（实现清单）

这是「完全兼容」的可勾选定义。每一行都对应 §4 之后的具体实现。

| 维度 | 取值 | 是否支持 | 实现要点 | 来源依据 |
|---|---|:---:|---|---|
| **版本** | v2.0 | ✅ | 计数字段 8 字节，含 `key_index_decomp_len` | fileformat.md L83 |
| | v1.2 | ✅ | 计数字段 4 字节，**缺** `key_index_decomp_len`，首尾词长度字段 1 字节 | js-mdict `mdict-base.ts:572` |
| **编码** | UTF-8 | ✅ | `Charsets.UTF_8` | — |
| | UTF-16 | ✅ | 强制 LE：`Charset.forName("UTF-16LE")` | mdict-java `mdBase.java:369` |
| | GBK / GB2312 | ✅ | **提升为 GB18030**（超集，解码更全） | mdict-java `mdBase.java:367` |
| | Big5 | ✅ | `Charset.forName("Big5")`（Android 内置） | — |
| **压缩** | 无 (`00`) | ✅ | 取 `block[8:]` 原样 | fileformat.md L231 |
| | zlib (`02`) | ✅ | `Inflater`，`setInput(buf, 8, len-8)` 跳过 4B 类型+4B adler | mdict-java `mdBase.java:779` |
| | **LZO (`01`)** | ✅ | **纯 Kotlin minilzo 移植**（见 §7） | js-mdict `lzo1x.ts` |
| **加密** | 无 | ✅ | — | — |
| | key-index 加密 (`& 2`) | ✅ | ripemd128 派生 key + swap-nibble XOR 链（见 §6） | mdict-java `BU.java:86` |
| | record 加密 (`& 1`，Salsa20) | ❌ | DRM，无密钥不可解 → 抛 `UnsupportedDictException` | fileformat.md L118 |
| **校验** | adler32 | ⭕ 可选 | 默认跳过（两个参考实现都跳过）；可作完整性校验开关 | js-mdict / mdict-java 均 TODO |
| **跨语言** | `@@@LINK=` 重定向 | ✅ | 释义以 `@@@LINK=` 开头时递归查目标词 | mdict-java `mdBase.java:842` |

---

## 4. 整体架构

### 4.1 分层

```
┌──────────────────────────────────────────────────────────┐
│  公开 API 层                                                │
│    MdxDictionary / MddDictionary（suspend lookup/prefix）   │
├──────────────────────────────────────────────────────────┤
│  解析引擎层（MdictParser）                                  │
│    HeaderParser · KeyIndexParser · KeyBlockReader           │
│    · RecordReader                                          │
├──────────────────────────────────────────────────────────┤
│  基础设施层                                                 │
│    BlockReader（随机 IO）· Decompressor（zlib/lzo/raw）     │
│    · MdxCrypto（ripemd128 + fast_decrypt）· BlockCache(LRU) │
└──────────────────────────────────────────────────────────┘
```

设计要点：**「索引常驻 + block 按需解压 + LRU 缓存」**。这是两个参考实现验证过的最优内存模型——

- 常驻内存：只有 `keyBlockInfoList`（每个 key block 的首尾词 + 压缩/解压尺寸 + 累加偏移）和 `recordInfoList`（前缀和索引），单词典约 1–3MB。
- 按需解压：实际词条名、释义都不预加载，查词时才读+解压单个 block。
- **关键偏离参考实现**：js-mdict 默认 `_readKeyBlocks()` 全量解出所有词条排序（作者自注「可能 OOM」）；mdict-java 用单槽缓存。本库**两者都不取**——用「块级 first/last 二分定位 block + 块内线性扫描」，配合小容量 LRU（见 §5.4、§8.1），兼顾内存与正确性。

### 4.2 模块结构

```
core/dictionary/mdx/                         ← 独立子模块，不依赖上层
  MdictParser.kt          ← 解析主入口：open() 跑 header+key_index，懒加载 block
  model/
    MdxHeader.kt          ← 版本/编码/加密标志/根标签 等元信息
    KeyBlockInfo.kt       ← 单个 key block 的 first/last word + pack/unpack size + 累加偏移
    RecordBlockInfo.kt    ← 单个 record block 的 pack/unpack size + 累加偏移
    MdxEntry.kt           ← 查询结果：keyword + recordOffset + recordSize（或直接 bytes）
  io/
    BlockReader.kt        ← FileChannel 随机读 slice；统一 Long offset，处理 >2GB
  codec/
    Decompressor.kt       ← comp_type 分派：raw / zlib(Inflater) / lzo
    MiniLzo.kt            ← 纯 Kotlin LZO1X 解压（minilzo 移植）
    MdxCrypto.kt          ← ripemd128 + fast_decrypt（key-index 解密）
    Ripemd128.kt          ← 纯 Kotlin RIPEMD-128
  cache/
    BlockCache.kt         ← key/record block 的 LRU（基于解压后字节）
  MdxIndexStore.kt        ← key/record block info 序列化落盘 + 加载（首次后免重解析）
  MdictException.kt       ← UnsupportedDictException / CorruptDictException
```

→ 该子模块对 37 号文档的对接点：`MdxParser.lookup(word)` / `prefixRange(prefix, limit)` / `readDefinition(offset, size)`（与 37 号文档 §4.4、§7.2 的接口签名一致），以及 `MddResourceLoader` 复用 `MddDictionary.locate(path)`。

---

## 5. 解析管线（落地步骤）

> 所有多字节整数除特别说明外均为**大端序（big-endian）**。Kotlin 用 `ByteBuffer.order(ByteOrder.BIG_ENDIAN)`，比手动移位清晰。注意 JVM 无无符号类型：8 字节计数用 `Long`，可能 > 2^31 的 size 字段一律用 `Long` 承载，按字节取值时 `and 0xFF`。

### 5.1 Header 解析（`HeaderParser`）

```
1. 读 [0,4)  → headerLen（大端 uint32）
2. 读 [4, 4+headerLen) → UTF-16LE 解码为 XML 字符串
3. 跳过 [4+headerLen, +4) 的 adler32（可选校验）
4. keySectionStart = 4 + headerLen + 4
5. 正则 /(\w+)="(.*?)"/ 抠出属性，反转义 &lt;&gt;&quot;&amp;
```

从 XML 属性提取并归一化：

| 属性 | 处理 |
|---|---|
| `GeneratedByEngineVersion` | `version = toFloat()`；**`version >= 2.0 → numberWidth = 8`，否则 4**。这是贯穿全程的开关 |
| `Encoding` | 空→UTF-8；`UTF-16`→UTF-16LE；`GBK`/`GB2312`→GB18030；`Big5`→Big5。**MDD 强制 UTF-16LE** |
| `Encrypted` | `No`/空→0；`Yes`→1；否则 `toInt()`。bit0=record 加密，bit1=key-index 加密 |
| `Format` | `Html` / `Text`（影响上层渲染，不影响解析） |
| `StyleSheet` | 每 3 行一组解析为 `{编号: [前缀, 后缀]}`，供释义 `` `n` `` 标记替换（见 §9.2） |

若 `Encrypted and 1 != 0` → 立即抛 `UnsupportedDictException("record-level Salsa20 encryption")`。

### 5.2 Keyword Header（5 个计数字段）

从 `keySectionStart` 读 `version>=2.0 ? 5×8 : 4×4` 字节：

| 字段 | v2.0 | v1.2 |
|---|---|---|
| `keyBlockNum` | 8B | 4B |
| `entriesNum` | 8B | 4B |
| `keyIndexDecompLen` | 8B | **不存在** |
| `keyIndexCompLen` | 8B | 4B |
| `keyBlocksTotalLen` | 8B | 4B |

v2.0 读完后再 +4 跳过这 40 字节的 adler32 校验。v1.2 无此校验字段。

### 5.3 Key Index 解码（`KeyIndexParser`）—— 最易错处

读 `keyIndexCompLen` 字节，**先解密、后解压**：

```
buf = read(keyIndexCompLen)
if (version >= 2.0 && (encrypted and 2) != 0):
    buf = MdxCrypto.decrypt(buf)        // §6，只解 buf[8:]，buf[0:8] 保留
if (version >= 2.0):
    compType = buf[0:4]
    keyIndex = Decompressor.decompress(buf)   // 跳过 8B 头解压
else:
    keyIndex = buf                      // v1.2 的 key index 不压缩不加密
```

解压后顺序解析 `keyBlockNum` 个条目，每条（**宽度随版本变化是核心坑点**）：

| 子字段 | 宽度 | 长度修正（关键） |
|---|---|---|
| `blockEntryCount` | numberWidth | — |
| `firstWordSize` | v2.0: 2B / v1.2: 1B | — |
| `firstWord` | 变长 | v2.0 且 UTF-16: `(size+1)*2`；v2.0 其他: `size+1`；v1.2 UTF-16: `size*2`；v1.2 其他: `size` |
| `lastWordSize` | v2.0: 2B / v1.2: 1B | 同上修正 |
| `lastWord` | 变长 | 同上 |
| `keyBlockCompSize` | numberWidth | 该 key block 压缩字节数 |
| `keyBlockDecompSize` | numberWidth | 该 key block 解压字节数 |

解析时**累加三个前缀和**（避免后续重复求和）：`compAccumulator`（用于定位 block 在文件中的位置）、`decompAccumulator`、`entryAccumulator`。结果存为 `List<KeyBlockInfo>`，常驻内存。`keyBlocksStart = 当前 offset`（即所有 key_block 的起点）。

> 首尾词长度修正的「v1.2/v2.0 × UTF-16/非」四种组合是整个库最易写错的地方。务必参照 js-mdict `mdict-base.ts:759-837` 与 mdict-java `mdBase.java:520-590` 双向对拍。

### 5.4 Key Block 查词（`KeyBlockReader`）

两级二分：

```
// 第一级：用 keyBlockInfoList 的 first/last word 定位目标 block
fun findKeyBlock(word): Int =
    二分找满足 first[i] <= word <= last[i] 的 block 下标
    // 比较用 Collator（大小写不敏感按 KeyCaseSensitive），见下方说明

// 第二级：解压该 block，块内线性/二分扫描
fun lookupInBlock(blockIdx, word): MdxEntry? {
    info = keyBlockInfoList[blockIdx]
    raw  = blockCache.getOrPut(blockIdx) {
        buf = blockReader.read(keyBlocksStart + info.compAccumulator, info.compSize)
        Decompressor.decompress(buf)            // raw/zlib/lzo
    }
    // 块内拆词：每条 = [recordOffset: numberWidth][keyword: 以 width 个 0x00 结尾]
    //   width = (encoding==UTF16 || isMdd) ? 2 : 1
    //   每条 keyword 的 recordEnd = 下一条的 recordOffset（链式补齐）
    return 块内匹配 word 的条目
}
```

**排序一致性问题**：MDX 的 first/last word 在跨大小写/多语言时排序可能不稳定（js-mdict 因此放弃块级二分、改全量加载）。本库的折中：
- `KeyCaseSensitive=No`（绝大多数中文词典）时，比较用 `word.lowercase()` 后的字节序，与 MDX 生成时一致；
- 块级二分失败（落在边界模糊区）时，**回退到相邻 ±1 block 线性扫描**，而非全量加载。这样既避免 OOM，又保证召回。

### 5.5 Record Section（`RecordReader`）

打开词典时解析 record 索引（不读 record 数据本身）：

```
// Record Header：version>=2.0 ? 4×8 : 4×4
recordBlockNum, entriesNum, recordInfoCompSize, recordBlocksTotalLen
assert(entriesNum == keywordHeader.entriesNum)

// Record Info：循环 recordBlockNum 次，每次读 (compSize, decompSize) 各 numberWidth
//   累加 compAccumulator / decompAccumulator 两个前缀和 → List<RecordBlockInfo>
recordBlocksStart = 当前 offset
```

取释义（按需）：

```
fun readRecord(entry: MdxEntry): ByteArray {
    // entry.recordOffset 是「所有 record block 解压后拼接流」中的偏移
    idx  = 二分 recordInfoList 找 decompAccumulator <= recordOffset 的最后一块
    info = recordInfoList[idx]
    raw  = recordCache.getOrPut(idx) {
        buf = blockReader.read(recordBlocksStart + info.compAccumulator, info.compSize)
        Decompressor.decompress(buf)
    }
    start = entry.recordOffset - info.decompAccumulator
    end   = entry.recordEnd    - info.decompAccumulator    // recordEnd 来自块内拆词链式补齐
    return raw.copyOfRange(start, end)
}
```

- **MDX**：`readRecord` 结果 `String(bytes, charset)` → 释义文本（再走 §9 渲染对接）。
- **MDD**：`readRecord` 结果**原样 ByteArray** 返回（CSS 文本 / 图片 / 音频二进制）。

### 5.6 `@@@LINK=` 重定向

MDX 释义若以 `@@@LINK=` 开头，其后是目标词头（去尾部空白/null）。处理：提取目标词 → 递归 `lookup(target)` → 返回目标释义。需设**递归深度上限（如 ≤ 5）**防环形链导致栈溢出。

---

## 6. 加密处理（`MdxCrypto` + `Ripemd128`）

仅实现 **key-index 加密**（`Encrypted and 2`，社区词典常见，无门槛可解）。Salsa20 record 加密不实现（§1.2）。

### 6.1 key-index 解密流程

参照 mdict-java `BU.java:86-110`（Apache 2.0，可逐行翻译）与 js-mdict `utils.ts:291-305`：

```kotlin
fun decrypt(compBlock: ByteArray): ByteArray {
    // 1. 派生 key：密文 [4,8) 4 字节 ++ 小端打包的 0x3695（即 95 36 00 00）
    val seed = compBlock.copyOfRange(4, 8) + byteArrayOf(0x95.toByte(), 0x36, 0, 0)
    val key = Ripemd128.hash(seed)              // 16 字节
    // 2. buf[0:8] 原样保留（comp_type + checksum），只解 buf[8:]
    val out = compBlock.copyOf()
    fastDecrypt(out, 8, key)
    return out
}

// swap-nibble XOR 链（注意 previous 用「原始字节」而非解密后字节）
fun fastDecrypt(buf: ByteArray, from: Int, key: ByteArray) {
    var previous = 0x36
    for (i in from until buf.size) {
        val orig = buf[i].toInt() and 0xFF
        val swapped = ((orig ushr 4) or (orig shl 4)) and 0xFF
        buf[i] = (swapped xor previous xor ((i - from) and 0xFF) xor (key[(i - from) % key.size].toInt() and 0xFF)).toByte()
        previous = orig
    }
}
```

> ⚠️ 两个参考实现在 key 取模索引上略有出入（`BU.java` 用 `i % len`，另一处用 `(i+len) % len`）——以 mdict-java `BU.java` 实际被调用的版本为准。`previous` 必须是**解密前的原始字节**，写错会全盘乱码。

### 6.2 RIPEMD-128（`Ripemd128`）

标准算法，纯 Kotlin 移植（参考 mdict-java `ripemd128.java`，Apache 2.0）：

- 4×32bit 初始向量 `0x67452301 / 0xefcdab89 / 0x98badcfe / 0x10325476`
- 64 字节分块，**小端**读 32bit 字，小端 64bit 长度填充
- 左右两路各 64 步，4 个布尔函数 F1–F4 + S 移位表 + X 消息序表 + K 常量表
- **无符号 32 位运算**：用 `Int` + `ushr`，或 `Long and 0xFFFFFFFFL` 模拟，`rol` 注意掩码

常量表可直接照搬，约 150 行。仅本库内部使用，不暴露为公开 API。

---

## 7. LZO 解压（`MiniLzo`）—— 最高难度模块

### 7.1 决策：纯 Kotlin 移植，不引入 JAR

| 方案 | 取舍 |
|---|---|
| 引入 `org.anarres.lzo`（mdict-java 用的 `lzo-core-1.0.6.jar`） | ❌ GPL/LGPL 依赖，增加 APK 体积，违背「轻量+零外部依赖」 |
| **纯 Kotlin 移植 minilzo 解码器** | ✅ 参考 `lzo1x.ts`（GPL-2.0）/ `minilzo-decompress.js` 的**算法**，自行用 Kotlin 重写。算法本身不受版权限制，独立实现即可 |
| 暂不支持 LZO | ⚠️ 可作为 Phase 早期临时态，但**老词库（v1.x 多用 LZO）会解压失败** |

> **现实权重**：现代 MDX 几乎全是 zlib（type=2）。LZO（type=1）主要见于较老词库。因此实现顺序上 **zlib 优先跑通，LZO 紧随其后**——但 LZO 属于「完全兼容」的必选项，不能永久缺席。

### 7.2 LZO1X 解码要点

只需**解压**（decompress），不需压缩。算法是 token 驱动的状态机：

```
读 token：
  t >= 64 : 短 match（距离/长度编码在 token 高位）
  t >= 32 : 长 match（长度续读后续字节）
  t >= 16 : 远 match（距离高位在 token）
  t < 16  : literal run（首 token 特殊处理）
literal → 从输入拷 N 字节到输出
match   → 从输出回看 distance 处拷 length 字节（可重叠拷贝）
直到 EOF token 终止
```

**改进点（相对 JS 参考）**：JS 版 `lzo1x-wrapper` 忽略了 `unpackSize` 参数、靠翻倍扩容缓冲。本库**用 `KeyBlockInfo.decompSize` 预分配精确输出数组**，零扩容、零拷贝，性能更优。约 200–300 行。

---

## 8. 性能与轻量化设计

### 8.1 文件 IO：用 `FileChannel`，不照搬参考实现

两个参考实现的 IO 方式都**不适合 Android**：
- mdict-java：反复 `new FileInputStream` + `skip()` 到 offset（作者注「磁盘不是瓶颈」——那是桌面 JVM 的结论，Android 大文件 skip 慢得多）。
- js-mdict：`fs.readSync` 按 position 读 slice（思路对，但无缓存）。

**本库方案**：

```kotlin
class BlockReader(file: File) : Closeable {
    private val channel = RandomAccessFile(file, "r").channel
    // 统一 Long offset，支持 >2GB（MDD 资源常超 2GB）
    fun read(offset: Long, size: Int): ByteArray {
        val buf = ByteBuffer.allocate(size)
        channel.read(buf, offset)          // 定位读，不改变全局 position
        return buf.array()
    }
}
```

- 用 `FileChannel.read(buf, position)` 做无状态随机读，天然支持 `Long` offset，规避 `MappedByteBuffer.position(Int)` 的 2GB 溢出陷阱（37 号文档审查时已指出此风险）。
- **不默认 mmap**：MDD 可达 1GB+，mmap 整文件占虚拟地址且 Android 上回收不可控；按需 slice 读 + LRU 缓存更稳。

### 8.2 内存模型

| 数据 | 驻留策略 | 单词典量级（7 万词条） |
|---|---|---|
| `MdxHeader` | 常驻 | < 1KB |
| `keyBlockInfoList` | 常驻 | ~700–1400 项 × ~150B ≈ 1–2MB |
| `recordInfoList` | 常驻 | 前缀和数组，< 0.5MB |
| 解压后的 key block | **LRU（容量 4）** | 单块数十 KB |
| 解压后的 record block | **LRU（容量 4）** | 单块数十 KB～数百 KB |
| 词条名全表 / 释义全文 | **不驻留** | 0 |

→ 单词典常驻 1–3MB，与 37 号文档 §7.3 的预算一致。多词典场景下，若总索引内存超阈值（如 64MB），由上层 `DictionaryManager` 对**整本词典的索引**做 LRU 卸载（关闭 `MdictParser` 释放 `keyBlockInfoList`）。

#### 8.2.1 释义渲染路径的取舍（WebView vs AST）

> 本库只负责解出释义**原始字符串/资源字节**（§9.2），渲染由上层负责。但渲染方式直接关系内存预算，故在此一并交代取舍。

MDX 释义是「自定义标签 + 词典自带 CSS」驱动的（如《漢語大詞典》的 `<CY><CTY><SC><TC><LZ>`，靠 `hydcdv2.css` 渲染）。把它变成可见 UI 有三条路：

| 路线 | 渲染引擎 | APK 体积 | 运行时内存 | 风格统一 | 冷启动 | 适用 |
|---|---|---|---|---|---|---|
| **系统 WebView** | 系统 Chromium（`android.webkit.WebView`） | 不增（用系统的） | **30–50MB/实例** | ❌ 词典 CSS 说了算 | 慢（首次初始化 Chromium，数百 ms） | 复杂 MDX 词典降级显示 |
| **AST + Compose**（默认） | 本库/上层自己的代码 | 不增 | ~1–3MB | ✅ 暗色/字号/配色全统一 | 快（1–3ms/词条） | 规整词典（CC-CEDICT/维基词典） |
| 打包独立引擎（GeckoView/CEF） | 自带 Chromium | **+几十 MB** | 高 | ❌ | — | ❌ 不考虑 |

**关于系统 WebView 的三点澄清**（常见误解）：

1. **是系统自带，不打包引擎** → 所以**不增加 APK 体积**。这是它唯一的体积优势。
2. **但运行时内存重** → 每个 WebView 实例承载一个 Chromium 渲染进程，30–50MB。在查词 BottomSheet 这种「弹出即用、用完即关」的场景代价过高。**体积省了，内存反而重**，二者是两回事。
3. **「系统的哪一个」不可控** → Android 7+ 的 WebView 由「Android System WebView / Chrome」独立更新，版本与系统解耦；**国内无 GMS 的 ROM（小米/华为/OPPO/vivo）由厂商自维护**，版本可能旧、行为有差异、甚至阉割。不能假设所有设备 WebView 行为一致。

**离线纯粹性注意**：WebView 渲染本地 HTML 不联网，但词典 HTML 里的外链资源（`<img src="http://…">`）、内嵌 JS 的网络请求会破坏「纯离线」定位，必须在 `WebViewClient.shouldInterceptRequest` 拦截，只放行 `entry://` 重写后的本地资源（即 Demo 服务端 `rewriteDefinition` 做的事）。

**结论（与 §8.4、37 号文档 §8 一致）**：Android app **默认走 AST + Compose 原生渲染**——轻、风格统一、秒出。系统 WebView 仅作复杂 MDX 词典的**降级选项**；又因国内 ROM 的 WebView 不可控，很多场景下**纯文本降级比 WebView 更稳**。这也是欧路、GoldenDict 等成熟产品的混合策略：规整词典精渲染、复杂词典降级。

> 注：`mdict/` Demo（`demo/DictDemoServer.kt`）当前用桌面浏览器 iframe 渲染，等价于上表「系统 WebView」行的行为——还原度高但风格不可控。它用于直观查看真实词典内容，不代表 app 最终渲染路径。

### 8.3 索引落盘缓存（`MdxIndexStore`）

大词典首次 `open()` 需解析 header + 解压解密 key_index（可能 100–300ms）。优化：把 `keyBlockInfoList` + `recordInfoList` + `MdxHeader` **序列化到 `filesDir/dict/cache/{dictId}-index.bin`**。

```
open() 流程：
  if (缓存存在 && 缓存记录的源文件 size+mtime 未变):
      mmap/读取缓存 → 反序列化索引（< 10ms）
  else:
      完整解析 → 写入缓存
```

这是两个参考实现都没做的优化点，对「7 万词条秒开」体验提升明显。缓存失效判据用源文件 `size + lastModified`（比 hash 快，足够可靠）。

### 8.4 并发与取消

- 所有 `open()` / `lookup()` / `readRecord()` 标记 `suspend`，运行在 `Dispatchers.IO`。
- 解析与解压是 CPU/IO 混合，配合 37 号文档 §7.4 的「多词典 `async` 并发 + 分层超时 + `withTimeoutOrNull`」。
- BottomSheet 关闭时上层 cancel job，本库的 `suspend` 函数响应协程取消（在解压大块前检查 `ensureActive()`）。

### 8.5 性能目标（对齐 37 号文档 §15.3）

| 操作 | 目标 |
|---|---|
| `open()` 冷启动（含解析 key_index，7 万词条） | < 300ms |
| `open()` 热启动（命中索引落盘缓存） | < 10ms |
| 单次 `lookup()`（block 已缓存） | < 1ms |
| 单次 `lookup()`（需读+解压 block） | < 10ms |
| zlib 单块解压（~64KB） | 1–3ms |
| LZO 单块解压（~64KB） | 2–5ms |

#### 实测（真实大词典基准）

样本：《漢語大詞典》tsiank 升级版，MDX 108MB + MDD 162MB，**v2.0 + UTF-8 + key-index 加密（Encrypted=2）+ HTML**，**606,592 词条**。基准代码 `BenchmarkTest`（本地无该词典时自动跳过）。

| 指标 | 实测 | 说明 |
|---|---|---|
| 词条总数解析 | 606,592 | 全量正确加载 |
| 随机 lookup + readDefinition | **~163µs / 词**（50 词全命中） | 远优于 < 10ms 目标（约 60×） |
| MDD 资源读取 | CSS 239,068 bytes 精确读出 | 路径归一化 + 二进制返回正确 |
| 热路径（block 已缓存） | 微秒级 | BlockCache 命中 |

> 注：`open()` 冷启动微基准受 OS 文件缓存影响（108MB 文件预读后近乎瞬时），未做清缓存的严格冷启动测量；但 60 万词条全解析、加密 key_index 正确解密、查询全命中已充分验证生产级正确性与性能。LZO 路径由 fixture 覆盖（该真实词典为 zlib）。

---

## 9. 公开 API 与上层对接

### 9.1 API 设计

```kotlin
// 打开：解析 header + key_index（或读索引缓存），不读 block 数据
suspend fun MdictParser.open(file: File, cacheDir: File? = null): MdictParser

interface MdictParser : Closeable {
    val header: MdxHeader
    val entryCount: Int

    /** 精确查询（大小写按 KeyCaseSensitive）。未命中返回 null */
    suspend fun lookup(word: String): MdxEntry?

    /** 前缀范围查询，最多 limit 条。供上层「联想/你是不是要找」 */
    suspend fun prefixRange(prefix: String, limit: Int): List<MdxEntry>

    /** MDX：返回释义文本（已解 @@@LINK、未做 stylesheet 替换） */
    suspend fun readDefinition(entry: MdxEntry): String

    /** MDD：返回资源二进制 */
    suspend fun readResource(path: String): ByteArray?
}
```

`MdxEntry` 携带 `keyword`、`recordOffset`、`recordEnd`、`keyBlockIdx`，足以二次取释义。

### 9.2 与 37 号文档渲染管线的边界

本库**只负责到「解出释义原始 HTML 字符串 / 资源字节」为止**，明确**不做**以下（均属 37 号文档 §8 渲染管线职责）：

- StyleSheet `` `n` `` 标记替换 → 由上层 `DictStyleResolver` 处理（但本库 `header.styleSheet` 暴露映射表供其使用）
- CSS class → SpanStyle、px→sp 转换、HTML→Compose
- `entry://` / 图片资源的实际加载渲染（本库提供 `readResource(path)` 原料，渲染由上层）

→ 对接关系：`MddDictionary.readResource("\style.css")` 喂给 37 号文档的 `MddResourceLoader.loadCss()`；`MdxDictionary.readDefinition()` 喂给 `DictMdxRenderer`。

---

## 10. 测试策略

| 层级 | 测试内容 |
|---|---|
| `Ripemd128` 单测 | 标准测试向量（空串、"abc"、长串）比对已知 hash |
| `MdxCrypto` 单测 | 已知加密 key-index 解密后与明文比对；previous 字节链正确性 |
| `MiniLzo` 单测 | 用 `lzo1x.ts` 跑一批样本压缩→本库解压；含重叠拷贝、跨缓冲边界用例 |
| `Decompressor` 单测 | raw/zlib/lzo 三类型分派；跳过 8B 头正确性；与明文对比一致 |
| `HeaderParser` 单测 | v1.2/v2.0 版本识别、四种编码归一化、Encrypted 解析、MDD 根标签、Salsa20 抛异常 |
| `KeyIndexParser` 单测 | **首尾词长度修正四组合**（v1.2/v2.0 × UTF-16/非）、前缀和累加、加密 key-index 解码 |
| `KeyBlockReader` 单测 | 块级二分定位、块内拆词（UTF-16 双 null / 单 null）、边界回退 ±1 block |
| `RecordReader` 单测 | record block 定位、`[start,end)` 切片、MDD 二进制原样返回 |
| `MdictParser` 集成测试 | 真实小词典端到端 `open→lookup→readDefinition`；MDD 取 CSS/图片；`@@@LINK` 重定向；递归深度上限 |
| `MdxIndexStore` 单测 | 序列化/反序列化往返一致；源文件变更后缓存失效重建 |
| 性能基准 | 7 万词条词典冷/热启动耗时、单次 lookup 耗时（对齐 §8.5） |
| 兼容性回归 | 准备 v1.2、v2.0、UTF-8、UTF-16、GBK、zlib、LZO、加密 key-index 各一份**最小样本词典**入库做回归 |

> 测试样本来源：用 `writemdict-master/writemdict.py`（MIT）可生成各种配置的最小 MDX/MDD 测试词典，覆盖矩阵各维度。避免使用版权词库做测试 fixture。

---

## 11. 分阶段实施计划

> 本库整体对应 37 号文档的 **Phase 2「多格式支持」**。内部再分三步，确保每步可独立验证。

### Step 1：zlib 路径跑通（覆盖 90% 现代词库）✅ 已完成
- [x] `BlockReader`（FileChannel 随机读）
- [x] `HeaderParser`（v1.2/v2.0 + 四编码 + Salsa20 抛异常）
- [x] `Decompressor`：raw + zlib（`Inflater`）
- [x] `KeyIndexParser`（**首尾词长度修正四组合**）+ `KeyBlockReader`（两级二分）
- [x] `RecordReader` + `MdictParser.lookup/readDefinition`
- [x] 单测覆盖 header/keyindex/keyblock/record + zlib 样本词典端到端

### Step 2：完整兼容（加密 + LZO + MDD）✅ 已完成
- [x] `Ripemd128`（4 个标准向量校验）+ `MdxCrypto`（key-index 加密解码）
- [x] `MiniLzo`（LZO1X 解压）+ 接入 `Decompressor`
- [x] `MdictParser.readResource`（路径归一化 + 二进制返回）
- [x] `@@@LINK=` 重定向（含递归深度上限 5）
- [x] 加密/LZO/MDD/重定向/Salsa20 拒绝 样本词典回归（共 14 本 fixture，30 个测试）

> LZO 测试数据用 minilzo 官方源码编译 DLL + ctypes 假 `lzo` 模块生成（见 `mdict/tools/fixtures/README.md`），因 python-lzo 在 Windows 上无 wheel、源码编译困难。

### Step 3：性能与体验
- [x] `BlockCache`（key/record block LRU，默认容量 4）
- [x] `MdxIndexStore`（KeyIndex 落盘缓存，source size+mtime 校验，秒开）
- [x] `prefixRange` 前缀查询（跨块扫描，供上层联想）
- [x] 协程外观 `MdictDictionary`（suspend + Dispatchers.IO + ensureActive 取消检查点）
- [x] 性能基准达标（§8.5，真实 60 万词条大词典实测）

---

## 12. 许可证合规

| 参考实现 | 许可 | 本库如何使用 |
|---|---|---|
| `writemdict`（fileformat.md + writemdict.py） | MIT | 参考格式规范；用其脚本生成测试词典 |
| `mdict-java` 核心包 `com.knziha.plod.dictionary.*` | **Apache 2.0** | 参考解析/解密算法实现；保留署名 |
| `mdict-java` builder / UI | GPL 3.0 | ❌ 不参考、不引用 |
| `js-mdict` v6.x | MIT | 参考解析主干、LZO 算法 |
| `js-mdict` v7+ / `lzo1x.ts` | AGPL / GPL-2.0 | 仅参考**算法思路**，独立 Kotlin 实现（算法不受版权保护） |
| `org.anarres.lzo` JAR | GPL/LGPL | ❌ 不引入 |

本库作为本项目一部分，随项目以 **AGPL-3.0-or-later** 发布。由于本项目本身即 copyleft，即便直接使用 GPL-3.0/AGPL 代码亦合规；选择「纯 Kotlin 独立实现」是出于**轻量、零外部依赖、Android 适配**的工程考量，而非法律强制。

> **法律声明（同 37 号文档附录 A）**：本库仅提供 MDX/MDD 格式**解析能力**，不提供任何词典数据，词库文件由用户自行导入。app 内不出现具体词典名称、不提供下载链接。

---

## 附录：四个参考实现速查

| 关注点 | 首选参考 | 文件 |
|---|---|---|
| 二进制格式规范 | writemdict | `fileformat.md` |
| 解析主干 / 版本分支 | js-mdict | `src/mdict-base.ts` |
| JVM 字节序 / Inflater 零拷贝 | mdict-java | `mdBase.java` |
| ripemd128 + fast_decrypt | mdict-java | `Utils/ripemd128.java`、`Utils/BU.java` |
| LZO1X 解压算法 | js-mdict / mdict-js | `lzo1x.ts`、`minilzo-decompress.js` |
| 极简嵌套结构理解 | mdict-js | `mdict-parser.js` |
| 测试词典生成 | writemdict | `writemdict.py`、`examples.py` |





