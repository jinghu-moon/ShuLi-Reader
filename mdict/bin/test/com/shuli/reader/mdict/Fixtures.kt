package com.shuli.reader.mdict

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 测试 fixture 访问层。从 classpath 资源 fixtures/ 读取词典文件与 manifest.json。
 *
 * fixture 由 mdict/tools/fixtures/generate_fixtures.py 生成。
 */
object Fixtures {

    private val json = Json { ignoreUnknownKeys = true }

    /** 解析后的单本 fixture 元数据 + 词条对照表。 */
    data class Spec(
        val file: String,
        val tags: List<String>,
        val entryCount: Int,
        val isMdd: Boolean,
        val version: String,
        val encoding: String,
        val compressionType: Int,
        val encryptIndex: Boolean,
        val skipped: Boolean,
        /** 词头 → 释义（MDD 为 路径 → base64）。skipped 时为空。 */
        val entries: Map<String, String>,
    )

    private val manifest: List<Spec> by lazy { loadManifest() }

    /** 所有未跳过的 fixture。 */
    fun all(): List<Spec> = manifest.filter { !it.skipped }

    /** 含全部指定 tag 的 fixture。 */
    fun withTags(vararg tags: String): List<Spec> =
        all().filter { spec -> tags.all { it in spec.tags } }

    fun byFile(file: String): Spec =
        manifest.firstOrNull { it.file == file }
            ?: error("fixture not found in manifest: $file")

    /** 将 fixture 资源解压到一个临时文件，返回 File（BlockReader 需要真实文件）。 */
    fun extract(file: String): File {
        val stream = javaClass.classLoader.getResourceAsStream("fixtures/$file")
            ?: error("fixture resource missing: fixtures/$file")
        val tmp = File.createTempFile("mdict-fixture-", "-$file")
        tmp.deleteOnExit()
        stream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        return tmp
    }

    private fun loadManifest(): List<Spec> {
        val text = javaClass.classLoader.getResourceAsStream("fixtures/manifest.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("fixtures/manifest.json missing — run generate_fixtures.py")
        val root = json.parseToJsonElement(text).jsonObject
        return root["fixtures"]!!.jsonArray.map { el ->
            val o = el.jsonObject
            val skipped = (o["skipped"] as? JsonPrimitive)?.boolean ?: false
            Spec(
                file = o["file"]!!.jsonPrimitive.content,
                tags = o["tags"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                entryCount = if (skipped) 0 else o["entryCount"]!!.jsonPrimitive.int,
                isMdd = if (skipped) false else o["isMdd"]!!.jsonPrimitive.boolean,
                version = if (skipped) "" else o["version"]!!.jsonPrimitive.content,
                encoding = if (skipped) "" else o["encoding"]!!.jsonPrimitive.content,
                compressionType = if (skipped) -1 else o["compressionType"]!!.jsonPrimitive.int,
                encryptIndex = if (skipped) false else o["encryptIndex"]!!.jsonPrimitive.boolean,
                skipped = skipped,
                entries = if (skipped) emptyMap() else parseEntries(o["entries"]!!.jsonObject),
            )
        }
    }

    private fun parseEntries(obj: JsonObject): Map<String, String> =
        obj.entries.associate { (k, v) -> k to (v.jsonPrimitive.contentOrNull ?: "") }
}
