package com.shuli.reader.core.i18n

import androidx.compose.runtime.staticCompositionLocalOf

sealed interface AppStrings {
    // 基础导航与通用
    val appName: String
    val bookshelf: String
    val settings: String
    val searchPlaceholder: String
    val todayReading: String
    val noBooksFound: String
    val emptyBookshelf: String
    val searchIconDesc: String
    val sortIconDesc: String
    val viewModeIconDesc: String
    val moreIconDesc: String
    val backIconDesc: String
    val clearIconDesc: String
    val loading: String

    // 外观与通用配置
    val appearance: String
    val themeModeLabel: String
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val themePaper: String
    val appFontLabel: String
    val appFontSystem: String
    val appFontLxgw: String
    val languageLabel: String
    val languageCn: String
    val languageTw: String
    val languageEn: String

    // 一、阅读器显示偏好
    val readerPreferences: String
    val defaultFontSize: String
    val defaultLineSpacing: String
    val lineSpacingCompact: String
    val lineSpacingMedium: String
    val lineSpacingWide: String
    val defaultPageAnim: String
    val pageAnimOverlay: String
    val pageAnimSlide: String
    val pageAnimSimulation: String
    val pageAnimFade: String
    val pageAnimNone: String
    val pageTurnDirection: String
    val pageTurnHorizontal: String
    val pageTurnVertical: String

    // 二、书库与导入设置
    val libraryImportSettings: String
    val duplicateCheck: String
    val duplicateCheckDesc: String
    val importCopy: String
    val importCopyDesc: String
    val clearTempCache: String
    val clearTempCacheDesc: String
    val clearCacheSuccess: String

    // 三、阅读统计
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

    // 四、同步设置
    val syncSettings: String
    val syncMethod: String
    val syncMethodLocal: String
    val syncMethodWebdav: String
    val webdavUrl: String
    val webdavUser: String
    val webdavPassword: String
    val testConnection: String
    val syncNow: String

    // 五、朗读设置 (TTS)
    val ttsSettings: String
    val ttsSpeed: String
    val ttsPitch: String
    val ttsAutoPage: String
    val ttsHighlightSentence: String

    // 六、高级设置
    val advancedSettings: String
    val gpuAcceleration: String
    val loggingEnabled: String
    val resetAllSettings: String
    val resetAllSettingsDesc: String
    val settingsResetSuccess: String

    // 七、关于与版权
    val aboutLabel: String
    val versionLabel: String
    val developerLabel: String
    val feedbackLabel: String
    val licenseLabel: String
    val checkUpdate: String

    // 交互响应提示
    val saveSuccess: String
    val saveFailed: String

    val selectAll: String
    val deselectAll: String
    val importSelected: (Int) -> String
    val alreadyLatestVersion: String
    val folderImportDesc: String

    val bookDeleted: String
    val importSuccess: String
    val bookAlreadyInShelf: String
    val importSuccessCount: (Int) -> String
    val importSuccessWithSkipped: (Int, Int) -> String
    val importSuccessWithFailed: (Int, Int) -> String
    val importSuccessWithBoth: (Int, Int, Int) -> String
    val importFailed: (String) -> String

    // 简体中文实现
    data object ZhHans : AppStrings {
        override val appName = "书里阅读器"
        override val bookshelf = "书架"
        override val settings = "设置"
        override val searchPlaceholder = "输入书名进行搜索..."
        override val todayReading = "今日"
        override val noBooksFound = "该文件夹下未找到 TXT 或 EPUB 文件"
        override val emptyBookshelf = "书架空空如也，点击右下角按钮导入书籍"
        override val searchIconDesc = "搜索"
        override val sortIconDesc = "排序"
        override val viewModeIconDesc = "切换视图"
        override val moreIconDesc = "更多"
        override val backIconDesc = "返回"
        override val clearIconDesc = "清除"
        override val loading = "加载中..."

