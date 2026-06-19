package com.shuli.reader.core.dictionary.engine

/**
 * 停用词表
 *
 * 高频虚词列表，用于过滤自动加入生词本的词汇
 * 这些词虽然会被查询，但不应自动加入生词本
 */
object StopWords {

    /** 中文高频虚词 */
    private val CHINESE_STOP_WORDS = setOf(
        // 代词
        "我", "你", "他", "她", "它", "我们", "你们", "他们", "她们", "它们",
        "这", "那", "这个", "那个", "这些", "那些", "这里", "那里",
        "谁", "什么", "哪", "哪里", "哪个", "怎么", "怎样", "多少",
        "自己", "别人", "大家", "人家",

        // 助词
        "的", "地", "得", "了", "着", "过", "所", "以",

        // 介词
        "在", "于", "从", "到", "向", "往", "被", "把", "将", "对", "对于",
        "关于", "由于", "为了", "按照", "根据", "通过", "经过",

        // 连词
        "和", "与", "及", "或", "或者", "还是", "而", "但", "但是", "却",
        "虽然", "尽管", "即使", "因为", "所以", "因此", "如果", "假如",
        "只要", "只有", "无论", "不管", "不但", "不仅", "而且", "并且",

        // 副词
        "不", "没", "没有", "很", "太", "非常", "十分", "特别", "更", "最",
        "都", "也", "还", "就", "才", "只", "仅", "仅仅", "已经", "曾", "曾经",
        "正在", "刚", "刚才", "马上", "立刻", "终于", "总", "总是", "常", "常常",
        "再", "又", "也", "还", "却", "倒", "可", "可是", "难道", "究竟",
        "大概", "也许", "或许", "可能", "必定", "一定", "准", "确实",

        // 量词
        "个", "些", "点", "种", "样", "般",

        // 语气词
        "吗", "吧", "呢", "啊", "呀", "哦", "哈", "嗯", "哎",

        // 其他高频词
        "是", "有", "在", "这", "那", "一", "不", "了", "人", "我",
        "他", "她", "它", "们", "你", "就", "都", "把", "被", "让",
        "给", "用", "向", "从", "到", "对", "为", "会", "能", "可以",
        "要", "想", "说", "看", "来", "去", "上", "下", "出", "入",
    )

    /** 英文高频虚词 */
    private val ENGLISH_STOP_WORDS = setOf(
        // 冠词
        "a", "an", "the",

        // 代词
        "i", "me", "my", "mine", "myself",
        "you", "your", "yours", "yourself",
        "he", "him", "his", "himself",
        "she", "her", "hers", "herself",
        "it", "its", "itself",
        "we", "us", "our", "ours", "ourselves",
        "they", "them", "their", "theirs", "themselves",
        "this", "that", "these", "those",
        "who", "whom", "whose", "which", "what",
        "whoever", "whatever", "whichever",

        // 介词
        "in", "on", "at", "to", "for", "with", "by", "from", "of",
        "about", "into", "through", "during", "before", "after",
        "above", "below", "between", "under", "over",
        "up", "down", "out", "off", "away",
        "along", "across", "behind", "beyond", "around",
        "among", "within", "without", "upon", "against",

        // 连词
        "and", "but", "or", "nor", "not", "so", "yet",
        "if", "then", "else", "when", "while", "as", "until",
        "because", "since", "although", "though", "even",
        "whether", "unless", "except", "besides",

        // 动词（be/have/do）
        "is", "am", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "having",
        "do", "does", "did", "doing", "done",

        // 情态动词
        "can", "could", "may", "might", "must", "shall", "should",
        "will", "would", "ought",

        // 副词
        "not", "no", "very", "too", "also", "just", "only",
        "even", "still", "already", "always", "never",
        "often", "sometimes", "usually", "here", "there",
        "now", "then", "today", "tomorrow", "yesterday",
        "again", "once", "twice", "soon", "later",
        "well", "badly", "quickly", "slowly", "really",
        "almost", "enough", "quite", "rather", "somewhat",
    )

    /**
     * 判断是否为停用词
     */
    fun isStopWord(word: String): Boolean {
        val lower = word.trim().lowercase()
        return lower in CHINESE_STOP_WORDS || lower in ENGLISH_STOP_WORDS
    }

    /**
     * 判断是否应自动加入生词本
     *
     * 停用词返回 false，非停用词返回 true
     */
    fun shouldAddToWordBook(word: String): Boolean {
        return !isStopWord(word)
    }
}
