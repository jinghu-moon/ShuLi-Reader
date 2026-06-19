package com.shuli.reader.mdict

import com.shuli.reader.mdict.engine.KeyIndexParser
import com.shuli.reader.mdict.model.KeyBlockInfo
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * KeyIndex 落盘缓存。对应 docs/38 §8.3。
 *
 * 大词典首次 open 的瓶颈是解压 + 解析 key_index（上千个带字符串首尾词的
 * KeyBlockInfo）。header 解析、record info 解析都很轻（读小块不解压），无需缓存。
 *
 * 缓存内容 = KeyIndex（keyBlockInfos + keyBlocksStart + entriesNum）。
 * 失效判据：源文件 size + lastModified（比 hash 快，足够可靠）。
 */
object MdxIndexStore {

    private const val MAGIC = 0x4D445849 // "MDXI"
    private const val FORMAT_VERSION = 1

    /** 缓存文件路径：{cacheDir}/{源文件名}-{size}.idxcache */
    fun cacheFileFor(source: File, cacheDir: File): File =
        File(cacheDir, "${source.name}-${source.length()}.idxcache")

    /**
     * 尝试从缓存加载 KeyIndex。缓存不存在/损坏/源文件已变 → 返回 null。
     */
    fun load(source: File, cacheFile: File): KeyIndexParser.KeyIndex? {
        if (!cacheFile.isFile) return null
        return try {
            DataInputStream(cacheFile.inputStream().buffered()).use { din ->
                if (din.readInt() != MAGIC) return null
                if (din.readInt() != FORMAT_VERSION) return null
                val srcSize = din.readLong()
                val srcMtime = din.readLong()
                if (srcSize != source.length() || srcMtime != source.lastModified()) return null

                val keyBlocksStart = din.readLong()
                val entriesNum = din.readLong()
                val count = din.readInt()
                val infos = ArrayList<KeyBlockInfo>(count)
                for (i in 0 until count) {
                    infos.add(
                        KeyBlockInfo(
                            entryCount = din.readLong(),
                            firstWord = din.readUTF(),
                            lastWord = din.readUTF(),
                            compSize = din.readLong(),
                            decompSize = din.readLong(),
                            compAccumulator = din.readLong(),
                            decompAccumulator = din.readLong(),
                        )
                    )
                }
                KeyIndexParser.KeyIndex(infos, keyBlocksStart, entriesNum)
            }
        } catch (e: Exception) {
            // 任何读取/格式异常都视为缓存不可用，回退到完整解析
            null
        }
    }

    /** 将 KeyIndex 写入缓存文件（含源文件 size/mtime 校验信息）。 */
    fun store(source: File, cacheFile: File, index: KeyIndexParser.KeyIndex) {
        cacheFile.parentFile?.mkdirs()
        // 先写临时文件再原子重命名，避免写一半被读到
        val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { dout ->
            dout.writeInt(MAGIC)
            dout.writeInt(FORMAT_VERSION)
            dout.writeLong(source.length())
            dout.writeLong(source.lastModified())
            dout.writeLong(index.keyBlocksStart)
            dout.writeLong(index.entriesNum)
            dout.writeInt(index.keyBlockInfos.size)
            for (info in index.keyBlockInfos) {
                dout.writeLong(info.entryCount)
                dout.writeUTF(info.firstWord)
                dout.writeUTF(info.lastWord)
                dout.writeLong(info.compSize)
                dout.writeLong(info.decompSize)
                dout.writeLong(info.compAccumulator)
                dout.writeLong(info.decompAccumulator)
            }
        }
        if (!tmp.renameTo(cacheFile)) {
            tmp.copyTo(cacheFile, overwrite = true)
            tmp.delete()
        }
    }
}
