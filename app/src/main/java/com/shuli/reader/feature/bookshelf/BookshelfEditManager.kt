package com.shuli.reader.feature.bookshelf

import com.shuli.reader.core.repository.BookQueryRepository
import com.shuli.reader.core.repository.FolderRepository
import com.shuli.reader.core.repository.ReadingProgressRepository
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.BookshelfNode
import com.shuli.reader.feature.bookshelf.model.FolderItem
import com.shuli.reader.core.i18n.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 书架编辑模式管理：节点选择、分组/合并、固定槽位、删除。
 *
 * 从 BookshelfViewModel 拆出，SRP —— 只负责"书架编辑操作"这一变更轴。
 */
internal class BookshelfEditManager(
    private val bookQueryRepository: BookQueryRepository,
    private val folderRepository: FolderRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val scope: CoroutineScope,
    private val events: MutableSharedFlow<BookshelfEvent>,
    private val selectedNodeIds: MutableStateFlow<Set<Long>>,
    private val isEditMode: MutableStateFlow<Boolean>,
    private val openFolderId: MutableStateFlow<Long?>,
    private val nodesProvider: () -> List<BookshelfNode>,
    private val toggleEditMode: () -> Unit,
) {

    fun onFolderClick(folderId: Long) {
        openFolderId.value = folderId
    }

    fun onFolderDismiss() {
        openFolderId.value = null
    }

    fun onToggleEditMode(selectNodeId: Long? = null) {
        isEditMode.value = !isEditMode.value
        if (isEditMode.value && selectNodeId != null) {
            selectedNodeIds.value = setOf(selectNodeId)
        } else if (!isEditMode.value) {
            selectedNodeIds.value = emptySet()
        }
    }

    fun onToggleNodeSelection(nodeId: Long) {
        val current = selectedNodeIds.value.toMutableSet()
        if (current.contains(nodeId)) current.remove(nodeId) else current.add(nodeId)
        selectedNodeIds.value = current
    }

    fun onSelectAllNodes(nodes: List<BookshelfNode>) {
        if (selectedNodeIds.value.size == nodes.size) {
            selectedNodeIds.value = emptySet()
        } else {
            selectedNodeIds.value = nodes.map { it.id }.toSet()
        }
    }

    fun onCreateFolderAndMove(folderName: String, sourceNodeId: Long, targetNodeId: Long) {
        scope.launch {
            val folderId = folderRepository.createFolder(folderName)
            folderRepository.moveBooksToFolder(listOf(sourceNodeId, targetNodeId), folderId)
            events.emit(BookshelfEvent.ShowMessage { it.bookshelf.folderCreated })
        }
    }

    fun onMoveSelectedToFolder(folderId: Long?) {
        val selectedIds = selectedNodeIds.value.toList()
        if (selectedIds.isEmpty()) return
        val bookIds = selectedIds.filter { it > 0 }
        val realFolderId = folderId?.let { if (it < 0) -it else it }

        scope.launch {
            if (bookIds.isNotEmpty()) folderRepository.moveBooksToFolder(bookIds, realFolderId)
            toggleEditMode()
            events.emit(BookshelfEvent.ShowMessage { if (folderId == null) it.bookshelf.removedFromFolder else it.bookshelf.addedToFolder })
        }
    }

    fun deleteNodes(nodeIds: Set<Long>) {
        scope.launch(Dispatchers.IO) {
            val nodesToDelete = nodesProvider().filter { it.id in nodeIds }
            nodesToDelete.forEach { node ->
                if (node is BookItem) bookQueryRepository.deleteBook(node.id)
                else if (node is FolderItem) folderRepository.deleteFolder(-node.id)
            }
        }
        selectedNodeIds.value = emptySet()
        isEditMode.value = false
    }

    fun onMoveSelectedToNewFolder(folderName: String) {
        val selectedIds = selectedNodeIds.value.toList()
        if (selectedIds.isEmpty()) return
        val bookIds = selectedIds.filter { it > 0 }

        scope.launch {
            val folderId = folderRepository.createFolder(folderName)
            if (bookIds.isNotEmpty()) folderRepository.moveBooksToFolder(bookIds, folderId)
            toggleEditMode()
            events.emit(BookshelfEvent.ShowMessage { it.bookshelf.groupCreatedAndMoved })
        }
    }

    fun mergeNodes(sourceId: Long, targetId: Long, sourceIsFolder: Boolean, targetIsFolder: Boolean, defaultFolderName: String = "New Folder") {
        val srcIsFolder = sourceIsFolder || sourceId < 0
        val tgtIsFolder = targetIsFolder || targetId < 0
        if (srcIsFolder || sourceId == targetId) return

        scope.launch(Dispatchers.IO) {
            if (tgtIsFolder) {
                folderRepository.moveBooksToFolder(listOf(sourceId), -targetId)
            } else {
                val newFolderId = folderRepository.createFolder(defaultFolderName)
                folderRepository.moveBooksToFolder(listOf(sourceId, targetId), newFolderId)
            }
        }
    }

    /** 将节点固定到指定槽位（O(1) 写入） */
    fun pinNode(nodeId: Long, slot: Int) {
        scope.launch(Dispatchers.IO) {
            if (nodeId > 0) readingProgressRepository.updateBookPinnedSlot(nodeId, slot)
            else readingProgressRepository.updateFolderPinnedSlot(-nodeId, slot)
        }
    }

    /** 取消节点固定（恢复自动排序） */
    fun unpinNode(nodeId: Long) {
        scope.launch(Dispatchers.IO) {
            if (nodeId > 0) readingProgressRepository.updateBookPinnedSlot(nodeId, null)
            else readingProgressRepository.updateFolderPinnedSlot(-nodeId, null)
        }
    }

    /** 清除所有固定项（重置为自动排序） */
    fun clearAllPinnedSlots() {
        scope.launch(Dispatchers.IO) {
            readingProgressRepository.clearAllPinnedSlots()
        }
    }

    /**
     * 拖拽排序后批量更新 pinnedSlot。
     * 对比新旧列表，找出位置变化的项，只更新变化的项（O(k) 写入，k = 变化数）。
     */
    fun commitDragResult(newNodes: List<BookshelfNode>) {
        scope.launch(Dispatchers.IO) {
            newNodes.forEachIndexed { index, node ->
                if (node.pinnedSlot != index) {
                    if (node is BookItem) readingProgressRepository.updateBookPinnedSlot(node.id, index)
                    else if (node is FolderItem) readingProgressRepository.updateFolderPinnedSlot(-node.id, index)
                }
            }
        }
    }

    /** 为单本书指定自定义封面色盘索引（0..19）；传 null 恢复自动散列。 */
    fun setBookCoverPalette(bookId: Long, paletteIndex: Int?) {
        scope.launch {
            readingProgressRepository.setCustomCoverPaletteIndex(bookId, paletteIndex)
        }
    }
}
