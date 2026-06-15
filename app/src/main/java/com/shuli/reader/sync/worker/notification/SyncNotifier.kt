package com.shuli.reader.sync.worker.notification

/**
 * 同步通知器接口（T-25）
 */
interface SyncNotifier {
    /**
     * 更新通知文本
     */
    fun update(text: String)

    /**
     * 取消通知
     */
    fun cancel()
}
