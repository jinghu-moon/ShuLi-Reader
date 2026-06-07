package com.shuli.reader.core.reader

import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.shuli.reader.R
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.core.reader.model.SelectionRange

/**
 * Canvas 视觉参数管理（从 ReaderCanvasView 拆出，SRP）
 *
 * 职责：页眉页脚、排版参数（字号/字距/字重/字体/对齐）、主题色、选区/TTS/笔记高亮。
 * 通过 [onInvalidate] 回调通知 View 重绘。
 */
internal class CanvasVisualParamsManager(
    private val textPaint: Paint,
    private val headerPaint: Paint,
    private val footerPaint: Paint,
    private val backgroundPaint: Paint,
    private val progressPaint: Paint,
    private val selectionPaint: Paint,
    private val ttsHighlightPaint: Paint,
    private val renderContext: RenderContext,
    private val pageRenderer: ReaderPageRenderer,
    private val fontManager: FontManager,
    private val onInvalidate: () -> Unit,
    private val onSubmitRenderTask: () -> Unit,
    private val onPagesInvalidate: () -> Unit,
) {
    private var currentFontKey: String = ""
    var headerTextRatio: Float = 0.75f
        private set
    var footerTextRatio: Float = 0.75f
        private set
    private var edgeTurnPageEnabled = true
    private var edgeWidthPercent = 0.33f

    private val notePaintCache = mutableMapOf<String, Paint>()

    // ── 页眉页脚 ──────────────────────────────────────────

    fun setHeaderText(text: String) {
        if (renderContext.headerText == text) return
        renderContext.headerText = text
        onInvalidate()
    }

    fun setFooterText(text: String) {
        if (renderContext.footerText == text) return
        renderContext.footerText = text
        onInvalidate()
    }

    fun setHeaderSlots(slots: SlotResolution) {
        updateRenderProperty(renderContext.headerSlots != slots) {
            renderContext.headerSlots = slots
        }
    }

    fun setFooterSlots(slots: SlotResolution) {
        updateRenderProperty(renderContext.footerSlots != slots) {
            renderContext.footerSlots = slots
        }
    }

    fun updateHeaderFooter(
        headerSlots: SlotResolution,
        footerSlots: SlotResolution,
        alpha: Float,
        showProgress: Boolean,
        showHeaderLine: Boolean = false,
        showFooterLine: Boolean = false,
    ) {
        val changed = renderContext.headerSlots != headerSlots ||
            renderContext.footerSlots != footerSlots ||
            renderContext.headerAlpha != alpha ||
            renderContext.footerAlpha != alpha ||
            renderContext.showProgress != showProgress ||
            renderContext.showHeaderLine != showHeaderLine ||
            renderContext.showFooterLine != showFooterLine
        if (!changed) return

        renderContext.headerSlots = headerSlots
        renderContext.footerSlots = footerSlots
        renderContext.headerAlpha = alpha
        renderContext.footerAlpha = alpha
        renderContext.showProgress = showProgress
        renderContext.showHeaderLine = showHeaderLine
        renderContext.showFooterLine = showFooterLine
        onPagesInvalidate()
        onSubmitRenderTask()
        onInvalidate()
    }

    fun setShowProgress(show: Boolean) {
        if (renderContext.showProgress == show) return
        renderContext.showProgress = show
    }

    fun setHeaderFooterAlpha(alpha: Float) {
        if (renderContext.headerAlpha == alpha && renderContext.footerAlpha == alpha) return
        renderContext.headerAlpha = alpha
        renderContext.footerAlpha = alpha
    }

    // ── 排版参数 ──────────────────────────────────────────

    fun setTextSizePx(textSize: Float) {
        updateRenderProperty(textPaint.textSize != textSize) {
            textPaint.textSize = textSize
            headerPaint.textSize = textSize * headerTextRatio
            footerPaint.textSize = textSize * footerTextRatio
        }
    }

    fun setLetterSpacing(emSpacing: Float) {
        updateRenderProperty(textPaint.letterSpacing != emSpacing) {
            textPaint.letterSpacing = emSpacing
        }
    }

    fun setFakeBoldText(fakeBold: Boolean) {
        updateRenderProperty(textPaint.isFakeBoldText != fakeBold) {
            textPaint.isFakeBoldText = fakeBold
        }
    }

    fun setTextAlign(align: ReaderTextAlign) {
        pageRenderer.setTextAlign(align)
        onPagesInvalidate()
        onSubmitRenderTask()
        onInvalidate()
    }

    fun setTitleStyle(style: TitleStyleConfig) {
        pageRenderer.setTitleStyle(style)
    }

    fun setFontFamily(fontKey: String) {
        if (fontKey == currentFontKey) return
        currentFontKey = fontKey
        val typeface = resolveTypeface(fontKey)
        if (textPaint.typeface == typeface) return
        textPaint.typeface = typeface
        onPagesInvalidate()
        onSubmitRenderTask()
        onInvalidate()
    }

    fun updatePaintSnapshot(
        textSize: Float? = null,
        letterSpacing: Float? = null,
        fakeBold: Boolean? = null,
        fontKey: String? = null,
        textAlign: ReaderTextAlign? = null,
        invalidateContent: Boolean = false,
    ) {
        var paintChanged = false
        textSize?.let {
            if (textPaint.textSize != it) {
                textPaint.textSize = it
                headerPaint.textSize = it * headerTextRatio
                footerPaint.textSize = it * footerTextRatio
                paintChanged = true
            }
        }
        letterSpacing?.let {
            if (textPaint.letterSpacing != it) {
                textPaint.letterSpacing = it
                paintChanged = true
            }
        }
        fakeBold?.let {
            if (textPaint.isFakeBoldText != it) {
                textPaint.isFakeBoldText = it
                paintChanged = true
            }
        }
        fontKey?.let { key ->
            if (key != currentFontKey) {
                currentFontKey = key
                textPaint.typeface = resolveTypeface(key)
                paintChanged = true
            }
        }
        textAlign?.let {
            pageRenderer.setTextAlign(it)
            paintChanged = true
        }
        if (invalidateContent && paintChanged) {
            onPagesInvalidate()
            onSubmitRenderTask()
            onInvalidate()
        }
    }

    // ── 选区 / TTS / 笔记 ──────────────────────────────────

    fun clearSelection() {
        if (renderContext.selectedRange == null) return
        renderContext.selectedRange = null
        onPagesInvalidate()
        onInvalidate()
    }

    fun setTtsActiveRange(range: SelectionRange?) {
        if (renderContext.ttsActiveRange == range) return
        renderContext.ttsActiveRange = range
        onPagesInvalidate()
        onInvalidate()
    }

    fun setNoteRanges(ranges: List<Pair<SelectionRange, String?>>) {
        val pairs = ranges.mapNotNull { (range, colorHex) ->
            if (colorHex.isNullOrBlank()) return@mapNotNull null
            val paint = notePaintCache.getOrPut(colorHex) {
                val colorInt = runCatching { android.graphics.Color.parseColor(colorHex) }.getOrDefault(android.graphics.Color.YELLOW)
                Paint().apply {
                    color = colorInt
                    alpha = 0x33
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
            }
            range to paint
        }
        renderContext.noteRanges = pairs
        onPagesInvalidate()
        onInvalidate()
    }

    // ── 主题 ──────────────────────────────────────────────

    fun setTheme(
        backgroundColor: Int,
        textColor: Int,
        headerColor: Int,
        footerColor: Int,
        progressColor: Int,
    ) {
        if (
            backgroundPaint.color == backgroundColor &&
            textPaint.color == textColor &&
            headerPaint.color == headerColor &&
            footerPaint.color == footerColor &&
            progressPaint.color == progressColor
        ) {
            return
        }
        backgroundPaint.color = backgroundColor
        textPaint.color = textColor
        headerPaint.color = headerColor
        footerPaint.color = footerColor
        progressPaint.color = progressColor
        selectionPaint.color = progressColor.withAlpha(SELECTION_ALPHA)
        ttsHighlightPaint.color = progressColor.withAlpha(TTS_HIGHLIGHT_ALPHA)
        onSubmitRenderTask()
        onInvalidate()
    }

    fun setThemeColors(colors: ThemeColors) {
        setTheme(
            backgroundColor = colors.backgroundColor,
            textColor = colors.textColor,
            headerColor = colors.headerColor,
            footerColor = colors.footerColor,
            progressColor = colors.progressColor,
        )
    }

    // ── 边缘翻页 / 字号比例 ──────────────────────────────

    fun setEdgeTurnPageEnabled(enabled: Boolean) {
        edgeTurnPageEnabled = enabled
    }

    fun isEdgeTurnPageEnabled() = edgeTurnPageEnabled

    fun setEdgeWidthPercent(percent: Float) {
        edgeWidthPercent = percent.coerceIn(0.1f, 0.5f)
    }

    fun getEdgeWidthPercent() = edgeWidthPercent

    fun setHeaderTextRatio(ratio: Float) {
        headerTextRatio = ratio.coerceIn(0.5f, 1.5f)
        headerPaint.textSize = textPaint.textSize * headerTextRatio
    }

    fun setFooterTextRatio(ratio: Float) {
        footerTextRatio = ratio.coerceIn(0.5f, 1.5f)
        footerPaint.textSize = textPaint.textSize * footerTextRatio
    }

    // ── 内部辅助 ──────────────────────────────────────────

    private fun updateRenderProperty(changed: Boolean, apply: () -> Unit) {
        if (!changed) return
        apply()
        onPagesInvalidate()
        onSubmitRenderTask()
        onInvalidate()
    }

    private fun resolveTypeface(fontKey: String): Typeface = when {
        fontKey == FontManager.KEY_SYSTEM -> Typeface.DEFAULT
        fontKey == FontManager.KEY_HARMONY -> try {
            ResourcesCompat.getFont(fontManager.context, R.font.harmonyos_sanssc_regular) ?: Typeface.DEFAULT
        } catch (_: Exception) { Typeface.DEFAULT }
        FontManager.isCustomFont(fontKey) -> fontManager.loadTypeface(fontKey) ?: Typeface.DEFAULT
        else -> Typeface.DEFAULT
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return (this and 0x00FFFFFF) or (alpha shl 24)
    }

    private companion object {
        private const val SELECTION_ALPHA = 0x33
        private const val TTS_HIGHLIGHT_ALPHA = 0x24
    }
}
