package com.shuli.reader.feature.stats

import java.time.LocalDate

class StatsDateNavigator {

    fun next(granularity: StatsGranularity, current: LocalDate): LocalDate {
        return when (granularity) {
            StatsGranularity.DAY -> current.plusDays(1)
            StatsGranularity.WEEK -> current.plusWeeks(1)
            StatsGranularity.MONTH -> current.plusMonths(1)
            StatsGranularity.YEAR -> current.plusYears(1)
        }
    }

    fun prev(granularity: StatsGranularity, current: LocalDate): LocalDate {
        return when (granularity) {
            StatsGranularity.DAY -> current.minusDays(1)
            StatsGranularity.WEEK -> current.minusWeeks(1)
            StatsGranularity.MONTH -> current.minusMonths(1)
            StatsGranularity.YEAR -> current.minusYears(1)
        }
    }
}
