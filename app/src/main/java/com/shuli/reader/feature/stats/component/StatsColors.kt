package com.shuli.reader.feature.stats.component

import androidx.compose.ui.graphics.Color

object StatsColors {
    // Nord Frost 蓝轴色系 — 热力图
    // L0: 未阅读, L1: 微量, L2: 轻度, L3: 中度, L4: 重度, L5: 极高

    // 亮色模式
    val lightL0 = Color(0xFFECEFF4) // Snow Storm
    val lightL1 = Color(0xFFD8DEE9)
    val lightL2 = Color(0xFF88C0D0) // Frost 浅
    val lightL3 = Color(0xFF81A1C1) // Frost
    val lightL4 = Color(0xFF5E81AC) // Frost Deep
    val lightL5 = Color(0xFF4C7FBF)

    // 暗色模式
    val darkL0 = Color(0xFF3B4252)  // Polar Night 中段
    val darkL1 = Color(0xFF434C5E)
    val darkL2 = Color(0xFF5E81AC)  // Frost Deep
    val darkL3 = Color(0xFF81A1C1)  // Frost
    val darkL4 = Color(0xFF88C0D0)  // Frost 浅
    val darkL5 = Color(0xFF8FBCBB)  // Frost 最浅

    // 分布图语义色
    val txtFormatColor = Color(0xFFD08770)   // Nord Aurora Orange
    val epubFormatColor = Color(0xFF5E81AC)  // Nord Frost Deep

    // 阅读状态色
    val statusReading = Color(0xFF5E81AC)
    val statusFinished = Color(0xFF2D7A52)
    val statusPaused = Color(0xFFD08770)
    val statusWantToRead = Color(0xFF81A1C1)

    // 目标环色
    val goalRingTrack = Color(0xFF3B4252).copy(alpha = 0.2f)
    val goalRingProgress = Color(0xFF5E81AC)

    // 周柱状图色
    val barThisWeek = Color(0xFF5E81AC)
    val barLastWeek = Color(0xFFD8DEE9)
    val barDarkLastWeek = Color(0xFF434C5E)
}
