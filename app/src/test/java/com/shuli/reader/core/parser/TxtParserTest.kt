package com.shuli.reader.core.parser

import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis

class TxtParserTest {

    @Test
    fun testRegexPerformance() {
        val file = File("""D:\100_Projects\110_Daily\ShuLi-Reader\refer\超神机械师加料.txt""")
        if (!file.exists()) {
            println("Test file not found")
            return
        }

        val content = file.readText()

        // Make CHAPTER_TITLE_REGEX accessible or use reflection. Since it's private in TxtParser.Companion, let's redefine it here or use reflection.
        val regex = Regex(
            listOf(
                """^[ \t　]{0,4}(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|第\s{0,4}[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+?\s{0,4}(?:章|节(?!课)|卷|集(?![合和])|部(?![分赛游])|回(?![合来事去])|场(?![和合比电是])|话|篇(?!张))).{0,30}$""",
                """^[ 　\t]{0,4}\d{1,5}[:：,.， 、_—\-].{1,30}$""",
                """^[ 　\t]{0,4}(?:[零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}章?)[ 、_—\-].{1,30}$""",
                """^[ 　\t]{0,4}正文[ 　]{1,4}.{0,20}$""",
                // ⑥ 英文格式：Chapter 1 / Section 2 / Part 3 / Episode 4 / No.5
                """^[ 　\t]{0,4}(?:[Cc]hapter|[Ss]ection|[Pp]art|ＰＡＲＴ|[Nn][oO][.、]|[Ee]pisode)\s{0,4}\d{1,4}.{0,30}$""",
                // ⑦ 特殊符号包裹章节（要求严格）：【第一章】/ [Chapter 1]
                """^[ \t　]{0,4}[【〔〖「『〈［\[](?:第|[Cc]hapter)[\d零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,10}[章节].{0,20}$""",
                // ⑧ 星号装饰（晋江风格）及特殊单行：☆、标题 / ★标题
                """^[ \t　]{0,4}[☆★✦✧].{1,30}$""",
                // ⑨ 卷/章+序号+标题：卷五 开源盛世 / 章三十 xxx
                """^[ \t　]{0,4}[卷章][\d零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}[ 　]{0,4}.{0,30}$""",
                // ⑩ 书名+序号/括号序号：龙族(12) / 龙族12 / 斗破苍穹（一百）
                """^[一-龥]{1,20}[ 　\t]{0,4}(?:[(（][\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}[)）]|[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8})[ 　\t]{0,4}$""",
                // ⑪ 独立关键词行：引子/序言/前言/扉页/上部/卷首语/附录/简介/文案/分节阅读/第X页
                """^[ \t　]{0,4}(?:[引楔]子|[引序前]言|扉页|[上中下][部篇卷]|卷首语|附录|(?:内容|文章)?简介|文案|.{0,15}分[页节章段]阅读|第\s{0,4}[\d零一二两三四五六七八九十百千万]{1,6}\s{0,4}[页节])[ 　]{0,4}.{0,20}$"""
            ).joinToString("|"),
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )

        var matches: List<MatchResult> = emptyList()
        val time = measureTimeMillis {
            matches = regex.findAll(content).toList()
        }

        println("===============================")
        println("File size: ${content.length / 1024 / 1024.0} MB")
        println("Found ${matches.size} chapters")
        println("Time taken: $time ms")
        println("===============================")
        
        // Print first 5 and last 5 matches to verify correctness
        matches.take(5).forEach { println("Start: ${it.value.trim()}") }
        println("...")
        matches.takeLast(5).forEach { println("End: ${it.value.trim()}") }
    }
}
