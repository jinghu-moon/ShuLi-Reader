package com.shuli.reader.core.i18n

import androidx.compose.runtime.staticCompositionLocalOf

sealed interface AppStrings {
    // 基础导航与通用
    val appName: String
    val bookshelf: String
    val settings: String
    val search: String
    val searchPlaceholder: String
    val todayReading: String
    val noBooksFound: String
    val emptyBookshelf: String
    val searchIconDesc: String
    val previousSearchResult: String
    val nextSearchResult: String
    val sortIconDesc: String
    val viewModeIconDesc: String
    val viewModeGrid: String
    val viewModeList: String
    val viewModeCompact: String
    val moreIconDesc: String
    val backIconDesc: String
    val clearIconDesc: String
    val deleteIconDesc: String
    val infoIconDesc: String
    val favoriteIconDesc: String
    val coverImageDesc: String
    val bookmarkIconDesc: String
    val loading: String
    val selected: String

    // 外观与通用配置
    val appearance: String
    val themeModeLabel: String
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val themePaper: String
    val readerThemeLabel: String
    val appFontLabel: String
    val appFontSystem: String
    val appFontHarmony: String
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
    val paragraphSpacing: String
    val paragraphSpacingCompact: String
    val paragraphSpacingNormal: String
    val paragraphSpacingWide: String
    val firstLineIndent: String
    val indentNone: String
    val indentTwoChars: String
    val fullScreenMode: String
    val keepScreenOn: String
    val brightness: String
    val brightnessFollowSystem: String
    val brightnessFollowSystemLabel: String
    val brightnessResetToSystem: String
    val readingFont: String
    val readingFontSystem: String
    val readingFontHarmony: String
    val readingFontLxgw: String
    val importFont: String
    val fontTestText: String
    val marginTopBottom: String
    val marginLeftRight: String
    val editValue: String
    val confirm: String

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
    val readingTargetMinutes: (Int) -> String

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
    val syncAndBackup: String
    val syncAndBackupDesc: String
    val cloudSyncConfig: String
    val localBackupDesc: String

    // 五、朗读设置 (TTS)
    val ttsSettings: String
    val ttsSpeed: String
    val ttsPitch: String
    val ttsAutoPage: String
    val ttsHighlightSentence: String
    val ttsStart: String
    val ttsPause: String
    val ttsStop: String

    // 六、快捷设置面板 (QuickSettingsSheet)
    val layoutTab: String
    val styleTab: String
    val settingsTab: String
    val letterSpacingLabel: String
    val fontWeightLabel: String
    val fontWeightLight: String
    val fontWeightNormal: String
    val fontWeightMedium: String
    val fontWeightBold: String
    val textAlignLabel: String
    val textAlignLeft: String
    val textAlignJustify: String
    val chineseConvertLabel: String
    val chineseConvertNone: String
    val chineseConvertSimplified: String
    val chineseConvertTraditional: String
    val useZhLayoutLabel: String
    val usePanguSpacingLabel: String
    val slotNone: String
    val slotChapterTitle: String
    val slotBookTitle: String
    val slotPageNumber: String
    val slotProgress: String
    val slotTime: String
    val slotBattery: String
    val slotDate: String
    val headerLabel: String
    val footerLabel: String
    val progressBarLabel: String
    val opacityLabel: String
    val headerFooterCustom: String
    val headerHidden: String
    val footerHidden: String
    val displayLabel: String
    val displayFollowStatusBar: String
    val displayAlwaysShow: String
    val displayAlwaysHide: String
    val positionLeft: String
    val positionCenter: String
    val positionRight: String
    val headerLeft: String
    val headerCenter: String
    val headerRight: String
    val footerLeft: String
    val footerCenter: String
    val footerRight: String
    val titleStyleLabel: String
    val titleAlignLeft: String
    val titleAlignCenter: String
    val titleAlignHidden: String
    val titleSizeOffset: String
    val titleMarginTop: String
    val titleMarginBottom: String
    val headerMarginTop: String
    val footerMarginBottom: String
    val keepScreenOnLabel: String
    val keepScreenOnDesc: String
    val volumeKeyLabel: String
    val volumeKeyDesc: String
    val edgeTurnPageLabel: String
    val edgeTurnPageDesc: String
    val edgeWidthLabel: String
    val headerLineLabel: String
    val footerLineLabel: String
    val headerFontSizeLabel: String
    val footerFontSizeLabel: String
    val bottomJustifyLabel: String
    val readingPresets: String
    val savePresetAction: String
    val resetToDefault: String
    val resetToDefaultConfirm: String
    val savePresetTitle: String
    val presetNameLabel: String
    val deletePresetTitle: String
    val deletePresetConfirm: String
    val confirmAction: String
    val cancelAction: String
    val saveAction: String
    val deleteAction: String
    val deleteBookmarkTitle: String
    val deleteBookmarkConfirm: String
    val deleteNoteTitle: String
    val deleteNoteConfirm: String

    // 七、高级设置
    val advancedSettings: String
    val gpuAcceleration: String
    val loggingEnabled: String
    val resetAllSettings: String
    val resetAllSettingsDesc: String
    val settingsResetSuccess: String

    // 七、关于与版权
    val chapterFullText: String
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
    val folderCreated: String
    val removedFromFolder: String
    val addedToFolder: String
    val newFolder: String
    val importSuccessCount: (Int) -> String
    val importSuccessWithSkipped: (Int, Int) -> String
    val importSuccessWithFailed: (Int, Int) -> String
    val importSuccessWithBoth: (Int, Int, Int) -> String
    val importFailed: (String) -> String
    val favoriteToggled: String

