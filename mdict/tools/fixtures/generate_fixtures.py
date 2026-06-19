# -*- coding: utf-8 -*-
"""
MDX/MDD 测试词典生成器 —— 为 mdict 解析库的 Kotlin 单测提供 fixture。

覆盖 docs/38 §3 兼容性矩阵的各维度组合，并产出 manifest.json，
记录每本词典的预期元数据 + 词条→释义对照表，供单测做断言。

用法：
    cd mdict/tools/fixtures
    python generate_fixtures.py

输出：
    ../../src/test/resources/fixtures/*.mdx / *.mdd
    ../../src/test/resources/fixtures/manifest.json

依赖：lib/writemdict.py（MIT，已 patch py3.8+ 的 cgi.escape）。
LZO 压缩需 python-lzo（C 扩展），未安装时自动跳过对应 fixture。
"""
from __future__ import unicode_literals, print_function

import io
import os
import sys
import json

# lib/ 内含 writemdict 及其依赖
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "lib"))
from writemdict import MDictWriter  # noqa: E402

OUT_DIR = os.path.normpath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)),
                 "..", "..", "src", "test", "resources", "fixtures")
)

# ── 词条数据 ───────────────────────────────────────────────────────────
# 小说高频生僻词，中文释义；键为词头，值为释义 HTML。
CJK_ENTRIES = {
    "踟蹰": "<b>chí chú</b>：迟疑不前，徘徊不决。亦作「踟躇」。",
    "霁色": "<b>jì sè</b>：雨雪初晴后天空明朗之色。",
    "迤逦": "<b>yǐ lǐ</b>：曲折连绵的样子。亦作「迤邐」。",
    "踯躅": "<b>zhí zhú</b>：徘徊不进；杜鹃花的别名。",
    "氤氲": "<b>yīn yūn</b>：烟气、烟云弥漫的样子。",
    "旖旎": "<b>yǐ nǐ</b>：柔和美好的样子。",
    "婵娟": "<b>chán juān</b>：姿态美好；指明月。",
    "皴裂": "<b>cūn liè</b>：皮肤因寒冷干燥而裂开。",
}

# ASCII 词条，用于测试英文查词 + 大小写不敏感。
ASCII_ENTRIES = {
    "alpha": "<i>alpha</i>: the first letter.",
    "Beta": "Letter <b>beta</b>, the second.",
    "gamma": "Capital is &#915; &lt;tag&gt;.",
}

# 多 block 数据集：足够多的词条 + 极小 block_size，强制生成多个 key block，
# 用于测试「块级二分定位 + 块内查找」两级查找路径。
MULTIBLOCK_ENTRIES = {
    "word%03d" % i: "definition number %d for word%03d" % (i, i)
    for i in range(200)
}

# @@@LINK= 重定向数据集（docs/38 §5.6）。
# 异体字「踟躇」重定向到「踟蹰」；末尾常带 \r\n\0，解析器需 trim。
LINK_ENTRIES = {
    "踟蹰": "<b>chí chú</b>：迟疑不前，徘徊不决。",
    "踟躇": "@@@LINK=踟蹰\r\n",          # 重定向到踟蹰
    "彷徨": "@@@LINK=踟蹰",              # 无尾随空白的重定向
}


# MDD 资源：键为反斜杠路径，值为二进制。一张 10x10 全红 PNG。
RED_PNG = (
    b"\x89PNG\r\n\x1a\n"
    b"\0\0\0\x0dIHDR\0\0\0\x0a\0\0\0\x0a\x08\x02\x00\x00\x00\x02\x50\x58\xea"
    b"\x00\x00\x00\x12IDAT\x18\xd3\x63\xfc\xcf\x80\x0f\x30\x31\x8c\x4a\x63"
    b"\x01\x00\x41\x2c\x01\x13\x65\x62\x10\x33\0\0\0\0IEND\xae\x42\x60\x82"
)
SIMPLE_CSS = b".def{color:#333;font-size:14px}\n.pinyin{color:#3A607A}\n"

MDD_ENTRIES = {
    "\\red.png": RED_PNG,
    "\\style.css": SIMPLE_CSS,
}

