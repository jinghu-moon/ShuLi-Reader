val CHAPTER_TITLE_REGEX = Regex(
    listOf(
        """^(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|第\s{0,4}[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+?\s{0,4}(?:章|节(?!课)|卷|集(?![合和])|部(?![分赛游])|回(?![合来事去])|场(?![和合比电是])|话|篇(?!张))).{0,30}$""",
        """^[ 　\t]{0,4}\d{1,5}[:：,.， 、_—\-].{1,30}$""",
        """^[ 　\t]{0,4}(?:[零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]{1,8}章?)[ 、_—\-].{1,30}$""",
    ).joinToString("|"),
    setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
)

val tests = listOf(
    "第01章：我的家庭",
    "第01章 我的家庭",
    "第1章：我的家庭",
    "第一章：我的家庭",
    "第01章:我的家庭",
)

for (t in tests) {
    val match = CHAPTER_TITLE_REGEX.find(t)
    println("$t -> ${if (match != null) "MATCH" else "NO MATCH"}")
}

// 单独测试 rule 1
val rule1 = Regex("""^(?:序章|楔子|正文(?!完|结)|终章|后记|尾声|番外|第\s{0,4}[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+?\s{0,4}(?:章|节(?!课)|卷|集(?![合和])|部(?![分赛游])|回(?![合来事去])|场(?![和合比电是])|话|篇(?!张))).{0,30}$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
println("--- Rule 1 only ---")
for (t in tests) {
    val match = rule1.find(t)
    println("$t -> ${if (match != null) "MATCH: '${match.value}'" else "NO MATCH"}")
}

// 字符分析
println("--- Char analysis of '第01章：我的家庭' ---")
val s = "第01章：我的家庭"
for (c in s) {
    println("  '${c}' U+${c.code.toString(16).uppercase().padStart(4, '0')}")
}
