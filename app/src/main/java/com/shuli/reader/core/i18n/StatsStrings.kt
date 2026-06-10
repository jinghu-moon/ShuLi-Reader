package com.shuli.reader.core.i18n

interface StatsStrings {
    val statsTitle: String
    val granularityDay: String
    val granularityWeek: String
    val granularityMonth: String
    val granularityYear: String
    val today: String
    val thisWeek: String
    val lastWeek: String
    val thisMonth: String
    val thisYear: String
    val cumulativeLabel: String
    val vsPrevious: String
    val words: String
    val chapters: String
    val wordsPerMinute: String
    val activeDays: String
    val goalTitle: (String, Int) -> String
    val goalProgress: (String) -> String
    val dailyNeededHint: (Long) -> String
    val hourlyPeak: (Int, Int) -> String
    val heatmapTitle: (String) -> String
    val heatmapSummary: (Int, Long) -> String
    val longestStreak: String
    val currentStreak: (Int) -> String
    val dailyAvg: String
    val distributionTitle: String
    val dimAuthor: String
    val dimGroup: String
    val dimFormat: String
    val dimWords: String
    val topNTitle: String
    val sortByDuration: String
    val sortByBookmarks: String
    val sortByNotes: String
    val sortBySpeed: String
    val readingStatus: String
    val statusReading: String
    val statusFinished: String
    val statusPaused: String
    val statusWantToRead: String
    val less: String
    val more: String
    val notRead: String
    val emptyStateHint: String
    val estimatedPrefix: String
    val readingTimeline: String
    val chapterSingle: (Int) -> String
    val chapterRange: (Int, Int) -> String
}