    // 书架操作菜单
    val addFavorite: String
    val removeFavorite: String
    val bookInfo: String
    val deleteBook: String
    val deleteBookTitle: String
    val deleteBookConfirm: (String) -> String
    val cancel: String

    // 书架筛选、排序与信息
    val filterAll: String
    val filterFinished: String
    val filterFavorite: String
    val sortTitle: String
    val sortDescending: String
    val sortAscending: String
    val sortLastRead: String
    val sortAddTime: String
    val sortBookTitle: String
    val sortReadingTime: String
    val sortReadingProgress: String
    val bookTitleLabel: String
    val bookAuthorLabel: String
    val unknownAuthor: String
    val bookFormatLabel: String
    val bookSizeLabel: String
    val bookProgressLabel: String
    val readingDurationLabel: String
    val notReadYet: String
    val unreadLabel: String
    val notStartedLabel: String
    val readProgress: (Int) -> String
    val filePathLabel: String
    val favoritedDesc: String

    // 阅读目录、书签与笔记
    val directoryTab: String
    val bookmarksTab: String
    val notesTab: String
    val currentChapterLabel: String
    val noBookmarks: String
    val noNotes: String
    val notePosition: (Int, Int, String) -> String
    val copySelection: String
    val addBookmarkAction: String
    val addNoteAction: String
    val previousChapter: String
    val nextChapter: String
    val customizeCover: String
    val resetCoverColor: String
    val unifiedCoverColor: String
    val unifiedCoverColorAuto: String
    val unifiedCoverColorActive: (Int) -> String

    // 领域错误提示
    val unableToReadFile: String
    val invalidFolderPath: String
    val invalidFolder: String
    val noImportableFiles: String

    // 简体中文实现
    data object ZhHans : AppStrings {
        override val appName = "书里阅读器"
        override val bookshelf = "书架"
        override val settings = "设置"
        override val search = "搜索"
        override val searchPlaceholder = "输入书名进行搜索..."
        override val todayReading = "今日"
        override val noBooksFound = "该文件夹下未找到 TXT 或 EPUB 文件"
        override val emptyBookshelf = "书架空空如也，点击右下角按钮导入书籍"
        override val searchIconDesc = "搜索"
        override val previousSearchResult = "上一个搜索结果"
        override val nextSearchResult = "下一个搜索结果"
        override val sortIconDesc = "排序"
        override val viewModeIconDesc = "切换视图"
        override val viewModeGrid = "网格"
        override val viewModeList = "列表"
        override val viewModeCompact = "紧凑"
        override val moreIconDesc = "更多"
        override val backIconDesc = "返回"
        override val clearIconDesc = "清除"
        override val deleteIconDesc = "删除"
        override val infoIconDesc = "详情"
        override val favoriteIconDesc = "收藏"
        override val coverImageDesc = "封面图片"
        override val bookmarkIconDesc = "书签"
        override val loading = "加载中..."
        override val selected = "已选中"

        override val appearance = "外观"
        override val themeModeLabel = "深浅主题"
        override val themeSystem = "跟随系统"
        override val themeLight = "浅色模式"
        override val themeDark = "深色模式"
        override val themePaper = "纸质护眼"
        override val readerThemeLabel = "阅读主题"
        override val appFontLabel = "界面字体"
        override val appFontSystem = "系统默认"
        override val appFontHarmony = "鸿蒙字体"
        override val appFontLxgw = "霞鹜文楷"
        override val languageLabel = "界面语言"
        override val languageCn = "简体中文"
        override val languageTw = "繁體中文"
        override val languageEn = "English"

        override val readerPreferences = "阅读器显示偏好"
        override val defaultFontSize = "字号"
        override val defaultLineSpacing = "行距"
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
        override val paragraphSpacing = "段距"
        override val paragraphSpacingCompact = "紧凑"
        override val paragraphSpacingNormal = "标准"
        override val paragraphSpacingWide = "宽敞"
        override val firstLineIndent = "缩进"
        override val indentNone = "无"
        override val indentTwoChars = "2字"
        override val fullScreenMode = "全屏模式"
        override val keepScreenOn = "屏幕常亮"
        override val brightness = "亮度调节"
        override val brightnessFollowSystem = "跟随系统"
        override val brightnessFollowSystemLabel = "跟随系统亮度"
        override val brightnessResetToSystem = "长按重置为系统亮度"
        override val readingFont = "阅读字体"
        override val readingFontSystem = "系统默认"
        override val readingFontHarmony = "鸿蒙字体"
        override val readingFontLxgw = "霞鹜文楷"
        override val importFont = "导入字体"
        override val fontTestText = "一只敏捷的棕色狐狸跳过一只懒惰的狗"
        override val marginTopBottom = "上下边距"
        override val marginLeftRight = "左右边距"
        override val editValue = "修改数值"
        override val confirm = "确定"

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
        override val readingTargetMinutes = { minutes: Int -> "$minutes 分钟" }

        override val syncSettings = "同步设置"
        override val syncMethod = "同步方式"
        override val syncMethodLocal = "本地备份"
        override val syncMethodWebdav = "WebDAV 同步"
        override val webdavUrl = "WebDAV 服务器地址"
        override val webdavUser = "用户名"
        override val webdavPassword = "密码"
        override val testConnection = "测试连接"
        override val syncNow = "立即同步"
        override val syncAndBackup = "同步与备份"
        override val syncAndBackupDesc = "云端同步配置、加密、设备管理"
        override val cloudSyncConfig = "云端同步配置"
        override val localBackupDesc = "导出与导入数据备份"

