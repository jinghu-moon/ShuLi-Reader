package com.shuli.reader.sync.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shuli.reader.sync.engine.SyncOrchestrator
import com.shuli.reader.sync.engine.SyncTarget

// Part of T-24 WorkManager 周期同步
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val orchestrator: SyncOrchestrator,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val target = when (inputData.getString(KEY_SYNC_TARGET)) {
                "cloud" -> SyncTarget.CLOUD
                "local" -> SyncTarget.LOCAL
                "both" -> SyncTarget.BOTH
                else -> SyncTarget.CLOUD
            }
            orchestrator.sync(target)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_SYNC_TARGET = "sync_target"
        const val MAX_RETRIES = 3
    }
}
