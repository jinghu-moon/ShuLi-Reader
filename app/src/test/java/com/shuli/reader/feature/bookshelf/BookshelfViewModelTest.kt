package com.shuli.reader.feature.bookshelf

import com.shuli.reader.MainDispatcherRule
import com.shuli.reader.core.repository.BookAlreadyExistsException
import com.shuli.reader.core.repository.BookQueryRepository
import com.shuli.reader.core.repository.BookImportRepository
import com.shuli.reader.core.repository.FolderRepository
import com.shuli.reader.core.repository.ReadingProgressRepository
import com.shuli.reader.core.repository.ImportConfig
import com.shuli.reader.core.repository.ImportResult
import com.shuli.reader.feature.bookshelf.model.FilterType
import com.shuli.reader.feature.bookshelf.model.SortOrder
import com.shuli.reader.feature.bookshelf.model.ViewMode
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class BookshelfViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var bookQueryRepository: BookQueryRepository
    private lateinit var readingProgressRepository: ReadingProgressRepository
    private lateinit var viewModel: BookshelfViewModel

    @Before
    fun setup() {
        bookQueryRepository = mockk(relaxed = true)
        readingProgressRepository = mockk(relaxed = true)
        every { bookQueryRepository.getAllBooks() } returns flowOf(emptyList())
        every { bookQueryRepository.getBookshelfPage(any(), any()) } returns flowOf(emptyList())
        every { bookQueryRepository.searchBooksPage(any(), any(), any()) } returns flowOf(emptyList())
        every { readingProgressRepository.getReadingDurations() } returns flowOf(emptyMap())
        every { readingProgressRepository.getTodayReadingTime() } returns flowOf(0L)
        viewModel = BookshelfViewModel(
            bookQueryRepository = bookQueryRepository,
            folderRepository = mockk(relaxed = true),
            readingProgressRepository = readingProgressRepository,
            bookImportRepository = mockk(relaxed = true),
        )
    }

    @Test
    fun initialState_isEmptyBookshelf() = runTest {
        val state = viewModel.uiState.first { !it.isLoading }
        assertTrue("书架应为空", state.isEmpty)
        assertTrue("书籍列表应为空", state.nodes.isEmpty())
        assertEquals(ViewMode.GRID, state.viewMode)
        assertEquals(SortOrder.LAST_READ, state.sortOrder)
        assertEquals(FilterType.ALL, state.filterType)
        assertEquals(false, state.isAscending)
        assertEquals("", state.searchQuery)
        assertEquals(false, state.isSearching)
    }

    @Test
    fun changingViewMode_updatesState() = runTest {
        viewModel.onViewModeChanged(ViewMode.LIST)
        val state = viewModel.uiState.first { it.viewMode == ViewMode.LIST }
        assertEquals(ViewMode.LIST, state.viewMode)
    }

    @Test
    fun changingSortOrder_updatesState() = runTest {
        viewModel.onSortOrderChanged(SortOrder.TITLE)
        val state = viewModel.uiState.first { it.sortOrder == SortOrder.TITLE }
        assertEquals(SortOrder.TITLE, state.sortOrder)
    }

    @Test
    fun changingFilterType_updatesState() = runTest {
        viewModel.onFilterChanged(FilterType.FAVORITE)
        val state = viewModel.uiState.first { it.filterType == FilterType.FAVORITE }
        assertEquals(FilterType.FAVORITE, state.filterType)
    }

    @Test
    fun enteringQueryWhenSearchActive_updatesState() = runTest {
        viewModel.onSearchActiveChanged(true)
        viewModel.onSearchQueryChanged("测试")
        val state = viewModel.uiState.first { it.isSearching && it.searchQuery == "测试" }
        assertEquals(true, state.isSearching)
        assertEquals("测试", state.searchQuery)
    }

    @Test
    fun closingSearch_clearsQuery() = runTest {
        viewModel.onSearchActiveChanged(true)
        viewModel.onSearchQueryChanged("测试")
        viewModel.onSearchActiveChanged(false)
        val state = viewModel.uiState.first { !it.isSearching && it.searchQuery.isEmpty() }
        assertEquals(false, state.isSearching)
        assertEquals("", state.searchQuery)
    }

    @Test
    fun initialCollection_usesPagedBookshelfQuery() = runTest {
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        advanceUntilIdle()

        verify { bookQueryRepository.getBookshelfPage(BookshelfViewModel.INITIAL_PAGE_SIZE, 0) }
        collection.cancel()
    }

    @Test
    fun activeSearch_usesPagedFtsQuery() = runTest {
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.onSearchActiveChanged(true)
        viewModel.onSearchQueryChanged("测试")
        advanceUntilIdle()

        verify { bookQueryRepository.searchBooksPage("测试", BookshelfViewModel.INITIAL_PAGE_SIZE, 0) }
        collection.cancel()
    }
}
