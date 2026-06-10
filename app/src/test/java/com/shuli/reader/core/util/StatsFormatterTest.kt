package com.shuli.reader.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class StatsFormatterTest {

    // ── formatDuration ──

    @Test
    fun formatDuration_zeroSeconds() {
        assertEquals("0m", StatsFormatter.formatDuration(0, Locale.CHINA))
    }

    @Test
    fun formatDuration_pureMinutes() {
        assertEquals("25m", StatsFormatter.formatDuration(1500, Locale.CHINA))
    }

    @Test
    fun formatDuration_hoursAndMinutes() {
        assertEquals("1h30m", StatsFormatter.formatDuration(5400, Locale.CHINA))
    }

    @Test
    fun formatDuration_exactHours() {
        assertEquals("2h", StatsFormatter.formatDuration(7200, Locale.CHINA))
    }

    @Test
    fun formatDuration_daysAndHours() {
        assertEquals("1d1h", StatsFormatter.formatDuration(90000, Locale.CHINA))
    }

    @Test
    fun formatDuration_exactDays() {
        assertEquals("1d", StatsFormatter.formatDuration(86400, Locale.CHINA))
    }

    @Test
    fun formatDuration_subMinute() {
        assertEquals("0m", StatsFormatter.formatDuration(30, Locale.CHINA))
    }

    // ── formatWords ──

    @Test
    fun formatWords_zhBelowTenThousand() {
        assertEquals("5000字", StatsFormatter.formatWords(5000, Locale.CHINA))
    }

    @Test
    fun formatWords_zhAboveTenThousand() {
        val result = StatsFormatter.formatWords(11840000, Locale.CHINA)
        assertEquals("≈1184.0万", result)
    }

    @Test
    fun formatWords_enBelowThousand() {
        assertEquals("500", StatsFormatter.formatWords(500, Locale.ENGLISH))
    }

    @Test
    fun formatWords_enAboveThousand() {
        assertEquals("≈1.5K", StatsFormatter.formatWords(1500, Locale.ENGLISH))
    }

    // ── formatPercent ──

    @Test
    fun formatPercent_integer() {
        assertEquals("75%", StatsFormatter.formatPercent(75.0f))
    }

    @Test
    fun formatPercent_decimal() {
        assertEquals("75.3%", StatsFormatter.formatPercent(75.3f))
    }

    // ── zeroOrNull ──

    @Test
    fun zeroOrNull_zero() {
        assertEquals("--", StatsFormatter.zeroOrNull(0))
    }

    @Test
    fun zeroOrNull_nonZero() {
        val result = StatsFormatter.zeroOrNull(3600)
        assertEquals("1h", result)
    }
}