        override val ttsSettings = "朗读设置 (TTS)"
        override val ttsSpeed = "语速调节"
        override val ttsPitch = "音调调节"
        override val ttsAutoPage = "自动翻页"
        override val ttsHighlightSentence = "高亮当前句子"
        override val ttsStart = "开始朗读"
        override val ttsPause = "暂停朗读"
        override val ttsStop = "停止朗读"

        override val layoutTab = "排版"
        override val styleTab = "样式"
        override val settingsTab = "设置"
        override val letterSpacingLabel = "字距"
        override val fontWeightLabel = "字重"
        override val fontWeightLight = "细"
        override val fontWeightNormal = "常规"
        override val fontWeightMedium = "中"
        override val fontWeightBold = "粗"
        override val textAlignLabel = "对齐"
        override val textAlignLeft = "左对齐"
        override val textAlignJustify = "两端"
        override val chineseConvertLabel = "简繁"
        override val chineseConvertNone = "原始"
        override val chineseConvertSimplified = "简体"
        override val chineseConvertTraditional = "繁体"
        override val useZhLayoutLabel = "自定义中文分行"
        override val usePanguSpacingLabel = "中英文间增加空格"
        override val slotNone = "无"
        override val slotChapterTitle = "章节名"
        override val slotBookTitle = "书名"
        override val slotPageNumber = "页码"
        override val slotProgress = "进度"
        override val slotTime = "时间"
        override val slotBattery = "电量"
        override val slotDate = "日期"
        override val headerLabel = "页眉"
        override val footerLabel = "页脚"
        override val progressBarLabel = "进度条"
        override val opacityLabel = "透明度"
        override val headerFooterCustom = "页眉脚自定义"
        override val headerHidden = "页眉已隐藏"
        override val footerHidden = "页脚已隐藏"
        override val displayLabel = "显示"
        override val displayFollowStatusBar = "跟随状态栏"
        override val displayAlwaysShow = "常显"
        override val displayAlwaysHide = "隐藏"
        override val positionLeft = "左"
        override val positionCenter = "中"
        override val positionRight = "右"
        override val headerLeft = "页眉左"
        override val headerCenter = "页眉中"
        override val headerRight = "页眉右"
        override val footerLeft = "页脚左"
        override val footerCenter = "页脚中"
        override val footerRight = "页脚右"
        override val titleStyleLabel = "标题样式"
        override val titleAlignLeft = "靠左"
        override val titleAlignCenter = "居中"
        override val titleAlignHidden = "隐藏"
        override val titleSizeOffset = "字号偏移"
        override val titleMarginTop = "上距"
        override val titleMarginBottom = "下距"
        override val headerMarginTop = "上边距"
        override val footerMarginBottom = "下边距"
        override val keepScreenOnLabel = "屏幕常亮"
        override val keepScreenOnDesc = "阅读时保持屏幕常亮"
        override val volumeKeyLabel = "音量键"
        override val volumeKeyDesc = "音量 +/- 键翻页"
        override val edgeTurnPageLabel = "边缘翻页"
        override val edgeTurnPageDesc = "点击屏幕左右边缘翻页"
        override val edgeWidthLabel = "边缘宽度"
        override val headerLineLabel = "页眉分割线"
        override val footerLineLabel = "页脚分割线"
        override val headerFontSizeLabel = "页眉字号"
        override val footerFontSizeLabel = "页脚字号"
        override val bottomJustifyLabel = "底部对齐"
        override val readingPresets = "阅读预设"
        override val savePresetAction = "＋ 保存当前"
        override val resetToDefault = "恢复默认设置"
        override val resetToDefaultConfirm = "确定要将所有阅读设置恢复为默认值吗？此操作不可撤销。"
        override val savePresetTitle = "保存预设"
        override val presetNameLabel = "预设名称"
        override val deletePresetTitle = "删除预设"
        override val deletePresetConfirm = "确定要删除这个预设吗？"
        override val confirmAction = "确定"
        override val cancelAction = "取消"
        override val saveAction = "保存"
        override val deleteAction = "删除"
        override val deleteBookmarkTitle = "删除书签"
        override val deleteBookmarkConfirm = "确定要删除这个书签吗？"
        override val deleteNoteTitle = "删除笔记"
        override val deleteNoteConfirm = "确定要删除这个笔记吗？"

        override val advancedSettings = "高级设置"
        override val gpuAcceleration = "GPU 硬件加速"
        override val loggingEnabled = "调试运行日志"
        override val resetAllSettings = "重置所有设置"
        override val resetAllSettingsDesc = "将应用设置项全部恢复为默认值"
        override val settingsResetSuccess = "设置重置成功"

