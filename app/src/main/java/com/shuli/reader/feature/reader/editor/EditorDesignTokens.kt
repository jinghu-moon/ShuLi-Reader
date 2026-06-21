package com.shuli.reader.feature.reader.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 沉浸式阅读编辑器设计令牌
 *
 * 基于「墨土 (MoTu)」暖灰棕无彩系配色
 * 参考 docs/prototypes/edit-interface-demo.html
 */
object EditorTokens {

    // ── 语义化映射 ──────────────────────────────────────

    /** 页面与面板底色 */
    val Surface = Color(0xFFF6F4F0)           // T50

    /** 输入框/高亮底色 */
    val SurfaceVariant = Color(0xFFEAE5DC)     // T100

    /** 主操作按钮 */
    val Primary = Color(0xFF453B2E)           // T700
    val OnPrimary = Color.White

    /** 主文字 */
    val TextPrimary = Color(0xFF1A130B)        // T900

    /** 次要文字/图标 */
    val TextSecondary = Color(0xFF5E5346)      // T600

    /** 边框/分割线 */
    val Outline = Color(0xFFD4CCC0)            // T200
    val OutlineVariant = Color(0xFFB9AFA0)     // T300

    /** 反转深色浮层 */
    val SurfaceInverse = Color(0xFF2C231A)     // T800
    val OnSurfaceInverse = Color(0xFFF6F4F0)   // T50

    /** 毛玻璃背景 */
    val GlassBg = Color(0xD9F6F4F0)           // rgba(246, 244, 240, 0.85)

    /** 遮罩层 */
    val Backdrop = Color(0x660C0804)           // rgba(12, 8, 4, 0.4)

    /** 按钮悬停 */
    val ButtonHover = Color(0x0F453B2E)        // rgba(69, 59, 46, 0.06)

    // ── 功能色 ──────────────────────────────────────────

    /** 当前查找焦点 */
    val WarningMain = Color(0xFF9A6500)
    val WarningBg = Color(0xFFF5ECD8)

    /** Diff 新增 */
    val SuccessMain = Color(0xFF2D7A52)
    val SuccessBg = Color(0xFFE8F4EE)

    /** Diff 删除 */
    val ErrorMain = Color(0xFF9B3525)
    val ErrorBg = Color(0xFFF5E5E2)

    /** 扫描进度 */
    val ScanBlue = Color(0xFF3B82F6)

    // ── 其他高亮 ────────────────────────────────────────

    /** 其他匹配高亮 */
    val HighlightOtherBg = Color(0x99F5ECD8)   // rgba(245, 236, 216, 0.6)

    /** 当前匹配高亮 */
    val HighlightCurrentBg = Color(0xFFF5ECD8)
    val HighlightCurrentBorder = Color(0xFF9A6500)

    // ── Z-Index ─────────────────────────────────────────

    const val Z_HISTORY_DROPDOWN = 99f
    const val Z_TOOLBAR = 100f
    const val Z_BACKDROP = 110f
    const val Z_HISTORY_SHEET = 120f
    const val Z_SIDEBAR = 130f
    const val Z_DIALOG = 200f

    // ── 尺寸 ────────────────────────────────────────────

    val ToolbarTopPadding = 16.dp
    val ToolbarHorizontalPadding = 16.dp
    val ToolbarCornerRadius = 16.dp
    val ToolbarMaxWidth = 382.dp
    val ToolbarHeight = 48.dp
    val ReplaceRowHeight = 48.dp

    val InputCornerRadius = 10.dp
    val ButtonCornerRadius = 6.dp
    val IconSize = 28.dp
    val IconInnerSize = 18.dp
    val DividerHeight = 16.dp

    val HistoryDropdownTopOffset = 72.dp
    val HistoryDropdownCornerRadius = 12.dp

    val SidebarWidth = 260.dp
    val SidebarProgressHeight = 2.dp

    val SheetCornerRadius = 20.dp
    val SheetMaxHeightRatio = 0.85f
    val SheetHeaderHeight = 56.dp
    val SheetFooterHeight = 76.dp
    val DiffCardCornerRadius = 12.dp

    val DialogWidth = 300.dp
    val DialogCornerRadius = 16.dp
    val DialogPadding = 24.dp

    val InlineEditTopOffset = 50.dp
    val InlineEditCornerRadius = 8.dp
    val TriangleSize = 8.dp

    // ── 字体 ────────────────────────────────────────────

    val SearchInputFontSize = 14.sp
    val ToggleFontSize = 12.sp
    val MatchCountFontSize = 12.sp
    val HistoryTextFontSize = 14.sp
    val SidebarTitleFontSize = 15.sp
    val SidebarItemFontSize = 14.sp
    val SheetTitleFontSize = 16.sp
    val ChapterTitleFontSize = 13.sp
    val DiffFontSize = 13.sp
    val DiffPrefixFontSize = 13.sp
    val DialogTitleFontSize = 18.sp
    val DialogDescFontSize = 14.sp
    val ButtonFontSize = 13.sp
    val FooterButtonFontSize = 15.sp
    val InlineEditFontSize = 14.sp
}
