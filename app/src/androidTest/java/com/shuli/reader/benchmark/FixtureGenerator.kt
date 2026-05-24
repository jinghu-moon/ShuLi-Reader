package com.shuli.reader.benchmark

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.io.File

/**
 * Macrobenchmark fixture 生成器。
 *
 * 在跑 [com.shuli.reader.benchmark.ReaderPerformanceBenchmark] 之前先跑一次：
 *
 * ```
 * ./gradlew.bat :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.shuli.reader.benchmark.FixtureGenerator
 * ```
 *
 * 生成的 fixture 位置（应用沙箱 externalCacheDir）：
 * `/sdcard/Android/data/com.shuli.reader.debug/cache/test_100mb.txt`
 *
 * 内容约 100MB UTF-8 中文 TXT，约 1000 章，每章含中文章节标题与若干段落正文。
 *
 * 仅供本地基准使用，不参与 CI 普通单测。
 */
class FixtureGenerator {

    @Test
    fun generate100MbTxtFixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        cacheDir.mkdirs()
        val file = File(cacheDir, FIXTURE_NAME)

        // fixture 已存在且尺寸符合预期 → 跳过重新生成，节省时间
        if (file.exists() && file.length() in (TARGET_SIZE_BYTES - SIZE_TOLERANCE)..(TARGET_SIZE_BYTES + SIZE_TOLERANCE)) {
            Log.i(TAG, "Fixture exists, reuse: ${file.absolutePath} (${file.length()} bytes)")
            return
        }

        var chapterIndex = 0
        file.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.appendLine("书名：test_100mb 性能测试")
            writer.appendLine("作者：基准测试 fixture")
            writer.appendLine()

            var bytesWritten = file.length()  // 估算用，不严格
            while (bytesWritten < TARGET_SIZE_BYTES) {
                chapterIndex++
                val title = "第${chapterIndex}章 性能测试章节"
                writer.appendLine()
                writer.appendLine(title)
                writer.appendLine()
                bytesWritten += title.utf8Bytes()

                var chapterBytes = 0L
                while (chapterBytes < CHAPTER_BYTE_TARGET && bytesWritten < TARGET_SIZE_BYTES) {
                    writer.appendLine(PARAGRAPH_TEMPLATE)
                    val emitted = PARAGRAPH_TEMPLATE.utf8Bytes() + 1
                    chapterBytes += emitted
                    bytesWritten += emitted
                }
            }
        }
        Log.i(TAG, "Generated fixture: ${file.absolutePath} size=${file.length()} chapters=$chapterIndex")
    }

    private fun String.utf8Bytes(): Long = toByteArray(Charsets.UTF_8).size.toLong()

    private companion object {
        private const val TAG = "FixtureGenerator"
        private const val FIXTURE_NAME = "test_100mb.txt"
        private const val TARGET_SIZE_BYTES = 100L * 1024 * 1024
        private const val SIZE_TOLERANCE = 5L * 1024 * 1024
        private const val CHAPTER_BYTE_TARGET = 100L * 1024

        // 重复段落模板（约 130 UTF-8 字节），用于堆出章节体积
        private const val PARAGRAPH_TEMPLATE =
            "这是一段用于性能基准测试的占位中文文本，包含若干常用汉字与标点。" +
                "段落长度足够触发 Paginator 的换行与禁则处理，让翻页时的文本测量真正发生。" +
                "重复出现以构造约 100KB 的章节体积，便于评估真实长篇阅读场景下的解析与渲染。"
    }
}