        override val appearance = "外观"
        override val themeModeLabel = "深浅主题"
        override val themeSystem = "跟随系统"
        override val themeLight = "浅色模式"
        override val themeDark = "深色模式"
        override val themePaper = "纸质护眼"
        override val appFontLabel = "界面字体"
        override val appFontSystem = "系统默认"
        override val appFontLxgw = "霞鹜文楷"
        override val languageLabel = "界面语言"
        override val languageCn = "简体中文"
        override val languageTw = "繁體中文"
        override val languageEn = "English"

        override val readerPreferences = "阅读器显示偏好"
        override val defaultFontSize = "默认字号缩放"
        override val defaultLineSpacing = "默认行距选择"
        override val lineSpacingCompact = "紧凑 (1.2)"
        override val lineSpacingMedium = "适中 (1.5)"
        override val lineSpacingWide = "宽敞 (1.8)"
        override val defaultPageAnim = "默认翻页动画"
        override val pageAnimOverlay = "覆盖"
        override val pageAnimSlide = "滑动"
        override val pageAnimSimulation = "仿真"
        override val pageAnimFade = "淡入淡出"
        override val pageAnimNone = "无动画"
        override val pageTurnDirection = "翻页方向"
        override val pageTurnHorizontal = "左右滑动"
        override val pageTurnVertical = "上下滚动"

        override val libraryImportSettings = "书库与导入设置"
        override val duplicateCheck = "自动查重开关"
        override val duplicateCheckDesc = "导入重复书籍时自动高亮定位"
        override val importCopy = "导入时复制文件"
        override val importCopyDesc = "复制书籍到应用私有目录"
        override val clearTempCache = "清除临时缓存"
        override val clearTempCacheDesc = "清理书籍导入解压过程中产生的临时缓存"
        override val clearCacheSuccess = "缓存清理成功"

        override val readingStats = "阅读统计"
        override val statsEnable = "阅读时长统计"
        override val statsEnableDesc = "开启记录每日/每周/每月阅读时长"
        override val statsDailyTarget = "每日阅读目标"
        override val resetStats = "重置统计数据"
        override val resetStatsDesc = "清除本地所有阅读记录"
        override val viewStatsReport = "查看详细阅读报告"
        override val statsTitle = "阅读统计看板"
        override val totalBooksCount = "藏书总数"
        override val totalReadingTime = "累计阅读时长"
        override val todayReadingProgress = "今日阅读进度"

        override val syncSettings = "同步设置"
        override val syncMethod = "同步方式"
        override val syncMethodLocal = "本地备份"
        override val syncMethodWebdav = "WebDAV 同步"
        override val webdavUrl = "WebDAV 服务器地址"
        override val webdavUser = "用户名"
        override val webdavPassword = "密码"
        override val testConnection = "测试连接"
        override val syncNow = "立即同步"

        override val ttsSettings = "朗读设置 (TTS)"
        override val ttsSpeed = "语速调节"
        override val ttsPitch = "音调调节"
        override val ttsAutoPage = "自动翻页"
        override val ttsHighlightSentence = "高亮当前句子"

        override val advancedSettings = "高级设置"
        override val gpuAcceleration = "GPU 硬件加速"
        override val loggingEnabled = "调试运行日志"
        override val resetAllSettings = "重置所有设置"
        override val resetAllSettingsDesc = "将应用设置项全部恢复为默认值"
        override val settingsResetSuccess = "设置重置成功"

        override val aboutLabel = "关于与版权"
        override val versionLabel = "软件版本"
        override val developerLabel = "开发者"
        override val feedbackLabel = "反馈建议"
        override val licenseLabel = "开源许可证 (AGPL-3.0)"
        override val checkUpdate = "检查更新"

        override val saveSuccess = "设置保存成功"
        override val saveFailed = "设置保存失败"

        override val selectAll = "全选"
        override val deselectAll = "取消全选"
        override val importSelected = { count: Int -> "导入所选 ($count)" }
        override val alreadyLatestVersion = "已是最新版本"
        override val folderImportDesc = "自动扫描文件夹内所有可导入的书籍"

