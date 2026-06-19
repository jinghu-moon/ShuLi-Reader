package com.shuli.reader.mdict

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

/**
 * 真实大词典性能基准。对应 docs/38 §8.5。
 *
 * 样本：mdict/test-dict/汉语大词典tsiank升级版（MDX 108MB + MDD 162MB，
 * v2.0 + UTF-8 + key-index 加密 + HTML）。本地无该文件时整组跳过，不阻塞 CI。
 *
 * 运行：./gradlew :mdict:test --tests "*BenchmarkTest" --info
 */
class BenchmarkTest {

    private val dir = File("test-dict/汉语大词典tsiank升级版")
    private val mdx = File(dir, "漢語大詞典.mdx")
    private val mdd = File(dir, "漢語大詞典.mdd")

    private fun requireMdx() = assumeTrue("real dict absent, skipping benchmark", mdx.isFile)

    @Test
    fun `cold open and lookup throughput`() {
        requireMdx()
        lateinit var parser: MdictParser
        val openMs = measureTimeMillis { parser = MdictParser.open(mdx) }
        parser.use {
            println("[bench] entries = ${it.entryCount}")
            println("[bench] cold open (parse key_index) = ${openMs}ms")

            // 采样若干词条做 lookup + readDefinition
            val samples = sampleWords(it, 50)
            // 预热一次（含解压相邻 block）
            samples.forEach { w -> it.lookup(w)?.let { e -> it.readDefinition(e) } }

            var hit = 0
            val ns = measureNanoTime {
                for (w in samples) {
                    val e = it.lookup(w)
                    if (e != null) { it.readDefinition(e); hit++ }
                }
            }
            println("[bench] lookup+define avg = ${ns / samples.size / 1000}µs over ${samples.size} words (hit=$hit)")
        }
    }

    @Test
    fun `warm index cache speeds up open`() {
        requireMdx()
        val cacheDir = File(System.getProperty("java.io.tmpdir"), "mdict-bench-cache").apply { mkdirs() }
        MdxIndexStore.cacheFileFor(mdx, cacheDir).delete() // 确保首次为冷

        val coldMs = measureTimeMillis { MdictParser.open(mdx, cacheDir).use {} }
        val warmMs = measureTimeMillis { MdictParser.open(mdx, cacheDir).use {} }
        println("[bench] open cold(build cache) = ${coldMs}ms, warm(load cache) = ${warmMs}ms")
    }

    @Test
    fun `mdd resource read`() {
        assumeTrue("real mdd absent", mdd.isFile)
        MdictParser.open(mdd).use { parser ->
            println("[bench] mdd entries = ${parser.entryCount}")
            val css = parser.readResource("\\hydcdv2.css") ?: parser.readResource("hydcdv2.css")
            println("[bench] css resource = ${css?.size ?: -1} bytes")
        }
    }

    /** 从前缀采样真实存在的词头。 */
    private fun sampleWords(parser: MdictParser, n: Int): List<String> {
        // 用常见汉字前缀拉取真实词头，避免硬编码不存在的词
        val seeds = listOf("一", "人", "山", "水", "天", "地", "中", "国", "文", "学")
        val out = ArrayList<String>(n)
        for (s in seeds) {
            parser.prefixRange(s, n / seeds.size + 2).forEach { out.add(it.keyword) }
            if (out.size >= n) break
        }
        return out.take(n)
    }
}
