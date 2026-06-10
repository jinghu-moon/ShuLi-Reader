package com.shuli.reader.feature.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsDateNavigatorTest {

    private val nav = StatsDateNavigator()
    private val base = LocalDate.of(2026, 6, 9)

    @Test
    fun nextDay() {
        assertEquals(LocalDate.of(2026, 6, 10), nav.next(StatsGranularity.DAY, base))
    }

    @Test
    fun prevDay() {
        assertEquals(LocalDate.of(2026, 6, 8), nav.prev(StatsGranularity.DAY, base))
    }

    @Test
    fun nextWeek() {
        assertEquals(LocalDate.of(2026, 6, 16), nav.next(StatsGranularity.WEEK, base))
    }

    @Test
    fun prevWeek() {
        assertEquals(LocalDate.of(2026, 6, 2), nav.prev(StatsGranularity.WEEK, base))
    }

    @Test
    fun nextMonth() {
        assertEquals(LocalDate.of(2026, 7, 9), nav.next(StatsGranularity.MONTH, base))
    }

    @Test
    fun prevMonth() {
        assertEquals(LocalDate.of(2026, 5, 9), nav.prev(StatsGranularity.MONTH, base))
    }

    @Test
    fun nextYear() {
        assertEquals(LocalDate.of(2027, 6, 9), nav.next(StatsGranularity.YEAR, base))
    }

    @Test
    fun prevYear() {
        assertEquals(LocalDate.of(2025, 6, 9), nav.prev(StatsGranularity.YEAR, base))
    }
}
