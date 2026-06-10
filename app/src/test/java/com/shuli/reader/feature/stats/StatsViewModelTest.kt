package com.shuli.reader.feature.stats

import com.shuli.reader.MainDispatcherRule
import com.shuli.reader.core.data.UserPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private lateinit var repo: StatsRepository
    private lateinit var prefs: UserPreferences
    private lateinit var vm: StatsViewModel
    private val fixedToday: LocalDate = LocalDate.of(2026, 6, 10)
    private val fixedClock = object : StatsClock {
        override fun now(): LocalDate = fixedToday
    }

    private lateinit var testScope: CoroutineScope
    private lateinit var collectJob: Job

    @Before
    fun setup() {
        repo = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        defaultMockSetup()
        vm = StatsViewModel(repo, prefs, fixedClock)
        testScope = CoroutineScope(testDispatcher + kotlinx.coroutines.SupervisorJob())
        collectJob = testScope.launch { vm.uiState.collect {} }
    }

    private fun defaultMockSetup() {
        every { repo.getHeatmapData(any(), any()) } returns flowOf(emptyList())
        coEvery { repo.getHeroMetrics(any(), any(), any(), any()) } returns HeroMetrics()
        every { repo.getHourlyData(any()) } returns flowOf(List(24) { 0 })
        every { repo.getWeeklyChartData(any(), any(), any(), any()) } returns
            flowOf(WeekChartData(List(7) { 0L }, List(7) { 0L }))
        every { repo.getDistribution(any()) } returns flowOf(emptyList())
        every { repo.getTopN(any(), any()) } returns flowOf(emptyList())
        every { repo.getReadingStatusDistribution() } returns flowOf(emptyList())
        every { prefs.readingDailyTarget } returns flowOf(30)
    }

    private fun reinitVm(): StatsUiState {
        collectJob.cancel()
        vm = StatsViewModel(repo, prefs, fixedClock)
        collectJob = testScope.launch { vm.uiState.collect {} }
        return vm.uiState.value
    }

    @After
    fun teardown() {
        collectJob.cancel()
        testScope.cancel()
    }

    private fun readState(): StatsUiState {
        // 等待状态更新
        testDispatcher.scheduler.advanceUntilIdle()
        return vm.uiState.value
    }

    @Test
    fun initialState_isYearTodayWithNoData() = runTest(testDispatcher) {
        val state = readState()
        assertEquals(StatsGranularity.YEAR, state.navigation.granularity)
        assertEquals(fixedToday, state.navigation.currentDate)
        assertFalse(state.hasAnyData)
    }

    @Test
    fun setGranularity_updatesNavigation() = runTest(testDispatcher) {
        vm.setGranularity(StatsGranularity.DAY)
        val state = readState()
        assertEquals(StatsGranularity.DAY, state.navigation.granularity)
    }

    @Test
    fun setGranularity_triggersNewDataQuery() = runTest(testDispatcher) {
        val startSlot = io.mockk.slot<Int>()
        val endSlot = io.mockk.slot<Int>()
        every { repo.getHeatmapData(capture(startSlot), capture(endSlot)) } returns flowOf(emptyList())
        vm.setGranularity(StatsGranularity.DAY)
        readState()
        assertEquals(startSlot.captured, endSlot.captured)
    }

    @Test
    fun cycleThroughAllGranularities() = runTest(testDispatcher) {
        vm.setGranularity(StatsGranularity.WEEK)
        assertEquals(StatsGranularity.WEEK, readState().navigation.granularity)
        vm.setGranularity(StatsGranularity.MONTH)
        assertEquals(StatsGranularity.MONTH, readState().navigation.granularity)
        vm.setGranularity(StatsGranularity.YEAR)
        assertEquals(StatsGranularity.YEAR, readState().navigation.granularity)
    }

    @Test
    fun goPrev_advancesDateBackByOneDay() = runTest(testDispatcher) {
        vm.setGranularity(StatsGranularity.DAY)
        val before = readState().navigation.currentDate
        vm.goPrev()
        val after = readState().navigation.currentDate
        assertEquals(before.minusDays(1), after)
    }

    @Test
    fun goPrev_weekSubtractsSevenDays() = runTest(testDispatcher) {
        vm.setGranularity(StatsGranularity.WEEK)
        val before = readState().navigation.currentDate
        vm.goPrev()
        val after = readState().navigation.currentDate
        assertEquals(before.minusWeeks(1), after)
    }

    @Test
    fun goNext_whenCurrentIsToday_doesNothing() = runTest(testDispatcher) {
        vm.setGranularity(StatsGranularity.DAY)
        val before = readState().navigation.currentDate
        vm.goNext()
        val after = readState().navigation.currentDate
        assertEquals(before, after)
    }

    @Test
    fun goNext_whenPastToday_advancesDate() = runTest(testDispatcher) {
        vm.setGranularity(StatsGranularity.DAY)
        vm.goPrev()
        val pastDate = readState().navigation.currentDate
        vm.goNext()
        val after = readState().navigation.currentDate
        assertEquals(pastDate.plusDays(1), after)
    }

    @Test
    fun canGoNext_falseWhenCurrentIsToday() = runTest(testDispatcher) {
        vm.setGranularity(StatsGranularity.DAY)
        val state = readState()
        assertFalse(state.navigation.canGoNext)
    }

    @Test
    fun canGoNext_trueWhenCurrentIsPast() = runTest(testDispatcher) {
        vm.setGranularity(StatsGranularity.DAY)
        vm.goPrev()
        val state = readState()
        assertTrue(state.navigation.canGoNext)
    }

    @Test
    fun dateKeyRange_yearCoversFullYear() = runTest(testDispatcher) {
        vm.setGranularity(StatsGranularity.YEAR)
        val state = readState()
        val date = state.navigation.currentDate
        val range = StatsGranularity.YEAR.dateRange(date)
        assertEquals(date.withDayOfYear(1).year * 10000 + 101, range.start)
    }

    @Test
    fun dateKeyRange_dayIsSingleDay() = runTest(testDispatcher) {
        vm.setGranularity(StatsGranularity.DAY)
        val state = readState()
        val date = state.navigation.currentDate
        val range = StatsGranularity.DAY.dateRange(date)
        assertEquals(range.start, range.end)
    }

    @Test
    fun flatMapLatest_cancelsOldSubscription() = runTest(testDispatcher) {
        val emissions = mutableListOf<List<DailyHeatCell>>()
        every { repo.getHeatmapData(any(), any()) } returns flowOf(
            listOf(DailyHeatCell(20260101, 60, HeatLevel.L3)),
        )
        val job = testScope.launch {
            vm.uiState.collect { emissions.add(it.heatmap.heatmapData) }
        }
        vm.setGranularity(StatsGranularity.WEEK)
        vm.setGranularity(StatsGranularity.MONTH)
        vm.setGranularity(StatsGranularity.DAY)
        readState()
        job.cancel()
        assertTrue(emissions.isNotEmpty())
    }

    @Test
    fun hasAnyData_trueWhenHeatmapHasMinutes() = runTest(testDispatcher) {
        every { repo.getHeatmapData(any(), any()) } returns flowOf(
            listOf(DailyHeatCell(20260610, 120, HeatLevel.L4)),
        )
        val state = reinitVm()
        assertTrue(state.hasAnyData)
    }

    @Test
    fun hasAnyData_trueWhenTopNHasBooks() = runTest(testDispatcher) {
        every { repo.getTopN(any(), any()) } returns flowOf(
            listOf(TopNBookItem(1L, "B1", "A", 100)),
        )
        val state = reinitVm()
        assertTrue(state.hasAnyData)
    }

    @Test
    fun heatmapData_mappedToState() = runTest(testDispatcher) {
        val cells = listOf(
            DailyHeatCell(20260610, 60, HeatLevel.L3),
            DailyHeatCell(20260609, 30, HeatLevel.L2),
        )
        every { repo.getHeatmapData(any(), any()) } returns flowOf(cells)
        val state = reinitVm()
        assertEquals(2, state.heatmap.heatmapData.size)
    }

    @Test
    fun heroMetrics_mappedToState() = runTest(testDispatcher) {
        coEvery { repo.getHeroMetrics(any(), any(), any(), any()) } returns HeroMetrics(
            totalMinutes = 500,
            activeDays = 10,
            currentStreak = 3,
            goalPercent = 75,
            goalMinutes = 600,
            dailyNeededMinutes = 20,
        )
        val state = reinitVm()
        assertEquals(500L, state.hero.totalMinutes)
        assertEquals(10, state.hero.activeDays)
        assertEquals(3, state.hero.currentStreak)
        assertEquals(75, state.hero.goalPercent)
        assertEquals(600L, state.hero.goalMinutes)
        assertEquals(20L, state.hero.dailyNeededMinutes)
    }

    @Test
    fun setTopNSort_updatesSortInState() = runTest(testDispatcher) {
        vm.setTopNSort(TopNSort.BOOKMARKS)
        val state = readState()
        assertEquals(TopNSort.BOOKMARKS, state.topN.sort)
    }

    @Test
    fun setDistributionDim_updatesDimInState() = runTest(testDispatcher) {
        vm.setDistributionDim(DistributionDim.FORMAT)
        val state = readState()
        assertEquals(DistributionDim.FORMAT, state.distribution.dimension)
    }
}
