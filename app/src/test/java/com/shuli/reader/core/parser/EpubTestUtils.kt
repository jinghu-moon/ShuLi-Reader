package com.shuli.reader.core.parser

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EpubTestUtils {

    /**
     * 创建一个最小的 EPUB 文件用于测试
     */
    fun createMinimalEpub(
        title: String = "Test Book",
        author: String = "Test Author",
        chapters: List<String> = listOf("Chapter 1", "Chapter 2"),
    ): File {
        val tempFile = File.createTempFile("test", ".epub")
        tempFile.deleteOnExit()

        ZipOutputStream(tempFile.outputStream()).use { zip ->
            // 1. mimetype 文件
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            // 2. container.xml
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("""
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
            """.trimIndent().toByteArray())
            zip.closeEntry()

            // 3. content.opf
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(createOpfContent(title, author, chapters).toByteArray())
            zip.closeEntry()

            // 4. 章节文件
            chapters.forEachIndexed { index, chapterTitle ->
                zip.putNextEntry(ZipEntry("OEBPS/chapter${index + 1}.html"))
                zip.write(createChapterHtml(chapterTitle, "Content of $chapterTitle").toByteArray())
                zip.closeEntry()
            }

            // 5. toc.ncx (NCX 目录)
            zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            zip.write(createNcxContent(title, chapters).toByteArray())
            zip.closeEntry()

            // 6. nav.xhtml (NAV 目录)
            zip.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
            zip.write(createNavContent(title, chapters).toByteArray())
            zip.closeEntry()
        }

        return tempFile
    }

    private fun createOpfContent(title: String, author: String, chapters: List<String>): String {
        val manifestItems = chapters.mapIndexed { index, _ ->
            """<item id="chapter${index + 1}" href="chapter${index + 1}.html" media-type="application/xhtml+xml"/>"""
        }.joinToString("\n            ")

        val spineItems = chapters.mapIndexed { index, _ ->
            """<itemref idref="chapter${index + 1}"/>"""
        }.joinToString("\n            ")

        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="3.0">
            |    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            |        <dc:title>$title</dc:title>
            |        <dc:creator>$author</dc:creator>
            |        <dc:identifier id="BookId">urn:uuid:12345678-1234-1234-1234-123456789012</dc:identifier>
            |        <dc:language>en</dc:language>
            |        <meta property="dcterms:modified">2024-01-01T00:00:00Z</meta>
            |    </metadata>
            |    <manifest>
            |        $manifestItems
            |        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
            |        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            |    </manifest>
            |    <spine toc="ncx">
            |        $spineItems
            |    </spine>
            |</package>
        """.trimMargin()
    }

    private fun createChapterHtml(title: String, content: String): String {
        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE html>
            |<html xmlns="http://www.w3.org/1999/xhtml">
            |<head>
            |    <title>$title</title>
            |</head>
            |<body>
            |    <h1>$title</h1>
            |    <p>$content</p>
            |</body>
            |</html>
        """.trimMargin()
    }

    private fun createNcxContent(title: String, chapters: List<String>): String {
        val navPoints = chapters.mapIndexed { index, chapterTitle ->
            """
            |        <navPoint id="navPoint-${index + 1}" playOrder="${index + 1}">
            |            <navLabel>
            |                <text>$chapterTitle</text>
            |            </navLabel>
            |            <content src="chapter${index + 1}.html"/>
            |        </navPoint>
            """.trimMargin()
        }.joinToString("\n")

        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
            |<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
            |    <head>
            |        <meta name="dtb:uid" content="urn:uuid:12345678-1234-1234-1234-123456789012"/>
            |    </head>
            |    <docTitle>
            |        <text>$title</text>
            |    </docTitle>
            |    <navMap>
            |$navPoints
            |    </navMap>
            |</ncx>
        """.trimMargin()
    }

    private fun createNavContent(title: String, chapters: List<String>): String {
        val navItems = chapters.mapIndexed { index, chapterTitle ->
            """                <li><a href="chapter${index + 1}.html">$chapterTitle</a></li>"""
        }.joinToString("\n")

        return """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE html>
            |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            |<head>
            |    <title>$title</title>
            |</head>
            |<body>
            |    <nav epub:type="toc" id="toc">
            |        <h1>Table of Contents</h1>
            |        <ol>
            |$navItems
            |        </ol>
            |    </nav>
            |</body>
            |</html>
        """.trimMargin()
    }

    /**
     * 创建含嵌套目录的 EPUB：分组标题 + 子章节链接
     * nav 结构：
     *   <ol>
     *     <li>上篇
     *       <ol>
     *         <li><a href="chapter1.html">第一章 开篇</a></li>
     *         <li><a href="chapter2.html">第二章 深入</a></li>
     *       </ol>
     *     </li>
     *     <li><a href="chapter3.html">第三章 总结</a></li>
     *   </ol>
     */
    fun createEpubWithNestedNav(): File {
        val tempFile = File.createTempFile("test-nested-nav", ".epub")
        tempFile.deleteOnExit()

        // nav 标题与 HTML 标题不同，用于区分来源
        val navTitles = listOf("NAV第一章", "NAV第二章", "NAV第三章")
        val htmlTitles = listOf("HTML第一章", "HTML第二章", "HTML第三章")

        ZipOutputStream(tempFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("""
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
            """.trimIndent().toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(createOpfContent("Nested Nav Book", "Author", htmlTitles).toByteArray())
            zip.closeEntry()

            // HTML 章节使用 htmlTitles
            htmlTitles.forEachIndexed { index, title ->
                zip.putNextEntry(ZipEntry("OEBPS/chapter${index + 1}.html"))
                zip.write(createChapterHtml(title, "Content of $title").toByteArray())
                zip.closeEntry()
            }

            // 嵌套 nav 使用 navTitles
            zip.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
            zip.write("""
                |<?xml version="1.0" encoding="UTF-8"?>
                |<!DOCTYPE html>
                |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                |<head><title>Navigation</title></head>
                |<body>
                |    <nav epub:type="toc" id="toc">
                |        <h1>目录</h1>
                |        <ol>
                |            <li>上篇
                |                <ol>
                |                    <li><a href="chapter1.html">${navTitles[0]}</a></li>
                |                    <li><a href="chapter2.html">${navTitles[1]}</a></li>
                |                </ol>
                |            </li>
                |            <li><a href="chapter3.html">${navTitles[2]}</a></li>
                |        </ol>
                |    </nav>
                |</body>
                |</html>
            """.trimMargin().toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            zip.write(createNcxContent("Nested Nav Book", htmlTitles).toByteArray())
            zip.closeEntry()
        }

        return tempFile
    }
}