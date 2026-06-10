package com.shuli.reader.feature.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class DateKeyRange(val start: Int, val end: Int) {
    operator fun contains(dateKey: Int) = dateKey in start..end
}

enum class StatsGranularity {
    DAY,
    WEEK,
    MONTH,
    YEAR;

    fun dateRange(date: LocalDate): DateKeyRange {
        return when (this) {
            DAY -> DateKeyRange(date.toDateKey(), date.toDateKey())
            WEEK -> {
                val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weekEnd = weekStart.plusDays(6)
                DateKeyRange(weekStart.toDateKey(), weekEnd.toDateKey())
            }
            MONTH -> {
                val monthStart = date.withDayOfMonth(1)
                val monthEnd = date.with(TemporalAdjusters.lastDayOfMonth())
                DateKeyRange(monthStart.toDateKey(), monthEnd.toDateKey())
            }
            YEAR -> {
                val yearStart = date.withDayOfYear(1)
                val yearEnd = date.with(TemporalAdjusters.lastDayOfYear())
                DateKeyRange(yearStart.toDateKey(), yearEnd.toDateKey())
            }
        }
    }

    fun previousRange(date: LocalDate): DateKeyRange {
        val prevDate = when (this) {
            DAY -> date.minusDays(1)
            WEEK -> date.minusWeeks(1)
            MONTH -> date.minusMonths(1)
            YEAR -> date.minusYears(1)
        }
        return dateRange(prevDate)
    }

    fun dateText(date: LocalDate, locale: java.util.Locale): String {
        return when (this) {
            DAY -> date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd", locale))
            WEEK -> {
                val range = dateRange(date)
                val startDate = dateKeyToLocalDate(range.start)
                val endDate = dateKeyToLocalDate(range.end)
                val fmt = java.time.format.DateTimeFormatter.ofPattern("M/d", locale)
                "${startDate.format(fmt)} - ${endDate.format(fmt)}"
            }
            MONTH -> date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM", locale))
            YEAR -> "${date.year}"
        }
    }

    fun canGoNext(date: LocalDate): Boolean {
        val periodEnd = when (this) {
            DAY -> date
            WEEK -> dateRange(date).let { dateKeyToLocalDate(it.end) }
            MONTH -> date.with(TemporalAdjusters.lastDayOfMonth())
            YEAR -> date.with(TemporalAdjusters.lastDayOfYear())
        }
        return periodEnd.isBefore(LocalDate.now())
    }
}

fun LocalDate.toDateKey(): Int {
    return year * 10000 + monthValue * 100 + dayOfMonth
}

fun dateKeyToLocalDate(dateKey: Int): LocalDate {
    val year = dateKey / 10000
    val month = (dateKey % 10000) / 100
    val day = dateKey % 100
    return LocalDate.of(year, month, day)
}