        override val chapterFullText = "全文"
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
        override val folderCreated = "分组创建成功"
        override val removedFromFolder = "已移出分组"
        override val addedToFolder = "已放入分组"
        override val newFolder = "新建文件夹"
        override val importSuccessCount = { count: Int -> "成功导入 $count 本书" }
        override val importSuccessWithSkipped = { success: Int, skipped: Int -> "成功导入 $success 本，有 $skipped 本已存在跳过" }
        override val importSuccessWithFailed = { success: Int, failed: Int -> "成功导入 $success 本，失败 $failed 本" }
        override val importSuccessWithBoth = { success: Int, skipped: Int, failed: Int -> "成功导入 $success 本，已存在 $skipped 本，失败 $failed 本" }
        override val importFailed = { error: String -> "导入失败: $error" }
        override val favoriteToggled = "收藏状态已更新"
        override val addFavorite = "收藏"
        override val removeFavorite = "取消收藏"
        override val bookInfo = "书籍信息"
        override val deleteBook = "删除"
        override val deleteBookTitle = "删除书籍"
        override val deleteBookConfirm = { title: String -> "确定要删除《$title》吗？此操作不可撤销。" }
        override val cancel = "取消"
        override val filterAll = "全部"
        override val filterFinished = "已读完"
        override val filterFavorite = "收藏"
        override val sortTitle = "排序方式"
        override val sortDescending = "降序"
        override val sortAscending = "升序"
        override val sortLastRead = "最近阅读"
        override val sortAddTime = "添加时间"
        override val sortBookTitle = "书名"
        override val sortReadingTime = "阅读时长"
        override val sortReadingProgress = "阅读进度"
        override val bookTitleLabel = "标题"
        override val bookAuthorLabel = "作者"
        override val unknownAuthor = "未知"
        override val bookFormatLabel = "格式"
        override val bookSizeLabel = "大小"
        override val bookProgressLabel = "进度"
        override val readingDurationLabel = "阅读时长"
        override val notReadYet = "未阅读"
        override val unreadLabel = "未读"
        override val notStartedLabel = "未开始"
        override val readProgress = { percent: Int -> "已读 $percent%" }
        override val filePathLabel = "文件路径"
        override val favoritedDesc = "已收藏"
        override val directoryTab = "目录"
        override val bookmarksTab = "书签"
        override val notesTab = "笔记"
        override val currentChapterLabel = "当前"
        override val noBookmarks = "暂无书签"
        override val noNotes = "暂无笔记"
        override val notePosition = { start: Int, end: Int, date: String -> "位置: $start-$end  $date" }
        override val copySelection = "复制"
        override val addBookmarkAction = "添加书签"
        override val addNoteAction = "添加笔记"
        override val previousChapter = "上一章"
        override val nextChapter = "下一章"
        override val customizeCover = "自定义封面颜色"
        override val resetCoverColor = "恢复自动配色"
        override val unifiedCoverColor = "统一封面颜色"
        override val unifiedCoverColorAuto = "自动（按书名散列）"
        override val unifiedCoverColorActive = { idx: Int -> "已统一为色盘 #$idx" }
        override val unableToReadFile = "无法读取文件"
        override val invalidFolderPath = "无效的文件夹路径"
        override val invalidFolder = "不是有效的文件夹"
        override val noImportableFiles = "未找到可导入的文件"
    }

    // 繁体中文实现（继承并覆盖差异项）
    data object ZhHant : AppStrings {
        override val appName = "書裡閱讀器"
        override val bookshelf = "書架"
        override val settings = "設定"
        override val search = "搜尋"
        override val searchPlaceholder = "輸入書名進行搜尋..."
        override val todayReading = "今日"
        override val noBooksFound = "該資料夾下未找到 TXT 或 EPUB 檔案"
        override val emptyBookshelf = "書架空空如也，點擊右下角按鈕匯入書籍"
        override val searchIconDesc = "搜尋"
        override val previousSearchResult = "上一個搜尋結果"
        override val nextSearchResult = "下一個搜尋結果"
        override val sortIconDesc = "排序"
        override val viewModeIconDesc = "切換檢視"
        override val viewModeGrid = "網格"
        override val viewModeList = "列表"
        override val viewModeCompact = "緊湊"
        override val moreIconDesc = "更多"
        override val backIconDesc = "返回"
        override val clearIconDesc = "清除"
        override val deleteIconDesc = "刪除"
        override val infoIconDesc = "詳情"
        override val favoriteIconDesc = "收藏"
        override val coverImageDesc = "封面圖片"
        override val bookmarkIconDesc = "書籤"
        override val loading = "載入中..."
        override val selected = "已選中"

        override val appearance = "外觀"
        override val themeModeLabel = "深淺主題"
        override val themeSystem = "跟隨系統"
        override val themeLight = "淺色模式"
        override val themeDark = "深色模式"
        override val themePaper = "紙質護眼"
        override val readerThemeLabel = "閱讀主題"
        override val appFontLabel = "介面字體"
        override val appFontSystem = "系統預設"
        override val appFontHarmony = "鴻蒙字體"
        override val appFontLxgw = "霞鶩文楷"
        override val languageLabel = "介面語言"
        override val languageCn = "簡體中文"
        override val languageTw = "繁體中文"
        override val languageEn = "English"

