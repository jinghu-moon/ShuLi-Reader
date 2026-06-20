package com.shuli.reader.feature.reader.editor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 编辑补丁存储。
 *
 * 内存中维护，支持撤销/重做。
 * 保存到文件后清空。
 */
class EditStore {

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
    fun addSingle(delta: EditDelta) {
        val patch = SinglePatch(delta)
        _patches.add(patch)
        undoStack.addLast(patch)
        redoStack.clear()
        updateState()
    }

    /** 添加批量编辑 */
    fun addBatch(batch: BatchEditDelta) {
        val patch = BatchPatch(batch)
        _patches.add(patch)
        undoStack.addLast(patch)
        redoStack.clear()
        updateState()
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
    fun undo(): Patch? {
        val patch = undoStack.removeLastOrNull() ?: return null
        _patches.remove(patch)
        redoStack.addLast(patch)
        updateState()
        return patch
    }

    /** 重做 */
    fun redo(): Patch? {
        val patch = redoStack.removeLastOrNull() ?: return null
        _patches.add(patch)
        undoStack.addLast(patch)
        updateState()
        return patch
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val isEmpty: Boolean get() = _patches.isEmpty()
    val isDirty: Boolean get() = _patches.isNotEmpty()

    fun clear() {
        _patches.clear()
        undoStack.clear()
        redoStack.clear()
        updateState()
    }
}
