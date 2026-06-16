package com.shuli.reader.core.reader.engine.cache

import com.shuli.reader.core.recorder.CanvasRecorder
import com.shuli.reader.core.recorder.CanvasRecorderFactory
import com.shuli.reader.core.recorder.record

/**
 * 页面渲染状态的不可变标识 key。
 *
 * 用于 [PageRenderStateStore] 索引页级 recorder。
 * 同一页面在不同 layout 下（字号/边距变化）产生不同 key，旧 key 的 recorder 会被回收。
 */
data class PageKey(
    val chapterIndex: Int,
    val pageIndex: Int,
    val startCharOffset: Int,
    val endCharOffset: Int,
)

/**
 * 行渲染状态的不可变标识 key。
 *
 * 用于 [PageRenderStateStore] 索引行级 recorder。
 */
data class LineKey(
    val pageKey: PageKey,
    val lineIndex: Int,
)

/**
 * 单个页面的渲染资源集合（4 个分层 recorder）。
 *
 * 由 [PageRenderStateStore] 统一管理生命周期，不再挂在 TextPage 上。
 * 只在 UI 线程操作。
 */
class PageRenderState {
    val content: CanvasRecorder = CanvasRecorderFactory.create(locked = true)
    val shell: CanvasRecorder = CanvasRecorderFactory.create(locked = true)
    val overlay: CanvasRecorder = CanvasRecorderFactory.create(locked = true)
    val composite: CanvasRecorder = CanvasRecorderFactory.create(locked = true)

    /** Phase 5: 上次录制使用的 key，用于 key-diff 驱动精确失效 */
    var contentKey: Any? = null
    var shellKey: Any? = null
    var overlayKey: Any? = null

    fun recycle() {
        content.recycle()
        shell.recycle()
        overlay.recycle()
        composite.recycle()
    }

    fun invalidateAll() {
        content.invalidate()
        shell.invalidate()
        overlay.invalidate()
        composite.invalidate()
    }

    fun invalidateContent() {
        content.invalidate()
        composite.invalidate()
    }

    fun invalidateShell() {
        shell.invalidate()
        composite.invalidate()
    }

    fun invalidateOverlay() {
        overlay.invalidate()
        composite.invalidate()
    }

    /**
     * Phase 5: key-diff 驱动失效判断。
     *
     * 比较新旧 key，只有 key 变化时才 invalidate 对应层。
     * key 未变化表示录制结果仍然有效，跳过重录。
     *
     * @return true 如果任何层的 key 发生了变化
     */
    fun applyKeyDiff(
        newContentKey: Any?,
        newShellKey: Any?,
        newOverlayKey: Any?,
    ): Boolean {
        var changed = false
        if (contentKey != newContentKey) {
            contentKey = newContentKey
            content.invalidate()
            changed = true
        }
        if (shellKey != newShellKey) {
            shellKey = newShellKey
            shell.invalidate()
            changed = true
        }
        if (overlayKey != newOverlayKey) {
            overlayKey = newOverlayKey
            overlay.invalidate()
            changed = true
        }
        if (changed) composite.invalidate()
        return changed
    }

    /**
     * 录制 composite：将 shell + content + overlay 叠加。
     * 任一子 recorder 已回收则跳过。
     */
    fun recordComposite(width: Int, height: Int) {
        if (!composite.needRecord()) return
        if (shell.isRecycled() || content.isRecycled() || overlay.isRecycled()) {
            composite.invalidate()
            return
        }
        composite.record(width, height) {
            shell.draw(this)
            content.draw(this)
            overlay.draw(this)
        }
    }
}

/**
 * 页面渲染状态的唯一 owner。
 *
 * 管理所有活跃页面的 recorder 生命周期。
 * TextPage/TextLine 不再持有任何 recorder，CacheManager 驱逐不再泄漏 native 资源。
 *
 * 线程约束：只在 UI 线程操作。
 */
class PageRenderStateStore {
    private val pageStates = mutableMapOf<PageKey, PageRenderState>()
    private val lineStates = mutableMapOf<LineKey, CanvasRecorder>()

    /** 获取或创建页面的渲染状态 */
    fun getPageState(key: PageKey): PageRenderState {
        return pageStates.getOrPut(key) { PageRenderState() }
    }

    /** 获取或创建行的 recorder */
    fun getLineRecorder(key: LineKey): CanvasRecorder {
        return lineStates.getOrPut(key) { CanvasRecorderFactory.create() }
    }

    /** 回收不在活跃集合中的页面和行 recorder */
    fun recycleUnused(activeKeys: Set<PageKey>) {
        val toRemove = pageStates.keys - activeKeys
        toRemove.forEach { key ->
            pageStates.remove(key)?.recycle()
        }
        // 清理不再活跃页面的行 recorder
        val lineKeysToRemove = lineStates.keys.filter { it.pageKey in toRemove }
        lineKeysToRemove.forEach { lineKey ->
            lineStates.remove(lineKey)?.recycle()
        }
    }

    /** 回收所有资源（View detach 时调用） */
    fun clear() {
        pageStates.values.forEach { it.recycle() }
        pageStates.clear()
        lineStates.values.forEach { it.recycle() }
        lineStates.clear()
    }
}
