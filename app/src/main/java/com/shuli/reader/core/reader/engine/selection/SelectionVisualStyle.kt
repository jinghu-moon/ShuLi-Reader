package com.shuli.reader.core.reader.engine.selection

/**
 * 阅读器文本选区视觉参数。
 */
internal object SelectionVisualStyle {
    const val HIGHLIGHT_COLOR: Int = 0x33F47B3D
    const val HANDLE_COLOR: Int = 0xFFFF7A2D.toInt()
    const val HANDLE_TOUCH_RADIUS: Float = 48f
    const val HANDLE_DOT_RADIUS: Float = 9f
    const val HANDLE_STEM_WIDTH: Float = 3f
    const val HIGHLIGHT_HORIZONTAL_PADDING: Float = 2f
    const val HIGHLIGHT_CORNER_RADIUS: Float = 2f
    const val MAGNIFIER_ZOOM: Float = 1.75f
    const val MAGNIFIER_WIDTH_DP: Float = 168f
    const val MAGNIFIER_HEIGHT_DP: Float = 82f
    const val MAGNIFIER_CORNER_RADIUS_DP: Float = 12f
    const val MAGNIFIER_EDGE_PADDING_DP: Float = 12f
    const val MAGNIFIER_HANDLE_GAP_DP: Float = 22f
    const val MAGNIFIER_BORDER_WIDTH_DP: Float = 1.5f
    const val MAGNIFIER_SHADOW_OFFSET_DP: Float = 2f
    const val MAGNIFIER_SHADOW_COLOR: Int = 0x33000000
    const val MAGNIFIER_BORDER_COLOR: Int = 0x40FF7A2D
}
