package com.shuli.reader.sync.network.webdav

/**
 * WebDAV 207 Multi-Status XML 解析器（T-08）
 *
 * 使用字符串解析 PROPFIND 响应，兼容 JVM 单元测试环境。
 */
object WebDavXmlParser {

    fun parsePropfindResponse(xml: String): List<ResourceInfo> {
        val resources = mutableListOf<ResourceInfo>()

        // 按 <D:response> 或 <response> 分割（仅开标签）
        val responsePattern = Regex("<(?![/])[a-zA-Z]*:response>|<response>")
        val responseBlocks = xml.split(responsePattern).drop(1)

        for (block in responseBlocks) {
            // 找到闭标签位置
            val endPattern = Regex("</[a-zA-Z]*:response>|</response>")
            val endMatch = endPattern.find(block) ?: continue
            val content = block.substring(0, endMatch.range.first)

            // 提取 href
            val href = extractTag(content, "href") ?: continue

            // 提取 getcontentlength
            val contentLength = extractTag(content, "getcontentlength")?.toLongOrNull() ?: 0L

            // 提取 getetag
            val etag = extractTag(content, "getetag")

            // 提取 getlastmodified
            val lastModified = extractTag(content, "getlastmodified")

            // 判断是否是目录（resourcetype 包含 collection）
            val isDirectory = content.contains(Regex("<[a-zA-Z]*:collection/>|<collection/>")) ||
                content.contains(Regex("<[a-zA-Z]*:collection>|<collection>"))

            resources.add(
                ResourceInfo(
                    path = href,
                    etag = etag,
                    contentLength = contentLength,
                    lastModified = lastModified,
                    isDirectory = isDirectory,
                )
            )
        }

        return resources
    }

    private fun extractTag(xml: String, tagName: String): String? {
        // 支持带命名空间前缀的标签，如 <D:href> 或 <href>
        val pattern = Regex("<[a-zA-Z]*:$tagName>([^<]*)</[a-zA-Z]*:$tagName>|<$tagName>([^<]*)</$tagName>")
        val match = pattern.find(xml) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }.ifEmpty { null }
    }
}
