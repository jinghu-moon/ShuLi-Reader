package com.shuli.reader.feature.reader.settings

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 单本书阅读偏好 Room 实体 —— 稀疏覆盖模型。
 *
 * 所有设置字段均可空：
 * - `null` = 未覆盖，跟随全局默认。
 * - 非 `null` = 本书独立设置，覆盖默认。
 *
 * "恢复全局默认" = 将全部字段设为 null（或删除该行）。
 * 全局默认变更后，null 字段自然跟随新默认。
 *
 * 字符串字段使用字符串存储（如 textAlign / fontWeight / chineseConvert），
 * 由 [ReaderSettingsResolver] 负责转换为 enum。
 */
@Entity(tableName = "book_reader_prefs")
data class BookReaderPrefsEntity(
    @PrimaryKey val bookId: Long,

    // ── 排版（reflow 级） ──
    val fontSize: Float? = null,
    val lineSpacing: Float? = null,
    val paragraphSpacing: Float? = null,
    val indent: Float? = null,
    val marginHorizontal: Float? = null,
    val marginVertical: Float? = null,
    val letterSpacing: Float? = null,
    val useZhLayout: Boolean? = null,
    val chineseConvert: String? = null,
    val usePanguSpacing: Boolean? = null,
    val bottomJustify: Boolean? = null,
    val titleAlign: String? = null,
    val titleSizeOffset: Int? = null,
    val titleMarginTop: Float? = null,
    val titleMarginBottom: Float? = null,

    // ── 外观（重绘级） ──
    val readingFont: String? = null,
    val fontWeight: String? = null,
    val textAlign: String? = null,
    val headerVisibility: String? = null,
    val headerLeft: String? = null,
    val headerCenter: String? = null,
    val headerRight: String? = null,
    val footerVisibility: String? = null,
    val footerLeft: String? = null,
    val footerCenter: String? = null,
    val footerRight: String? = null,
    val headerFooterAlpha: Float? = null,
    val showProgress: Boolean? = null,
    val showHeaderLine: Boolean? = null,
    val showFooterLine: Boolean? = null,
    val headerFontSizeRatio: Float? = null,
    val footerFontSizeRatio: Float? = null,
    val headerMarginTop: Float? = null,
    val footerMarginBottom: Float? = null,

    // ── 行为（标志位） ──
    val brightness: Float? = null,
    val keepScreenOn: Boolean? = null,
    val edgeTurnPage: Boolean? = null,
    val edgeWidthPercent: Float? = null,
    val volumeKeyTurnPage: Boolean? = null,
)
