# -*- coding: utf-8 -*-
"""
假的 `lzo` 模块 —— 仅供 fixture 生成时让 writemdict 能用 LZO 压缩。

writemdict 只调用 `lzo.compress(data)` 并取返回值 `[5:]`（去掉 python-lzo 的
5 字节头），得到原始 lzo1x_1_compress 输出。本模块用 ctypes 调用 lzowrap.dll
（由 minilzo.c 编译）复现这一行为：返回「5 个占位字节 + 原始 lzo1x 数据」。

放在 lib/ 下，sys.path 优先级高于真正的 python-lzo（本机也没装）。
"""
import os
import ctypes

_DLL_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                         "..", "_lzobuild", "lzowrap.dll")
_dll = ctypes.CDLL(os.path.normpath(_DLL_PATH))
_dll.shuli_lzo1x_compress.argtypes = [
    ctypes.c_char_p, ctypes.c_uint,
    ctypes.c_char_p, ctypes.POINTER(ctypes.c_uint),
]
_dll.shuli_lzo1x_compress.restype = ctypes.c_int


def compress(data, level=1, header=True):
    """模拟 python-lzo.compress：返回 5 字节占位头 + 原始 lzo1x 压缩数据。

    writemdict 取 `[5:]`，所以前 5 字节内容无关紧要（用 python-lzo 的格式占位：
    1 字节标志 + 4 字节大端原长）。
    """
    in_len = len(data)
    # lzo1x 最坏膨胀：in_len + in_len/16 + 64 + 3
    out_cap = in_len + (in_len // 16) + 64 + 3
    out_buf = ctypes.create_string_buffer(out_cap)
    out_len = ctypes.c_uint(out_cap)
    rc = _dll.shuli_lzo1x_compress(data, in_len, out_buf, ctypes.byref(out_len))
    if rc != 0:
        raise RuntimeError("lzo1x_1_compress failed: rc=%d" % rc)
    raw = out_buf.raw[:out_len.value]
    # python-lzo 头：版本标志(0xf0|1) + 4 字节大端原长
    placeholder = bytes([0xf0 | 1]) + in_len.to_bytes(4, "big")
    return placeholder + raw
