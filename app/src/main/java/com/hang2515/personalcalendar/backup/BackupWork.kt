package com.hang2515.personalcalendar.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

object BackupWork {
    private const val NAME = "calendar-cloud-backup"
    fun schedule(context: Context, followUp: Boolean = true) {
        if (!CloudConfig.isConfigured) return
        enqueue(WorkManager.getInstance(context), followUp)
    }
    internal fun enqueue(manager: WorkManager, followUp: Boolean) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(30, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        // 编辑发生在运行中任务的最后一次读取之后，也必须留下一个后继任务。
        manager.enqueueUniqueWork(NAME, if (followUp) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, request)
    }
    fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(NAME) }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val coordinator = BackupCoordinator.open(applicationContext)
        return try {
            if (coordinator.upload()) Result.success()
            else if (runAttemptCount < 4) Result.retry()
            // 上传已成功，结束当前任务，不能让失败状态连带取消编辑追加的后继任务。
            else { coordinator.recordError("仍有新修改，后续备份任务将继续处理"); Result.success() }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            val permanent = error is BackupApiException && error.status in setOf(400,401,403,409,412,413,428) || error is IllegalArgumentException
            coordinator.recordError(if (error is BackupApiException || error is IllegalStateException || error is IllegalArgumentException)
                error.message ?: "备份失败，请重试" else "网络或存储异常，备份尚未完成")
            if (permanent || runAttemptCount >= 4) Result.failure() else Result.retry()
        }
    }
}
