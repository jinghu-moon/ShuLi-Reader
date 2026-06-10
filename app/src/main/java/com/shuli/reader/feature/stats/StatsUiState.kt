package com.shuli.reader.feature.stats

import java.time.LocalDate

data class StatsUiState(
    val navigation: StatsNavigationState = StatsNavigationState(),
    val hero: StatsHeroState = StatsHeroState(),
    val heatmap: StatsHeatmapState = StatsHeatmapState(),
    val distribution: StatsDistributionState = StatsDistributionState(),
    val topN: StatsTopNState = StatsTopNState(),
    val status: StatsStatusState = StatsStatusState(),
    val hasAnyData: Boolean = false,
)

data class StatsNavigationState(
    val granularity: StatsGranularity = StatsGranularity.YEAR,
    val currentDate: LocalDate = LocalDate.now(),
    val canGoNext: Boolean = false,
)

data class StatsHeroState(
    val totalMinutes: Long = 0,
    val activeDays: Int = 0,
    val deltaPercent: Float = 0f,
    val deltaIsUp: Boolean = true,
    val currentStreak: Int = 0,
    val goalMinutes: Long = 0,
    val goalPercent: Int = 0,
    val dailyNeededMinutes: Long = 0,
)

data class StatsHeatmapState(
    val heatmapData: List<DailyHeatCell> = emptyList(),
    val hourlyMinutes: List<Int> = List(24) { 0 },
    val weekData: WeekChartData? = null,
    val timelineSessions: List<com.shuli.reader.core.database.entity.ReadingSessionEntity> = emptyList(),
    val timelineBookTitles: Map<Long, String> = emptyMap(),
)

data class StatsDistributionState(
    val dimension: DistributionDim = DistributionDim.AUTHOR,
    val items: List<DistributionItem> = emptyList(),
)

data class StatsTopNState(
    val sort: TopNSort = TopNSort.DURATION,
    val books: List<TopNBookItem> = emptyList(),
)

data class StatsStatusState(
    val items: List<StatusItem> = emptyList(),
)