        override val readerPreferences = "閱讀器顯示偏好"
        override val defaultFontSize = "字號"
        override val defaultLineSpacing = "行距"
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
        override val paragraphSpacing = "段距"
        override val paragraphSpacingCompact = "緊湊"
        override val paragraphSpacingNormal = "標準"
        override val paragraphSpacingWide = "寬敞"
        override val firstLineIndent = "縮排"
        override val indentNone = "無"
        override val indentTwoChars = "2字"
        override val fullScreenMode = "全螢幕模式"
        override val keepScreenOn = "螢幕常亮"
        override val brightness = "亮度調節"
        override val brightnessFollowSystem = "跟隨系統"
        override val brightnessFollowSystemLabel = "跟隨系統亮度"
        override val brightnessResetToSystem = "長按重置為系統亮度"
        override val readingFont = "閱讀字體"
        override val readingFontSystem = "系統預設"
        override val readingFontHarmony = "鴻蒙字體"
        override val readingFontLxgw = "霞鶩文楷"
        override val importFont = "匯入字型"
        override val fontTestText = "一隻敏捷的棕色狐狸跳過一隻懶惰的狗"
        override val marginTopBottom = "上下邊距"
        override val marginLeftRight = "左右邊距"
        override val editValue = "修改數值"
        override val confirm = "確定"

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
        override val readingTargetMinutes = { minutes: Int -> "$minutes 分鐘" }

        override val syncSettings = "同步設定"
        override val syncMethod = "同步方式"
        override val syncMethodLocal = "本地備份"
        override val syncMethodWebdav = "WebDAV 同步"
        override val webdavUrl = "WebDAV 伺服器位址"
        override val webdavUser = "使用者名稱"
        override val webdavPassword = "密碼"
        override val testConnection = "測試連線"
        override val syncNow = "立即同步"
        override val syncAndBackup = "同步與備份"
        override val syncAndBackupDesc = "雲端同步配置、加密、裝置管理"
        override val cloudSyncConfig = "雲端同步配置"
        override val localBackupDesc = "匯出與匯入資料備份"

        override val ttsSettings = "朗讀設定 (TTS)"
        override val ttsSpeed = "語速調節"
        override val ttsPitch = "音調調節"
        override val ttsAutoPage = "自動翻頁"
        override val ttsHighlightSentence = "高亮當前句子"
        override val ttsStart = "開始朗讀"
        override val ttsPause = "暫停朗讀"
        override val ttsStop = "停止朗讀"

        override val layoutTab = "排版"
        override val styleTab = "樣式"
        override val settingsTab = "設定"
        override val letterSpacingLabel = "字距"
        override val fontWeightLabel = "字重"
        override val fontWeightLight = "細"
        override val fontWeightNormal = "常規"
        override val fontWeightMedium = "中"
        override val fontWeightBold = "粗"
        override val textAlignLabel = "對齊"
        override val textAlignLeft = "左對齊"
        override val textAlignJustify = "兩端"
        override val chineseConvertLabel = "簡繁"
        override val chineseConvertNone = "原始"
        override val chineseConvertSimplified = "簡體"
        override val chineseConvertTraditional = "繁體"
        override val useZhLayoutLabel = "自定義中文分行"
        override val usePanguSpacingLabel = "中英文間增加空格"
        override val slotNone = "無"
        override val slotChapterTitle = "章節名"
        override val slotBookTitle = "書名"
        override val slotPageNumber = "頁碼"
        override val slotProgress = "進度"
        override val slotTime = "時間"
        override val slotBattery = "電量"
        override val slotDate = "日期"
        override val headerLabel = "頁眉"
        override val footerLabel = "頁腳"
        override val progressBarLabel = "進度條"
        override val opacityLabel = "透明度"
        override val headerFooterCustom = "頁眉腳自定義"
        override val headerHidden = "頁眉已隱藏"
        override val footerHidden = "頁腳已隱藏"
        override val displayLabel = "顯示"
        override val displayFollowStatusBar = "跟隨狀態欄"
        override val displayAlwaysShow = "常顯"
        override val displayAlwaysHide = "隱藏"
        override val positionLeft = "左"
        override val positionCenter = "中"
        override val positionRight = "右"
        override val headerLeft = "頁眉左"
        override val headerCenter = "頁眉中"
        override val headerRight = "頁眉右"
        override val footerLeft = "頁腳左"
        override val footerCenter = "頁腳中"
        override val footerRight = "頁腳右"
        override val titleStyleLabel = "標題樣式"
        override val titleAlignLeft = "靠左"
        override val titleAlignCenter = "居中"
        override val titleAlignHidden = "隱藏"
        override val titleSizeOffset = "字號偏移"
        override val titleMarginTop = "上距"
        override val titleMarginBottom = "下距"
        override val headerMarginTop = "上邊距"
        override val footerMarginBottom = "下邊距"
        override val keepScreenOnLabel = "螢幕常亮"
        override val keepScreenOnDesc = "閱讀時保持螢幕常亮"
        override val volumeKeyLabel = "音量鍵"
        override val volumeKeyDesc = "音量 +/- 鍵翻頁"
        override val edgeTurnPageLabel = "邊緣翻頁"
        override val edgeTurnPageDesc = "點擊螢幕左右邊緣翻頁"
        override val edgeWidthLabel = "邊緣寬度"
        override val headerLineLabel = "頁眉分割線"
        override val footerLineLabel = "頁腳分割線"
        override val headerFontSizeLabel = "頁眉字號"
        override val footerFontSizeLabel = "頁腳字號"
        override val bottomJustifyLabel = "底部對齊"
        override val readingPresets = "閱讀預設"
        override val savePresetAction = "＋ 儲存當前"
        override val resetToDefault = "恢復預設設定"
        override val resetToDefaultConfirm = "確定要將所有閱讀設定恢復為預設值嗎？此操作不可撤銷。"
        override val savePresetTitle = "儲存預設"
        override val presetNameLabel = "預設名稱"
        override val deletePresetTitle = "刪除預設"
        override val deletePresetConfirm = "確定要刪除這個預設嗎？"
        override val confirmAction = "確定"
        override val cancelAction = "取消"
        override val saveAction = "儲存"
        override val deleteAction = "刪除"
        override val deleteBookmarkTitle = "刪除書籤"
        override val deleteBookmarkConfirm = "確定要刪除這個書籤嗎？"
        override val deleteNoteTitle = "刪除筆記"
        override val deleteNoteConfirm = "確定要刪除這個筆記嗎？"

