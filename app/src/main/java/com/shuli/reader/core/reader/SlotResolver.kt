package com.shuli.reader.core.reader

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 槽位内容解析器
 */
object SlotResolver {

    /**
     * 解析槽位内容为文本
     */
    fun resolve(
        slot: SlotContent,
        chapterTitle: String = "",
        bookTitle: String = "",
        pageNumber: Int = 0,
        totalPages: Int = 0,
        bookProgressPercent: Float = 0f,
        bookCurrentPosition: Long = 0L,
        bookTotalPosition: Long = 0L,
        batteryLevel: Int = 100,
    ): String {
        return when (slot) {
            SlotContent.NONE -> ""
            SlotContent.CHAPTER_TITLE -> chapterTitle
            SlotContent.BOOK_TITLE -> bookTitle
            SlotContent.CHAPTER_PROGRESS_FRACTION -> "$pageNumber/$totalPages"
            SlotContent.CHAPTER_PROGRESS_PERCENT -> {
                val p = if (totalPages > 0) pageNumber.toFloat() / totalPages else 0f
                "%.1f%%".format(p * 100)
            }
            SlotContent.BOOK_PROGRESS_PERCENT -> "%.1f%%".format(bookProgressPercent * 100)
            SlotContent.BOOK_PROGRESS_FRACTION -> {
                if (bookTotalPosition > 0L) "$bookCurrentPosition/$bookTotalPosition" else ""
            }
            SlotContent.TIME -> {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                sdf.format(Date())
            }
            SlotContent.BATTERY -> "$batteryLevel%"
            SlotContent.DATE -> {
                val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
                sdf.format(Date())
            }
        }
    }

    /**
     * 解析页眉配置
     */
    fun resolveHeader(
        config: HeaderConfig,
        chapterTitle: String = "",
        bookTitle: String = "",
        pageNumber: Int = 0,
        totalPages: Int = 0,
        bookProgressPercent: Float = 0f,
        bookCurrentPosition: Long = 0L,
        bookTotalPosition: Long = 0L,
        batteryLevel: Int = 100,
    ): SlotResolution {
        return SlotResolution(
            left = resolve(config.left, chapterTitle, bookTitle, pageNumber, totalPages, bookProgressPercent, bookCurrentPosition, bookTotalPosition, batteryLevel),
            center = resolve(config.center, chapterTitle, bookTitle, pageNumber, totalPages, bookProgressPercent, bookCurrentPosition, bookTotalPosition, batteryLevel),
            right = resolve(config.right, chapterTitle, bookTitle, pageNumber, totalPages, bookProgressPercent, bookCurrentPosition, bookTotalPosition, batteryLevel),
            leftContent = config.left,
            centerContent = config.center,
            rightContent = config.right,
        )
    }

    /**
     * 解析页脚配置
     */
    fun resolveFooter(
        config: FooterConfig,
        chapterTitle: String = "",
        bookTitle: String = "",
        pageNumber: Int = 0,
        totalPages: Int = 0,
        bookProgressPercent: Float = 0f,
        bookCurrentPosition: Long = 0L,
        bookTotalPosition: Long = 0L,
        batteryLevel: Int = 100,
    ): SlotResolution {
        return SlotResolution(
            left = resolve(config.left, chapterTitle, bookTitle, pageNumber, totalPages, bookProgressPercent, bookCurrentPosition, bookTotalPosition, batteryLevel),
            center = resolve(config.center, chapterTitle, bookTitle, pageNumber, totalPages, bookProgressPercent, bookCurrentPosition, bookTotalPosition, batteryLevel),
            right = resolve(config.right, chapterTitle, bookTitle, pageNumber, totalPages, bookProgressPercent, bookCurrentPosition, bookTotalPosition, batteryLevel),
            leftContent = config.left,
            centerContent = config.center,
            rightContent = config.right,
        )
    }
}