        override val bookDeleted = "已删除"
        override val importSuccess = "导入成功"
        override val bookAlreadyInShelf = "书籍已在书架中"
        override val importSuccessCount = { count: Int -> "成功导入 $count 本书" }
        override val importSuccessWithSkipped = { success: Int, skipped: Int -> "成功导入 $success 本，有 $skipped 本已存在跳过" }
        override val importSuccessWithFailed = { success: Int, failed: Int -> "成功导入 $success 本，失败 $failed 本" }
        override val importSuccessWithBoth = { success: Int, skipped: Int, failed: Int -> "成功导入 $success 本，已存在 $skipped 本，失败 $failed 本" }
        override val importFailed = { error: String -> "导入失败: $error" }
    }

    // 繁体中文实现（继承并覆盖差异项）
    data object ZhHant : AppStrings {
        override val appName = "書裡閱讀器"
        override val bookshelf = "書架"
        override val settings = "設定"
        override val searchPlaceholder = "輸入書名進行搜尋..."
        override val todayReading = "今日"
        override val noBooksFound = "該資料夾下未找到 TXT 或 EPUB 檔案"
        override val emptyBookshelf = "書架空空如也，點擊右下角按鈕匯入書籍"
        override val searchIconDesc = "搜尋"
        override val sortIconDesc = "排序"
        override val viewModeIconDesc = "切換檢視"
        override val moreIconDesc = "更多"
        override val backIconDesc = "返回"
        override val clearIconDesc = "清除"
        override val loading = "載入中..."

        override val appearance = "外觀"
        override val themeModeLabel = "深淺主題"
        override val themeSystem = "跟隨系統"
        override val themeLight = "淺色模式"
        override val themeDark = "深色模式"
        override val themePaper = "紙質護眼"
        override val appFontLabel = "介面字體"
        override val appFontSystem = "系統預設"
        override val appFontLxgw = "霞鶩文楷"
        override val languageLabel = "介面語言"
        override val languageCn = "簡體中文"
        override val languageTw = "繁體中文"
        override val languageEn = "English"

        override val readerPreferences = "閱讀器顯示偏好"
        override val defaultFontSize = "預設字型縮放"
        override val defaultLineSpacing = "預設行距選擇"
        override val lineSpacingCompact = "緊湊 (1.2)"
        override val lineSpacingMedium = "適中 (1.5)"
        override val lineSpacingWide = "寬敞 (1.8)"
        override val defaultPageAnim = "預設翻頁動畫"
        override val pageAnimOverlay = "覆蓋"
        override val pageAnimSlide = "滑動"
        override val pageAnimSimulation = "模擬"
        override val pageAnimFade = "淡入淡出"
        override val pageAnimNone = "無動畫"
        override val pageTurnDirection = "翻頁方向"
        override val pageTurnHorizontal = "左右滑動"
        override val pageTurnVertical = "上下滾動"

        override val libraryImportSettings = "書庫與匯入設定"
        override val duplicateCheck = "自動查重開關"
        override val duplicateCheckDesc = "匯入重複書籍時自動高亮定位"
        override val importCopy = "匯入時複製檔案"
        override val importCopyDesc = "複製書籍到應用程式私有目錄"
        override val clearTempCache = "清除暫存快取"
        override val clearTempCacheDesc = "清理書籍匯入解壓過程中產生的暫存快取"
        override val clearCacheSuccess = "快取清理成功"

        override val readingStats = "閱讀統計"
        override val statsEnable = "閱讀時長統計"
        override val statsEnableDesc = "開啟記錄每日/每週/每月閱讀時長"
        override val statsDailyTarget = "每日閱讀目標"
        override val resetStats = "重置統計數據"
        override val resetStatsDesc = "清除本地所有閱讀記錄"
        override val viewStatsReport = "查看詳細閱讀報告"
        override val statsTitle = "閱讀統計看板"
        override val totalBooksCount = "藏書總數"
        override val totalReadingTime = "累計閱讀時長"
        override val todayReadingProgress = "今日閱讀進度"