        override val advancedSettings = "高級設定"
        override val gpuAcceleration = "GPU 硬體加速"
        override val loggingEnabled = "調試運行日誌"
        override val resetAllSettings = "重置所有設定"
        override val resetAllSettingsDesc = "將應用程式設定全部恢復為預設值"
        override val settingsResetSuccess = "設定重置成功"

        override val chapterFullText = "全文"
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
        override val folderCreated = "分組建立成功"
        override val removedFromFolder = "已移出分組"
        override val addedToFolder = "已放入分組"
        override val newFolder = "新建資料夾"
        override val importSuccessCount = { count: Int -> "成功匯入 $count 本書" }
        override val importSuccessWithSkipped = { success: Int, skipped: Int -> "成功匯入 $success 本，有 $skipped 本已存在跳過" }
        override val importSuccessWithFailed = { success: Int, failed: Int -> "成功匯入 $success 本，失敗 $failed 本" }
        override val importSuccessWithBoth = { success: Int, skipped: Int, failed: Int -> "成功匯入 $success 本，已存在 $skipped 本，失敗 $failed 本" }
        override val importFailed = { error: String -> "匯入失敗: $error" }
        override val favoriteToggled = "收藏狀態已更新"
        override val addFavorite = "收藏"
        override val removeFavorite = "取消收藏"
        override val bookInfo = "書籍資訊"
        override val deleteBook = "刪除"
        override val deleteBookTitle = "刪除書籍"
        override val deleteBookConfirm = { title: String -> "確定要刪除《$title》嗎？此操作不可撤銷。" }
        override val cancel = "取消"
        override val filterAll = "全部"
        override val filterFinished = "已讀完"
        override val filterFavorite = "收藏"
        override val sortTitle = "排序方式"
        override val sortDescending = "降序"
        override val sortAscending = "升序"
        override val sortLastRead = "最近閱讀"
        override val sortAddTime = "加入時間"
        override val sortBookTitle = "書名"
        override val sortReadingTime = "閱讀時長"
        override val sortReadingProgress = "閱讀進度"
        override val bookTitleLabel = "標題"
        override val bookAuthorLabel = "作者"
        override val unknownAuthor = "未知"
        override val bookFormatLabel = "格式"
        override val bookSizeLabel = "大小"
        override val bookProgressLabel = "進度"
        override val readingDurationLabel = "閱讀時長"
        override val notReadYet = "未閱讀"
        override val unreadLabel = "未讀"
        override val notStartedLabel = "未開始"
        override val readProgress = { percent: Int -> "已讀 $percent%" }
        override val filePathLabel = "檔案路徑"
        override val favoritedDesc = "已收藏"
        override val directoryTab = "目錄"
        override val bookmarksTab = "書籤"
        override val notesTab = "筆記"
        override val currentChapterLabel = "目前"
        override val noBookmarks = "暫無書籤"
        override val noNotes = "暫無筆記"
        override val notePosition = { start: Int, end: Int, date: String -> "位置: $start-$end  $date" }
        override val copySelection = "複製"
        override val addBookmarkAction = "新增書籤"
        override val addNoteAction = "新增筆記"
        override val previousChapter = "上一章"
        override val nextChapter = "下一章"
        override val customizeCover = "自訂封面顏色"
        override val resetCoverColor = "恢復自動配色"
        override val unifiedCoverColor = "統一封面顏色"
        override val unifiedCoverColorAuto = "自動（按書名散列）"
        override val unifiedCoverColorActive = { idx: Int -> "已統一為色盤 #$idx" }
        override val unableToReadFile = "無法讀取檔案"
        override val invalidFolderPath = "無效的資料夾路徑"
        override val invalidFolder = "不是有效的資料夾"
        override val noImportableFiles = "未找到可匯入的檔案"
    }

    // 英文实现
    data object En : AppStrings {
        override val appName = "ShuLi Reader"
        override val bookshelf = "Bookshelf"
        override val settings = "Settings"
        override val search = "Search"
        override val searchPlaceholder = "Search books..."
        override val todayReading = "Today"
        override val noBooksFound = "No TXT or EPUB files found in this folder"
        override val emptyBookshelf = "The bookshelf is empty. Click the button at the bottom-right to import books"
        override val searchIconDesc = "Search"
        override val previousSearchResult = "Previous search result"
        override val nextSearchResult = "Next search result"
        override val sortIconDesc = "Sort"
        override val viewModeIconDesc = "Toggle View"
        override val viewModeGrid = "Grid"
        override val viewModeList = "List"
        override val viewModeCompact = "Compact"
        override val moreIconDesc = "More"
        override val backIconDesc = "Back"
        override val clearIconDesc = "Clear"
        override val deleteIconDesc = "Delete"
        override val infoIconDesc = "Info"
        override val favoriteIconDesc = "Favorite"
        override val coverImageDesc = "Cover Image"
        override val bookmarkIconDesc = "Bookmark"
        override val loading = "Loading..."
        override val selected = "Selected"

