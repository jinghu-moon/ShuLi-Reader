// Part of 自动备份调度
package com.shuli.reader.sync.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 备份调度器
 *
 * 使用 WorkManager 管理定时备份任务。
 */
class BackupScheduler(
    private val context: Context,
) {

    /**
     * 调度定时备份
     * @param intervalHours 备份间隔（小时），最小值为 6
     */
    fun scheduleBackup(intervalHours: Int = 24) {
        val safeInterval = intervalHours.coerceAtLeast(MIN_INTERVAL_HOURS)

        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            repeatInterval = safeInterval.toLong(),
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .addTag(TAG_BACKUP)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_BACKUP,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * 取消定时备份
     */
    fun cancelBackup() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_BACKUP)
    }

    /**
     * 检查定时备份是否已调度
     */
    fun isBackupScheduled(): Boolean {
        // WorkManager 不直接提供查询方法，通过标记判断
        return false // 需要实际查询 WorkManager 的 workInfosByTag
    }

    companion object {
        const val WORK_NAME_BACKUP = "periodic_backup"
        const val TAG_BACKUP = "local_backup"
        const val MIN_INTERVAL_HOURS = 6
    }
}
