# -*- coding: utf-8 -*-
"""
最小 MDX 读取器 —— 仅用于交叉验证 fixture 的可解析性（不是生产代码）。

只支持 v2.0 + zlib + 无加密（覆盖关键 fixture，尤其 multiblock）。
用标准库 zlib/struct，零第三方依赖，规避 readmdict 强依赖 python-lzo 的问题。
同时作为 Kotlin 解析逻辑的「参考 trace」。

用法：
    cd mdict/tools/fixtures
    python verify_fixtures.py
"""
from __future__ import unicode_literals, print_function
import os
import re
import sys
import json
import zlib
import struct

FIX_DIR = os.path.normpath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)),
                 "..", "..", "src", "test", "resources", "fixtures")
)


def _decompress(block):
    """block = [comp_type:4][adler32:4][data]。仅处理 zlib(2)/none(0)。"""
    comp = block[0]
    data = block[8:]
    if comp == 0:
        return data
    if comp == 2:
        return zlib.decompress(data)
    raise NotImplementedError("comp_type=%d (本验证器只支持 0/2)" % comp)


def read_mdx_zlib_v2(path):
    """返回 {keyword: definition_str}。仅 v2.0 + UTF-8 + zlib + 无加密。"""
    with open(path, "rb") as f:
        buf = f.read()
    pos = 0
    # ── Header ──
    header_len = struct.unpack(">I", buf[pos:pos + 4])[0]; pos += 4
    xml = buf[pos:pos + header_len].decode("utf-16-le"); pos += header_len
    pos += 4  # header adler32
    attrs = dict(re.findall(r'(\w+)="(.*?)"', xml))
    assert attrs.get("GeneratedByEngineVersion") == "2.0", "本验证器仅支持 v2.0"
    assert int(attrs.get("Encrypted", "0") or "0") == 0, "本验证器不支持加密"
    enc = {"UTF-8": "utf-8", "UTF-16": "utf-16-le"}[attrs.get("Encoding", "UTF-8")]
    width = 2 if enc == "utf-16-le" else 1

    # ── Keyword header（5×8）──
    key_block_num, entries_num, key_idx_decomp, key_idx_comp, key_blocks_len = \
        struct.unpack(">QQQQQ", buf[pos:pos + 40]); pos += 40
    pos += 4  # adler32

    # ── Key index（压缩）──
    key_index = _decompress(buf[pos:pos + key_idx_comp]); pos += key_idx_comp
    blocks = []
    ip = 0
    for _ in range(key_block_num):
        n_entries = struct.unpack(">Q", key_index[ip:ip + 8])[0]; ip += 8
        fw_size = struct.unpack(">H", key_index[ip:ip + 2])[0]; ip += 2
        fw_len = (fw_size + 1) * 2 if width == 2 else fw_size + 1
        ip += fw_len
        lw_size = struct.unpack(">H", key_index[ip:ip + 2])[0]; ip += 2
        lw_len = (lw_size + 1) * 2 if width == 2 else lw_size + 1
        ip += lw_len
        comp_size = struct.unpack(">Q", key_index[ip:ip + 8])[0]; ip += 8
        decomp_size = struct.unpack(">Q", key_index[ip:ip + 8])[0]; ip += 8
        blocks.append((comp_size, decomp_size))

    # ── Key blocks：拆出 (record_offset, keyword) ──
    key_list = []
    for comp_size, decomp_size in blocks:
        kb = _decompress(buf[pos:pos + comp_size]); pos += comp_size
        kp = 0
        while kp < len(kb):
            rec_off = struct.unpack(">Q", kb[kp:kp + 8])[0]; kp += 8
            # 找连续 width 个 0 作为 key 结尾
            end = kp
            while True:
                if kb[end:end + width] == b"\x00" * width and (end - kp) % width == 0:
                    break
                end += 1
            keyword = kb[kp:end].decode(enc)
            key_list.append((rec_off, keyword))
            kp = end + width

    # ── Record header + info ──
    rec_block_num, rec_entries, rec_info_size, rec_blocks_size = \
        struct.unpack(">QQQQ", buf[pos:pos + 32]); pos += 32
    rec_infos = []
    for _ in range(rec_block_num):
        cs = struct.unpack(">Q", buf[pos:pos + 8])[0]; pos += 8
        ds = struct.unpack(">Q", buf[pos:pos + 8])[0]; pos += 8
        rec_infos.append((cs, ds))

    # ── Record blocks：拼接解压后的全部 record 数据 ──
    records = b""
    for cs, ds in rec_infos:
        records += _decompress(buf[pos:pos + cs]); pos += cs

    # ── 按 offset 切释义（end = 下一条 offset）──
    result = {}
    for i, (off, kw) in enumerate(key_list):
        end = key_list[i + 1][0] if i + 1 < len(key_list) else len(records)
        raw = records[off:end]
        # MDX 记录以 null 结尾
        text = raw.rstrip(b"\x00").decode(enc)
        result[kw] = text
    return result


def main():
    manifest = json.load(open(os.path.join(FIX_DIR, "manifest.json"), encoding="utf-8"))
    targets = [fx for fx in manifest["fixtures"]
               if not fx.get("skipped")
               and not fx.get("isMdd")
               and fx.get("version") == "2.0"
               and not fx.get("encryptIndex")
               and fx.get("compressionType") in (0, 2)
               and fx.get("encoding") in ("utf8", "utf16")]

    print("交叉验证 %d 本 v2.0/zlib/未加密 MDX …\n" % len(targets))
    all_ok = True
    for fx in targets:
        path = os.path.join(FIX_DIR, fx["file"])
        try:
            parsed = read_mdx_zlib_v2(path)
        except Exception as e:
            print("  [FAIL] %-28s 解析异常: %s" % (fx["file"], e))
            all_ok = False
            continue
        expected = fx["entries"]
        ok = (parsed == expected)
        if ok:
            print("  [PASS] %-28s %d 词条全部匹配" % (fx["file"], len(parsed)))
        else:
            all_ok = False
            miss = set(expected) - set(parsed)
            mism = {k for k in expected if k in parsed and parsed[k] != expected[k]}
            print("  [FAIL] %-28s 缺失=%d 不符=%d" % (fx["file"], len(miss), len(mism)))
            for k in list(miss)[:2]:
                print("           缺失词头: %r" % k)
            for k in list(mism)[:2]:
                print("           释义不符: %r\n             期望=%r\n             实得=%r"
                      % (k, expected[k], parsed[k]))

    print("\n结果:", "全部通过 ✓" if all_ok else "存在失败 ✗")
    sys.exit(0 if all_ok else 1)


if __name__ == "__main__":
    main()
