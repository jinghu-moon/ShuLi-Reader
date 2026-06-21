package com.shuli.reader.feature.reader.editor

import com.shuli.reader.core.database.dao.EditDeltaDao
import com.shuli.reader.core.database.entity.EditDeltaEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 编辑补丁存储。
 *
 * 内存 + DB 双层存储：
 * - 内存：快速访问（撤销/重做/应用）
 * - Room edit_delta 表：防崩溃丢失
 *
 * 保存到文件后清空内存和 DB。
 */
class EditStore(
    private val editDeltaDao: EditDeltaDao? = null,
    private var bookId: Long = 0L,
) {

    /** 设置当前书籍 ID */
    fun setBookId(id: Long) {
        bookId = id
    }

    /** 补丁类型（单个或批量） */
    sealed interface Patch {
        val chapterIndex: Int
        val timestamp: Long
    }

    data class SinglePatch(val delta: EditDelta) : Patch {
        override val chapterIndex get() = delta.chapterIndex
        override val timestamp get() = delta.timestamp
    }

    data class BatchPatch(val batch: BatchEditDelta) : Patch {
        override val chapterIndex get() = batch.chapterIndex
        override val timestamp get() = batch.timestamp
    }

    private val _patches = mutableListOf<Patch>()
    val patches: List<Patch> get() = _patches.toList()

    private val undoStack = ArrayDeque<Patch>()
    private val redoStack = ArrayDeque<Patch>()

    /** 编辑状态 Flow（用于 UI 观察） */
    private val _editState = MutableStateFlow(EditState())
    val editState: StateFlow<EditState> = _editState.asStateFlow()

    data class EditState(
        val patchCount: Int = 0,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val isDirty: Boolean = false,
    )

    private fun updateState() {
        _editState.value = EditState(
            patchCount = _patches.size,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            isDirty = _patches.isNotEmpty(),
        )
    }

    /** 添加单个编辑 */
    suspend fun addSingle(delta: EditDelta) {
        val patch = SinglePatch(delta)
        _patches.add(patch)
        undoStack.addLast(patch)
        redoStack.clear()
        updateState()

        // 持久化到 DB
        editDeltaDao?.insert(
            EditDeltaEntity(
                bookId = bookId,
                chapterIndex = delta.chapterIndex,
                charStart = delta.charStart,
                charEnd = delta.charEnd,
                newText = delta.newText,
                originalText = delta.originalText,
                timestamp = delta.timestamp,
            )
        )
    }

    /** 添加批量编辑 */
    suspend fun addBatch(batch: BatchEditDelta) {
        val patch = BatchPatch(batch)
        _patches.add(patch)
        undoStack.addLast(patch)
        redoStack.clear()
        updateState()

        // 持久化到 DB（batchId = timestamp，同一批次共享）
        val entities = batch.expand().map { delta ->
            EditDeltaEntity(
                bookId = bookId,
                chapterIndex = delta.chapterIndex,
                charStart = delta.charStart,
                charEnd = delta.charEnd,
                newText = delta.newText,
                originalText = delta.originalText,
                timestamp = delta.timestamp,
                batchId = batch.timestamp,
            )
        }
        editDeltaDao?.insertAll(entities)
    }

    /** 获取指定章节的所有 Delta（展开批量，按 charStart 降序） */
    fun getDeltasForChapter(chapterIndex: Int): List<EditDelta> =
        _patches
            .filter { it.chapterIndex == chapterIndex }
            .flatMap { patch ->
                when (patch) {
                    is SinglePatch -> listOf(patch.delta)
                    is BatchPatch -> patch.batch.expand()
                }
            }
            .sortedByDescending { it.charStart }

    /** 获取所有 Delta（按章节分组） */
    fun getAllDeltasGrouped(): Map<Int, List<EditDelta>> =
        _patches
            .groupBy { it.chapterIndex }
            .mapValues { (_, patches) ->
                patches.flatMap { patch ->
                    when (patch) {
                        is SinglePatch -> listOf(patch.delta)
                        is BatchPatch -> patch.batch.expand()
                    }
                }.sortedByDescending { it.charStart }
            }

    /** 撤销最后一条编辑 */
    suspend fun undo(): Patch? {
        val patch = undoStack.removeLastOrNull() ?: return null
        _patches.remove(patch)
        redoStack.addLast(patch)
        updateState()

        // 定向删除被撤销的 patch 对应的 DB 记录
        editDeltaDao?.let { dao ->
            val deltas = when (patch) {
                is SinglePatch -> listOf(patch.delta)
                is BatchPatch -> patch.batch.expand()
            }
            for (delta in deltas) {
                dao.deleteByPosition(bookId, delta.chapterIndex, delta.charStart, delta.timestamp)
            }
        }
        return patch
    }

    /** 重做 */
    suspend fun redo(): Patch? {
        val patch = redoStack.removeLastOrNull() ?: return null
        _patches.add(patch)
        undoStack.addLast(patch)
        updateState()

        // 定向插入重做的 patch 对应的 DB 记录
        editDeltaDao?.let { dao ->
            val entities = when (patch) {
                is SinglePatch -> listOf(patch.delta)
                is BatchPatch -> patch.batch.expand()
            }.map { delta ->
                EditDeltaEntity(
                    bookId = bookId,
                    chapterIndex = delta.chapterIndex,
                    charStart = delta.charStart,
                    charEnd = delta.charEnd,
                    newText = delta.newText,
                    originalText = delta.originalText,
                    timestamp = delta.timestamp,
                )
            }
            dao.insertAll(entities)
        }
        return patch
    }

    /** 同步当前状态到 DB */
    private suspend fun syncToDb() {
        editDeltaDao?.let { dao ->
            dao.deleteAll()
            val entities = _patches.flatMap { patch ->
                when (patch) {
                    is SinglePatch -> listOf(patch.delta)
                    is BatchPatch -> patch.batch.expand()
                }
            }.map { delta ->
                EditDeltaEntity(
                    bookId = bookId,
                    chapterIndex = delta.chapterIndex,
                    charStart = delta.charStart,
                    charEnd = delta.charEnd,
                    newText = delta.newText,
                    originalText = delta.originalText,
                    timestamp = delta.timestamp,
                )
            }
            if (entities.isNotEmpty()) {
                dao.insertAll(entities)
            }
        }
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val isEmpty: Boolean get() = _patches.isEmpty()
    val isDirty: Boolean get() = _patches.isNotEmpty()

    suspend fun clear() {
        _patches.clear()
        undoStack.clear()
        redoStack.clear()
        updateState()

        // 清空 DB
        editDeltaDao?.deleteAll()
    }

    /** 从 DB 恢复编辑（崩溃恢复） */
    suspend fun restoreFromDb(bookId: Long) {
        editDeltaDao?.let { dao ->
            val entities = dao.getByBookId(bookId)
            if (entities.isEmpty()) return

            val patches = mutableListOf<Patch>()

            // 按 batchId 分组：batchId=0 为单个编辑，batchId>0 为批量编辑
            val grouped = entities.groupBy { it.batchId }
            for ((batchId, group) in grouped) {
                if (batchId == 0L) {
                    // 单个编辑：每个 entity 是一个 SinglePatch
                    group.forEach { entity ->
                        patches.add(SinglePatch(
                            EditDelta(
                                chapterIndex = entity.chapterIndex,
                                charStart = entity.charStart,
                                charEnd = entity.charEnd,
                                newText = entity.newText,
                                originalText = entity.originalText,
                                timestamp = entity.timestamp,
                            )
                        ))
                    }
                } else {
                    // 批量编辑：同一 batchId 的所有 entity 组成一个 BatchPatch
                    val first = group.first()
                    val ranges = group.map { it.charStart until it.charEnd }
                    patches.add(BatchPatch(
                        BatchEditDelta(
                            chapterIndex = first.chapterIndex,
                            findText = first.originalText,
                            replaceText = first.newText,
                            ranges = ranges,
                            timestamp = batchId,
                        )
                    ))
                }
            }

            // 按 timestamp 排序恢复顺序
            patches.sortBy { it.timestamp }

            _patches.clear()
            _patches.addAll(patches)
            undoStack.clear()
            undoStack.addAll(patches)
            redoStack.clear()
            updateState()
        }
    }
}
