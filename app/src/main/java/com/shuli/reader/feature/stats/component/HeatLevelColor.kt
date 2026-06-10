package com.shuli.reader.feature.stats.component

import androidx.compose.ui.graphics.Color
import com.shuli.reader.feature.stats.HeatLevel

fun HeatLevel.color(isDark: Boolean): Color {
    return if (isDark) {
        when (this) {
            HeatLevel.L0 -> StatsColors.darkL0
            HeatLevel.L1 -> StatsColors.darkL1
            HeatLevel.L2 -> StatsColors.darkL2
            HeatLevel.L3 -> StatsColors.darkL3
            HeatLevel.L4 -> StatsColors.darkL4
            HeatLevel.L5 -> StatsColors.darkL5
        }
    } else {
        when (this) {
            HeatLevel.L0 -> StatsColors.lightL0
            HeatLevel.L1 -> StatsColors.lightL1
            HeatLevel.L2 -> StatsColors.lightL2
            HeatLevel.L3 -> StatsColors.lightL3
            HeatLevel.L4 -> StatsColors.lightL4
            HeatLevel.L5 -> StatsColors.lightL5
        }
    }
}