        override val appearance = "Appearance"
        override val themeModeLabel = "Theme Mode"
        override val themeSystem = "Follow System"
        override val themeLight = "Light Theme"
        override val themeDark = "Dark Theme"
        override val themePaper = "Paper Eye-care"
        override val readerThemeLabel = "Reading Theme"
        override val appFontLabel = "App Font"
        override val appFontSystem = "System Default"
        override val appFontHarmony = "HarmonyOS Sans"
        override val appFontLxgw = "LXGW Wenkai"
        override val languageLabel = "App Language"
        override val languageCn = "简体中文"
        override val languageTw = "繁體中文"
        override val languageEn = "English"

        override val readerPreferences = "Reader Preferences"
        override val defaultFontSize = "Size"
        override val defaultLineSpacing = "Spacing"
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
        override val paragraphSpacing = "Para Space"
        override val paragraphSpacingCompact = "Compact"
        override val paragraphSpacingNormal = "Normal"
        override val paragraphSpacingWide = "Wide"
        override val firstLineIndent = "Indent"
        override val indentNone = "None"
        override val indentTwoChars = "2 Chars"
        override val fullScreenMode = "Full Screen Mode"
        override val keepScreenOn = "Keep Screen On"
        override val brightness = "Brightness"
        override val brightnessFollowSystem = "Follow System"
        override val brightnessFollowSystemLabel = "Follow System Brightness"
        override val brightnessResetToSystem = "Long press to reset to system brightness"
        override val readingFont = "Reading Font"
        override val readingFontSystem = "System Default"
        override val readingFontHarmony = "HarmonyOS Sans"
        override val readingFontLxgw = "LXGW Wenkai"
        override val importFont = "Import Font"
        override val fontTestText = "The quick brown fox jumps over the lazy dog"
        override val marginTopBottom = "Top/Bottom Margin"
        override val marginLeftRight = "Left/Right Margin"
        override val editValue = "Edit Value"
        override val confirm = "OK"

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
        override val readingTargetMinutes = { minutes: Int -> "$minutes min" }

        override val syncSettings = "Synchronization"
        override val syncMethod = "Sync Method"
        override val syncMethodLocal = "Local Backup"
        override val syncMethodWebdav = "WebDAV Sync"
        override val webdavUrl = "WebDAV Server URL"
        override val webdavUser = "Username"
        override val webdavPassword = "Password"
        override val testConnection = "Test Connection"
        override val syncNow = "Sync Now"
        override val syncAndBackup = "Sync & Backup"
        override val syncAndBackupDesc = "Cloud sync settings, encryption, device management"
        override val cloudSyncConfig = "Cloud Sync Settings"
        override val localBackupDesc = "Export and import data backup"

        override val ttsSettings = "TTS Settings"
        override val ttsSpeed = "Speech Speed"
        override val ttsPitch = "Speech Pitch"
        override val ttsAutoPage = "Auto Page Turn"
        override val ttsHighlightSentence = "Highlight Active Sentence"
        override val ttsStart = "Start Reading"
        override val ttsPause = "Pause Reading"
        override val ttsStop = "Stop Reading"

        override val layoutTab = "Layout"
        override val styleTab = "Style"
        override val settingsTab = "Settings"
        override val letterSpacingLabel = "Letter Spacing"
        override val fontWeightLabel = "Font Weight"
        override val fontWeightLight = "Light"
        override val fontWeightNormal = "Normal"
        override val fontWeightMedium = "Medium"
        override val fontWeightBold = "Bold"
        override val textAlignLabel = "Alignment"
        override val textAlignLeft = "Left"
        override val textAlignJustify = "Justify"
        override val chineseConvertLabel = "Chinese Convert"
        override val chineseConvertNone = "Original"
        override val chineseConvertSimplified = "Simplified"
        override val chineseConvertTraditional = "Traditional"
        override val useZhLayoutLabel = "Custom Chinese Layout"
        override val usePanguSpacingLabel = "Space Between CJK and Latin"
        override val slotNone = "None"
        override val slotChapterTitle = "Chapter Title"
        override val slotBookTitle = "Book Title"
        override val slotPageNumber = "Page Number"
        override val slotProgress = "Progress"
        override val slotTime = "Time"
        override val slotBattery = "Battery"
        override val slotDate = "Date"
        override val headerLabel = "Header"
        override val footerLabel = "Footer"
        override val progressBarLabel = "Progress Bar"
        override val opacityLabel = "Opacity"
        override val headerFooterCustom = "Header & Footer Customization"
        override val headerHidden = "Header Hidden"
        override val footerHidden = "Footer Hidden"
        override val displayLabel = "Display"
        override val displayFollowStatusBar = "Follow Status Bar"
        override val displayAlwaysShow = "Always Show"
        override val displayAlwaysHide = "Always Hide"
        override val positionLeft = "Left"
        override val positionCenter = "Center"
        override val positionRight = "Right"
        override val headerLeft = "Header Left"
        override val headerCenter = "Header Center"
        override val headerRight = "Header Right"
        override val footerLeft = "Footer Left"
        override val footerCenter = "Footer Center"
        override val footerRight = "Footer Right"
        override val titleStyleLabel = "Title Style"
        override val titleAlignLeft = "Left"
        override val titleAlignCenter = "Center"
        override val titleAlignHidden = "Hidden"
        override val titleSizeOffset = "Size Offset"
        override val titleMarginTop = "Margin Top"
        override val titleMarginBottom = "Margin Bottom"
        override val headerMarginTop = "Margin Top"
        override val footerMarginBottom = "Margin Bottom"
        override val keepScreenOnLabel = "Keep Screen On"
        override val keepScreenOnDesc = "Keep screen on while reading"
        override val volumeKeyLabel = "Volume Keys"
        override val volumeKeyDesc = "Volume +/- to turn pages"
        override val edgeTurnPageLabel = "Edge Turn Page"
        override val edgeTurnPageDesc = "Tap screen edges to turn pages"
        override val edgeWidthLabel = "Edge Width"
        override val headerLineLabel = "Header Divider"
        override val footerLineLabel = "Footer Divider"
        override val headerFontSizeLabel = "Header Font Size"
        override val footerFontSizeLabel = "Footer Font Size"
        override val bottomJustifyLabel = "Bottom Justify"
        override val readingPresets = "Reading Presets"
        override val savePresetAction = "+ Save Current"
        override val resetToDefault = "Reset to Default"
        override val resetToDefaultConfirm = "Are you sure you want to reset all reading settings to default? This action cannot be undone."
        override val savePresetTitle = "Save Preset"
        override val presetNameLabel = "Preset Name"
        override val deletePresetTitle = "Delete Preset"
        override val deletePresetConfirm = "Are you sure you want to delete this preset?"
        override val confirmAction = "Confirm"
        override val cancelAction = "Cancel"
        override val saveAction = "Save"
        override val deleteAction = "Delete"
        override val deleteBookmarkTitle = "Delete Bookmark"
        override val deleteBookmarkConfirm = "Are you sure you want to delete this bookmark?"
        override val deleteNoteTitle = "Delete Note"
        override val deleteNoteConfirm = "Are you sure you want to delete this note?"

