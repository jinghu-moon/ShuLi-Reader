package com.shuli.reader.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuli.reader.core.data.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val statsRepository: StatsRepository,
    private val userPreferences: UserPreferences? = null,
    private val clock: StatsClock = StatsClock.System,
) : ViewModel() {

    private val granularity = MutableStateFlow(StatsGranularity.YEAR)
    private val currentDate = MutableStateFlow(clock.now())
    private val distributionDim = MutableStateFlow(DistributionDim.AUTHOR)
    private val topNSort = MutableStateFlow(TopNSort.DURATION)

    private val dateKeyRange = combine(granularity, currentDate) { gran, date ->
        gran.dateRange(date)
    }

    private val prevRange = combine(granularity, currentDate) { gran, date ->
        gran.previousRange(date)
    }

    private val navState = combine(granularity, currentDate) { gran, date ->
        StatsNavigationState(
            granularity = gran,
            currentDate = date,
            canGoNext = canGoNext(gran, date),
        )
    }

    private fun canGoNext(gran: StatsGranularity, date: LocalDate): Boolean {
        val periodEnd = when (gran) {
            StatsGranularity.DAY -> date
            StatsGranularity.WEEK -> {
                val range = gran.dateRange(date)
                dateKeyToLocalDate(range.end)
            }
            StatsGranularity.MONTH -> date.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth())
            StatsGranularity.YEAR -> date.with(java.time.temporal.TemporalAdjusters.lastDayOfYear())
        }
        return periodEnd.isBefore(clock.now())
    }

    private val heatmapFlow: kotlinx.coroutines.flow.Flow<List<DailyHeatCell>> =
        dateKeyRange.flatMapLatest { range ->
            statsRepository.getHeatmapData(range.start, range.end)
        }

    private val heroFlow: kotlinx.coroutines.flow.Flow<HeroMetrics> =
        combine(dateKeyRange, prevRange) { cur, prev -> cur to prev }
            .flatMapLatest { (cur, prev) ->
                flow {
                    emit(statsRepository.getHeroMetrics(cur.start, cur.end, prev.start, prev.end))
                }
            }

    private val hourlyFlow: kotlinx.coroutines.flow.Flow<List<Int>> =
        combine(dateKeyRange, granularity) { range, gran ->
            if (gran == StatsGranularity.DAY) {
                statsRepository.getHourlyData(range.start)
            } else {
                flowOf(List(24) { 0 })
            }
        }.flatMapLatest { flow -> flow }

    private val weeklyChartFlow: kotlinx.coroutines.flow.Flow<WeekChartData?> =
        combine(dateKeyRange, granularity, prevRange) { cur, gran, prev ->
            if (gran == StatsGranularity.WEEK) {
                statsRepository.getWeeklyChartData(cur.start, cur.end, prev.start, prev.end)
            } else {
                flowOf(null)
            }
        }.flatMapLatest { flow -> flow }

    private val timelineFlow: kotlinx.coroutines.flow.Flow<Pair<List<com.shuli.reader.core.database.entity.ReadingSessionEntity>, Map<Long, String>>> =
        dateKeyRange.flatMapLatest { range ->
            combine(
                statsRepository.getSessionsForTimeline(range.start, range.end),
                statsRepository.getBookTitles(),
            ) { sessions, titles -> sessions to titles }
        }

    private val distributionState: kotlinx.coroutines.flow.Flow<StatsDistributionState> =
        distributionDim.flatMapLatest { dim ->
            statsRepository.getDistribution(dim).map { items ->
                StatsDistributionState(dimension = dim, items = items)
            }
        }

    private val topNState: kotlinx.coroutines.flow.Flow<StatsTopNState> =
        topNSort.flatMapLatest { sort ->
            statsRepository.getTopN(sort).map { books ->
                StatsTopNState(sort = sort, books = books)
            }
        }

    private val statusState: kotlinx.coroutines.flow.Flow<StatsStatusState> =
        statsRepository.getReadingStatusDistribution().map { items ->
            StatsStatusState(items = items)
        }

    private val _uiState = MutableStateFlow(StatsUiState())

    val uiState: StateFlow<StatsUiState> = _uiState

    init {
        // 监听导航状态变化
        viewModelScope.launch {
            navState.collect { nav ->
                _uiState.value = _uiState.value.copy(navigation = nav)
            }
        }

        // 监听热力图数据
        viewModelScope.launch {
            heatmapFlow.collect { cells ->
                _uiState.value = _uiState.value.copy(
                    heatmap = _uiState.value.heatmap.copy(heatmapData = cells),
                    hasAnyData = cells.any { it.minutes > 0 },
                )
            }
        }

        // 监听 Hero 指标
        viewModelScope.launch {
            heroFlow.collect { hero ->
                _uiState.value = _uiState.value.copy(hero = mapHero(hero))
            }
        }

        // 监听小时数据
        viewModelScope.launch {
            hourlyFlow.collect { hourly ->
                _uiState.value = _uiState.value.copy(
                    heatmap = _uiState.value.heatmap.copy(hourlyMinutes = hourly),
                )
            }
        }

        // 监听周数据
        viewModelScope.launch {
            weeklyChartFlow.collect { weekly ->
                _uiState.value = _uiState.value.copy(
                    heatmap = _uiState.value.heatmap.copy(weekData = weekly),
                )
            }
        }

        // 监听时间轴数据
        viewModelScope.launch {
            timelineFlow.collect { (sessions, titles) ->
                _uiState.value = _uiState.value.copy(
                    heatmap = _uiState.value.heatmap.copy(
                        timelineSessions = sessions,
                        timelineBookTitles = titles,
                    ),
                )
            }
        }

        // 监听分布数据
        viewModelScope.launch {
            distributionState.collect { dist ->
                _uiState.value = _uiState.value.copy(distribution = dist)
            }
        }

        // 监听 TopN 数据
        viewModelScope.launch {
            topNState.collect { topN ->
                _uiState.value = _uiState.value.copy(
                    topN = topN,
                    hasAnyData = _uiState.value.heatmap.heatmapData.any { it.minutes > 0 } || topN.books.isNotEmpty(),
                )
            }
        }

        // 监听状态分布
        viewModelScope.launch {
            statusState.collect { status ->
                _uiState.value = _uiState.value.copy(status = status)
            }
        }
    }

    private fun mapHero(metrics: HeroMetrics): StatsHeroState = StatsHeroState(
        totalMinutes = metrics.totalMinutes,
        activeDays = metrics.activeDays,
        currentStreak = metrics.currentStreak,
        deltaPercent = metrics.deltaPercent,
        deltaIsUp = metrics.deltaIsUp,
        goalMinutes = metrics.goalMinutes,
        goalPercent = metrics.goalPercent,
        dailyNeededMinutes = metrics.dailyNeededMinutes,
    )

    fun setGranularity(gran: StatsGranularity) {
        granularity.value = gran
    }

    fun goNext() {
        if (!canGoNext(granularity.value, currentDate.value)) return
        currentDate.value = StatsDateNavigator().next(granularity.value, currentDate.value)
    }

    fun goPrev() {
        currentDate.value = StatsDateNavigator().prev(granularity.value, currentDate.value)
    }

    fun setDistributionDim(dim: DistributionDim) {
        distributionDim.value = dim
    }

    fun setTopNSort(sort: TopNSort) {
        topNSort.value = sort
    }
}

interface StatsClock {
    fun now(): LocalDate

    object System : StatsClock {
        override fun now(): LocalDate = LocalDate.now()
    }
}
