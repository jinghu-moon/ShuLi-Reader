package com.shuli.reader.feature.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsGranularityTest {

    @Test
    fun dayDateRange_returnsSingleDay() {
        val date = LocalDate.of(2026, 6, 9)
        val range = StatsGranularity.DAY.dateRange(date)
        assertEquals(DateKeyRange(20260609, 20260609), range)
    }

    @Test
    fun weekDateRange_returnsMondayToSunday() {
        val wednesday = LocalDate.of(2026, 6, 10)
        val range = StatsGranularity.WEEK.dateRange(wednesday)
        assertEquals(DateKeyRange(20260608, 20260614), range)
    }

    @Test
    fun monthDateRange_returnsFullMonth() {
        val date = LocalDate.of(2026, 2, 15)
        val range = StatsGranularity.MONTH.dateRange(date)
        assertEquals(DateKeyRange(20260201, 20260228), range)
    }

    @Test
    fun yearDateRange_returnsFullYear() {
        val date = LocalDate.of(2026, 6, 9)
        val range = StatsGranularity.YEAR.dateRange(date)
        assertEquals(DateKeyRange(20260101, 20261231), range)
    }

    @Test
    fun previousRange_day() {
        val date = LocalDate.of(2026, 6, 9)
        val range = StatsGranularity.DAY.previousRange(date)
        assertEquals(DateKeyRange(20260608, 20260608), range)
    }

    @Test
    fun previousRange_week() {
        val date = LocalDate.of(2026, 6, 10)
        val range = StatsGranularity.WEEK.previousRange(date)
        assertEquals(DateKeyRange(20260601, 20260607), range)
    }

    @Test
    fun previousRange_month() {
        val date = LocalDate.of(2026, 3, 15)
        val range = StatsGranularity.MONTH.previousRange(date)
        assertEquals(DateKeyRange(20260201, 20260228), range)
    }

    @Test
    fun previousRange_year() {
        val date = LocalDate.of(2026, 6, 9)
        val range = StatsGranularity.YEAR.previousRange(date)
        assertEquals(DateKeyRange(20250101, 20251231), range)
    }

    @Test
    fun canGoNext_todayReturnsFalse() {
        val today = LocalDate.now()
        assertFalse(StatsGranularity.DAY.canGoNext(today))
    }

    @Test
    fun canGoNext_yesterdayReturnsTrue() {
        val yesterday = LocalDate.now().minusDays(1)
        assertTrue(StatsGranularity.DAY.canGoNext(yesterday))
    }
}
