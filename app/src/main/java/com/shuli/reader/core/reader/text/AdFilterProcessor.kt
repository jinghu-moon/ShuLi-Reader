package com.shuli.reader.core.reader.text

/**
 * 广告过滤处理器。
 *
 * 过滤文本中的 URL 广告和中文广告关键词。
 * 仅在 [ProcessingContext.adFiltering] 为 true 时生效。
 */
class AdFilterProcessor(
    override val order: Int = 50,
) : TextProcessor {

    companion object {
        /** URL 广告模式 */
        private val AD_URL_PATTERNS = listOf(
            Regex("""https?://\S+"""),
            Regex("""www\.\S+"""),
            Regex("""\S+\.(com|cn|net|org)\S*"""),
        )

        /** 中文广告关键词 */
        private val AD_KEYWORD_PATTERNS = listOf(
            Regex("""(扫码|关注|获取|领取|优惠|折扣|促销|免费试用|点击|注册|下载)"""),
            Regex("""(广告|推广|赞助|合作|商务|招商)"""),
        )
    }

    override fun process(text: String, context: ProcessingContext): String {
        if (!context.adFiltering) return text

        var result = text

        // 过滤 URL 广告
        for (pattern in AD_URL_PATTERNS) {
            result = pattern.replace(result, "")
        }

        // 过滤中文广告关键词
        for (pattern in AD_KEYWORD_PATTERNS) {
            result = pattern.replace(result, "")
        }

        // 清理多余空白
        result = result.replace(Regex("""\s{2,}"""), " ").trim()

        return result
    }
}
