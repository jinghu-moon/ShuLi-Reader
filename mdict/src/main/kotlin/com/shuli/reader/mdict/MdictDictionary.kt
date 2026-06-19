package com.shuli.reader.mdict

import com.shuli.reader.mdict.model.MdxEntry
import com.shuli.reader.mdict.model.MdxHeader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.coroutines.coroutineContext
/**
 * [MdictParser] 的协程外观。对应 docs/38 §8.4。
 *
 * 所有 IO/解压操作切到 [ioDispatcher]（默认 Dispatchers.IO），并在进入耗时
 * 操作前检查协程取消（BottomSheet 关闭时上层 cancel job，查询及时中止）。
 *
 * 内部委托同步的 [MdictParser]；本类不持有额外解析状态，只负责调度与取消。
 * 非并发安全：同一实例的查询应串行，或上层每协程独立实例。
 */
class MdictDictionary private constructor(
    private val parser: MdictParser,
    private val ioDispatcher: CoroutineDispatcher,
) : Closeable {

    val header: MdxHeader get() = parser.header
    val entryCount: Long get() = parser.entryCount

    /** 精确查词。未命中返回 null。 */
    suspend fun lookup(word: String): MdxEntry? = onIo { parser.lookup(word) }

    /** 前缀查询。 */
    suspend fun prefixRange(prefix: String, limit: Int = 10): List<MdxEntry> =
        onIo { parser.prefixRange(prefix, limit) }

    /** 取释义文本（MDX，已处理 @@@LINK 重定向）。 */
    suspend fun readDefinition(entry: MdxEntry): String = onIo { parser.readDefinition(entry) }

    /** 按路径取资源二进制（MDD）。未找到返回 null。 */
    suspend fun readResource(path: String): ByteArray? = onIo { parser.readResource(path) }

    /** 便捷：查词并取释义，一步到位。未命中返回 null。 */
    suspend fun define(word: String): String? = onIo {
        parser.lookup(word)?.let { parser.readDefinition(it) }
    }

    private suspend inline fun <T> onIo(crossinline block: () -> T): T =
        withContext(ioDispatcher) {
            coroutineContext.ensureActive() // 进入耗时操作前的取消检查点
            block()
        }

    override fun close() = parser.close()

    companion object {
        /**
         * 打开词典（在 [ioDispatcher] 上完成解析）。
         * @param cacheDir 可选 KeyIndex 落盘缓存目录（docs/38 §8.3）。
         */
        suspend fun open(
            file: File,
            cacheDir: File? = null,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): MdictDictionary = withContext(ioDispatcher) {
            coroutineContext.ensureActive()
            MdictDictionary(MdictParser.open(file, cacheDir), ioDispatcher)
        }
    }
}