        override val syncSettings = "同步設定"
        override val syncMethod = "同步方式"
        override val syncMethodLocal = "本地備份"
        override val syncMethodWebdav = "WebDAV 同步"
        override val webdavUrl = "WebDAV 伺服器位址"
        override val webdavUser = "使用者名稱"
        override val webdavPassword = "密碼"
        override val testConnection = "測試連線"
        override val syncNow = "立即同步"

        override val ttsSettings = "朗讀設定 (TTS)"
        override val ttsSpeed = "語速調節"
        override val ttsPitch = "音調調節"
        override val ttsAutoPage = "自動翻頁"
        override val ttsHighlightSentence = "高亮當前句子"

        override val advancedSettings = "高級設定"
        override val gpuAcceleration = "GPU 硬體加速"
        override val loggingEnabled = "調試運行日誌"
        override val resetAllSettings = "重置所有設定"
        override val resetAllSettingsDesc = "將應用程式設定全部恢復為預設值"
        override val settingsResetSuccess = "設定重置成功"

        override val aboutLabel = "關於與版權"
        override val versionLabel = "軟體版本"
        override val developerLabel = "開發者"
        override val feedbackLabel = "反饋建議"
        override val licenseLabel = "開源許可證 (AGPL-3.0)"
        override val checkUpdate = "檢查更新"

        override val saveSuccess = "設定儲存成功"
        override val saveFailed = "設定儲存失敗"

        override val selectAll = "全選"
        override val deselectAll = "取消全選"
        override val importSelected = { count: Int -> "匯入所選 ($count)" }
        override val alreadyLatestVersion = "已是最新版本"
        override val folderImportDesc = "自動掃描資料夾內所有可匯入的書籍"

        override val bookDeleted = "已刪除"
        override val importSuccess = "匯入成功"
        override val bookAlreadyInShelf = "書籍已在書架中"
        override val importSuccessCount = { count: Int -> "成功匯入 $count 本書" }
        override val importSuccessWithSkipped = { success: Int, skipped: Int -> "成功匯入 $success 本，有 $skipped 本已存在跳過" }
        override val importSuccessWithFailed = { success: Int, failed: Int -> "成功匯入 $success 本，失敗 $failed 本" }
        override val importSuccessWithBoth = { success: Int, skipped: Int, failed: Int -> "成功匯入 $success 本，已存在 $skipped 本，失敗 $failed 本" }
        override val importFailed = { error: String -> "匯入失敗: $error" }
    }

    // 英文实现
    data object En : AppStrings {
        override val appName = "ShuLi Reader"
        override val bookshelf = "Bookshelf"
        override val settings = "Settings"
        override val searchPlaceholder = "Search books..."
        override val todayReading = "Today"
        override val noBooksFound = "No TXT or EPUB files found in this folder"
        override val emptyBookshelf = "The bookshelf is empty. Click the button at the bottom-right to import books"
        override val searchIconDesc = "Search"
        override val sortIconDesc = "Sort"
        override val viewModeIconDesc = "Toggle View"
        override val moreIconDesc = "More"
        override val backIconDesc = "Back"
        override val clearIconDesc = "Clear"
        override val loading = "Loading..."

        override val appearance = "Appearance"
        override val themeModeLabel = "Theme Mode"
        override val themeSystem = "Follow System"
        override val themeLight = "Light Mode"
        override val themeDark = "Dark Mode"
        override val themePaper = "Paper Mode"
        override val appFontLabel = "App UI Font"
        override val appFontSystem = "System Default"
        override val appFontLxgw = "LXGW Wenkai"
        override val languageLabel = "App Language"
        override val languageCn = "简体中文"
        override val languageTw = "繁體中文"
        override val languageEn = "English"

