package com.shuli.reader.core.reader

import kotlinx.serialization.Serializable

/**
 * 页眉页脚可见性
 */
@Serializable
enum class HeaderVisibility {
    ALWAYS_SHOW,
    ALWAYS_HIDE,
    HIDE_WHEN_STATUS_BAR,
}

/**
 * 槽位内容类型
 */
@Serializable
enum class SlotContent {
    NONE,
    CHAPTER_TITLE,
    BOOK_TITLE,
    PAGE_NUMBER,
    PROGRESS,
    TIME,
    BATTERY,
    DATE,
}

/**
 * 槽位解析结果
 */
data class SlotResolution(
    val left: String = "",
    val center: String = "",
    val right: String = "",
)

/**
 * 页眉配置
 */
@Serializable
data class HeaderConfig(
    val visibility: HeaderVisibility = HeaderVisibility.HIDE_WHEN_STATUS_BAR,
    val left: SlotContent = SlotContent.CHAPTER_TITLE,
    val center: SlotContent = SlotContent.NONE,
    val right: SlotContent = SlotContent.NONE,
)

/**
 * 页脚配置
 */
@Serializable
data class FooterConfig(
    val visibility: HeaderVisibility = HeaderVisibility.ALWAYS_SHOW,
    val left: SlotContent = SlotContent.PROGRESS,
    val center: SlotContent = SlotContent.PAGE_NUMBER,
    val right: SlotContent = SlotContent.TIME,
)

/**
 * 标题对齐方式
 */
@Serializable
enum class TitleAlign {
    LEFT,
    CENTER,
    HIDDEN,
}

/**
 * 标题样式配置
 */
@Serializable
data class TitleStyleConfig(
    val align: TitleAlign = TitleAlign.CENTER,
    val sizeOffsetSp: Int = 4,
    val marginTopDp: Float = 9f,
    val marginBottomDp: Float = 60f,
)
