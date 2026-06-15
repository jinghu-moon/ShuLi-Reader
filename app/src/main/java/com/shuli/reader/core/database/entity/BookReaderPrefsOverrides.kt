package com.shuli.reader.core.database.entity

import com.shuli.reader.feature.reader.settings.GestureConfig
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * 本书级偏好覆盖 —— 所有字段 nullable，null 表示"继承全局默认"。
 *
 * 序列化时使用 `encodeDefaults = false` + `@EncodeDefault(NEVER)` 确保 null 字段不写入 JSON，
 * 实现增量存储。
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BookReaderPrefsOverrides(
    // ── 排版 ──
    val fontSize: Float? = null,
    val lineSpacing: Float? = null,
    val paragraphSpacing: Float? = null,
    val indent: Float? = null,
    val preserveOriginalIndent: Boolean? = null,
    val indentUnit: String? = null,
    val marginHorizontal: Float? = null,
    val marginVertical: Float? = null,
    val marginTop: Float? = null,
    val marginBottom: Float? = null,
    val marginLeft: Float? = null,
    val marginRight: Float? = null,
    val letterSpacing: Float? = null,
    val paragraphDivider: Boolean? = null,
    val readingFont: String? = null,
    val fontWeight: String? = null,
    val textAlign: String? = null,
    val chineseConvert: String? = null,
    val useZhLayout: Boolean? = null,
    val usePanguSpacing: Boolean? = null,
    val bionicReading: Boolean? = null,
    val bottomJustify: Boolean? = null,
    val maxPageWidth: Float? = null,
    val removeEmptyLines: Boolean? = null,
    val cleanChapterTitle: Boolean? = null,
    val adFiltering: Boolean? = null,

    // ── 主题 ──
    val backgroundColor: String? = null,
    val customBackgroundColor: Int? = null,
    val customTextColor: Int? = null,
    val customTitleColor: Int? = null,
    val customHeaderFooterColor: Int? = null,
    val brightness: Float? = null,
    val colorTemperature: Float? = null,
    val backgroundTexture: String? = null,

    // ── 页眉页脚 ──
    val headerVisibility: String? = null,
    val headerLeft: String? = null,
    val headerCenter: String? = null,
    val headerRight: String? = null,
    val footerVisibility: String? = null,
    val footerLeft: String? = null,
    val footerCenter: String? = null,
    val footerRight: String? = null,
    val headerFooterAlpha: Float? = null,
    val headerMarginTop: Float? = null,
    val footerMarginBottom: Float? = null,
    val showProgress: Boolean? = null,
    val progressStyle: String? = null,
    val titleAlign: String? = null,
    val titleSizeOffset: Int? = null,
    val titleMarginTop: Float? = null,
    val titleMarginBottom: Float? = null,
    val showHeaderLine: Boolean? = null,
    val showFooterLine: Boolean? = null,
    val headerFontSizeRatio: Float? = null,
    val footerFontSizeRatio: Float? = null,

    // ── 行为 ──
    val keepScreenOn: Boolean? = null,
    val volumeKeyTurnPage: Boolean? = null,
    val edgeTurnPage: Boolean? = null,
    val edgeWidthPercent: Float? = null,
    val immersiveMode: Boolean? = null,
    val leftZoneRatio: Float? = null,
    val autoPageTurn: Boolean? = null,
    val autoPageTurnInterval: Float? = null,
    val epubOverrideStyle: Boolean? = null,
    val hapticFeedback: Boolean? = null,
    val orientationLock: String? = null,

    // ── 翻页动画 ──
    val pageAnimType: String? = null,
    val pageAnimSpeed: String? = null,

    // ── 显示模式 ──
    val verticalText: Boolean? = null,
    val dualPageMode: String? = null,

    // ── 辅助 ──
    val eyeCareReminderInterval: Int? = null,

    // ── 手势（类型安全：@Serializable GestureConfig，非 JSON String）──
    val gestureConfig: GestureConfig? = null,

    // ── TTS ──
    val ttsSpeed: Float? = null,
    val ttsPitch: Float? = null,
    val ttsVoice: String? = null,
    val ttsAutoPage: Boolean? = null,
    val ttsTimer: Int? = null,
)