        override val advancedSettings = "Advanced"
        override val gpuAcceleration = "GPU Hardware Acceleration"
        override val loggingEnabled = "Debug Log Recording"
        override val resetAllSettings = "Reset All Settings"
        override val resetAllSettingsDesc = "Restore all settings to system default values"
        override val settingsResetSuccess = "Settings reset successfully"

        override val chapterFullText = "Full Text"
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
        override val folderCreated = "Folder created"
        override val removedFromFolder = "Removed from folder"
        override val addedToFolder = "Added to folder"
        override val newFolder = "New Folder"
        override val importSuccessCount = { count: Int -> "Successfully imported $count books" }
        override val importSuccessWithSkipped = { success: Int, skipped: Int -> "Successfully imported $success, skipped $skipped duplicates" }
        override val importSuccessWithFailed = { success: Int, failed: Int -> "Successfully imported $success, failed to import $failed" }
        override val importSuccessWithBoth = { success: Int, skipped: Int, failed: Int -> "Successfully imported $success, skipped $skipped, failed $failed" }
        override val importFailed = { error: String -> "Failed to import: $error" }
        override val favoriteToggled = "Favorite status updated"
        override val addFavorite = "Add to favorites"
        override val removeFavorite = "Remove from favorites"
        override val bookInfo = "Book info"
        override val deleteBook = "Delete"
        override val deleteBookTitle = "Delete book"
        override val deleteBookConfirm = { title: String -> "Are you sure you want to delete \"$title\"? This action cannot be undone." }
        override val cancel = "Cancel"
        override val filterAll = "All"
        override val filterFinished = "Finished"
        override val filterFavorite = "Favorites"
        override val sortTitle = "Sort"
        override val sortDescending = "Descending"
        override val sortAscending = "Ascending"
        override val sortLastRead = "Last read"
        override val sortAddTime = "Added time"
        override val sortBookTitle = "Title"
        override val sortReadingTime = "Reading time"
        override val sortReadingProgress = "Reading progress"
        override val bookTitleLabel = "Title"
        override val bookAuthorLabel = "Author"
        override val unknownAuthor = "Unknown"
        override val bookFormatLabel = "Format"
        override val bookSizeLabel = "Size"
        override val bookProgressLabel = "Progress"
        override val readingDurationLabel = "Reading time"
        override val notReadYet = "Not read yet"
        override val unreadLabel = "Unread"
        override val notStartedLabel = "Not started"
        override val readProgress = { percent: Int -> "Read $percent%" }
        override val filePathLabel = "File path"
        override val favoritedDesc = "Favorited"
        override val directoryTab = "Contents"
        override val bookmarksTab = "Bookmarks"
        override val notesTab = "Notes"
        override val currentChapterLabel = "Current"
        override val noBookmarks = "No bookmarks"
        override val noNotes = "No notes"
        override val notePosition = { start: Int, end: Int, date: String -> "Position: $start-$end  $date" }
        override val copySelection = "Copy"
        override val addBookmarkAction = "Bookmark"
        override val addNoteAction = "Note"
        override val previousChapter = "Previous chapter"
        override val nextChapter = "Next chapter"
        override val customizeCover = "Customize cover color"
        override val resetCoverColor = "Reset to auto"
        override val unifiedCoverColor = "Unified cover color"
        override val unifiedCoverColorAuto = "Auto (hash by title)"
        override val unifiedCoverColorActive = { idx: Int -> "Unified to palette #$idx" }
        override val unableToReadFile = "Unable to read file"
        override val invalidFolderPath = "Invalid folder path"
        override val invalidFolder = "Invalid folder"
        override val noImportableFiles = "No importable files found"
    }
}

val LocalAppStrings = staticCompositionLocalOf<AppStrings> { AppStrings.ZhHans }
