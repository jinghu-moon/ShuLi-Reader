package com.shuli.reader.sync.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

// Part of T-24 WorkManager 周期同步
class SyncScheduler(
    private val context: Context,
) {

    fun scheduleCloudSync(intervalHours: Long = 6) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = intervalHours,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .addTag(TAG_CLOUD_SYNC)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_CLOUD_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun scheduleLocalSync(intervalHours: Long = 6) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = intervalHours,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .addTag(TAG_LOCAL_SYNC)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_LOCAL_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelAllSyncs() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_CLOUD_SYNC)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_LOCAL_SYNC)
    }

    companion object {
        const val WORK_NAME_CLOUD_SYNC = "webdav_periodic_sync"
        const val WORK_NAME_LOCAL_SYNC = "local_periodic_sync"
        const val TAG_CLOUD_SYNC = "cloud_sync"
        const val TAG_LOCAL_SYNC = "local_sync"
    }
}
