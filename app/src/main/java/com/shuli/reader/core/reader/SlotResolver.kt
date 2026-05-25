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
        progress: Float = 0f,
        batteryLevel: Int = 100,
    ): String {
        return when (slot) {
            SlotContent.NONE -> ""
            SlotContent.CHAPTER_TITLE -> chapterTitle
            SlotContent.BOOK_TITLE -> bookTitle
            SlotContent.PAGE_NUMBER -> "$pageNumber / $totalPages"
            SlotContent.PROGRESS -> "%.1f%%".format(progress * 100)
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
        progress: Float = 0f,
        batteryLevel: Int = 100,
    ): SlotResolution {
        return SlotResolution(
            left = resolve(config.left, chapterTitle, bookTitle, pageNumber, totalPages, progress, batteryLevel),
            center = resolve(config.center, chapterTitle, bookTitle, pageNumber, totalPages, progress, batteryLevel),
            right = resolve(config.right, chapterTitle, bookTitle, pageNumber, totalPages, progress, batteryLevel),
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
        progress: Float = 0f,
        batteryLevel: Int = 100,
    ): SlotResolution {
        return SlotResolution(
            left = resolve(config.left, chapterTitle, bookTitle, pageNumber, totalPages, progress, batteryLevel),
            center = resolve(config.center, chapterTitle, bookTitle, pageNumber, totalPages, progress, batteryLevel),
            right = resolve(config.right, chapterTitle, bookTitle, pageNumber, totalPages, progress, batteryLevel),
        )
    }
}
