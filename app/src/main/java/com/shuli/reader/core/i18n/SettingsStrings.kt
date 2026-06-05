package com.shuli.reader.core.i18n

/**
 * 高级设置、关于页、阅读统计字符串。
 */
interface SettingsStrings {
    val advancedSettings: String
    val gpuAcceleration: String
    val loggingEnabled: String
    val resetAllSettings: String
    val resetAllSettingsDesc: String
    val settingsResetSuccess: String
    val chapterFullText: String
    val aboutLabel: String
    val versionLabel: String
    val developerLabel: String
    val feedbackLabel: String
    val licenseLabel: String
    val checkUpdate: String
    val readingStats: String
    val statsEnable: String
    val statsEnableDesc: String
    val statsDailyTarget: String
    val resetStats: String
    val resetStatsDesc: String
    val viewStatsReport: String
    val statsTitle: String
    val totalBooksCount: String
    val totalReadingTime: String
    val todayReadingProgress: String
    val readingTargetMinutes: (Int) -> String
}