        override val readerPreferences = "Reader Preferences"
        override val defaultFontSize = "Default Font Size"
        override val defaultLineSpacing = "Default Line Spacing"
        override val lineSpacingCompact = "Compact (1.2)"
        override val lineSpacingMedium = "Medium (1.5)"
        override val lineSpacingWide = "Wide (1.8)"
        override val defaultPageAnim = "Default Page Animation"
        override val pageAnimOverlay = "Overlay"
        override val pageAnimSlide = "Slide"
        override val pageAnimSimulation = "Simulation"
        override val pageAnimFade = "Fade"
        override val pageAnimNone = "None"
        override val pageTurnDirection = "Page Turn Direction"
        override val pageTurnHorizontal = "Horizontal"
        override val pageTurnVertical = "Vertical"

        override val libraryImportSettings = "Library & Import"
        override val duplicateCheck = "Check Duplicates"
        override val duplicateCheckDesc = "Auto scroll and highlight existing books when importing duplicates"
        override val importCopy = "Copy File on Import"
        override val importCopyDesc = "Copy imported book files to app private storage"
        override val clearTempCache = "Clear Temp Cache"
        override val clearTempCacheDesc = "Clear temporary cache files generated during extraction"
        override val clearCacheSuccess = "Cache cleared successfully"

        override val readingStats = "Reading Stats"
        override val statsEnable = "Track Reading Time"
        override val statsEnableDesc = "Record and analyze daily/weekly/monthly reading durations"
        override val statsDailyTarget = "Daily Reading Goal"
        override val resetStats = "Reset Statistics"
        override val resetStatsDesc = "Clear all reading duration logs from this device"
        override val viewStatsReport = "View Details"
        override val statsTitle = "Reading Statistics"
        override val totalBooksCount = "Total Books"
        override val totalReadingTime = "Accumulated Time"
        override val todayReadingProgress = "Today's Progress"

        override val syncSettings = "Synchronization"
        override val syncMethod = "Sync Method"
        override val syncMethodLocal = "Local Backup"
        override val syncMethodWebdav = "WebDAV Sync"
        override val webdavUrl = "WebDAV Server URL"
        override val webdavUser = "Username"
        override val webdavPassword = "Password"
        override val testConnection = "Test Connection"
        override val syncNow = "Sync Now"

        override val ttsSettings = "TTS Settings"
        override val ttsSpeed = "Speech Speed"
        override val ttsPitch = "Speech Pitch"
        override val ttsAutoPage = "Auto Page Turn"
        override val ttsHighlightSentence = "Highlight Active Sentence"

        override val advancedSettings = "Advanced"
        override val gpuAcceleration = "GPU Hardware Acceleration"
        override val loggingEnabled = "Debug Log Recording"
        override val resetAllSettings = "Reset All Settings"
        override val resetAllSettingsDesc = "Restore all settings to system default values"
        override val settingsResetSuccess = "Settings reset successfully"

        override val aboutLabel = "About & Copyright"
        override val versionLabel = "Software Version"
        override val developerLabel = "Developer"
        override val feedbackLabel = "Feedback"
        override val licenseLabel = "License (AGPL-3.0)"
        override val checkUpdate = "Check for Update"

        override val saveSuccess = "Settings saved successfully"
        override val saveFailed = "Failed to save settings"

        override val selectAll = "Select All"
        override val deselectAll = "Deselect All"
        override val importSelected = { count: Int -> "Import Selected ($count)" }
        override val alreadyLatestVersion = "Already up to date"
        override val folderImportDesc = "Auto-scan all importable books in the folder"

        override val bookDeleted = "Deleted"
        override val importSuccess = "Imported successfully"
        override val bookAlreadyInShelf = "Book already in library"
        override val importSuccessCount = { count: Int -> "Successfully imported $count books" }
        override val importSuccessWithSkipped = { success: Int, skipped: Int -> "Successfully imported $success, skipped $skipped duplicates" }
        override val importSuccessWithFailed = { success: Int, failed: Int -> "Successfully imported $success, failed to import $failed" }
        override val importSuccessWithBoth = { success: Int, skipped: Int, failed: Int -> "Successfully imported $success, skipped $skipped, failed $failed" }
        override val importFailed = { error: String -> "Failed to import: $error" }
    }
}

val LocalAppStrings = staticCompositionLocalOf<AppStrings> { AppStrings.ZhHans }
