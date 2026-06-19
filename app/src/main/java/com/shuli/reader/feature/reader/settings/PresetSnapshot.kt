package com.shuli.reader.feature.reader.settings

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.getValueByKey
import com.shuli.reader.core.reader.model.BoxInsetsDp
import com.shuli.reader.core.reader.model.TitleStyleConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 预设快照：仅包含 `includeInPreset == true` 的设置字段。
 *
 * 字段列表由 [ReaderSettingRegistry.presetFields] 驱动。
 * [fromPreferences] 使用 [ReaderPreferences.getValueByKey] 按 key 读取值，
 * 确保新增 Registry 设置时无需手动维护字段映射。
 *
 * 翻页动画类（page_anim_type / page_anim_speed）、行为类（haptic_feedback / orientation_lock）、
 * TTS、护眼、覆盖层类字段均不在预设内。
 */
@Serializable
data class PresetSnapshot(
    // ── 排版（Layout + Style）──
    val fontSize: Float = 16f,
    val lineSpacing: Float = 1.5f,
    val paragraphSpacing: Float = 1.0f,
    val indent: Float = 2.0f,
    val indentUnit: String = "CHARACTER",
    val letterSpacing: Float = 0f,
    val paragraphDivider: Boolean = false,
    val readingFont: String = "harmony",
    val fontWeight: String = "NORMAL",
    val textAlign: String = "LEFT",
    val chineseConvert: String = "NONE",
    val useZhLayout: Boolean = false,
    val usePanguSpacing: Boolean = false,
    val bionicReading: Boolean = false,
    val bottomJustify: Boolean = false,
    val maxPageWidth: Float = 0f,
    val removeEmptyLines: Boolean = false,
    val cleanChapterTitle: Boolean = false,
    val preserveOriginalIndent: Boolean = false,
    val epubOverrideStyle: Boolean = true,
    val bodyBox: BoxInsetsDp = BoxInsetsDp(top = 48f, bottom = 48f, left = 24f, right = 24f),
    val headerBox: BoxInsetsDp = BoxInsetsDp(top = 16f, bottom = 0f, left = 24f, right = 24f),
    val footerBox: BoxInsetsDp = BoxInsetsDp(top = 0f, bottom = 16f, left = 24f, right = 24f),
    val titleBox: BoxInsetsDp = BoxInsetsDp(top = 9f, bottom = 10f, left = 24f, right = 24f),
    val verticalText: Boolean = false,
    val dualPageMode: String = "AUTO",
    val adFiltering: Boolean = false,

    // ── 页眉页脚（Chrome）──
    val headerVisibility: String = "HIDE_WHEN_STATUS_BAR",
    val footerVisibility: String = "HIDE_WHEN_STATUS_BAR",
    val headerFooterAlpha: Float = 0.4f,
    val showProgress: Boolean = true,
    val progressStyle: String = "CHAPTER_FRACTION",
    val showHeaderLine: Boolean = false,
    val showFooterLine: Boolean = false,
    val headerFontSizeRatio: Float = 0.75f,
    val footerFontSizeRatio: Float = 0.75f,

    // ── 标题样式 ──
    val titleFont: String = "",
    val titleStyle: TitleStyleConfig = TitleStyleConfig(),
) {
    fun toJson(): String = Companion.json.encodeToString(this)

    /**
     * 将快照中的预设字段应用到 [ReaderPreferences]。
     *
     * 仅覆盖 `includeInPreset == true` 的字段，其余字段保持不变。
     * 枚举字段从存储字符串反向转换。
     */
    fun applyOnto(prefs: ReaderPreferences): ReaderPreferences = prefs.copy(
        fontSize = fontSize,
        lineSpacing = lineSpacing,
        paragraphSpacing = paragraphSpacing,
        indent = indent,
        letterSpacing = letterSpacing,
        paragraphDivider = paragraphDivider,
        readingFont = readingFont,
        bionicReading = bionicReading,
        bottomJustify = bottomJustify,
        maxPageWidth = maxPageWidth,
        removeEmptyLines = removeEmptyLines,
        cleanChapterTitle = cleanChapterTitle,
        preserveOriginalIndent = preserveOriginalIndent,
        epubOverrideStyle = epubOverrideStyle,
        bodyBox = bodyBox,
        headerBox = headerBox,
        footerBox = footerBox,
        titleBox = titleBox,
        verticalText = verticalText,
        adFiltering = adFiltering,
        headerFooterAlpha = headerFooterAlpha,
        showProgress = showProgress,
        showHeaderLine = showHeaderLine,
        showFooterLine = showFooterLine,
        headerFontSizeRatio = headerFontSizeRatio,
        footerFontSizeRatio = footerFontSizeRatio,
        titleStyle = titleStyle,
    )

    companion object {
        private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

        /**
         * 从 [ReaderPreferences] 创建快照。
         *
         * 使用 [ReaderSettingRegistry.presetFields] 确定需要包含的字段，
         * 通过 [ReaderPreferences.getValueByKey] 按 key 读取值。
         * 枚举字段转换为 [Enum.name] 字符串存储。
         */
        fun fromPreferences(prefs: ReaderPreferences): PresetSnapshot {
            val presetKeys = ReaderSettingRegistry.presetFields().map { it.key }.toSet()
            val values: Map<String, Any?> = presetKeys.associateWith { key ->
                prefs.getValueByKey<Any>(key)
            }
            return PresetSnapshot(
                fontSize = (values["font_size"] as? Float) ?: 16f,
                lineSpacing = (values["line_spacing"] as? Float) ?: 1.5f,
                paragraphSpacing = (values["paragraph_spacing"] as? Float) ?: 1.0f,
                indent = (values["indent"] as? Float) ?: 2.0f,
                indentUnit = (values["indent_unit"] as? com.shuli.reader.core.data.IndentUnit)?.name ?: "CHARACTER",
                letterSpacing = (values["letter_spacing"] as? Float) ?: 0f,
                paragraphDivider = (values["paragraph_divider"] as? Boolean) ?: false,
                readingFont = (values["reading_font"] as? String) ?: "harmony",
                fontWeight = (values["font_weight"] as? com.shuli.reader.core.data.ReaderFontWeight)?.name ?: "NORMAL",
                textAlign = (values["text_align"] as? com.shuli.reader.core.data.ReaderTextAlign)?.name ?: "LEFT",
                chineseConvert = (values["chinese_convert"] as? com.shuli.reader.core.data.ChineseConvert)?.name ?: "NONE",
                useZhLayout = (values["use_zh_layout"] as? Boolean) ?: false,
                usePanguSpacing = (values["use_pangu_spacing"] as? Boolean) ?: false,
                bionicReading = (values["bionic_reading"] as? Boolean) ?: false,
                bottomJustify = (values["bottom_justify"] as? Boolean) ?: false,
                maxPageWidth = (values["max_page_width"] as? Float) ?: 0f,
                removeEmptyLines = (values["remove_empty_lines"] as? Boolean) ?: false,
                cleanChapterTitle = (values["clean_chapter_title"] as? Boolean) ?: false,
                preserveOriginalIndent = (values["preserve_original_indent"] as? Boolean) ?: false,
                epubOverrideStyle = (values["epub_override_style"] as? Boolean) ?: true,
                bodyBox = (values["body_box"] as? BoxInsetsDp) ?: BoxInsetsDp(top = 48f, bottom = 48f, left = 24f, right = 24f),
                headerBox = (values["header_box"] as? BoxInsetsDp) ?: BoxInsetsDp(top = 16f, bottom = 0f, left = 24f, right = 24f),
                footerBox = (values["footer_box"] as? BoxInsetsDp) ?: BoxInsetsDp(top = 0f, bottom = 16f, left = 24f, right = 24f),
                titleBox = (values["title_box"] as? BoxInsetsDp) ?: BoxInsetsDp(top = 9f, bottom = 10f, left = 24f, right = 24f),
                verticalText = (values["vertical_text"] as? Boolean) ?: false,
                dualPageMode = (values["dual_page_mode"] as? com.shuli.reader.core.data.DualPageMode)?.name ?: "AUTO",
                adFiltering = (values["ad_filtering"] as? Boolean) ?: false,
                headerVisibility = (values["header_visibility"] as? com.shuli.reader.core.reader.model.HeaderVisibility)?.name ?: "HIDE_WHEN_STATUS_BAR",
                footerVisibility = (values["footer_visibility"] as? com.shuli.reader.core.reader.model.HeaderVisibility)?.name ?: "HIDE_WHEN_STATUS_BAR",
                headerFooterAlpha = (values["header_footer_alpha"] as? Float) ?: 0.4f,
                showProgress = (values["show_progress"] as? Boolean) ?: true,
                progressStyle = (values["progress_style"] as? com.shuli.reader.core.data.ProgressStyle)?.name ?: "CHAPTER_FRACTION",
                showHeaderLine = (values["show_header_line"] as? Boolean) ?: false,
                showFooterLine = (values["show_footer_line"] as? Boolean) ?: false,
                headerFontSizeRatio = (values["header_font_size_ratio"] as? Float) ?: 0.75f,
                footerFontSizeRatio = (values["footer_font_size_ratio"] as? Float) ?: 0.75f,
                titleFont = (values["title_font"] as? String) ?: "",
                titleStyle = prefs.titleStyle,
            )
        }

        fun fromJson(jsonString: String): PresetSnapshot =
            json.decodeFromString<PresetSnapshot>(jsonString)
    }
}