# ── fixture 规格表 ─────────────────────────────────────────────────────
# 每项：(文件名, 词条, 描述, MDictWriter kwargs, 标签集)
# 标签供 manifest 标注，便于单测按维度筛选。
FIXTURES = [
    ("v2_utf8_zlib.mdx", CJK_ENTRIES,
     "v2.0 + UTF-8 + zlib（最常见现代词库）",
     dict(encoding="utf8", version="2.0", compression_type=2),
     ["v2.0", "utf-8", "zlib", "mdx"]),

    ("v2_utf16_zlib.mdx", CJK_ENTRIES,
     "v2.0 + UTF-16LE + zlib",
     dict(encoding="utf16", version="2.0", compression_type=2),
     ["v2.0", "utf-16", "zlib", "mdx"]),

    ("v2_gbk_zlib.mdx", CJK_ENTRIES,
     "v2.0 + GBK + zlib（应被解析器提升为 GB18030）",
     dict(encoding="gbk", version="2.0", compression_type=2),
     ["v2.0", "gbk", "zlib", "mdx"]),

    ("v2_big5_zlib.mdx", ASCII_ENTRIES,
     "v2.0 + Big5 + zlib（ASCII 词条，规避简体字 Big5 不可编码）",
     dict(encoding="big5", version="2.0", compression_type=2),
     ["v2.0", "big5", "zlib", "mdx"]),

    ("v2_utf8_none.mdx", CJK_ENTRIES,
     "v2.0 + UTF-8 + 无压缩（comp_type=0）",
     dict(encoding="utf8", version="2.0", compression_type=0),
     ["v2.0", "utf-8", "none", "mdx"]),

    ("v2_utf8_zlib_encindex.mdx", CJK_ENTRIES,
     "v2.0 + UTF-8 + zlib + key-index 加密（Encrypted&2）",
     dict(encoding="utf8", version="2.0", compression_type=2, encrypt_index=True),
     ["v2.0", "utf-8", "zlib", "enc-index", "mdx"]),

    ("v12_utf8_zlib.mdx", CJK_ENTRIES,
     "v1.2 + UTF-8 + zlib（4 字节计数，缺 key_index_decomp_len）",
     dict(encoding="utf8", version="1.2", compression_type=2),
     ["v1.2", "utf-8", "zlib", "mdx"]),

    ("v12_utf16_zlib.mdx", CJK_ENTRIES,
     "v1.2 + UTF-16LE + zlib（首尾词长度字段 1 字节 + 双字节单元）",
     dict(encoding="utf16", version="1.2", compression_type=2),
     ["v1.2", "utf-16", "zlib", "mdx"]),

    ("multiblock_v2_utf8.mdx", MULTIBLOCK_ENTRIES,
     "v2.0 + UTF-8 + zlib + 极小 block_size（多 key block，测两级二分）",
     dict(encoding="utf8", version="2.0", compression_type=2, block_size=256),
     ["v2.0", "utf-8", "zlib", "multiblock", "mdx"]),

    ("v2_utf8_link.mdx", LINK_ENTRIES,
     "v2.0 + UTF-8 + zlib + @@@LINK= 重定向词条",
     dict(encoding="utf8", version="2.0", compression_type=2),
     ["v2.0", "utf-8", "zlib", "link", "mdx"]),

    ("v2_utf8_salsa.mdx", CJK_ENTRIES,
     "v2.0 + UTF-8 + 注册码加密（Encrypted&1，Salsa20，本库应拒绝）",
     dict(encoding="utf8", version="2.0", compression_type=2,
          encrypt_key=b"abc", register_by="email"),
     ["v2.0", "utf-8", "salsa", "unsupported", "mdx"]),
]

# LZO fixture 单列：python-lzo 缺失时跳过（Step 1 不阻塞）。
LZO_FIXTURES = [
    ("v2_utf8_lzo.mdx", CJK_ENTRIES,
     "v2.0 + UTF-8 + LZO（comp_type=1，老词库常见）",
     dict(encoding="utf8", version="2.0", compression_type=1),
     ["v2.0", "utf-8", "lzo", "mdx"]),

    ("multiblock_v2_lzo.mdx", MULTIBLOCK_ENTRIES,
     "v2.0 + UTF-8 + LZO + 多 block（覆盖 LZO match 各分支与长 run）",
     dict(encoding="utf8", version="2.0", compression_type=1, block_size=4096),
     ["v2.0", "utf-8", "lzo", "multiblock", "mdx"]),
]

