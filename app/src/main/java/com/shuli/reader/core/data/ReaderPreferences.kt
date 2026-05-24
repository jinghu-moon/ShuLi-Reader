package com.shuli.reader.core.data

/**
 * 阅读器显示偏好数据类
 * 用于统一管理阅读页的显示设置
 */
data class ReaderPreferences(
    val fontSize: Float = 16f,
    val lineSpacing: Float = 1.5f,
    val paragraphSpacing: Float = 1.0f,
    val indent: Float = 2.0f,
    val pageAnimType: PageAnimType = PageAnimType.HORIZONTAL,
    val backgroundColor: ReaderTheme = ReaderTheme.PAPER,
    val marginHorizontal: Float = 24f,
    val marginVertical: Float = 48f,
    val brightness: Float = -1f,
    val readingFont: String = "system",
    val optimizeRender: Boolean = true,  // 渲染优化总开关，关闭则降级为 Bitmap 逐帧绘制
)

/**
 * 翻页动画类型
 */
enum class PageAnimType {
    NONE,
    COVER,
    HORIZONTAL,
    SIMULATION,
    SCROLL,
}

/**
 * 阅读主题
 */
enum class ReaderTheme {
    LIGHT,    // 浅色
    DARK,     // 暗色
    PAPER,    // 纸质
    OLED,     // OLED
}

/**
 * 主题颜色配置
 */
data class ThemeColors(
    val backgroundColor: Int,
    val textColor: Int,
    val headerColor: Int,
    val footerColor: Int,
    val progressColor: Int,
    val accentColor: Int,
)

/**
 * PageAnimType 转换为 PageDelegateFactory.PageAnimType
 */
fun PageAnimType.toFactoryType(): com.shuli.reader.core.reader.animation.PageDelegateFactory.PageAnimType {
    return when (this) {
        PageAnimType.NONE -> com.shuli.reader.core.reader.animation.PageDelegateFactory.PageAnimType.NONE
        PageAnimType.COVER -> com.shuli.reader.core.reader.animation.PageDelegateFactory.PageAnimType.COVER
        PageAnimType.HORIZONTAL -> com.shuli.reader.core.reader.animation.PageDelegateFactory.PageAnimType.HORIZONTAL
        PageAnimType.SIMULATION -> com.shuli.reader.core.reader.animation.PageDelegateFactory.PageAnimType.SIMULATION
        PageAnimType.SCROLL -> com.shuli.reader.core.reader.animation.PageDelegateFactory.PageAnimType.SCROLL
    }
}

/**
 * 从 UserPreferences 转换为 PageAnimType
 */
fun String.toPageAnimType(): PageAnimType {
    return when (this) {
        PageAnimConst.NONE -> PageAnimType.NONE
        PageAnimConst.OVERLAY -> PageAnimType.COVER
        PageAnimConst.SLIDE -> PageAnimType.HORIZONTAL
        PageAnimConst.SIMULATION -> PageAnimType.SIMULATION
        PageAnimConst.FADE -> PageAnimType.NONE  // 淡入淡出暂用无动画
        else -> PageAnimType.HORIZONTAL
    }
}
