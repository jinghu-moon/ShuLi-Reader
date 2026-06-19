# 38 - 划词查词功能设计方案（V6）

> 编写时间：2026-06-18
> 版本：V6（整合技术审查 · 原型交互 · MDX 架构 · 词库生态 · 社区建议 · 性能优化 · 成熟产品对标 · 架构缺口修复）
> 原型参考：`docs/prototypes/dict-popup-prototype.html`
> 范围：划词触发 · 词典数据层（Stardict + MDX）· 查词弹窗 UI · 词库管理 · 释义渲染管线
> 原则：纯离线 · 零额外权限 · 与现有选区系统无缝衔接 · 不引入 WebView

---

## 1. 现状分析

### 1.1 已有选区基础设施

| 模块 | 文件 | 能力 |
|---|---|---|
| `CanvasTextSelection` | `core/reader/engine/selection/` | 长按命中行检测、选区矩形计算、`selectedRange` 状态管理 |
| `SelectionRange` | `core/reader/model/` | 值对象，携带 `chapterIndex`、`startPos`、`endPos`、`selectedText` |
| `CanvasTouchHandler` | `core/reader/engine/input/` | 长按手势触发选区 |
| 选区操作菜单 | `ReaderStrings` | 已有「复制」「加书签」「加笔记」三个操作入口 |

### 1.2 缺口

| 需求 | 现状 |
|---|---|
| 划词后即时查词 | ❌ 选区菜单仅有复制/书签/笔记 |
| 本地词典数据 | ❌ 无任何词典基础设施 |
| 查词结果展示 | ❌ 无 |
| 词库管理界面 | ❌ 无 |
| 查词历史 | ❌ 无 |

---

## 2. 词典格式选型

### 2.1 格式对比