# MDD fixture 单列（is_mdd=True，编码固定 UTF-16LE，记录为二进制）。
MDD_FIXTURES = [
    ("resources_v2.mdd", MDD_ENTRIES,
     "v2.0 MDD 资源（PNG + CSS），路径键，二进制值",
     dict(version="2.0", compression_type=2, is_mdd=True),
     ["v2.0", "mdd"]),
]


# ── 生成逻辑 ───────────────────────────────────────────────────────────
def _ensure_lzo():
    """确保 writemdict 能 import 到 `lzo` 模块，返回来源描述（None=不可用）。

    双路径（docs/38 §7、tools/fixtures/README.md）：
    1. 优先用真 python-lzo（如 MSYS2 UCRT64 的 Python 自带）；
    2. 否则用 _fake_lzo（ctypes 调 _lzobuild/lzowrap.dll，由 minilzo 编译）注册为 `lzo`。
    两者产出的都是标准 lzo1x 数据，Kotlin MiniLzo 均可解压。
    """
    try:
        import lzo  # noqa: F401  真 python-lzo
        return "python-lzo (real)"
    except ImportError:
        pass
    # 回退：尝试假模块（依赖已编译的 DLL）
    try:
        import _fake_lzo
        dll = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                           "_lzobuild", "lzowrap.dll")
        if not os.path.exists(dll):
            return None
        sys.modules["lzo"] = _fake_lzo  # 注册为 lzo，供 writemdict import
        return "minilzo DLL via ctypes (fake)"
    except Exception:
        return None


def _b64(data):
    import base64
    return base64.b64encode(data).decode("ascii")


def write_fixture(filename, entries, description, kwargs, tags, manifest, is_binary=False):
    path = os.path.join(OUT_DIR, filename)
    title = os.path.splitext(filename)[0]
    writer = MDictWriter(entries, title=title, description=description, **kwargs)
    with open(path, "wb") as f:
        writer.write(f)

    # manifest 记录预期值，供单测断言。
    record = {
        "file": filename,
        "title": title,
        "description": description,
        "tags": tags,
        "entryCount": len(entries),
        "isMdd": kwargs.get("is_mdd", False),
        "version": kwargs.get("version", "2.0"),
        "encoding": ("UTF-16" if kwargs.get("is_mdd")
                     else kwargs.get("encoding", "utf8")),
        "compressionType": kwargs.get("compression_type", 2),
        "encryptIndex": kwargs.get("encrypt_index", False),
        "fileSize": os.path.getsize(path),
    }
    if is_binary:
        # MDD：记录路径 → base64，单测比对二进制。
        record["entries"] = {k: _b64(v) for k, v in entries.items()}
    else:
        record["entries"] = entries
    manifest["fixtures"].append(record)
    print("  [OK] %-32s %6d bytes  (%s)" % (filename, record["fileSize"], description))


def main():
    if not os.path.isdir(OUT_DIR):
        os.makedirs(OUT_DIR)

    manifest = {
        "_comment": "由 mdict/tools/fixtures/generate_fixtures.py 生成，勿手改。"
                    "每项 entries 为词头→释义（MDD 为路径→base64）的预期对照表。",
        "fixtures": [],
    }

    print("生成 MDX fixtures …")
    for spec in FIXTURES:
        write_fixture(*spec, manifest=manifest)

    print("生成 MDD fixtures …")
    for spec in MDD_FIXTURES:
        write_fixture(*spec, manifest=manifest, is_binary=True)

    print("生成 LZO fixtures …")
    lzo_source = _ensure_lzo()
    if lzo_source is not None:
        print("  使用 LZO 压缩来源：%s" % lzo_source)
        for spec in LZO_FIXTURES:
            write_fixture(*spec, manifest=manifest)
    else:
        print("  [SKIP] 无可用 LZO（既无 python-lzo，也无 _lzobuild/lzowrap.dll）。"
              "跳过 LZO fixtures（zlib 路径不受影响）。")
        for filename, entries, description, kwargs, tags in LZO_FIXTURES:
            manifest["fixtures"].append({
                "file": filename, "skipped": True, "reason": "no LZO compressor available",
                "tags": tags, "description": description,
            })

    manifest_path = os.path.join(OUT_DIR, "manifest.json")
    with io.open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    print("\nmanifest.json 已写入：%s" % manifest_path)
    print("共生成 %d 本词典（不含跳过）。"
          % sum(1 for x in manifest["fixtures"] if not x.get("skipped")))


if __name__ == "__main__":
    main()


