package com.shuli.reader.core.util

import java.util.Locale
import kotlin.math.roundToInt

object StatsFormatter {

    fun formatDuration(seconds: Long, locale: Locale = Locale.getDefault()): String {
        if (seconds < 60) return "0m"
        val totalMinutes = seconds / 60
        val days = totalMinutes / 1440
        val hours = (totalMinutes % 1440) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> {
                if (hours > 0) "${days}d${hours}h" else "${days}d"
            }
            hours > 0 -> {
                if (minutes > 0) "${hours}h${minutes}m" else "${hours}h"
            }
            else -> "${minutes}m"
        }
    }

    fun formatWords(words: Long, locale: Locale = Locale.getDefault()): String {
        val lang = locale.language
        return when {
            lang == "zh" -> {
                if (words < 10_000) "${words}字"
                else {
                    val wan = words / 10_000.0
                    "≈${"%.1f".format(locale, wan)}万"
                }
            }
            else -> {
                if (words < 1_000) "$words"
                else {
                    val k = words / 1_000.0
                    "≈${"%.1f".format(locale, k)}K"
                }
            }
        }
    }

    fun formatPercent(value: Float): String {
        return if (value == value.roundToInt().toFloat()) {
            "${value.roundToInt()}%"
        } else {
            "${"%.1f".format(Locale.ROOT, value)}%"
        }
    }

    fun zeroOrNull(seconds: Long): String {
        return if (seconds == 0L) "--" else formatDuration(seconds)
    }
}