| 格式 | 开放性 | 生态 | 解析复杂度 | 渲染需求 | 结论 |
|---|---|---|---|---|---|
| **Stardict** (.ifo/.idx/.dict/.dict.dz) | [公开规范](https://github.com/huzheng001/stardict-3/blob/master/dict/doc/StarDictFileFormat) | 开源词库丰富（CC-CEDICT 等） | 中 | AnnotatedString 足够 | ✅ Phase 1 主选 |
| **DSL** (.dsl/.dsl.dz) | ABBYY Lingvo 私有格式，有开源解析库 [dsl4j](https://codeberg.org/miurahr/dsl4j)（GPL 3.0+，AGPL 兼容） | GoldenDict-ng 核心格式，俄语/英语词典丰富 | 低（文本格式） | AnnotatedString 足够 | ✅ Phase 2 支持 |
| **MDX/MDD** (MDict) | 私有格式，有开源解析库 [mdict-java](https://github.com/KnIfER/mdict-java)（Apache 2.0 + GPL 3.0），也可参考 [mdict-js](https://github.com/fengdh/mdict-js)（MIT）自实现 | 中文词库质量和数量远超 Stardict | 高 | 需要 HTML+CSS 渲染管线 | ✅ Phase 2 支持 |
| SQLite 词典 | 无统一 schema | 零散 | 低 | — | ❌ 无标准 |
| 系统 `TextClassificationManager` | 系统 API | 依赖 OEM | 低 | — | ❌ 国内 ROM 不可用 |

**决定：Phase 1 以 Stardict 为主格式并内置 CC-CEDICT + 维基词典中文；Phase 2 同时增加 DSL 和 MDX/MDD 支持。**

#### DSL 格式优势

DSL 是 ABBYY Lingvo 的文本格式词典，GoldenDict-ng 将其作为核心支持格式之一。与 MDX 相比：

| 维度 | DSL | MDX |
|---|---|---|
| 格式 | 纯文本（UTF-16LE/UTF-8），可 git 管理 | 二进制 |
| 解析 | 文本行解析 + 标签处理，~200 行代码 | 二进制解析 + 块解压，~500 行代码 |
| 版权 | 格式本身无版权争议（ABBYY 未主张） | 私有格式 |
| 体积 | 压缩后 .dsl.dz 通常 < 5MB | .mdx + .mdd 可达数百 MB |
| 中文生态 | 较少 | 非常丰富 |

DSL 适合作为轻量补充格式，特别是俄语/英语/多语种词典场景。解析库 [dsl4j](https://codeberg.org/miurahr/dsl4j) 采用 GPL 3.0+，与本项目 AGPL-3.0 兼容。

### 2.2 MDX 格式的技术特征

MDX 是 MDict 的私有二进制格式。与 Stardict 的核心区别：

| 维度 | Stardict | MDX |
|---|---|---|
| 词条内容 | 纯文本或简单标记（`m`/`h`/`l`） | **HTML + CSS class**，可带复杂排版 |
| 配套资源 | 无 | `.mdd` 文件存 CSS、图片、音频 |
| 索引 | 简单排序 + 二分查找 | 红黑树 + 二分列表（更复杂） |
| 压缩 | dictzip（gzip 分块） | zlib 分块（v2 引擎） |
| 渲染 | `AnnotatedString` 足够 | **需要 HTML 解析 + CSS class 映射** |

**MDX 词库的渲染层代价：** 词条内容取出后是 HTML 字符串，其中大量使用 CSS class（`.def`、`.pinyin`、`.example` 等）驱动视觉层级。当前 `AnnotatedString.fromHtml()` 不支持 CSS class，不支持 `<div>` 内联样式，不支持 `<table>`，对 MDX 词库几乎无效。需要独立的 HTML+CSS 渲染管线。

### 2.3 MDX 词库生态（实际价值分析）

中文词典社区（FreeMdict 论坛等）流通的优质词库几乎清一色是 MDX 格式：

| 词典 | 词条/字头 | 对小说读者的价值 | 版权状态 |
|---|---|---|---|
| 现代汉语词典（第7版） | ~7万 | ⭐⭐⭐⭐⭐ 最常用，释义权威 | 商务印书馆版权，网上电子版为灰色地带 |
| 汉语大词典（全13卷） | ~37万 | ⭐⭐⭐⭐⭐ 覆盖最全，古词生词首选 | 版权不清，不建议引导下载 |
| 王力《古汉语字典》 | ~1.2万 | ⭐⭐⭐⭐ 古风/武侠类必备 | 版权状态不明 |
| 成语大词典 | ~3万 | ⭐⭐⭐⭐ 四字成语解析 | 灰色地带 |
| 辞海（第七版） | ~13万 | ⭐⭐⭐ 百科性强 | 灰色地带 |
| 国语辞典（教育部） | ~16万 | ⭐⭐⭐⭐ 即萌典数据，繁体版 | CC BY-ND 3.0 台湾 |

**这些 MDX 词库绝大多数是版权书籍的未授权电子化。** app 内不提供任何具体下载链接，仅在格式支持说明中标注「支持导入 MDX 格式词典文件（.mdx/.mdd）」，由用户自行解决来源。这与欧路词典、DictTango 等现有产品的做法一致。

**支持 MDX 的理由：** 不支持公众号内引导下载，但用户已经持有这些文件（从欧路词典、GoldenDict 等迁移），不支持 MDX 等于拒绝最有价值的词库。

---

## 3. Stardict 格式规范

> 规范来源：[StarDict-3 官方文档](https://github.com/huzheng001/stardict-3/blob/master/dict/doc/StarDictFileFormat)

### 3.1 文件组成

| 文件 | 必须 | 说明 |
|---|---|---|
| `.ifo` | ✅ | 纯文本元信息，第一行固定为 `StarDict's dict ifo file`，后续为 `key=value` 键值对 |
| `.idx` | ✅ | 二进制索引文件，按词头排序存储 |
| `.dict` 或 `.dict.dz` | ✅ | 释义正文数据文件（`.dict.dz` 为 dictzip 压缩版） |
| `.syn` | ❌ 可选 | 同义词索引文件 |

### 3.2 .ifo 关键字段

```
StarDict's dict ifo file
version=2.4.2              # 或 3.0.0（64 位偏移）
bookname=词典显示名称
wordcount=125031           # idx 中的词条数
idxfilesize=3145728        # idx 文件的字节大小
synwordcount=5000          # syn 文件中的同义词数（若存在 .syn）
sametypesequence=m         # 释义类型优化标志（见下文）
author=...
description=...
```

**`sametypesequence` 优化：** 当此字段存在时，`.dict` 文件中省略每条释义的类型标记字符和末尾字段的长度/null 终止符。解析器需通过 idx 记录的总字节数减去已知字段的长度来推算末尾字段的实际大小。**这是常见词库使用的优化，必须支持。**

### 3.3 .idx 二进制格式

每条记录连续存储：

```
[ word: UTF-8 字符串，以 \0 结尾，长度 < 256 字节 ]
[ word_data_offset: 32 位无符号整数，网络字节序（大端） ]
[ word_data_size: 32 位无符号整数，网络字节序（大端） ]
```

排序规则：大小写不敏感的 ASCII 比较 + 后续字节比较。

> **⚠️ 实现陷阱：** idx 是变长记录文件（word 长度不固定），不能按逻辑序号直接 seek 到字节偏移。正确做法是全量读入 `ByteArray` 后**顺序解析一次**，将所有词条解析为 `List<IdxEntry>`，再对该 List 做二分查找。

### 3.4 .dict 释义数据格式

释义内容由类型标记 + 数据交替组成：

| 标记 | 类型 | 数据格式 |
|---|---|---|
| `m` | 纯文本 | null 结尾字符串 |
| `l` | Pango 标记 | null 结尾字符串 |
| `g` | Pango 标记 | null 结尾字符串 |
| `t` | 英文音标 | null 结尾字符串 |
| `x` | XDXF 标记 | null 结尾字符串 |
| `y` | 中文音标 | null 结尾字符串 |
| `k` | KingSoft PowerWord 数据 | null 结尾字符串 |
| `w` | MediaWiki 标记 | null 结尾字符串 |
| `h` | HTML | null 结尾字符串 |
| `r` | 资源文件列表 | null 结尾字符串 |
| `W` | 媒体文件 | 前缀 4 字节长度 + 二进制数据 |
| `P` | 图片 | 前缀 4 字节长度 + 二进制数据 |
| `X` | 扩展类型 | 前缀 4 字节长度 + 二进制数据 |

### 3.5 .dict.dz（dictzip）压缩格式

> 参考：[dictzip 规范](https://man.archlinux.org/man/dictzip.1.en)，[ragzip 实现文档](https://github.com/ddeschenes-1/ragzip)

`.dict.dz` 使用与 gzip 相同的压缩算法，但通过在 gzip 扩展字段中嵌入块索引表来支持随机访问：

```
gzip 扩展字段结构（小端序 16 位整数）：
  [ 'R' (1B) ] [ 'A' (1B) ]    ← 双字节标识符
  [ extra_len (2B) ]            ← 扩展数据总长度
  [ version (2B) ]              ← 版本号（固定为 1）
  [ chunk_size (2B) ]           ← 原始块大小（通常 50–64KB）
  [ chunk_count (2B) ]          ← 块总数
  [ compressed_sizes[] ]        ← 每个块的压缩后字节数数组
```

**随机访问流程：**

1. 读取文件头部，解析扩展字段中的块索引表
2. 计算目标 offset 落在哪个原始块中：`chunk_index = offset / chunk_size`
3. 累加 `compressed_sizes[0..chunk_index-1]` 得到该块在文件中的起始字节位置
4. seek 到该位置，仅解压该块（若读取范围跨块边界则继续解压下一块）
5. 从解压数据中提取所需字节

**兼容性：** 标准 `gunzip` 可完整解压 `.dict.dz`；但不支持随机访问。

> **⚠️ 兼容性现实：** 大量流通的 Stardict 词库以 `.dict.dz` 形式分发。不支持此格式会导致用户导入后释义读取失败。Phase 1 即需支持。

### 3.6 .syn 同义词文件

```
每条记录：
  [ synonym_word: UTF-8 字符串，以 \0 结尾 ]
  [ original_word_index: 32 位无符号整数，网络字节序 ]
                    ↑ 指向 .idx 中第 N 个词条（从 0 开始计数）
```

排序规则与 `.idx` 相同。多个同义词可指向同一个原始词条。

查询时：先在 `.idx` 中查找，未命中则在 `.syn` 中查找同义词，命中后用 `original_word_index` 定位到 `.idx` 对应词条读取释义。

---

## 4. MDX 格式规范

> 参考实现：[fengdh/mdict-js](https://github.com/fengdh/mdict-js)（MIT 许可，纯 JavaScript，可作为 Kotlin 实现的算法参考）
>
> **本章为概要。MDX/MDD 解析引擎的完整实现规格（兼容性矩阵、解析管线、LZO/加密/IO/性能设计）已独立成文：[`38-mdx-mdd-parser-library-design.md`](./38-mdx-mdd-parser-library-design.md)。本文档的 `MdxParser`（§4.4、§7.2、§12）即由该库实现。**

### 4.1 文件组成

| 文件 | 必须 | 说明 |
|---|---|---|
| `.mdx` | ✅ | 词条索引 + 释义 HTML |
| `.mdd` | ❌ 可选 | 二进制资源（CSS 样式表、图片、音频） |

### 4.2 MDX 内部结构

```
Header（元信息，含标题、描述、编码、加密级别等）
  ↓
Key Block Index（块索引的索引，zlib 压缩）
  ↓  解压后得到每个 Key Block 的起始词和偏移量
Key Block（词条索引块，按词头排序，支持二分查找）
  ↓  每条记录：[ keyword: UTF-16/UTF-8 ] [ record_offset: Long ]
Record Block（词条内容，zlib 分块压缩 v2）
  ↓  按 offset 定位并解压后获得 HTML 字符串
```

### 4.3 MDD 资源文件

MDD 与 MDX 结构相同（Header + Key Block + Record Block），但存储的是二进制资源：

```
CSS 文件：entry://style.css      → 词典样式表
图片文件：entry://img/xxx.png    → 字形图、插图
音频文件：entry://sound/xxx.mp3  → 发音（Phase 3 考虑）
```

词条 HTML 中通过 `@@@LINK=` 或 `entry://` 引用这些资源。

### 4.4 MDX 解析器实现策略

**许可兼容性：** 本项目采用 AGPL-3.0-or-later 许可，与 mdict-java 的 GPL 3.0 部分完全兼容（AGPL-3.0 和 GPL-3.0 是 FSF 确认的兼容许可组合）。因此有两种可行方案：

| 方案 | 优点 | 缺点 |
|---|---|---|
| **A. 引入 mdict-java** | 成熟稳定，被多个 Android 词典 App 验证；代码量零 | 库体积 ~100KB；可能包含不需要的功能（RIPEMD128、LZO） |
| **B. 参考 mdict-js (MIT) 纯 Kotlin 自实现** | 仅实现需要的功能子集；更轻量；无外部依赖 | 需 ~400-600 行 Kotlin；需自行测试边界条件 |

**推荐方案 A：引入 mdict-java。** 理由：

1. AGPL-3.0 项目使用 GPL-3.0 库在法律上完全合规，无需额外工作
2. mdict-java 已被 GoldenDict、DictTango 等多个产品在生产环境验证
3. 自实现虽然更轻量，但 MDX 格式的边界条件多（v1/v2、UTF-8/UTF-16、加密级别等），测试成本高于引入现成库

**如果选择方案 B（自实现）作为备选：** 参考 [fengdh/mdict-js](https://github.com/fengdh/mdict-js)（MIT 许可）的算法，仅实现需要的功能子集：

1. **Header 解析**：读取文件头，解析 `Encoding`（UTF-8/UTF-16）、`Format`（v1.x/v2.x）、`KeyCaseSensitive` 等标志位
2. **Key Block Index 解压**：读取块索引的 zlib 压缩数据，解压后解析每个 Key Block 的起始词和文件偏移量
3. **Key Block 查找**：在块索引中二分查找目标词所在的块，然后在该块内二分查找精确词条
4. **Record Block 解压**：根据词条的 `record_offset` 定位并解压对应的 Record Block，提取 HTML 字符串

```kotlin
// core/dictionary/engine/MdxParser.kt

/**
 * MDX/MDD 解析器。
 *
 * 方案 A：封装 mdict-java 的 MdictReader
 * 方案 B：纯 Kotlin 实现，参考 mdict-js (MIT) 算法
 *
 * 仅实现本 app 需要的功能子集：
 * - Header 解析与校验
 * - Key Block Index 加载
 * - Key Block 二分查找
 * - Record Block 解压（zlib）
 */
class MdxParser(private val file: File) {

    private val header: MdxHeader
    private val keyBlockIndex: List<KeyBlockInfo>  // 仅块索引加载到内存

    /** 精确查找词条 */
    fun lookup(word: String): MdxEntry?

    /** 前缀范围查找 */
    fun prefixRange(prefix: String, limit: Int): List<MdxEntry>

    /** 读取指定 offset 的释义 HTML */
    fun readDefinition(offset: Long, size: Int): String
}
```

> **性能预期：** 无论方案 A 或 B，仅加载 Key Block Index 到内存（通常 < 1MB），词条查找时按需读取和解压单个 Record Block。单词典查找 < 10ms。

---

## 5. 词典存储与组织

```
app 私有目录/
  dict/
    builtin/
      cc-cedict.ifo          ← 内置，首次启动时从 assets 解压
      cc-cedict.idx
      cc-cedict.dict         ← 或按需解压的 .dict.dz
    stardict/                ← 用户导入的 Stardict 词库
      my-dict-1.ifo
      my-dict-1.idx
      my-dict-1.dict         ← 或 my-dict-1.dict.dz
      my-dict-1.syn          ← 可选同义词文件
      ...
    mdx/                     ← 用户导入的 MDX 词库
      modern-chinese.mdx
      modern-chinese.mdd     ← 可选资源文件
      ...
    cache/                   ← MDX Key Block Index 缓存
      {dict-id}-keyindex.bin
```

导入方式：SAF `ActivityResultContracts.OpenDocument`

- **Stardict：** 多选 `.ifo` 文件，自动检测同目录下 `.idx`/`.dict`/`.dict.dz`/`.syn` 并一并复制
- **MDX：** 多选 `.mdx` 文件，自动检测同目录下同名 `.mdd` 并一并复制

### 5.1 导入校验流程

**Stardict 校验：**

1. 三文件完整性：`.ifo` + `.idx` + (`.dict` 或 `.dict.dz`) 必须齐全
2. `.ifo` 字段校验：解析 `idxfilesize` 并与实际 `.idx` 文件大小比对，不一致则拒绝导入并提示文件可能损坏
3. `.syn` 可选：若存在则一并导入

**MDX 校验：**

1. `.mdx` 文件可独立使用（`.mdd` 可选）
2. 读取 MDX Header，验证格式版本号（支持 v1.x 和 v2.x）
3. 若存在同名 `.mdd`，一并导入

---

## 6. 数据模型

### 6.1 词典元数据

```kotlin
// core/dictionary/model/DictionaryMeta.kt

data class DictionaryMeta(
    val id: String,               // 文件名 hash，唯一标识
    val name: String,             // 显示名称
    val wordCount: Int,           // 词条数
    val synWordCount: Int,        // 同义词数（仅 Stardict）
    val author: String?,
    val description: String?,
    val langFrom: String,         // 源语言
    val langTo: String,           // 目标语言
    val format: DictFormat,       // STARDICT 或 MDX
    val sametypesequence: String?, // 仅 Stardict
    val isBuiltin: Boolean,
    val priority: Int,            // 查询优先级，数字越小越先查
    val hasDictDz: Boolean,       // 仅 Stardict
    val hasSyn: Boolean,          // 仅 Stardict
    val hasMdd: Boolean,          // 仅 MDX
    val files: DictFiles,
)

enum class DictFormat { STARDICT, MDX }

data class DictFiles(
    val primary: File,            // .ifo 或 .mdx
    val index: File?,             // .idx（仅 Stardict）
    val data: File?,              // .dict / .dict.dz（仅 Stardict）
    val syn: File?,               // .syn（仅 Stardict，可选）
    val mdd: File?,               // .mdd（仅 MDX，可选）
)
```

### 6.2 查询结果

```kotlin
// core/dictionary/model/DictEntry.kt

data class DictEntry(
    val word: String,             // 词头（原始大小写）
    val definition: String,       // 释义原文
    val definitionType: DefType,  // 释义类型
    val phonetic: String?,        // 音标/拼音
    val dictionaryId: String,
    val dictionaryName: String,
    val dictionaryFormat: DictFormat, // 来源格式（影响渲染路径）
    val isSynonymMatch: Boolean,  // 是否通过 .syn 同义词命中
)

enum class DefType {
    PLAIN_TEXT,     // Stardict m
    HTML,           // Stardict h / MDX 词条
    PANGO,          // Stardict l, g
    XDXF,           // Stardict x
    PHONETIC_ONLY,  // 仅有音标
}
```

### 6.3 Room 表

```kotlin
@Entity(tableName = "dict_meta")
data class DictMetaEntity(
    @PrimaryKey val id: String,        // 文件名 hash
    val name: String,
    val wordCount: Int,
    val synWordCount: Int,
    val author: String?,
    val description: String?,
    val langFrom: String,
    val langTo: String,
    val format: String,                // "stardict" / "mdx"
    val sametypesequence: String?,
    val isBuiltin: Boolean,
    val priority: Int,
    val enabled: Boolean,
    val hasDictDz: Boolean,
    val hasSyn: Boolean,
    val hasMdd: Boolean,
    val addedTime: Long,
)

@Entity(tableName = "dict_history")
data class DictHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val dictionaryId: String?,         // null = 所有词典均未命中
    val matched: Boolean,
    val queryTime: Long,               // epoch millis
    val isFavorite: Boolean = false,   // 收藏标记，Phase 4 生词本使用
    val contextSentence: String = "",  // 查词时的上下文句子，生词本使用
)

@Entity(tableName = "word_book")
data class WordBookEntry(
    @PrimaryKey val word: String,
    val phonetic: String = "",         // 拼音/音标
    val definition: String = "",       // 简要释义（取第一个义项）
    val bookId: Long = 0,              // 来源书籍 ID
    val chapterId: Long = 0,           // 来源章节 ID
    val contextSentence: String = "",  // 查词时的上下文句子
    val dictionaryId: String = "",     // 来源词典 ID
    val createTime: Long = 0,          // 加入时间
    val reviewCount: Int = 0,          // 复习次数
    val lastReviewTime: Long = 0,      // 最后复习时间
    val tags: String = "",             // 用户自定义标签（逗号分隔）
)
```

#### Anki 导出（Phase 3）

生词本支持导出为 Anki 兼容的 TSV 格式：

```
# 导出格式（tab 分隔）
word	phonetic	definition	contextSentence
踟蹰	chí chú	迟疑不前	暮色将至，踟蹰于归途……
```

用户可将导出的文件直接导入 Anki 进行间隔重复复习。这是阅读器用户的核心需求链路：阅读 → 查词 → 生词本 → Anki。

### 6.4 DAO

```kotlin
@Dao
interface DictMetaDao {
    @Query("SELECT * FROM dict_meta WHERE enabled = 1 ORDER BY priority ASC")
    fun getEnabledDicts(): Flow<List<DictMetaEntity>>

    @Query("SELECT * FROM dict_meta ORDER BY priority ASC")
    fun getAllDicts(): Flow<List<DictMetaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: DictMetaEntity)

    @Query("UPDATE dict_meta SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: String, priority: Int)

    @Query("UPDATE dict_meta SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM dict_meta WHERE id = :id AND isBuiltin = 0")
    suspend fun delete(id: String)
}

@Dao
interface DictHistoryDao {
    @Query("SELECT * FROM dict_history ORDER BY queryTime DESC LIMIT :limit")
    fun getRecent(limit: Int = 100): Flow<List<DictHistoryEntity>>

    @Insert
    suspend fun insert(entry: DictHistoryEntity)

    @Query("DELETE FROM dict_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM dict_history")
    suspend fun clearAll()
}
```

---

## 7. 词典查询引擎

```kotlin
// core/dictionary/engine/DictLookupEngine.kt

class DictLookupEngine(
    private val dictManager: DictionaryManager,
    private val wordNormalizer: WordNormalizer,
) {
    /**
     * 高频查词 LRU 缓存。
     * 用户阅读时往往反复查询某几个生词，缓存可消除重复 IO 开销。
     * key = 归一化后的词头，value = 查询结果列表。
     */
    private val cache = object : LinkedHashMap<String, List<DictEntry>>(320, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<DictEntry>>): Boolean = size > 256
    }

    /**
     * 查询取消支持。
     * 用户可能在查询结果返回前关闭 BottomSheet，
     * 此时应取消所有 pending 的查询以释放 IO 资源。
     */
    private var currentJob: Job? = null

    fun cancelPending() {
        currentJob?.cancel()
    }

    /**
     * 后台索引预加载。
     * 在阅读器 openBook 时启动低优先级任务，
     * 预加载所有启用词典的 idx/Key Block Index，
     * 消除首次查词的 200ms 延迟。
     */
    fun preloadIndexes(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO + Job().apply { /* 低优先级 */ }) {
            enabledDicts.forEach { dictManager.loadIndex(it) }
        }
    }

    /**
     * 精确查询：对归一化后的词头按 priority 升序依次查所有启用的词库，
     * 返回所有命中结果（空列表 = 未找到）。
     * 同时查 Stardict 和 MDX 词库，结果合并后按 priority 排序。
     * 命中缓存时直接返回，0ms 响应。
     */
    suspend fun lookup(word: String): List<DictEntry>

    /**
     * 智能查询（中文场景核心路径）：
     *
     * 1. 归一化：trim + 去标点 + 小写
     * 2. 精确匹配：全串直接查
     * 3. 前向最大匹配（中文）：若未命中且词头含 CJK 字符，
     *    从全串开始逐字截短尾部，直到命中词典词条
     *    例："踟蹰不前" → "踟蹰不" → "踟蹰" ✓
     * 4. 同义词 fallback：在 .syn 中查找（仅 Stardict）
     * 5. 单字 fallback（最终）：拆分为单字，各查一遍
     */
    suspend fun smartLookup(word: String): List<DictEntry>

    /**
     * 前缀查询：返回以 prefix 开头的最多 limit 个词条。
     */
    suspend fun prefixSearch(prefix: String, limit: Int = 10): List<DictEntry>
}
```

### 7.1 Stardict 索引加载

**推荐方案：MappedByteBuffer + offset table（所有词典统一使用）**

传统方案将 `.idx` 全量解析为 `List<IdxEntry>`，每条记录创建 Kotlin data class 对象。对 12.5 万条的 CC-CEDICT，约产生 12.5 万个对象（每个 ~50 bytes），总计 ~6MB GC 压力。多词典场景下更严重。

**优化方案：** 直接用 `MappedByteBuffer` 映射 `.idx` 文件，构建轻量 offset table：

```kotlin
// core/dictionary/engine/StardictIndex.kt

/**
 * 基于 mmap 的 idx 索引，零 Java 对象开销。
 *
 * 不创建 List<IdxEntry>，而是构建一个紧凑的 offset table：
 * wordOffsets: IntArray — 每条记录的 word 起始字节偏移
 * dataOffsets: LongArray — 每条记录的 .dict offset
 * dataSizes: IntArray — 每条记录的 .dict size
 *
 * 查找时直接在 MappedByteBuffer 中读取 word 字节做比较，
 * 无需创建 String 对象（仅在命中时提取）。
 */
class StardictIndex(
    private val buffer: MappedByteBuffer,
    private val wordOffsets: IntArray,
    private val dataOffsets: LongArray,
    private val dataSizes: IntArray,
) {
    /** 二分查找，直接在 buffer 中比较字节 */
    fun lookup(key: String): LookupResult?

    /** 前缀范围查找 */
    fun prefixRange(prefix: String, limit: Int): List<LookupResult>
}
```

**性能对比：**

| 方案 | 内存占用 | GC 压力 | 查找速度 |
|---|---|---|---|
| `List<IdxEntry>` | ~6MB（12.5万条） | 12.5 万对象 | < 1ms |
| **mmap + offset table** | ~1.5MB（3 个 primitive 数组） | 零对象 | < 1ms |

> **注意：** 二分查找直接在 `MappedByteBuffer` 上按字节比较，**不创建 String 对象**。仅在命中时提取 word 字节并解码为 String。这避免了 `entries[mid].word.lowercase()` 在每次比较时创建临时字符串的 GC 开销。

```kotlin
// 二分查找核心逻辑（零分配）
fun lookup(key: String): LookupResult? {
    val keyBytes = key.lowercase().toByteArray(Charsets.UTF_8)
    var lo = 0
    var hi = wordOffsets.size - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        // 直接在 buffer 中按字节比较，不创建 String
        val cmp = compareBytes(buffer, wordOffsets[mid], keyBytes)
        when {
            cmp < 0 -> lo = mid + 1
            cmp > 0 -> hi = mid - 1
            else -> return LookupResult(dataOffsets[mid], dataSizes[mid])
        }
    }
    return null
}
```

> **字节序注意：** idx 中的 `offset` 和 `size` 均为网络字节序（大端）。Kotlin 的 `RandomAccessFile.readInt()` 和 `ByteBuffer` 默认均为大端，保持默认即可，需在代码注释中显式标注 `// big-endian per Stardict spec`。

### 7.2 MDX 索引加载

通过纯 Kotlin `MdxParser` 加载，仅 Key Block Index 驻留内存：

```kotlin
// core/dictionary/engine/MdxParser.kt

class MdxParser(private val file: File) {
    private val keyBlockIndex: List<KeyBlockInfo>  // 仅块索引加载到内存（通常 < 1MB）

    fun lookup(key: String): MdxEntry?
    fun prefixRange(prefix: String, limit: Int): List<MdxEntry>
    fun readDefinition(offset: Long, size: Int): String
}
```

#### MDX 索引文件缓存

大型 MDX 词典（如现代汉语词典 7 万条）的 Key Block Index 解压可能耗时较长。首次加载后将解压后的索引缓存到本地文件，后续直接 mmap：

```
filesDir/dict/cache/
  {dict-id}-keyindex.bin    ← 解压后的 Key Block Index 二进制缓存
```

加载流程：缓存存在且文件 hash 未变 → 直接 mmap 缓存文件（< 10ms）；否则解压并写入缓存（首次 < 300ms）。加载时显示进度指示（「正在加载词典索引…」）。

### 7.3 索引内存跟踪与 LRU 卸载

mmap + offset table 方案将每本词典的内存占用从 ~6MB 降至 ~1.5MB（3 个 primitive 数组），GC 压力接近零。但仍需跟踪总量并在超限时卸载：

```kotlin
class DictionaryManager {
    /** 跟踪所有已加载索引的总内存占用 */
    private var totalIndexMemoryMb = 0

    /** 索引 LRU 卸载策略：
     *  超过 64MB 上限时，卸载最久未查询的词典索引。
     *  下次查询该词典时重新加载（延迟 < 200ms）。*/
    private val indexLru = object : LinkedHashMap<String, DictIndex>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DictIndex>): Boolean {
            return totalIndexMemoryMb > 64
        }
    }

    /** 每本词典约 1.5MB，8 本词典约 12MB，远低于 64MB 上限 */
    fun loadIndex(meta: DictionaryMeta): DictIndex
}
```

### 7.4 多词典并发查询与超时

```kotlin
class DictLookupEngine {
    /**
     * 多词典并发查询策略：
     * - 使用 Dispatchers.IO + async 并发查询所有启用词典
     * - 分层超时：首次查询 300ms（MDX 需要解压索引），热路径 50ms（索引已加载）
     * - 超时则跳过该词典
     * - 结果按 priority 排序后返回
     *
     * 首次查词体验：BottomSheet 弹出时显示骨架屏/loading 动画（原型已有 ldg 动画），
     * 覆盖首次索引加载延迟。preloadIndexes(openBook) 确保大多数情况下索引已预热。
     */
    private val isFirstQuery = AtomicBoolean(true)

    suspend fun lookup(word: String): List<DictEntry> = coroutineScope {
        val timeoutMs = if (isFirstQuery.compareAndSet(true, false)) 300L else 50L
        val deferreds = enabledDicts.map { dict ->
            async(Dispatchers.IO) {
                withTimeoutOrNull(timeoutMs) {
                    querySingleDict(word, dict)
                }
            }
        }
        deferreds.awaitAll().filterNotNull().flatten()
            .sortedBy { it.dictionaryPriority }
    }
}
```

---

## 8. 释义渲染管线

### 8.0 统一渲染接口

三条渲染路径（Stardict / MDX / CC-CEDICT）通过统一接口抽象。引入 **DictNode AST** 中间层，将 HTML 解析与 UI 渲染解耦：

```
HTML / 纯文本 / CC-CEDICT 原始格式
  ↓ 解析
DictNode AST（平台无关的文档树）
  ↓ 渲染
Compose UI
```

```kotlin
// core/dictionary/render/DictNode.kt

sealed interface DictNode {
    data class Text(val content: String, val style: TextStyle = TextStyle.Plain) : DictNode
    data class Paragraph(val children: List<DictNode>) : DictNode
    data class ListBlock(val ordered: Boolean, val items: List<List<DictNode>>) : DictNode
    data class Image(val path: String, val alt: String = "") : DictNode
    data class Table(val rows: List<List<List<DictNode>>>) : DictNode
    data class EntryRef(val word: String, val children: List<DictNode>) : DictNode // entry:// 链接
    data class Phonetic(val text: String) : DictNode
    data class Root(val children: List<DictNode>) : DictNode
}

enum class TextStyle { Plain, Bold, Italic, BoldItalic }

interface DictRenderer {
    @Composable
    fun Render(entry: DictEntry)
}

/** HTML → DictNode AST → Compose */
class StardictDictRenderer : DictRenderer { ... }
class MdxDictRenderer(private val resolver: DictStyleResolver) : DictRenderer { ... }
class CedictDictRenderer : DictRenderer { ... }
```

**AST 中间层的价值：**
- 未来可扩展到 Desktop / Web 端，共用同一套解析逻辑
- MDX 的混乱 HTML 先规整为标准 AST，再统一渲染，避免直接在 Compose 中处理边界条件
- 表格、图片等复杂元素降级策略在 AST 层决定，而非在 Compose 层

### 8.1 渲染架构总览

Stardict 和 MDX 的释义内容格式不同，需要两条渲染路径，但共享同一个 BottomSheet 容器：

```
DictEntry.definition
  │
  ├─ format == STARDICT
  │    ├─ DefType.PLAIN_TEXT  → Text() 直接显示
  │    ├─ DefType.HTML        → DictHtmlRenderer (Jsoup + AnnotatedString)
  │    ├─ DefType.PANGO       → 提取纯文本 → Text()
  │    └─ DefType.XDXF        → 提取纯文本 → Text()
  │
  ├─ format == MDX
  │    └─ DefType.HTML        → DictMdxRenderer (Jsoup + CSS Resolver + Compose)
  │
  └─ CC-CEDICT 内置
       └─ CedictEntryParser   → 专项渲染（繁简分离、拼音声调、义项列表）
```

### 8.2 不用 WebView 的理由

| 方案 | 内存占用 | CSS class 支持 | 适用性 |
|---|---|---|---|
| WebView | 30-50MB/实例 | ✅ 完整 | ❌ 在阅读器 BottomSheet 中代价过高 |
| `AnnotatedString.fromHtml()` | 极低 | ❌ 不支持 | ❌ MDX 词条完全靠 CSS class 驱动 |
| **Jsoup + 自定义渲染器** | ~1-3ms/词条 | ✅ 子集（color/weight/style/size） | ✅ 轻量、可控 |

### 8.3 Stardict HTML 渲染

```kotlin
// core/dictionary/render/DictHtmlRenderer.kt

/**
 * Stardict HTML 释义的轻量渲染。
 * 使用 Jsoup 解析 HTML，输出 AnnotatedString。
 */
object DictHtmlRenderer {
    fun render(html: String): AnnotatedString {
        val doc = Jsoup.parse(html)
        // 支持：<b>, <i>, <em>, <strong>, <br>, <p>, <span>, <font color>
        // 忽略：<table>, <img>, <script>, <style>
        return buildAnnotatedString {
            traverseDom(doc.body())
        }
    }
}
```

### 8.4 MDX HTML+CSS 渲染管线

MDX 词条需要三级处理：CSS 解析 → HTML 解析 → Compose 布局。

#### 8.4.1 DictStyleResolver（CSS class → SpanStyle 映射）

```kotlin
// core/dictionary/render/DictStyleResolver.kt

/**
 * 从 MDD 中提取 CSS 文本，解析为 className → SpanStyle 映射表。
 *
 * 词库加载时一次性执行，结果缓存在 DictionaryManager 中。
 * 仅需处理常见 CSS 属性子集：
 *   color, background-color, font-weight, font-style,
 *   font-size, text-decoration, font-family
 */
class DictStyleResolver(cssText: String) {
    private val styleMap: Map<String, SpanStyle> = parseCss(cssText)

    fun resolve(className: String): SpanStyle =
        styleMap[className] ?: SpanStyle.Default

    private fun parseCss(css: String): Map<String, SpanStyle> {
        // 正则或简单字符串解析，不需要完整 CSS 引擎
        // 格式：.className { property: value; ... }
    }
}
```

#### 8.4.2 DictMdxRenderer（HTML DOM → Compose 布局）

```kotlin
// core/dictionary/render/DictMdxRenderer.kt

/**
 * MDX 词条 HTML 的 Compose 渲染器。
 *
 * 遍历 Jsoup DOM 树，结合 DictStyleResolver 解析 CSS class，
 * 输出 Compose 布局组件。
 *
 * 性能预期：Jsoup 解析 + DOM 遍历 + 布局构建，单词条 1-3ms。
 */
object DictMdxRenderer {

    fun render(
        html: String,
        resolver: DictStyleResolver,
    ): @Composable () -> Unit {
        val doc = Jsoup.parse(html)
        return { RenderNode(doc.body(), resolver) }
    }
}

@Composable
fun RenderNode(node: Node, resolver: DictStyleResolver) {
    when {
        node is TextNode -> {
            Text(node.text(), style = resolver.resolveParent(node))
        }
        node is Element -> when (node.tagName().lowercase()) {
            "div", "p" -> {
                Column {
                    node.childNodes().forEach { RenderNode(it, resolver) }
                }
            }
            "span", "b", "i", "em", "strong" -> {
                InlineStyledText(node, resolver)
            }
            "ol" -> OrderedList(node, resolver)
            "ul" -> UnorderedList(node, resolver)
            "br" -> Spacer(modifier = Modifier.height(4.dp))
            "table" -> TableFallback(node) // 降级为纯文本
            "img" -> ImagePlaceholder(node) // Phase 3: MDD 资源解包后显示
            "a" -> {
                // 词典内交叉引用，Phase 3 可支持跳转
                InlineStyledText(node, resolver)
            }
            else -> {
                // 未知标签：递归渲染子节点
                node.childNodes().forEach { RenderNode(it, resolver) }
            }
        }
    }
}

/**
 * 表格降级：提取纯文本，用等宽字体显示。
 * 不值得为 5-10% 的复杂词条引入完整表格布局。
 */
@Composable
fun TableFallback(table: Element) {
    Text(
        text = table.text(),
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
}
```

#### 8.4.3 MDD 资源加载

```kotlin
// core/dictionary/engine/MddResourceLoader.kt

class MddResourceLoader(private val mddFile: File) {

    /**
     * 首次加载时提取 CSS 文本。
     * 从 MDD 中查找 *.css 资源并读取内容。
     */
    suspend fun loadCss(): String?

    /**
     * Phase 3：按 entry:// 路径提取二进制资源（图片等）。
     */
    suspend fun loadResource(path: String): ByteArray?
}
```

### 8.4.4 MDX 渲染期望值管理

MDX 词库的 CSS 千奇百怪（绝对定位、负 margin、`<table>` 排版、web 字体），`DictStyleResolver` 仅处理常见子集会导致部分词库视觉错乱。

**应对措施：**

1. **纯文本模式降级开关：** 在词典管理界面为每本 MDX 词典提供「纯文本模式」开关。开启后忽略所有 CSS/HTML 排版，仅提取纯文本展示。适用于排版严重错乱的词库。

2. **暗色模式颜色覆盖：** MDX 词条 CSS 可能写死浅色背景/深色文字（如 `background: white; color: black`），在暗色模式下变成「黑底黑字」不可读。提供选项「忽略词库自带颜色」：开启后所有文字颜色强制使用 `MaterialTheme.colorScheme.onSurface`，背景使用 `surface`。

3. **用户提示：** 首次导入 MDX 词库时显示提示「部分词库排版可能在暗色模式下异常，可在词典设置中开启纯文本模式」。

### 8.5 CC-CEDICT 专项渲染

CC-CEDICT 的原始条目格式为：

```
踟蹰 踟躕 [chi2 chu2] /to hesitate/to waver/
```

直接裸显体验差，需做专项解析：

1. **繁简字头分离：** `踟蹰 踟躕` → 简体「踟蹰」+ 繁体「踟躕」，UI 并排显示或仅展示简体
2. **拼音声调转换：** `chi2 chu2` → `chí chú`（数字 1-4 转为 Unicode 声调符号，5/0 为轻声）
3. **义项分割：** `/sense1/sense2/` → 换行或编号列表（单义项不显示序号）

```kotlin
// core/dictionary/engine/CedictEntryParser.kt

data class CedictParsed(
    val simplified: String,     // 简体词头
    val traditional: String,    // 繁体词头
    val pinyin: String,         // 带声调拼音
    val senses: List<String>,   // 义项列表
)

object CedictEntryParser {
    fun parse(raw: String): CedictParsed

    /**
     * 拼音数字声调转为 Unicode 声调符号。
     *
     * 规则（韵母优先级 a > e > ou > 其他韵母最后一个元音）：
     *   1 → ā, 2 → á, 3 → ǎ, 4 → à, 5/0 → 轻声不标
     *
     * 例：chi2 → chí, nv3 → nǚ, lve4 → lüè
     */
    fun toneNumberToSymbol(pinyin: String): String

    fun splitHead(wordField: String): Pair<String, String>  // 繁简分离
    fun splitSenses(defField: String): List<String>          // 义项分割
}
```

---

## 9. 词典来源与生态

### 9.1 内置词典

Phase 1 内置两个词典，**维基词典中文版为主力**（中→中），CC-CEDICT 为补充（中→英）：

#### A. 维基词典中文版（中→中主力，首要内置）

| 属性 | 值 |
|---|---|
| 来源 | [Vuizur/Wiktionary-Dictionaries](https://github.com/Vuizur/Wiktionary-Dictionaries) |
| 协议 | CC BY-SA 3.0/4.0（维基数据） |
| 内容 | 现代汉语、文言文、成语，覆盖小说高频生词 |
| 方向 | 中文 → 中文 |
| 格式 | Stardict |

> **为什么维基词典做主力：** 中文小说读者查「踟蹰」「踯躅」「迤逦」时需要中文释义而非英文翻译。维基词典中文版的 CC BY-SA 协议允许合法打包分发，是目前唯一可同时满足"中→中 + 版权干净 + 规模足够"三个条件的词典。
>
> **质量验证要求：** 内置前须用小说高频生僻词清单（50 词，含踟蹰、踯躅、迤逦、霁色、蹀躞等）做覆盖率与释义质量抽测。若命中率/质量不达标，则改用萌典转换脚本方案（用户自转自用）作为首选引导。

#### B. CC-CEDICT（中→英补充）

| 属性 | 值 |
|---|---|
| 来源 | [CC-CEDICT](https://www.mdbg.net/chinese/dictionary?page=cedict) |
| 协议 | **CC BY-SA 3.0**（上游权威来源 [cc-cedict.org/wiki/](https://cc-cedict.org/wiki/) 确认为 3.0） |
| 词量 | 125,031 条（截至 2026-06-17） |
| 方向 | 中文 → 英文（拼音 + 英文释义） |
| 格式 | Stardict |
| APK 占用 | Stardict 包 tar.bz2 约 6.8MB（[来源](https://simonwiles.net/projects/cc-cedict/)），解压后 .ifo + .idx + .dict 约 20-25MB |

> **CC-CEDICT 的定位：** 作为中→英补充词典，而非首要内置。用户查中文生词时优先命中维基词典的中文释义，CC-CEDICT 的英文释义作为第二 Tab 展示。

#### C. 成语词典（Phase 2 考虑）

小说用户使用成语的频率极高（踌躇满志、沆瀣一气、不虞之誉等）。可在 Phase 2 内置一个小型成语词典（约 3 万条，Stardict 格式，CC 协议），作为中→中释义的补充。

#### APK 体积优化策略

CC-CEDICT 的 Stardict 文件解压后约 20-25MB，直接打包会显著增加 APK 体积。优化方案：

1. **APK 中仅打包 `.ifo` + `.idx`**（约 3-5MB，压缩后更小）
2. **`.dict.dz` 存储在 assets 中**，首次查词时按需解压到 `filesDir/dict/builtin/`
3. 解压使用 `SharedPreferences` 记录版本号，保证幂等性

这样 APK 增量约 4-6MB，而非 20-25MB。

#### CC-CEDICT 作为兜底的局限性

CC-CEDICT 是中→英词典。本 app 的用户主要是中文母语读者，查生词通常期望看到中文释义。这是 MVP 的合理妥协，避免 APK 体积失控。

### 9.2 可推荐的合法词库来源

#### 中→中词典

| 词典 | 词条 | 协议 | 合规分发方式 | 分发风险 |
|---|---|---|---|---|
| **CC-CEDICT**（内置） | 12.5万 | CC BY-SA 3.0 | 直接打包进 APK | 无 |
| **萌典**（教育部《重编国语辞典修订本》） | 16万 | CC BY-ND 3.0 台湾 | **不可打包转换文件**。ND 条款禁止分发衍生作品。用户自行转换供个人使用属于合理使用 | 不可打包 |
| **中文维基词典** | 海量（含现代汉语、成语、文言单字） | CC BY-SA 3.0/4.0 | 社区有现成 Stardict 离线包（[Vuizur/Wiktionary-Dictionaries](https://github.com/Vuizur/Wiktionary-Dictionaries)），**可考虑未来内置或提供直接下载** | 低（CC BY-SA 允许再分发） |
| **漢語大詞典** Stardict 版 | 37万 | 来源不明 | 不引导下载。仅标注「支持导入 Stardict 格式」 | 灰色地带 |

> **Wiktionary 作为萌典的平替：** 萌典的 CC BY-ND 条款给小白用户带来转换门槛。中文维基词典采用 CC BY-SA，允许再分发，且社区有现成的、定期更新的 Stardict 格式离线包。它不仅包含海量现代汉语和成语，还包含丰富的文言文单字解析。**建议在空态引导中同时推荐萌典（权威但需转换）和维基词典（开箱即用但需下载）。**

#### 多语种词典

| 来源 | 说明 | 协议 |
|---|---|---|
| [FreeDict](https://freedict.org/) | 提供上百种完全开源的 Stardict 格式双语词典（如中→印尼、中→拉丁、中→挪威等） | GPL（词典数据），Stardict 格式兼容 |

> FreeDict 对纯中文阅读场景价值有限，但对多语种阅读者是有价值的合法词库来源。在帮助文档中提及即可。

### 9.3 萌典转换脚本

**建议由项目维护一个开源的 moedict → Stardict 转换工具：**

```
tools/moedict2stardict/
  convert.py             ← 转换脚本
  README.md              ← 使用说明
```

用户运行脚本生成 Stardict 文件，再导入 app。这条路径不触碰 ND 条款（脚本是工具，生成的文件用户自用不分发），也能让用户用上最好的中→中词典。

### 9.4 大体积词库导入（Wi-Fi 传书扩展，Phase 3）

高质量 MDX 词库（含 `.mdd` 音频/图片资源）体积通常在 100MB 到 1GB+。Android SAF 在处理大文件复制时容易出现 OOM 或耗时极长。建议在 Phase 3 复用阅读器现有的 Wi-Fi 传书服务，增加「局域网词典传输」入口，直接将文件流写入私有目录，绕过 SAF 的大文件限制。

### 9.5 词库管理界面推荐策略

| 展示 | 内容 |
|---|---|
| 内置 | CC-CEDICT，直接可用 |
| 引导导入 | 萌典（权威中→中，需转换）+ 中文维基词典（开箱即用，需下载） |
| 格式支持 | 标注「支持 Stardict (.ifo) 和 MDX (.mdx) 格式」 |
| 多语种 | 提及 FreeDict 开源双语词典 |

**空态文案（仅有内置词典时）：**

> 内置 CC-CEDICT（中→英）。如需中文释义，推荐：
> · 中文维基词典（Stardict 格式，可直接导入）
> · 萌典（教育部国语辞典，需转换工具）
> 支持 Stardict (.ifo) 和 MDX (.mdx) 格式。

---

## 10. 交互设计

### 10.1 触发流程

```
长按文字
  └─ CanvasTextSelection.selectLineAt() 命中行
      └─ 选区高亮 + 弹出操作菜单（方向自适应，见 10.2.1）
          └─ 菜单项：[ 查词 ] ┃ [ 复制 ] [ 书签 ] [ 笔记 ]
              └─ 点击「查词」
                  └─ 提取 selectedText + 上下文句子（见 15.1）
                  └─ DictLookupEngine.smartLookup()
                  └─ 防遮挡滚动（见 10.3.1）
                  └─ 显示查词结果 BottomSheet
```

### 10.1.1 ⚠️ 关键架构缺口：字符级命中（Phase 1 第一项工作）

> **这是整个查词功能成立的地基，必须在其他 UI 工作之前解决。**

**问题本质：** 原型 HTML 中每个生词是独立的 `<span class="w">`，点击即查该词。但实际代码 `CanvasTextSelection.selectLineAt()` 的命中粒度是**整行**（从 `line.startCharOffset` 到 `line.endCharOffset`），不是词级。

如果用户长按「兰子出了院门，踟蹰于石径之上」，`selectLineAt` 返回整行文本。此时「前向最大匹配」无从下手——对整行做 FMM 会从行首「兰」开始，永远查不到用户想要的「踟蹰」。

**解决方案：字符级命中 + 单词自动选区**

```kotlin
// core/reader/engine/selection/CanvasTextSelection.kt 扩展

/**
 * 字符级命中：根据长按坐标定位到行内具体字符偏移。
 *
 * 实现思路：
 * 1. selectLineAt() 命中行后，获取行的 startCharOffset 和每字 x 坐标
 * 2. 用按下的 x 坐标在字符 x 坐标数组中二分查找，定位到具体字符
 * 3. 以该字符为起点，向两侧扩展找到词边界（CJK 连续字 + 标点边界）
 * 4. 高亮该词（而非整行），返回该词的 SelectionRange
 */
fun selectWordAt(
    x: Float,
    y: Float,
    page: TextPage,
    content: CharSequence,
    viewWidth: Float,
    paint: Paint,
): SelectionRange? {
    // Step 1: 命中行（复用现有 selectLineAt 逻辑）
    val line = hitLine(x, y, page, viewWidth) ?: return null

    // Step 2: 在行内定位字符偏移
    // 利用 Paint.measureText() 逐字累加宽度，二分找到按下的字符
    val charIndex = findCharAtX(x, line, content, paint)

    // Step 3: 以该字符为起点，扩展词边界
    // 简单规则：CJK 连续字符为一个词，标点/空格/换行为边界
    val wordStart = findWordBoundaryStart(content, charIndex)
    val wordEnd = findWordBoundaryEnd(content, charIndex)

    // Step 4: 返回词级 SelectionRange
    val selectedText = content.substring(wordStart, wordEnd)
    return SelectionRange(
        chapterIndex = page.chapterIndex,
        startPos = wordStart,
        endPos = wordEnd,
        selectedText = selectedText,
    )
}
```

**词边界规则（Phase 1 简单版，不需要 jieba）：**

| 字符类型 | 边界行为 |
|---|---|
| CJK 字符（`\u4E00-\u9FFF`） | 连续 CJK 字符视为一个词 |
| 标点（`，。！？、；：""''`） | 作为词边界 |
| 空格/换行 | 作为词边界 |
| 英文字母 | 连续字母视为一个词 |
| 数字 | 连续数字视为一个词 |

**与现有系统的关系：** `selectLineAt()` 保留用于书签/笔记的整行选区。新增 `selectWordAt()` 专用于查词触发。选区菜单中「查词」按钮使用 `selectWordAt()` 的结果，「复制/书签/笔记」仍使用 `selectLineAt()` 的整行结果。

### 10.1.2 UI 路线决策：固定底栏 vs 浮动气泡

**现状：** 项目已有 `ReaderSelectionActionBar`（固定底部居中的 Compose 组件）。原型演示的是跟随选区的浮动气泡（带向下小三角）。

**决策：**

| 阶段 | 方案 | 理由 |
|---|---|---|
| **Phase 1** | **保留固定底栏**，仅新增「查词」按钮 | 成本极低，不引入定位逻辑，先让查词功能跑通 |
| Phase 2 | 改造为浮动气泡 | 体验升级，需要重写定位逻辑（方向自适应 + 水平边界 clamp） |

文档中 10.2.1 的浮动气泡定位代码（方向自适应 + 水平 clamp）作为 Phase 2 参考保留。

### 10.1.3 BottomSheet 实现选型

**决策：使用 Material 3 `ModalBottomSheet` + 自定义 detents。**

不使用 View 体系的 `BottomSheetBehavior`（项目是纯 Compose，混用 View 体系增加复杂度）。M3 的 `SheetState` 支持自定义 `detents`（锚点），可实现 36%/55%/90% 三锚点行为。

```kotlin
val sheetState = rememberModalBottomSheetState(
    skipPartiallyExpanded = false,
    // 自定义 detents 实现三锚点
)
```

### 10.2 选区菜单扩展

当前选区菜单有 3 个操作（复制 / 书签 / 笔记），新增「查词」作为第 4 个。

**布局（基于原型设计）：**

```
┌──────────────────────────────────┐
│  [📖]   ┃   [⎘]   [🔖]   [✎]  │
│  查词   ┃   复制   书签   笔记  │
└──────────────────────────────────┘
         ↑ 选区高亮区域
```

**设计决策：**

- 「查词」与其余操作之间用 **竖向分隔线** 隔开，强化「查词」是主操作的层级（原型已实现）
- **小屏适配：** 360dp 宽度的手机上采用图标 + tooltip 模式，按钮仅显示图标，长按显示文字标签（无障碍描述始终设置）
- 图标：查词 `Icons.Filled.MenuBook`，复制 `Icons.Filled.ContentCopy`，书签 `Icons.Filled.BookmarkBorder`，笔记 `Icons.Filled.EditNote`

#### 10.2.1 选区菜单方向自适应

当选中词位于屏幕顶端第一行时，菜单在词上方弹出会被系统状态栏遮挡。需在定位计算时增加边界判断：

```kotlin
// 垂直方向：上方空间不足时改为下方弹出
val spaceAbove = selectionY - statusBarHeight
val spaceBelow = screenHeight - selectionY - selectionHeight

if (spaceAbove < menuHeight + margin && spaceBelow > spaceAbove) {
    // 改为在词的下方弹出，气泡小三角方向反转（朝上）
} else {
    // 默认：在词的上方弹出
}

// 水平方向：选中词在屏幕最左/右时，菜单可能超出屏幕边界
val menuHalfWidth = menuWidth / 2
val clampedX = selectionCenterX.coerceIn(
    menuHalfWidth + margin,
    screenWidth - menuHalfWidth - margin
)
```

### 10.3 查词结果弹窗（BottomSheet）

> 原型参考：`docs/prototypes/dict-popup-prototype.html`

采用 `BottomSheet` 形式，从屏幕底部滑出，不遮挡阅读上下文。

**弹窗参数（基于原型验证）：**

| 参数 | 值 | 说明 |
|---|---|---|
| 默认高度 | **55%** 屏幕高度 | 40% 在竖屏仅 ~300dp，CC-CEDICT 双义项 + 第二本词典刚好超出。55% 让单本词典一屏收完 |
| 最大高度 | 90% | 保留上下文可见区域 |
| 最小高度 | 200dp | 防止过小无法阅读 |
| 拖拽锚点 | 36% / 55% / 90% | 松手时吸附最近锚点 |
| 关闭方式 | 下滑超过 22% 阈值 / 点击遮罩 / 关闭按钮 | — |
| 双击把手 | 在 55% ↔ 90% 间切换 | 快速展开/收起 |

#### 10.3.1 防遮挡设计（智能滚动）

如果用户选中的词刚好在屏幕下半部分，BottomSheet 弹出后会完全盖住目标词及其上下文。

**解决方案：** 在触发查词时，获取选区在屏幕上的 Y 坐标。如果选中词在下半屏（`y > screenHeight * 0.45`），BottomSheet 弹出前自动将 Canvas 向上滚动一定偏移量，确保「选中词 + 前后一句话」始终暴露在可视区域内：

```kotlin
val selectionScreenY = /* 选区在屏幕上的 Y 坐标 */
val threshold = screenHeight * 0.45f  // BottomSheet 55% 时的上边界
if (selectionScreenY > threshold) {
    val scrollOffset = selectionScreenY - threshold + lineHeight * 2
    canvas.smoothScrollBy(0, scrollOffset.toInt())
}
```

#### 10.3.2 嵌套滑动展开（Nested Scrolling）

"双击把手"对普通用户缺乏可发现性。利用 Android 标准 `BottomSheetBehavior` 的嵌套滑动行为：当用户向上滑动释义列表且面板处于 55% 时，先将整个 BottomSheet 拉升到 90%，然后再继续滚动列表内容。这比双击更符合用户直觉。

#### 10.3.3 词头标题自适应字号

如果用户误操作划选了一个长达十几字的短句，固定 28px 的标题字号会导致文字截断。

**解决方案：** 当词头字数超过 6 字时，动态缩小字号：

```kotlin
val titleFontSize = when {
    word.length <= 4 -> 28.sp
    word.length <= 6 -> 24.sp
    word.length <= 10 -> 20.sp
    else -> 17.sp
}
```

同时取消 `maxLines = 1`，允许长词头换行显示（最多 2 行）。

#### 10.3.4 Compose 性能优化

BottomSheet 释义区域使用 `LazyColumn` 而非 `Column`，避免一次性构建所有词条的组件树（特别是多词典结果时）。MDX 复杂词条使用 `DictRenderer.Render()` 按需构建。

#### 10.3.5 多词典 Tab 切换（HorizontalPager）

多词典结果使用 `HorizontalPager` + `ScrollableTabRow` 联动实现滑动切换：

```kotlin
val pagerState = rememberPagerState(pageCount = { entries.size })

ScrollableTabRow(
    selectedTabIndex = pagerState.currentPage,
    indicator = { /* info 色底部指示器 */ },
) {
    entries.forEachIndexed { index, entry ->
        Tab(
            selected = pagerState.currentPage == index,
            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
            text = { Text(entry.dictionaryName) },
        )
    }
}

HorizontalPager(state = pagerState) { page ->
    LazyColumn {
        item { DictRenderer.Render(entries[page]) }
    }
}
```

**Tab 切换状态保持：** 每个 Tab 页的滚动位置独立保持。用户从 Tab A 滚到第 3 个义项，切换到 Tab B 再切回来，仍在第 3 个义项。通过为每个 page 维护独立的 `LazyListState` 实现。

#### 10.3.6 选区扩展（拖动把手）

当前设计仅支持长按选中一个词（`CanvasTextSelection.selectLineAt` 命中整行）。用户经常需要选中 2-4 个词的短语来查。

**Phase 2 扩展方案：** 长按选中后，选区两端显示拖动把手（类似系统文本选择），允许用户扩展/缩小选区范围：

```kotlin
// CanvasTextSelection 扩展
var selectionStart: Int    // 选区起始字符偏移
var selectionEnd: Int      // 选区结束字符偏移

/** 拖动左把手调整起始位置 */
fun dragStartHandle(x: Float, y: Float)

/** 拖动右把手调整结束位置 */
fun dragEndHandle(x: Float, y: Float)
```

此功能需要扩展 `CanvasTextSelection` 基础设施，在 Phase 2 实现。Phase 1 的「前向最大匹配」作为过渡方案。

#### 10.3.7 复制释义按钮

在查词结果弹窗的 footer 中，除「生词本」和「历史」外，增加「复制释义」按钮（仅在释义显示时可见）：

```
[＋ 生词本]     [⎘ 复制释义]     [📋 历史]
```

用户可能需要将释义粘贴到其他 App（笔记、聊天），提供一键复制。

**可编辑搜索框：**

弹窗顶部的词头区域默认为**只读大字展示**（不获焦、不弹键盘），点击后进入编辑态（`TextField` 获焦 + 弹出软键盘）。用户可在不关闭弹窗的情况下连续查词（踟蹰 → 踯躅 → 踌躇）。编辑后按回车或失去焦点时触发新查询。

> **为什么默认不获焦：** BottomSheet 弹出时若 TextField 自动获焦会强行弹起软键盘，遮挡释义内容，破坏「划词→看释义」的沉浸感。

**上下文释义展示：**

如果 `LookupWord` 携带了 `contextSentence`，在弹窗标题下方显示：

```
来自：
"暮色将至，踯躅于归途……"
```

帮助区分同义词在不同语境下的含义（如「踯躅于归途」vs「杜鹃花名为踯躅」）。

**高频词过滤：**

用户误触选中的高频词（于是、忽然、只是、然而等）不应触发生词本自动收藏。在 `DictionaryViewModel` 中维护一个停用词表（约 200 个高频虚词），命中时：
- 仍正常显示查词结果
- **仍写入查词历史**（用户主动查的词应留痕）
- **不自动加入生词本**（避免生词本被高频虚词污染）

```
┌──────────────────────────────────────┐
│  ━━━  (拖拽条 / 双击切换高度)          │
│                                       │
│  [ 踟蹰                         ] [✕] │  ← 可编辑搜索框
│  chí chú                             │
│  ──────────────────────────────────  │
│  [CC-CEDICT] [萌典] [成语词典]  ←→   │  ← ScrollableTabRow
│  ──────────────────────────────────  │
│  ┌ CC-CEDICT ┐                  中→英 │  ← 粘性词典徽章（Sticky Header）
│  │ v.                             │  │
│  │ 1. （书）迟疑不前，彷徨不决     │  │
│  │ 2. to hesitate / to waver     │  │
│  │ ↳ 亦作「踟躇」                 │  │
│  ──────────────────────────────────  │
│                                       │
│  [＋ 生词本]           [📋 历史]     │  ← footer 按钮
└──────────────────────────────────────┘
```

**多词典 Tab 设计：**

| 条件 | 展示方式 |
|---|---|
| 仅 1 本词典命中 | 不显示 Tab，直接展示释义（当前原型行为） |
| 2+ 本词典命中 | 显示 `ScrollableTabRow`，每个 Tab 对应一本词典，左右滑动切换 |
| Tab 样式 | Material 3 `ScrollableTabRow`，未选中 Tab 半透明，底部指示器用 info 色 |

Tab 化的理由：当用户导入 3-4 本词典后，垂直滚动寻找某一本词典的释义体验差。Tab 让每本词典独占一屏，切换更直观，也更容易控制 BottomSheet 高度。

**UI 设计决策（基于原型验证 + 优化）：**

| 决策 | 理由 |
|---|---|
| 词典来源用 **info 色徽章**（亮色 `#3A607A`，暗色 `#6AA0BE`） | 区别于正文暖灰，用 MoTu 信息色提供层级锚点，比纯文字标签更易快速定位不同词典的分界 |
| **词典徽章粘性吸顶**（Sticky Header） | 长释义滚动时，词典名始终可见，用户不会迷失"当前看的是哪本词典" |
| 单义项**不显示序号** | 只有一条释义时避免只有「1.」的孤零感 |
| 多义项显示序号 + **悬挂缩进** | 序号占固定宽度（14dp），释义文本左边缘对齐，折行后不顶到序号下方 |
| 「也作/亦作」用 `↳` 前缀、斜体浅灰 | 次要信息视觉降级，不干扰主要释义 |
| 音标/拼音区为空时**不渲染** | 部分词典无注音，避免留空心区域破坏紧凑感。TTS 图标放在此行（如有） |
| footer 包含「生词本」和「历史」两个按钮 | 「生词本」为主操作（填充色按钮），「历史」为次要操作（描边按钮） |

**释义渲染策略：**

| 释义来源 | 渲染路径 |
|---|---|
| CC-CEDICT 内置 | `CedictEntryParser` 专项解析 → `CedictRenderer` 组件 |
| Stardict 纯文本 (`m`) | `Text()` 直接显示 |
| Stardict HTML (`h`) | `DictHtmlRenderer` (Jsoup → AnnotatedString) |
| Stardict Pango (`l`, `g`) | 提取纯文本 → `Text()` |
| Stardict XDXF (`x`) | 提取纯文本 → `Text()` |
| MDX HTML | `DictMdxRenderer` (Jsoup + DictStyleResolver + Compose) |

**MDX 释义字体适配：** MDX 词库 HTML 中常写死 `font-size: 14px` 等绝对值。`DictStyleResolver` 解析 CSS 时必须做一层拦截：将 `px` 强制转换为相对单位 `sp`（根据用户当前阅读器字体大小按比例缩放）。否则在平板或大字体模式下，查词弹窗里的字会小得无法阅读。同时，对没有明确颜色设定的文字，默认使用 `MaterialTheme.colorScheme.onSurface`，兼容夜间模式。

**未找到状态（基于原型优化）：**

```
┌──────────────────────────────────────┐
│  ━━━                                  │
│                                       │
│              ◌                       │
│          未找到释义                    │
│          「踯躅」                     │
│                                       │
│    你是不是要找：                     │
│    · 踯躅 → 踟蹰（相似词）           │
│    · 踯（单字查询）                  │
│                                       │
│    [ 在浏览器中搜索 ]  [ 复制该词 ]   │
│                                       │
│    可在「设置 → 词典管理」中          │
│    导入更多 StarDict 词库             │
└──────────────────────────────────────┘
```

- 虚线圆环图标（`◌`）表达"空缺"语义
- **前缀匹配建议：** 精确匹配失败时，调用 `prefixSearch()` 显示"你是不是要找：XXX？"建议列表（最多 3 条）
- **繁简转换建议：** 如果用户输入的是繁体字，自动建议简体版本（反之亦然），使用 OpenCC4J（项目已有该依赖）
- **新增「复制该词」按钮：** 查不到时用户可能想复制到其他 App（微信、翻译软件）查询，提供完善的兜底体验
- 引导文案直接承接下一步操作，不需要用户自己去找设置
- 若用户仅有中→英词典，文案改为「建议导入中→中词典以获取中文释义」

### 10.4 查词历史

- 每次查词写入 `dict_history` 表（词头 + 时间戳 + 是否命中 + 来源词典 id）
- **入口位置：** 查词弹窗 footer 的「📋 历史」按钮 + 词典管理页面底部链接
- 列表按时间倒序，显示词头 + 查询时间 + 来源词典（或「未命中」）
- 支持左滑删除、底部「清空全部」按钮
- 点击历史条目可重新触发查词

---

## 11. 查询智能匹配

### 11.1 中文前向最大匹配

```kotlin
// core/dictionary/engine/WordNormalizer.kt

class WordNormalizer {

    /** 归一化：trim + 去首尾标点 + 小写 */
    fun normalize(word: String): String

    /**
     * 中文前向最大匹配（Forward Maximum Matching）。
     *
     * 当 smartLookup 全串未命中时，从最长子串开始逐字截短尾部，
     * 找到词典中最长的匹配词条。
     *
     * 例：用户选中 "踟蹰不前"
     *   查 "踟蹰不前" → 未命中
     *   查 "踟蹰不"   → 未命中
     *   查 "踟蹰"     → ✅ 命中
     *
     * 与单字拆分的区别：最大匹配优先返回有意义的词条，
     * 单字拆分是最终 fallback（每个字单独查一次）。
     */
    fun forwardMaxMatch(
        text: String,
        lookup: suspend (String) -> DictEntry?,
    ): List<DictEntry>

    /** 检测字符串是否包含 CJK 字符 */
    fun containsCjk(text: String): Boolean
}
```

### 11.2 英文词形还原（轻量后缀剥离）

内置的 CC-CEDICT 面向英文词汇时有一个缺陷：如果用户选中了复数或过去式（如 "waving"、"hesitated"），直接精确匹配会落空。

**解决方案：** 在归一化阶段加入轻量级的英文后缀剥离规则（Porter Stemmer 简化版），不需要引入 NLP 库：

```kotlin
// core/dictionary/engine/EnglishStemmer.kt

/**
 * 轻量级英文后缀剥离，用于 CC-CEDICT 查词时的词形还原。
 *
 * 不是完整的 Porter Stemmer，仅处理最常见的变形后缀：
 * - 复数：-s, -es, -ies → 去掉或还原
 * - 过去式/过去分词：-ed, -ied → 去掉
 * - 现在分词：-ing → 去掉
 * - 比较级：-er, -est → 去掉
 *
 * 代码量约 50 行，无外部依赖。
 */
object EnglishStemmer {

    /**
     * 返回词头的所有可能原形候选列表（含原词）。
     * 例：输入 "hesitated" → ["hesitated", "hesitate"]
     * 例：输入 "waving" → ["waving", "wave", "wav"]
     *
     * smartLookup 会依次尝试每个候选，直到命中词典。
     */
    fun stemCandidates(word: String): List<String>
}
```

**集成到 smartLookup 流程：**

```
归一化后的词头
  ↓
精确匹配（原词）→ 命中？返回
  ↓ 未命中
如果是英文（!containsCjk）：
  EnglishStemmer.stemCandidates(word) → 依次查每个候选
  ↓ 未命中
如果是中文（containsCjk）：
  forwardMaxMatch() → 前向最大匹配
  ↓ 未命中
  .syn 同义词查找（仅 Stardict）
  ↓ 未命中
  单字拆分 fallback
  ↓ 未命中
  模糊匹配（Levenshtein 距离 ≤ 1）→ 纠正常见选错字
```

#### 11.3 模糊匹配（Levenshtein 距离）

用户经常选错字（如「踟躇」vs「踟蹰」、「迤邐」vs「迤逦」）。在所有精确匹配和前向最大匹配都失败后，使用 Levenshtein 编辑距离 ≤ 1 作为最后一层 fallback：

```kotlin
// core/dictionary/engine/FuzzyMatcher.kt

object FuzzyMatcher {
    /**
     * 在词典 idx 中查找与输入词编辑距离 ≤ 1 的词条。
     * 仅在词头长度 ≤ 6 时启用（避免长句的模糊匹配噪音）。
     * 返回最多 3 个候选词条。
     */
    fun fuzzyMatch(word: String, index: DictIndex): List<IdxEntry>
}
```

性能考量：对 12.5 万条的 idx 做全量 Levenshtein 计算开销大。优化策略：仅对词头长度匹配（±1）的词条计算距离，利用 idx 的有序性做范围扫描。

---

## 12. 模块结构

```
core/dictionary/
  model/
    DictionaryMeta.kt          ← 词典元数据值对象 + DictFormat 枚举
    DictEntry.kt               ← 查询结果值对象 + DefType 枚举
    IdxEntry.kt                ← Stardict idx 词条结构体
  engine/
    StardictParser.kt          ← .ifo 解析 + .idx 预解析 + .dict 随机读取
    StardictIndex.kt           ← idx 二分查找 + 前缀查找
    DictZipReader.kt           ← .dict.dz 块索引解析 + 按需解压
    SynIndex.kt                ← .syn 同义词索引解析与查找
    MdxParser.kt               ← MDX 解析器（方案 A：封装 mdict-java；方案 B：纯 Kotlin 自实现）
    MddResourceLoader.kt       ← MDD 资源提取（CSS、图片）
    DictLookupEngine.kt        ← 查询主逻辑（精确 + smartLookup + LRU 缓存 + 多词典聚合）
    FuzzyMatcher.kt            ← 模糊匹配（Levenshtein 距离 ≤ 1）
    CedictEntryParser.kt       ← CC-CEDICT 条目专项解析
    EnglishStemmer.kt          ← 轻量级英文后缀剥离（词形还原）
    WordNormalizer.kt          ← 词头归一化（去标点、小写、CJK 前向最大匹配）
  render/
    DictNode.kt                ← AST 中间层（平台无关的文档树）
    DictRenderer.kt            ← 统一渲染接口（interface DictRenderer）
    DictHtmlRenderer.kt        ← Stardict HTML → AnnotatedString
    DictMdxRenderer.kt         ← MDX HTML → Compose 布局
    DictStyleResolver.kt       ← CSS class → SpanStyle 映射（含 px→sp 转换）
    CedictRenderer.kt          ← CC-CEDICT 释义专项 Compose 组件
  manager/
    DictionaryManager.kt       ← 词库增删改查、文件 I/O、导入校验、启用/禁用
    DictHistoryManager.kt      ← 查词历史管理
  builtin/
    BuiltinDictInstaller.kt    ← 首次启动时解压内置词典到私有目录

feature/reader/dictionary/
  DictionaryBottomSheet.kt     ← 查词结果弹窗 UI（共享容器 + Tab + 防遮挡）
  DictionaryViewModel.kt       ← 查询状态管理（含上下文句子暂存）

feature/settings/dictionary/
  DictManagementScreen.kt      ← 词库管理界面（导入/删除/排序/启用/空状态引导）
  DictManagementViewModel.kt
  DictHistoryScreen.kt         ← 查词历史列表页

tools/moedict2stardict/
  convert.py                   ← 萌典 → Stardict 转换脚本（开源工具）
  README.md                    ← 使用说明
```

---

## 13. 关键实现细节

### 13.1 dictzip 随机访问

```kotlin
// core/dictionary/engine/DictZipReader.kt

/**
 * 支持 .dict.dz（dictzip）格式的随机访问读取。
 *
 * 原理：dictzip 在 gzip 扩展字段中嵌入块索引表，
 * 每个原始块 50-64KB，仅需解压目标块即可获取指定偏移的数据。
 *
 * 参考：https://man.archlinux.org/man/dictzip.1.en
 */
class DictZipReader(private val file: File) : DictAccessor {

    private val chunkSize: Int
    private val compressedSizes: IntArray
    private val headerSize: Long

    /**
     * 初始化：读取 gzip 头，解析 RA 扩展字段（标识符 'R''A'），
     * 提取 chunk_size、chunk_count、各块 compressed_size。
     * 所有 16 位整数为小端序。
     */
    init { /* 解析头部 */ }

    /**
     * 读取 [offset, offset+size) 范围的解压后数据。
     *
     * 1. 计算起始块：startChunk = offset / chunkSize
     * 2. 计算结束块：endChunk = (offset + size - 1) / chunkSize
     * 3. 累加 compressedSizes 定位文件中的压缩块起始位置
     * 4. 仅解压 startChunk..endChunk 范围的块
     * 5. 从解压数据中提取 [offset % chunkSize, ...] 的目标字节
     */
    override fun read(offset: Long, size: Int): ByteArray
}

/** .dict 文件的统一访问接口 */
interface DictAccessor {
    fun read(offset: Long, size: Int): ByteArray
}

/** 普通 .dict 文件的访问实现 */
class MappedDictAccessor(private val buffer: MappedByteBuffer) : DictAccessor {
    override fun read(offset: Long, size: Int): ByteArray {
        // ⚠️ MappedByteBuffer.position() 只接受 Int，>2GB 文件会溢出
        // 解决方案：对 >2GB 的 .dict 文件回退到 RandomAccessFile
        require(offset <= Int.MAX_VALUE.toLong()) {
            "MappedByteBuffer 不支持 >2GB 偏移，请使用 RandomAccessFileAccessor"
        }
        buffer.position(offset.toInt())
        val result = ByteArray(size)
        buffer.get(result)
        return result
    }
}

/** >2GB .dict 文件的回退实现 */
class RandomAccessFileAccessor(private val file: File) : DictAccessor {
    private val raf = RandomAccessFile(file, "r")
    override fun read(offset: Long, size: Int): ByteArray {
        raf.seek(offset)
        val result = ByteArray(size)
        raf.readFully(result)
        return result
    }
}
```

### 13.2 .dict 大文件处理选型

**决定：普通 `.dict` 使用 `MappedByteBuffer`，`.dict.dz` 使用 `RandomAccessFile` + 按需解压。**

| 场景 | 实现 | 理由 |
|---|---|---|
| `.dict`（明文） | `MappedDictAccessor` (MappedByteBuffer) | 只读文件，内存映射减少系统调用，OS 页缓存命中率高 |
| `.dict.dz`（压缩） | `DictZipReader` (RandomAccessFile + Inflater) | 需要 seek + 按需解压块，无法直接内存映射 |

两者通过 `DictAccessor` 接口统一，上层代码不感知差异。

### 13.3 内置词典解压

```kotlin
class BuiltinDictInstaller(private val context: Context) {
    /**
     * 首次启动或版本升级时，将 assets/builtin-dict/ 下的
     * cc-cedict.ifo/.idx/.dict 解压到 filesDir/dict/builtin/
     *
     * 使用 SharedPreferences 记录已安装版本号，避免重复解压。
     * APK 中存储为 gzip 压缩（减小 APK 体积），解压后为原始三文件。
     */
    suspend fun installIfNeeded()
}
```

---

## 14. UI 界面设计

### 14.1 词库管理界面（设置 → 词典管理）

**有词库状态：**

```
┌──────────────────────────────────────┐
│  ← 词典管理                          │
│                                       │
│  已安装词库（拖拽调整优先级）           │
│  ┌──────────────────────────────────┐│
│  │ ☰ CC-CEDICT (内置)     [开关]   ││
│  │   中→英 · 125,031 词 · Stardict ││
│  └──────────────────────────────────┘│
│  ┌──────────────────────────────────┐│
│  │ ☰ 萌典 (国语)          [开关]   ││
│  │   中→中 · 160,000 词 · Stardict ││
│  │                         [删除]   ││
│  └──────────────────────────────────┘│
│  ┌──────────────────────────────────┐│
│  │ ☰ 现代汉语词典          [开关]   ││
│  │   中→中 · 70,000 词 · MDX       ││
│  │                         [删除]   ││
│  └──────────────────────────────────┘│
│                                       │
│  [+ 导入词库]                         │
│  支持 Stardict (.ifo) 和 MDX (.mdx)  │
│                                       │
│  ──────────────────────────────────  │
│  查词历史（42 条）           [清空]  │
└──────────────────────────────────────┘
```

**空状态（仅有内置词典，无用户导入）：**

```
┌──────────────────────────────────────┐
│  ← 词典管理                          │
│                                       │
│  已安装词库                           │
│  ┌──────────────────────────────────┐│
│  │ 维基词典中文 (内置)     [开关]   ││
│  │ 中→中 · 现代汉语/文言/成语       ││
│  └──────────────────────────────────┘│
│  ┌──────────────────────────────────┐│
│  │ CC-CEDICT (内置)        [开关]   ││
│  │ 中→英 · 125,031 词               ││
│  └──────────────────────────────────┘│
│                                       │
│  ┌──────────────────────────────────┐│
│  │  💡 导入更多词库                 ││
│  │                                   ││
│  │  · 萌典（国语辞典，16万条）       ││
│  │    可使用转换工具生成 Stardict    ││
│  │  · 成语词典（CC 协议，约3万条）   ││
│  │  · 支持 MDX / DSL 格式           ││
│  │                                   ││
│  │  [查看转换工具]                    ││
│  │  [+ 导入词库]                     ││
│  └──────────────────────────────────┘│
└──────────────────────────────────────┘
```

**功能细节：**

| 功能 | 实现 |
|---|---|
| 导入 Stardict | SAF 多选 `.ifo` 文件，自动扫描同目录下 `.idx`/`.dict`/`.dict.dz`/`.syn`，校验 `idxfilesize` 一致性 |
| 导入 MDX | SAF 多选 `.mdx` 文件，自动检测同目录下同名 `.mdd`，校验 Header 格式版本 |
| 删除 | 仅允许删除非内置词典，删除前确认对话框，同时删除关联文件 |
| 排序 | `Reorderable` 拖拽，拖拽手柄在列表项左侧 |
| 启用/禁用 | Switch 控制，禁用后灰显且不参与查询 |
| 格式标签 | 每项显示格式标识（Stardict / MDX），便于用户区分 |

### 14.2 查词历史页

- 入口：查词弹窗 footer「📋 历史」按钮 + 词典管理页面底部链接
- 列表按时间倒序，显示词头 + 查询时间 + 来源词典（或「未命中」）
- 支持左滑删除、底部「清空全部」按钮
- 点击历史条目可重新触发查词

---

## 15. 与现有系统集成

### 15.1 ReaderIntent 扩展

```kotlin
sealed class ReaderIntent {
    // ... 现有 intent ...

    /**
     * 触发查词。
     * @param word 选中的词头文本
     * @param contextSentence 所在段落的上下文句子（前后各约 20 字符），
     *   暂存在 DictionaryViewModel 中，后续加入生词本时连带语境一起保存。
     */
    data class LookupWord(
        val word: String,
        val contextSentence: String = "",
    ) : ReaderIntent()

    data object DismissDictionary : ReaderIntent()
}
```

### 15.2 i18n 扩展

在 `ReaderStrings` 中新增：

```kotlin
// 选区菜单
val lookupWord: String          // "查词"

// 查词弹窗
val noDefinition: String        // "未找到释义"
val noDefinitionHint: String    // "可在「设置 → 词典管理」中导入更多词库"
val noDefinitionZhHint: String  // "建议导入中→中词典以获取中文释义"
val synonymLabel: String        // "(同义词)"
val foundInDicts: (Int) -> String  // "找到 N 本词典的释义"

// 词库管理
val dictManagement: String      // "词典管理"
val importDict: String          // "导入词库"
val builtinLabel: String        // "内置"
val dictFormatStardict: String  // "Stardict"
val dictFormatMdx: String       // "MDX"
val deleteDictConfirm: String   // "确定删除此词库？"
val importFailedIncomplete: String  // "词库文件不完整"
val needZhDictHint: String      // "需要中文释义？"

// 查词历史
val dictHistory: String         // "查词历史"
val clearAllHistory: String     // "清空全部"

// 生词本（预留）
val addToVocab: String          // "加入生词本"
```

### 15.3 性能要求

| 操作 | 目标 | 评估 |
|---|---|---|
| 单词典精确查询（Stardict） | < 5ms | List 二分查找，实测应 < 1ms |
| 单词典精确查询（MDX） | < 10ms | 纯 Kotlin 块索引二分查找 |
| LRU 缓存命中 | 0ms | LinkedHashMap 直接返回 |
| 多词典聚合查询（5 本混合） | < 30ms | 5 × <5ms + dispatch ≈ 15-25ms |
| idx 首次加载 + 预解析 | < 200ms（单本） | 5MB I/O ≈ 20-50ms + 12 万条解析 ≈ 50-100ms |
| MDX 首次加载 | < 300ms（单本） | 纯 Kotlin 块索引加载，比 mdict-java 更轻量 |
| dictzip 随机读取（单块解压） | < 5ms | 解压一个 50KB 块 ≈ 1-3ms |
| MDX 词条 HTML 渲染 | < 3ms | Jsoup 解析 + DOM 遍历 + Compose |
| CSS 首次解析（MDD） | < 50ms | 一次性，词库加载时执行并缓存 |
| 查词弹窗弹出延迟 | < 100ms | BottomSheet 动画 ≈ 50ms + 查询 < 10ms |

---

## 16. 分阶段实施计划

### Phase 1：字符级命中 + Stardict 核心查词 + 生词本（MVP）✅ 已完成

**⚠️ 第一项工作（地基层）：**
- [x] **字符级命中 + 单词自动选区**（`selectWordAt()`）— 这是整个查词功能成立的前提。原型掩盖了 `selectLineAt()` 只命中整行的事实，必须先解决。详见 10.1.1

**数据层：**
- [x] Stardict 解析器（`.ifo` / `.idx` / `.dict`）
- [x] **`.dict.dz` 随机访问支持**（`DictZipReader`，不可推迟）
- [x] **mmap + offset table 索引**（零 Java 对象，~1.5MB 内存）
- [x] `MappedByteBuffer` 实现 `.dict` 随机读取（>2GB 回退 RandomAccessFile）
- [ ] 内置 **维基词典中文版**（中→中主力）+ CC-CEDICT（中→英补充）— 未内置，用户自行导入
- [ ] 维基词典质量验证（50 词小说生僻词清单抽测）— 未内置
- [x] `dict_history` 表（含 `isFavorite` + `contextSentence`）
- [x] **`word_book` 表**（`WordBookEntry`，完整生词本数据模型）

**查询引擎：**
- [x] 单词典精确查询 + **LRU 缓存**（256 条）
- [x] **后台索引预加载**（openBook 时启动低优先级任务）
- [x] **查询取消支持**（BottomSheet 关闭时取消 pending 查询）
- [x] **分层超时**（首次 300ms + 骨架屏 loading，热路径 50ms）
- [ ] **索引 LRU 卸载**（64MB 上限，卸载最冷词典）— 未实现
- [x] `LookupWord` intent 携带上下文句子

**UI：**
- [x] **固定底栏**新增「查词」按钮（Phase 1 不改造为浮动气泡，详见 10.1.2）
- [x] 查词结果 BottomSheet（M3 `ModalBottomSheet` + 自定义 detents，详见 10.1.3）
- [ ] **可编辑搜索框**（默认只读，点击进入编辑态，不自动弹键盘）— Phase 3
- [ ] **多词典 Tab 切换**（`HorizontalPager` + `ScrollableTabRow`，Tab 状态保持）— Phase 3
- [ ] **词典徽章粘性吸顶**（Sticky Header）— Phase 3
- [ ] **防遮挡智能滚动**（下半屏选词自动上滚 Canvas）— Phase 3
- [ ] **词头标题自适应字号**（长句动态缩小）— Phase 3
- [ ] **上下文释义展示**（显示 `contextSentence`）— Phase 3
- [ ] **高频词过滤**（停用词表，仅拦截自动加入生词本，历史照常记录）— Phase 3
- [ ] 未找到空态（虚线圆环 + **前缀匹配建议** + **繁简转换建议** + 「复制该词」按钮）— Phase 3
- [ ] footer 三按钮（生词本 + 复制释义 + 历史）— Phase 3
- [x] **Anki TSV 导出**（word + phonetic + definition + context，SAF 写文件）

**渲染：**
- [x] CC-CEDICT 条目专项渲染（繁简分离、拼音声调转换、义项列表、单义项无序号）
- [x] Stardict HTML 释义渲染（Jsoup → **DictNode AST** → AnnotatedString）
- [x] **统一渲染接口**（`DictRenderer` interface + DictNode AST）
- [ ] **TTS 朗读词头**（Android `TextToSpeech` API，~15 行代码）— Phase 3

**智能匹配：**
- [x] **中文前向最大匹配**（基于字符级命中的起点，`smartLookup` 核心路径）
- [x] **英文词形还原**（`EnglishStemmer`，轻量后缀剥离）

### Phase 2：多格式支持 + 词典内跳转 + UI 升级 ✅ 已完成

- [x] **mdict 解析引擎**（mdict 模块完整实现，非 mdict-java 集成）
- [x] 用户导入词库（Stardict + MDX SAF 多选）
- [x] 导入校验（Stardict 三文件完整性 + MDX Header）
- [x] 词库管理界面（启用/禁用/删除）+ 空状态引导
- [x] 多词典聚合查询（Stardict + MDX 混合）
- [x] **选区扩展把手**（拖动调整选区起止位置）
- [ ] **浮动气泡菜单**（从 Phase 1 固定底栏升级为浮动气泡，方向自适应 + 水平边界 clamp）— Phase 3
- [ ] **`.syn` 同义词支持** — Phase 3
- [ ] **模糊匹配**（Levenshtein ≤ 1，结果标注「你是不是要找」而非直接展示释义）— Phase 3
- [x] **MDX 解析引擎**（mdict 模块自实现）
- [x] **MDX 渲染管线**（DictStyleResolver + DictMdxRenderer + 暗色模式颜色覆盖）
- [ ] **MDX 索引文件缓存**（Key Block Index 缓存到 filesDir/dict/cache/）— Phase 3
- [x] **MDX 词典内跳转**（`entry://` 链接触发新查询）
- [ ] **DSL 格式支持**（引入 dsl4j，GPL 3.0+ AGPL 兼容）— 未实现
- [ ] MDD 资源加载（CSS + 图片提取）— Phase 3
- [x] **生词本界面**（列表 + 搜索 + 删除）
- [ ] 内置成语词典（~3 万条，Stardict，CC 协议）— Phase 3
- [ ] 开源萌典转换工具（`tools/moedict2stardict/`）— 未实现
- [x] **「关于」页面法律声明**（不提供词典数据声明）

### Phase 3：增强与扩展

**从 Phase 1/2 延期的功能：**
- [ ] **可编辑搜索框**（默认只读，点击进入编辑态，不自动弹键盘）
- [ ] **多词典 Tab 切换**（`HorizontalPager` + `ScrollableTabRow`，Tab 状态保持）
- [ ] **词典徽章粘性吸顶**（Sticky Header）
- [ ] **防遮挡智能滚动**（下半屏选词自动上滚 Canvas）
- [ ] **词头标题自适应字号**（长句动态缩小）
- [ ] **上下文释义展示**（显示 `contextSentence`）
- [ ] **高频词过滤**（停用词表，仅拦截自动加入生词本，历史照常记录）
- [ ] 未找到空态（虚线圆环 + **前缀匹配建议** + **繁简转换建议** + 「复制该词」按钮）
- [ ] footer 三按钮（生词本 + 复制释义 + 历史）
- [ ] **TTS 朗读词头**（Android `TextToSpeech` API，~15 行代码）
- [ ] **浮动气泡菜单**（方向自适应 + 水平边界 clamp）
- [ ] **`.syn` 同义词支持**
- [ ] **模糊匹配**（Levenshtein ≤ 1）
- [ ] **MDX 索引文件缓存**（Key Block Index 缓存）
- [ ] MDD 资源加载（CSS + 图片提取）
- [ ] 内置成语词典（~3 万条，Stardict，CC 协议）

**Phase 3 新增：**
- [ ] 查词历史记录（持久化 + 列表页 + footer 入口）
- [ ] 词头前缀匹配（联想建议）
- [ ] MDD 图片资源完整展示
- [ ] **大体积词库 Wi-Fi 传书导入**（复用现有 Wi-Fi 传书服务）
- [ ] 生词本复习提醒（基于间隔重复算法）
- [ ] 全文搜索（在所有词典中搜索关键词）

### Phase 4（可选扩展，不阻塞前三阶段）

- [ ] 词典联动：在笔记界面也能调用查词
- [ ] OCR 查词（截图识别文字后查词）
- [ ] AI 释义（可选，需联网，结合上下文生成释义）
- [ ] 云同步生词本（通过 WebDAV）

---

## 17. 依赖影响评估

| 依赖 | 现有 | 新增 |
|---|---|---|
| Jsoup（HTML 解析） | ✅ 已有 | — |
| Room（数据库） | ✅ 已有 | 新增 3 张表（dict_meta, dict_history, word_book），Database version +1 |
| SAF（文件导入） | ✅ 已有 | — |
| Reorderable（拖拽排序） | ✅ 已有 | — |
| Android `TextToSpeech`（TTS） | ✅ 系统 API | — |
| Material 3 `ScrollableTabRow` + `HorizontalPager` | ✅ 已有（Compose BOM） | — |
| **mdict-java**（MDX 解析，Phase 2） | ❌ | **新增**，~100KB，Apache 2.0 + GPL 3.0（与 AGPL-3.0 兼容） |
| **dsl4j**（DSL 解析，Phase 2） | ❌ | **新增**，GPL 3.0+（与 AGPL-3.0 兼容） |
| 其他新增外部依赖 | — | **无** |

> **mdict-java 许可说明：** mdict-java 核心为 Apache 2.0，部分模块为 GPL 3.0。本项目采用 AGPL-3.0-or-later，与 GPL-3.0 是 FSF 确认的兼容许可组合，引入 mdict-java 在法律上完全合规。若选择方案 B（纯 Kotlin 自实现），则无新增外部依赖。

---

## 18. 测试策略

| 层级 | 测试内容 |
|---|---|
| `StardictParser` 单元测试 | ifo 解析、idx 全量预解析正确性、二分查找边界条件、dict 随机读取、`sametypesequence` 解析 |
| `DictZipReader` 单元测试 | 头部解析（RA 标识、块大小、块数）、单块解压、跨块边界读取、与明文 dict 对比一致性 |
| `SynIndex` 单元测试 | syn 解析、同义词查找、映射到 idx 词条 |
| `CedictEntryParser` 单元测试 | 繁简分离、拼音声调转换（各声调、ü、轻声）、义项分割、异常条目容错 |
| `WordNormalizer` 单元测试 | 英文大小写、中文标点去除、CJK 检测、前向最大匹配逻辑 |
| `EnglishStemmer` 单元测试 | 复数（-s/-es/-ies）、过去式（-ed/-ied）、现在分词（-ing）、比较级（-er/-est）、不规则词不变、短词不截断 |
| `MdxParser` 单元测试 | Header 解析（UTF-8/UTF-16 编码、v1/v2 格式）、Key Block Index 解压、块内二分查找、Record Block 解压、未命中返回 null |
| `DictStyleResolver` 单元测试 | CSS 解析正确性、未知 class 回退、空 CSS 处理、**px→sp 转换**、颜色缺失回退 onSurface |
| `DictHtmlRenderer` 单元测试 | 常见标签渲染、未知标签降级、空 HTML 处理 |
| `DictMdxRenderer` 单元测试 | CSS class 应用、嵌套元素、表格降级、图片占位、px→sp 实际效果 |
| `DictLookupEngine` 单元测试 | 精确命中、未命中、多词典聚合、优先级顺序、syn 同义词命中、smartLookup 各 fallback 路径、Stardict+MDX 混合查询、**LRU 缓存命中/淘汰**、英文词形还原命中 |
| `DictionaryManager` 单元测试 | Stardict 导入校验（三文件完整性 + idxfilesize 一致性）、MDX 导入校验（Header 版本）、删除限制（内置不可删）、启用/禁用 |
| `BuiltinDictInstaller` 单元测试 | 首次安装、重复调用幂等性、版本升级触发重装 |
| UI 集成测试 | 选区 → 查词菜单 → 弹窗弹出 → 释义渲染（Stardict / MDX / CC-CEDICT）→ 拖拽锚点 → 嵌套滑动展开 → 关闭 |
| UI 集成测试 | **防遮挡：** 下半屏选词 → Canvas 自动上滚 → 选中词在 BottomSheet 上方可见 |
| UI 集成测试 | **多词典 Tab：** 2+ 本词典命中 → Tab 显示 → 左右滑动切换 |
| UI 集成测试 | **选区菜单方向：** 屏幕顶端选词 → 菜单在词下方弹出 |
| UI 集成测试 | 未找到空态 → 引导文案 + 「复制该词」按钮可用 |
| UI 集成测试 | 词库管理 → 导入 → 排序 → 启用/禁用 → 删除 |
| UI 集成测试 | **长词头：** 10+ 字符 → 标题字号自动缩小 → 不截断 |

---

## 附录 A：MDX 格式法律风险说明

> 注：Phase 2 支持 MDX 格式解析，但 app 不提供任何 MDX 词库下载或引导。

MDX/MDD 是 MDict 软件（作者 Rayman Zhang）的私有二进制格式。风险分析：

1. **格式解析风险：** MDict 用户协议禁止反向工程。但本 app 使用开源库 mdict-java（Apache 2.0 + GPL 3.0）解析 MDX 文件，该库已在全球范围内广泛使用多年，不涉及本 app 自行逆向工程。

2. **许可兼容性：** 本项目采用 **AGPL-3.0-or-later** 许可。mdict-java 的 GPL 3.0 部分与 AGPL-3.0 是 FSF 确认的兼容许可组合，引入 mdict-java 在法律上完全合规。

3. **词库版权风险（更现实）：** MDX 社区流通的词库文件绝大多数是从商业词典盗版提取的。**app 不提供任何词库下载链接**，仅支持格式导入，由用户自行解决来源。这与欧路词典、DictTango 等现有产品的做法一致。

与之对比，Stardict 格式规范公开发布，CC-CEDICT 采用明确的 CC BY-SA 3.0 协议，法律状态清晰。Phase 1 仅支持 Stardict + CC-CEDICT，法律风险为零。

### 法律声明要求

为降低法律风险，app 必须：

1. **「关于」页面声明：** 明确标注「本应用不提供任何词典数据，所有词典文件由用户自行提供」
2. **不在 app 内提及具体 MDX 词典名称：** 即使是引导文案中也不出现「现代汉语词典」「汉语大词典」等具体名称，仅说「支持导入 MDX 格式词典文件（.mdx/.mdd）」
3. **不链接任何 MDX 词库下载页面：** FreeMdict 论坛、网盘分享等均不可链接

---

## 附录 B：参考资料

- [StarDict-3 官方文件格式规范](https://github.com/huzheng001/stardict-3/blob/master/dict/doc/StarDictFileFormat)
- [CC-CEDICT 下载页（MDBG）](https://www.mdbg.net/chinese/dictionary?page=cedict)
- [dictzip 格式说明（Arch Linux man page）](https://man.archlinux.org/man/dictzip.1.en)
- [ragzip 随机访问 gzip 实现文档](https://github.com/ddeschenes-1/ragzip)
- [GoldenDict-ng 词典格式参考](https://xiaoyifang.github.io/goldendict-ng/dictformats/)
- [萌典 GitHub 仓库](https://github.com/g0v/moedict-app)
- [萌典网站](https://www.moedict.tw/)
- [fengdh/mdict-js（MIT 许可 MDX 解析参考）](https://github.com/fengdh/mdict-js)
- [Vuizur/Wiktionary-Dictionaries（维基词典 Stardict 离线包）](https://github.com/Vuizur/Wiktionary-Dictionaries)
- [FreeDict 开源双语词典](https://freedict.org/)
- [FreeMdict 社区词典论坛](https://forum.freemdict.com/)
- [Android TextToSpeech API](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
- [Material 3 ScrollableTabRow](https://composables.com/jetpack-compose/androidx.compose.material3/material3/components/ScrollableTabRow/api)
