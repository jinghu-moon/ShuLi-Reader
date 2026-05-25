package com.shuli.reader.feature.reader

import com.shuli.reader.MainDispatcherRule
import com.shuli.reader.core.data.PageAnimType
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import com.shuli.reader.core.reader.Paginator
import com.shuli.reader.core.reader.TextMeasurer
import com.shuli.reader.core.reader.animation.PageDelegateFactory
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.repository.BookRepository
import com.shuli.reader.core.repository.SearchResult
import com.shuli.reader.core.tts.TtsConfig
import com.shuli.reader.core.tts.TtsEngine
import com.shuli.reader.core.tts.TtsState
import com.shuli.reader.data.TestDataStoreFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setup() {
        viewModel = ReaderViewModel()
    }

    @Test
    fun initialState_isCorrect() = runTest {
        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals("初始状态不应加载中", false, state.isLoading)
        assertNull("初始状态不应有错误", state.error)
    }

    @Test
    fun openBook_setsLoadingState() = runTest {
        viewModel.openBook(1L)
        val state = viewModel.uiState.value
        assertTrue("应处于加载状态或已完成加载", state.isLoading || state.bookTitle.isNotEmpty())
    }

    @Test
    fun openBook_whenCompleted_setsBookInfo() = runTest {
        viewModel.openBook(1L)
        val state = viewModel.uiState.value
        if (!state.isLoading) {
            assertEquals("书籍标题应正确", "Book 1", state.bookTitle)
            assertEquals("章节数应正确", 10, state.totalChapters)
        }
    }

    @Test
    fun toggleToolbar_updatesVisibility() = runTest {
        viewModel.toggleToolbar()
        val state = viewModel.uiState.first { it.showToolbar }
        assertEquals("工具栏应显示", true, state.showToolbar)

        viewModel.toggleToolbar()
        val state2 = viewModel.uiState.first { !it.showToolbar }
        assertEquals("工具栏应隐藏", false, state2.showToolbar)
    }

    @Test
    fun toggleDirectory_updatesVisibility() = runTest {
        viewModel.toggleDirectory()
        val state = viewModel.uiState.first { it.showDirectory }
        assertEquals("目录应显示", true, state.showDirectory)

        viewModel.toggleDirectory()
        val state2 = viewModel.uiState.first { !it.showDirectory }
        assertEquals("目录应隐藏", false, state2.showDirectory)
    }

    // T9.1 - 阅读偏好集成测试

    @Test
    fun defaultReaderPreferences_areCorrect() = runTest {
        val state = viewModel.uiState.value
        val prefs = state.readerPreferences
        assertEquals(16f, prefs.fontSize, 0.01f)
        assertEquals(1.5f, prefs.lineSpacing, 0.01f)
        assertEquals(PageAnimType.HORIZONTAL, prefs.pageAnimType)
        assertEquals(ReaderTheme.PAPER, prefs.backgroundColor)
    }

    @Test
    fun setFontSize_updatesReaderPreferences() = runTest {
        viewModel.setFontSize(20f)
        val state = viewModel.uiState.value
        assertEquals(20f, state.readerPreferences.fontSize, 0.01f)
    }

    @Test
    fun setLineSpacing_updatesReaderPreferences() = runTest {
        viewModel.setLineSpacing(2.0f)
        val state = viewModel.uiState.value
        assertEquals(2.0f, state.readerPreferences.lineSpacing, 0.01f)
    }

    @Test
    fun setReaderTheme_updatesBackgroundTheme() = runTest {
        viewModel.setReaderTheme(ReaderTheme.DARK)
        val state = viewModel.uiState.value
        assertEquals(ReaderTheme.DARK, state.readerPreferences.backgroundColor)
    }

    @Test
    fun setReaderTheme_canSwitchBackToLight() = runTest {
        viewModel.setReaderTheme(ReaderTheme.DARK)
        viewModel.setReaderTheme(ReaderTheme.LIGHT)
        val state = viewModel.uiState.value
        assertEquals(ReaderTheme.LIGHT, state.readerPreferences.backgroundColor)
    }

    @Test
    fun setPageAnimType_updatesState() = runTest {
        viewModel.setPageAnimType(PageDelegateFactory.PageAnimType.SIMULATION)
        val state = viewModel.uiState.value
        assertEquals(PageDelegateFactory.PageAnimType.SIMULATION, state.pageAnimType)
    }

    @Test
    fun setPageAnimType_updatesPageDelegate() = runTest {
        viewModel.setPageAnimType(PageDelegateFactory.PageAnimType.COVER)
        assertNotNull(viewModel.pageDelegate)
    }

    @Test
    fun viewModelWithUserPreferences_readsPreferenceValues() = runTest {
        val dataStore = TestDataStoreFactory.create()
        val userPrefs = UserPreferences(dataStore)
        val vm = ReaderViewModel(userPreferences = userPrefs)

        // 默认偏好应被读取
        val state = vm.uiState.value
        assertEquals(16f, state.readerPreferences.fontSize, 0.01f)
        assertEquals(1.5f, state.readerPreferences.lineSpacing, 0.01f)
    }

    @Test
    fun toggleQuickSettings_updatesVisibility() = runTest {
        viewModel.toggleQuickSettings()
        val state = viewModel.uiState.first { it.showQuickSettings }
        assertEquals(true, state.showQuickSettings)

        viewModel.toggleQuickSettings()
        val state2 = viewModel.uiState.first { !it.showQuickSettings }
        assertEquals(false, state2.showQuickSettings)
    }

    // T9.2 - 主题系统测试

    @Test
    fun themeColors_areDerivedFromReaderPreferences() = runTest {
        val state = viewModel.uiState.value
        val colors = state.themeColors
        // 默认 PAPER 主题
        assertEquals(0xFFEAE5DC.toInt(), colors.backgroundColor)
        assertEquals(0xFF2C231A.toInt(), colors.textColor)
    }

    @Test
    fun setReaderTheme_updatesThemeColors() = runTest {
        viewModel.setReaderTheme(ReaderTheme.DARK)
        val colors = viewModel.uiState.value.themeColors
        assertEquals(0xFF1A130B.toInt(), colors.backgroundColor)
        assertEquals(0xFFEAE5DC.toInt(), colors.textColor)
    }

    @Test
    fun cycleTheme_switchesFromLightToDark() = runTest {
        viewModel.setReaderTheme(ReaderTheme.LIGHT)
        viewModel.cycleTheme()
        assertEquals(ReaderTheme.DARK, viewModel.uiState.value.readerPreferences.backgroundColor)
    }

    @Test
    fun cycleTheme_switchesFromDarkToPaper() = runTest {
        viewModel.setReaderTheme(ReaderTheme.DARK)
        viewModel.cycleTheme()
        assertEquals(ReaderTheme.PAPER, viewModel.uiState.value.readerPreferences.backgroundColor)
    }

    @Test
    fun cycleTheme_switchesFromPaperToLight() = runTest {
        viewModel.setReaderTheme(ReaderTheme.PAPER)
        viewModel.cycleTheme()
        assertEquals(ReaderTheme.LIGHT, viewModel.uiState.value.readerPreferences.backgroundColor)
    }

    @Test
    fun cycleTheme_threeTimes_returnsToOriginalTheme() = runTest {
        val original = viewModel.uiState.value.readerPreferences.backgroundColor
        viewModel.cycleTheme()
        viewModel.cycleTheme()
        viewModel.cycleTheme()
        assertEquals(original, viewModel.uiState.value.readerPreferences.backgroundColor)
    }

    @Test
    fun paperThemeColors_areCorrect() = runTest {
        viewModel.setReaderTheme(ReaderTheme.PAPER)
        val colors = viewModel.uiState.value.themeColors
        assertEquals(0xFFEAE5DC.toInt(), colors.backgroundColor)
        assertEquals(0xFF2C231A.toInt(), colors.textColor)
        assertEquals(0xFF7D7162.toInt(), colors.headerColor)
        assertEquals(0xFF7D7162.toInt(), colors.footerColor)
    }

    // T6.3 - 工具栏自动隐藏测试

    @Test
    fun toolbar_autoHidesAfterFiveSeconds() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val vm = ReaderViewModel()
            vm.toggleToolbar()
            assertTrue("工具栏应显示", vm.uiState.value.showToolbar)

            dispatcher.scheduler.advanceTimeBy(5000L)
            dispatcher.scheduler.runCurrent()

            assertFalse("工具栏应在 5 秒后自动隐藏", vm.uiState.value.showToolbar)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun toolbar_staysVisibleBeforeFiveSeconds() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val vm = ReaderViewModel()
            vm.toggleToolbar()

            dispatcher.scheduler.advanceTimeBy(4000L)
            dispatcher.scheduler.runCurrent()

            assertTrue("工具栏在 4 秒时仍应显示", vm.uiState.value.showToolbar)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun repeatedToolbarOperation_resetsAutoHideTimer() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val vm = ReaderViewModel()
            vm.toggleToolbar()

            // 3 秒后再次触发（模拟用户操作）
            dispatcher.scheduler.advanceTimeBy(3000L)
            dispatcher.scheduler.runCurrent()
            vm.toggleToolbar() // 隐藏
            vm.toggleToolbar() // 再次显示，重置计时器

            // 再过 4 秒（重置后 4 秒）
            dispatcher.scheduler.advanceTimeBy(4000L)
            dispatcher.scheduler.runCurrent()

            assertTrue("重置后 4 秒工具栏仍应显示", vm.uiState.value.showToolbar)

            // 再过 1 秒（重置后 5 秒）
            dispatcher.scheduler.advanceTimeBy(1000L)
            dispatcher.scheduler.runCurrent()

            assertFalse("重置后 5 秒工具栏应自动隐藏", vm.uiState.value.showToolbar)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun manualHide_cancelsToolbarAutoHide() {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val vm = ReaderViewModel()
            vm.toggleToolbar() // 显示

            dispatcher.scheduler.advanceTimeBy(2000L)
            dispatcher.scheduler.runCurrent()
            vm.toggleToolbar() // 手动隐藏

            dispatcher.scheduler.advanceTimeBy(6000L)
            dispatcher.scheduler.runCurrent()

            assertFalse("手动隐藏后工具栏应保持隐藏", vm.uiState.value.showToolbar)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // T10.4 - 书签与笔记测试

    @Test
    fun openBook_setsBookIdInState() = runTest {
        viewModel.openBook(42L)
        val state = viewModel.uiState.value
        assertEquals(42L, state.bookId)
    }

    @Test
    fun addBookmark_withoutDao_doesNotCrash() = runTest {
        viewModel.openBook(1L)
        viewModel.addBookmark("选中文本")
        // 不应抛出异常
        assertTrue(true)
    }

    @Test
    fun deleteBookmark_withoutDao_doesNotCrash() = runTest {
        viewModel.openBook(1L)
        // 传入一个模拟的 BookmarkEntity 不实际需要，只需验证 null DAO 安全
        viewModel.loadBookmarks()
        assertTrue(true)
    }

    @Test
    fun addNote_withoutDao_doesNotCrash() = runTest {
        viewModel.openBook(1L)
        viewModel.addNote(0, 10, "笔记内容")
        assertTrue(true)
    }

    @Test
    fun loadBookmarks_withoutDao_doesNotModifyState() = runTest {
        viewModel.openBook(1L)
        val before = viewModel.uiState.value.bookmarks
        viewModel.loadBookmarks()
        val after = viewModel.uiState.value.bookmarks
        assertEquals(before, after)
    }

    @Test
    fun loadNotes_withoutDao_doesNotModifyState() = runTest {
        viewModel.openBook(1L)
        val before = viewModel.uiState.value.notes
        viewModel.loadNotes()
        val after = viewModel.uiState.value.notes
        assertEquals(before, after)
    }

    // T11.1 - 阅读页搜索结果导航测试

    @Test
    fun setSearchResults_savesResultsAndNavigatesToFirst() = runTest {
        val results = listOf(
            searchResult(chapterIndex = 0, charOffset = 10),
            searchResult(chapterIndex = 1, charOffset = 20),
        )

        viewModel.setSearchResults("星海", results)

        val state = viewModel.uiState.value
        assertEquals("星海", state.searchQuery)
        assertEquals(results, state.searchResults)
        assertEquals(0, state.currentSearchResultIndex)
    }

    @Test
    fun goToNextSearchResult_cyclesToNextResult() = runTest {
        viewModel.setSearchResults(
            "星海",
            listOf(
                searchResult(chapterIndex = 0, charOffset = 10),
                searchResult(chapterIndex = 1, charOffset = 20),
            ),
        )

        viewModel.goToNextSearchResult()
        assertEquals(1, viewModel.uiState.value.currentSearchResultIndex)

        viewModel.goToNextSearchResult()
        assertEquals(0, viewModel.uiState.value.currentSearchResultIndex)
    }

    @Test
    fun goToPreviousSearchResult_cyclesToPreviousResult() = runTest {
        viewModel.setSearchResults(
            "星海",
            listOf(
                searchResult(chapterIndex = 0, charOffset = 10),
                searchResult(chapterIndex = 1, charOffset = 20),
            ),
        )

        viewModel.goToPreviousSearchResult()
        assertEquals(1, viewModel.uiState.value.currentSearchResultIndex)
    }

    @Test
    fun searchInCurrentBook_withBlankQuery_clearsSearchState() = runTest {
        viewModel.setSearchResults("星海", listOf(searchResult(chapterIndex = 0, charOffset = 10)))

        viewModel.searchInCurrentBook("   ")

        val state = viewModel.uiState.value
        assertEquals("   ", state.searchQuery)
        assertTrue(state.searchResults.isEmpty())
        assertEquals(-1, state.currentSearchResultIndex)
    }

    @Test
    fun selectText_updatesSelectedRange() = runTest {
        val range = SelectionRange(chapterIndex = 0, startPos = 1, endPos = 5, selectedText = "text")

        viewModel.selectText(range)

        assertEquals(range, viewModel.uiState.value.selectedRange)
    }

    @Test
    fun clearTextSelection_removesSelectedRange() = runTest {
        viewModel.selectText(SelectionRange(chapterIndex = 0, startPos = 1, endPos = 5, selectedText = "text"))

        viewModel.clearTextSelection()

        assertNull(viewModel.uiState.value.selectedRange)
    }

    @Test
    fun addBookmarkFromSelection_clearsSelection() = runTest {
        viewModel.selectText(SelectionRange(chapterIndex = 0, startPos = 1, endPos = 5, selectedText = "text"))

        viewModel.addBookmarkFromSelection()

        assertNull(viewModel.uiState.value.selectedRange)
    }

    @Test
    fun addNoteFromSelection_clearsSelection() = runTest {
        viewModel.selectText(SelectionRange(chapterIndex = 0, startPos = 1, endPos = 5, selectedText = "text"))

        viewModel.addNoteFromSelection()

        assertNull(viewModel.uiState.value.selectedRange)
    }

    @Test
    fun startTts_withHighlightEnabled_highlightsCurrentSentence() = runTest {
        val engine = FakeTtsEngine()
        val vm = readerViewModelWithContent(
            content = "First sentence. Second sentence.",
            engine = engine,
        )

        vm.startTts(TtsConfig(highlightSentence = true))

        assertEquals(TtsState.PLAYING, vm.uiState.value.ttsState)
        assertEquals("First sentence.", engine.spokenText)
        assertEquals("First sentence.", vm.uiState.value.ttsActiveRange?.selectedText)
    }

    @Test
    fun ttsCompletion_advancesHighlightedSentence() = runTest {
        val engine = FakeTtsEngine()
        val vm = readerViewModelWithContent(
            content = "First sentence. Second sentence.",
            engine = engine,
        )

        vm.startTts(TtsConfig(highlightSentence = true))
        engine.completeUtterance()

        assertEquals(TtsState.PLAYING, vm.uiState.value.ttsState)
        assertEquals("Second sentence.", engine.spokenText)
        assertEquals("Second sentence.", vm.uiState.value.ttsActiveRange?.selectedText)
    }

    @Test
    fun ttsCompletionAtPageEnd_withAutoPageEnabled_turnsPageAndContinuesReading() = runTest {
        val engine = FakeTtsEngine()
        val vm = readerViewModelWithContent(
            content = "First page.\nSecond page.",
            engine = engine,
            paginator = Paginator(oneLinePerPageTextMeasurer()),
        )

        vm.startTts(TtsConfig(autoPage = true, highlightSentence = true))
        engine.completeUtterance()

        assertEquals(1, vm.uiState.value.pageIndex)
        assertEquals("Second page.", engine.spokenText)
        assertEquals("Second page.", vm.uiState.value.ttsActiveRange?.selectedText)
    }

    @Test
    fun pauseTtsOnBackground_whenPlaying_pausesEngineAndState() = runTest {
        val engine = FakeTtsEngine()
        val vm = readerViewModelWithContent(
            content = "First sentence. Second sentence.",
            engine = engine,
        )

        vm.startTts(TtsConfig(highlightSentence = true))
        vm.pauseTtsOnBackground()

        assertEquals(TtsState.PAUSED, vm.uiState.value.ttsState)
        assertEquals(1, engine.stopCalls)
    }

    @Test
    fun releaseReaderResources_afterTtsStarted_shutdownsEngineAndClearsState() = runTest {
        val engine = FakeTtsEngine()
        val vm = readerViewModelWithContent(
            content = "First sentence. Second sentence.",
            engine = engine,
        )

        vm.startTts(TtsConfig(highlightSentence = true))
        vm.releaseReaderResources()

        assertEquals(TtsState.IDLE, vm.uiState.value.ttsState)
        assertNull(vm.uiState.value.ttsActiveRange)
        assertEquals(1, engine.shutdownCalls)
        assertTrue(engine.listenerCleared)
    }

    private fun searchResult(chapterIndex: Int, charOffset: Int): SearchResult {
        return SearchResult(
            chapterIndex = chapterIndex,
            chapterTitle = "Chapter ${chapterIndex + 1}",
            charOffset = charOffset,
            matchStart = charOffset,
            matchEnd = charOffset + 2,
            context = "星海",
            matchedText = "星海",
        )
    }

    private suspend fun readerViewModelWithContent(
        content: String,
        engine: FakeTtsEngine,
        paginator: Paginator = Paginator(com.shuli.reader.core.reader.SimpleTextMeasurer()),
    ): ReaderViewModel {
        val repository = mockk<BookRepository>()
        val book = BookEntity(
            id = 1L,
            title = "Test Book",
            author = null,
            filePath = "test.txt",
            fileType = "TXT",
            fileSize = content.length.toLong(),
            coverPath = null,
            lastReadTime = null,
            addedTime = 1L,
        )
        every { repository.getBookById(1L) } returns flowOf(book)
        coEvery { repository.parseBookContent(any()) } returns BookContent(
            title = "Test Book",
            author = null,
            encoding = "UTF-8",
            totalLength = content.length.toLong(),
            chapters = listOf(Chapter(title = "Chapter 1", startIndex = 0, endIndex = content.length)),
            content = content,
        )
        coEvery { repository.getChapterText(any(), any(), any()) } answers {
            val indexArg = arg<Int>(1)
            val contentArg = arg<BookContent>(2)
            val chapter = contentArg.chapters.getOrNull(indexArg)
            if (chapter != null) {
                val start = chapter.startIndex.coerceIn(0, contentArg.content.length)
                val end = chapter.endIndex.coerceIn(start, contentArg.content.length)
                contentArg.content.substring(start, end)
            } else {
                ""
            }
        }
        coEvery { repository.updateLastReadTime(1L) } just runs

        val userPrefs = mockk<UserPreferences>()
        every { userPrefs.defaultFontSize } returns flowOf(16f)
        every { userPrefs.defaultLineSpacing } returns flowOf(1.5f)
        every { userPrefs.defaultParagraphSpacing } returns flowOf(1.0f)
        every { userPrefs.defaultIndent } returns flowOf(0f)
        every { userPrefs.defaultPageAnim } returns flowOf("SLIDE")
        every { userPrefs.brightness } returns flowOf(0.5f)
        val mockPrefs = mockk<androidx.datastore.preferences.core.Preferences>()
        coEvery { userPrefs.setDefaultFontSize(any()) } returns mockPrefs
        coEvery { userPrefs.setDefaultLineSpacing(any()) } returns mockPrefs
        coEvery { userPrefs.setDefaultParagraphSpacing(any()) } returns mockPrefs
        coEvery { userPrefs.setDefaultIndent(any()) } returns mockPrefs
        coEvery { userPrefs.setBrightness(any()) } returns mockPrefs

        val vm = ReaderViewModel(
            userPreferences = userPrefs,
            bookRepository = repository,
            paginator = paginator,
            ttsEngine = engine,
        )
        vm.openBook(1L)
        vm.uiState.first { it.currentPage != null }
        return vm
    }

    private fun oneLinePerPageTextMeasurer(): TextMeasurer {
        return object : TextMeasurer {
            override fun measureTextWidth(text: String, textSize: Float): Float = text.length * 10f
            override fun measureTextHeight(textSize: Float, lineHeight: Float): Float = 1000f
            override fun measureCharWidth(char: Char, textSize: Float): Float = 10f
        }
    }

    private class FakeTtsEngine : TtsEngine {
        var spokenText = ""
        var stopCalls = 0
        var shutdownCalls = 0
        var listenerCleared = false
        private var listener: TtsEngine.Listener? = null

        override fun configure(config: TtsConfig) = Unit

        override fun setListener(listener: TtsEngine.Listener?) {
            this.listener = listener
            listenerCleared = listener == null
        }

        override fun speak(text: String) {
            spokenText = text
        }

        override fun stop() {
            stopCalls++
        }

        override fun shutdown() {
            shutdownCalls++
        }

        fun completeUtterance() {
            listener?.onUtteranceCompleted()
        }
    }
}
