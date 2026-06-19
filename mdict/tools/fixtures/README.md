# MDX/MDD 测试词典 Fixtures

为 `mdict` 解析库的 Kotlin 单测提供可复现的测试词典，覆盖
[`docs/38-mdx-mdd-parser-library-design.md`](../../../docs/38-mdx-mdd-parser-library-design.md) §3 兼容性矩阵。

## 目录

```
tools/fixtures/
  generate_fixtures.py     ← 生成所有 fixture + manifest.json
  verify_fixtures.py       ← 独立交叉验证（纯标准库 zlib 读取器）
  lib/                     ← writemdict（MIT，已 patch py3.8+ 的 cgi.escape）
src/test/resources/fixtures/
  *.mdx / *.mdd            ← 生成的测试词典
  manifest.json            ← 每本词典的预期元数据 + 词条→释义对照表（单测断言基准）
```

## 用法

```bash
cd mdict/tools/fixtures
python generate_fixtures.py    # 生成词典 + manifest
python verify_fixtures.py      # 交叉验证可解析性（v2.0/zlib/未加密子集）
```

需要 Python 3.8+。`generate_fixtures.py` 仅依赖 `lib/writemdict.py`（标准库）。

## Fixture 清单

| 文件 | 维度 |
|---|---|
| `v2_utf8_zlib.mdx` | v2.0 · UTF-8 · zlib（最常见现代词库） |
| `v2_utf16_zlib.mdx` | v2.0 · UTF-16LE · zlib |
| `v2_gbk_zlib.mdx` | v2.0 · GBK · zlib（应被解析器提升为 GB18030） |
| `v2_big5_zlib.mdx` | v2.0 · Big5 · zlib（ASCII 词条） |
| `v2_utf8_none.mdx` | v2.0 · UTF-8 · 无压缩（comp_type=0） |
| `v2_utf8_zlib_encindex.mdx` | v2.0 · UTF-8 · zlib · key-index 加密（Encrypted&2） |
| `v12_utf8_zlib.mdx` | v1.2 · UTF-8 · zlib（4 字节计数，缺 key_index_decomp_len） |
| `v12_utf16_zlib.mdx` | v1.2 · UTF-16LE · zlib（首尾词长度 1 字节 + 双字节单元） |
| `multiblock_v2_utf8.mdx` | v2.0 · UTF-8 · zlib · 200 词条 + 小 block（多 key block，测两级二分） |
| `resources_v2.mdd` | v2.0 MDD（PNG + CSS），路径键，二进制值 |
| `v2_utf8_lzo.mdx` | v2.0 · UTF-8 · LZO（comp_type=1，老词库常见） |
| `multiblock_v2_lzo.mdx` | v2.0 · UTF-8 · LZO · 200 词条多 block（覆盖 LZO match 各分支与长 run） |

## LZO fixture（双路径，自动选择）

`generate_fixtures.py` 生成 LZO fixture 时按以下优先级自动选 LZO 压缩来源：

**路径 1 — 真 python-lzo（优先）**
若运行脚本的 Python 能 `import lzo`，直接用。最简单的获取方式是 **MSYS2 的 UCRT64 Python**：

```bash
# MSYS2 UCRT64 终端
pacman -S mingw-w64-ucrt-x86_64-python-lzo
# 然后用该 Python 跑生成脚本（注意是 MSYS2 的 python，不是系统 Python）
PYTHONIOENCODING=utf-8 /c/A_Softwares/MSYS2/ucrt64/bin/python.exe generate_fixtures.py
```

> 注意：MSYS2 装的 python-lzo 只在 MSYS2 UCRT64 的 Python 里可用，系统 Python（`C:\…\Python`）import 不到——这是 MSVC vs MinGW 的 ABI 隔离，正常现象。

**路径 2 — minilzo DLL 假模块（回退）**
系统 Python（无 python-lzo）会自动回退到这条。它用 minilzo 官方源码编译的 DLL + ctypes 伪装成 `lzo`：

1. 下载 minilzo 源码（[oberhumer.com](https://www.oberhumer.com/opensource/lzo/)，2.10）到 `_lzobuild/`
2. `gcc -O2 -shared -I minilzo-2.10 -o lzowrap.dll lzowrap.c minilzo-2.10/minilzo.c`
3. `lib/_fake_lzo.py` 用 ctypes 调 `lzowrap.dll`，脚本检测到无真 lzo 时注册它为 `lzo`

**两条路径产出的都是标准 lzo1x 数据**，Kotlin `MiniLzo` 均可解压——已用两个独立来源交叉验证（30 个测试全绿）。
若两条路径都不可用（无 python-lzo 且无 DLL），脚本自动跳过 LZO fixture，不阻塞 zlib 路径。

`_lzobuild/`（含 DLL）是本地构建产物，已 gitignore；`lib/_fake_lzo.py`、`_lzobuild/lzowrap.c` 纳入版本控制，重建只需重跑路径 2 的 1–2 步。

## manifest.json 结构

```jsonc
{
  "fixtures": [
    {
      "file": "v2_utf8_zlib.mdx",
      "tags": ["v2.0", "utf-8", "zlib", "mdx"],
      "entryCount": 8,
      "version": "2.0", "encoding": "utf8",
      "compressionType": 2, "encryptIndex": false,
      "entries": { "踟蹰": "<b>chí chú</b>：…" }   // MDD 为 路径→base64
    }
  ]
}
```

Kotlin 单测读 classpath 资源 `fixtures/manifest.json`，按 `tags` 筛选目标词典，
用 `entries` 做 `lookup` 结果断言。

## 协议

`lib/writemdict.py` 来自 [zhansliu/writemdict](https://github.com/zhansliu/writemdict)（MIT，
见 `lib/LICENSE.writemdict`）。生成的测试词典内容为本项目自编的中文小说生僻词释义，
不含任何版权词典数据。
