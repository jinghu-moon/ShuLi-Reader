package com.shuli.reader.mdict.demo

import com.shuli.reader.mdict.MdictParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 划词查词 Demo 服务（对接 docs/prototypes/dict-popup-prototype.html 的真实数据版）。
 *
 * 用 JDK 自带 HttpServer（零新依赖）加载真实 MDX(+MDD)，浏览器里测试查词效果。
 *
 * 运行：
 *   ./gradlew :mdict:runDemo                      # 用默认词典（test-dict 下）
 *   ./gradlew :mdict:runDemo --args="<mdx路径>"   # 指定 MDX
 * 然后浏览器打开 http://localhost:8765
 */
object DictDemoServer {

    private const val PORT = 8765

    @JvmStatic
    fun main(args: Array<String>) {
        val mdxPath = args.firstOrNull()
            ?: "test-dict/汉语大词典tsiank升级版/漢語大詞典.mdx"
        val mdxFile = File(mdxPath)
        require(mdxFile.isFile) { "MDX 不存在：${mdxFile.absolutePath}（用 --args=\"路径\" 指定）" }

        val dictDir = mdxFile.parentFile
        val mddFile = File(dictDir, mdxFile.nameWithoutExtension + ".mdd").takeIf { it.isFile }

        println("加载 MDX：${mdxFile.absolutePath}")
        val mdx = MdictParser.open(mdxFile)
        val mdd = mddFile?.let { println("加载 MDD：${it.absolutePath}"); MdictParser.open(it) }
        println("词条数：${mdx.entryCount}")

        val server = HttpServer.create(InetSocketAddress(PORT), 0)
        val page = loadResource("demo/index.html")

        server.createContext("/") { ex ->
            when (ex.requestURI.path) {
                "/" -> respond(ex, 200, "text/html; charset=utf-8", page.toByteArray(Charsets.UTF_8))
                "/info" -> {
                    val json = """{"name":"${escapeJson(mdxFile.nameWithoutExtension)}","entries":${mdx.entryCount}}"""
                    respond(ex, 200, "application/json; charset=utf-8", json.toByteArray(Charsets.UTF_8))
                }
                "/lookup" -> handleLookup(ex, mdx)
                "/res" -> handleRes(ex, dictDir, mdd)
                else -> respond(ex, 404, "text/plain", "not found".toByteArray())
            }
        }
        server.executor = null
        server.start()
        println("\n✅ Demo 已启动：http://localhost:$PORT  （Ctrl+C 停止）")
    }

    private fun handleLookup(ex: HttpExchange, mdx: MdictParser) {
        val word = queryParam(ex, "word")?.trim().orEmpty()
        if (word.isEmpty()) {
            respond(ex, 200, jsonType(), """{"found":false}""".toByteArray(Charsets.UTF_8))
            return
        }
        val entry = mdx.lookup(word)
        val json = if (entry != null) {
            val html = rewriteDefinition(mdx.readDefinition(entry))
            """{"found":true,"html":${jsonStr(html)}}"""
        } else {
            // 前缀联想作为「你是不是要找」
            val sug = mdx.prefixRange(word.take(1), 5).map { it.keyword }.filter { it != word }.take(3)
            """{"found":false,"suggestions":[${sug.joinToString(","){ jsonStr(it) }}]}"""
        }
        respond(ex, 200, jsonType(), json.toByteArray(Charsets.UTF_8))
    }

    private fun handleRes(ex: HttpExchange, dictDir: File, mdd: MdictParser?) {
        val path = queryParam(ex, "path").orEmpty()
        if (path.isEmpty()) { respond(ex, 404, "text/plain", ByteArray(0)); return }
        // 优先词典目录下的同名文件（CSS/JS 常以独立文件分发），否则从 MDD 取
        val local = File(dictDir, path.substringAfterLast('/').substringAfterLast('\\'))
        val bytes = if (local.isFile) local.readBytes() else mdd?.let {
            it.lookup("\\" + path.replace('/', '\\').trimStart('\\'))
                ?.let { e -> it.readResourceBytes(e) }
        }
        if (bytes == null) { respond(ex, 404, "text/plain", ByteArray(0)); return }
        respond(ex, 200, contentType(path), bytes)
    }

    /** 重写释义 HTML：外链资源指向 /res，entry:// 链接改为触发父页面查词。 */
    private fun rewriteDefinition(html: String): String {
        var h = html
        // href="x.css" / src='x.js'（相对资源）→ /res?path=
        h = Regex("""(href|src)=(["'])([^"']+\.(css|js|png|jpg|gif))\2""", RegexOption.IGNORE_CASE)
            .replace(h) { m -> "${m.groupValues[1]}=\"/res?path=${m.groupValues[3]}\"" }
        // entry://词#锚点 → 点击触发父页面 lookup（去掉锚点）
        h = Regex("""href=(["'])entry://([^"'#]+)(#[^"']*)?\1""")
            .replace(h) { m ->
                val target = URLDecoder.decode(m.groupValues[2], "UTF-8")
                "href=\"javascript:void(0)\" onclick=\"parent.lookup('${escapeJsAttr(target)}')\""
            }
        // 注入基础内边距，避免 iframe 内容贴边
        return "<style>html,body{margin:0;padding:12px 14px;font-size:15px;line-height:1.7}</style>$h"
    }

    // ── 工具 ──
    private fun loadResource(path: String): String =
        DictDemoServer::class.java.classLoader.getResourceAsStream(path)
            ?.bufferedReader()?.use { it.readText() }
            ?: error("缺少资源：$path")

    private fun queryParam(ex: HttpExchange, key: String): String? =
        ex.requestURI.rawQuery?.split("&")?.firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }

    private fun respond(ex: HttpExchange, code: Int, type: String, body: ByteArray) {
        ex.responseHeaders.add("Content-Type", type)
        ex.sendResponseHeaders(code, body.size.toLong())
        ex.responseBody.use { it.write(body) }
    }

    private fun jsonType() = "application/json; charset=utf-8"
    private fun contentType(path: String): String = when (path.substringAfterLast('.').lowercase()) {
        "css" -> "text/css; charset=utf-8"
        "js" -> "application/javascript; charset=utf-8"
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"
        else -> "application/octet-stream"
    }

    private fun jsonStr(s: String): String = "\"${escapeJson(s)}\""
    private fun escapeJson(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\"); '"' -> append("\\\""); '\n' -> append("\\n")
            '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
    private fun escapeJsAttr(s: String): String = s.replace("\\", "\\\\").replace("'", "\\'")
}
