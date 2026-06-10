package com.shuli.reader.feature.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.stats.component.CalendarHeatmap
import com.shuli.reader.feature.stats.component.DateNavigator
import com.shuli.reader.feature.stats.component.DistributionChart
import com.shuli.reader.feature.stats.component.EmptyStatsState
import com.shuli.reader.feature.stats.component.GranularitySelector
import com.shuli.reader.feature.stats.component.HeroSection
import com.shuli.reader.feature.stats.component.HourlyHeatmap
import com.shuli.reader.feature.stats.component.ReadingStatusChart
import com.shuli.reader.feature.stats.component.ReadingTimeline
import com.shuli.reader.feature.stats.component.TopNList
import com.shuli.reader.feature.stats.component.WeeklyBarChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBackClick: () -> Unit,
    onBookClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalAppStrings.current.stats

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.statsTitle,
                        )
                    }
                },
                title = {
                    Text(
                        text = strings.statsTitle,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            GranularitySelector(
                selectedGranularity = uiState.navigation.granularity,
                onGranularityChange = viewModel::setGranularity,
            )

            DateNavigator(
                granularity = uiState.navigation.granularity,
                currentDate = uiState.navigation.currentDate,
                canGoNext = uiState.navigation.canGoNext,
                onNext = viewModel::goNext,
                onPrev = viewModel::goPrev,
            )

            if (!uiState.hasAnyData) {
                EmptyStatsState(
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        HeroSection(
                            heroState = uiState.hero,
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    when (uiState.navigation.granularity) {
                        StatsGranularity.DAY -> {
                            item {
                                HourlyHeatmap(
                                    hourlyMinutes = uiState.heatmap.hourlyMinutes,
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                            item {
                                ReadingTimeline(
                                    sessions = uiState.heatmap.timelineSessions,
                                    bookTitles = uiState.heatmap.timelineBookTitles,
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                        StatsGranularity.WEEK -> {
                            item {
                                uiState.heatmap.weekData?.let { data ->
                                    WeeklyBarChart(chartData = data)
                                    Spacer(Modifier.height(16.dp))
                                }
                            }
                            item {
                                ReadingTimeline(
                                    sessions = uiState.heatmap.timelineSessions,
                                    bookTitles = uiState.heatmap.timelineBookTitles,
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                        StatsGranularity.MONTH -> {
                            item {
                                CalendarHeatmap(
                                    cells = uiState.heatmap.heatmapData,
                                    granularity = StatsGranularity.MONTH,
                                    currentStreak = uiState.hero.currentStreak,
                                    dailyAvgMinutes = if (uiState.hero.activeDays > 0)
                                        uiState.hero.totalMinutes / uiState.hero.activeDays else 0L,
                                )
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                        StatsGranularity.YEAR -> {
                            item {
                                CalendarHeatmap(
                                    cells = uiState.heatmap.heatmapData,
                                    granularity = StatsGranularity.YEAR,
                                )
                                Spacer(Modifier.height(24.dp))
                            }
                            item {
                                DistributionChart(
                                    dimension = uiState.distribution.dimension,
                                    items = uiState.distribution.items,
                                    onDimensionChange = viewModel::setDistributionDim,
                                )
                                Spacer(Modifier.height(24.dp))
                            }
                            item {
                                TopNList(
                                    sort = uiState.topN.sort,
                                    books = uiState.topN.books,
                                    onSortChange = viewModel::setTopNSort,
                                    onBookClick = onBookClick,
                                )
                                Spacer(Modifier.height(24.dp))
                            }
                            item {
                                ReadingStatusChart(
                                    items = uiState.status.items,
                                )
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
