package com.hang2515.personalcalendar.backup

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.hang2515.personalcalendar.data.CalendarDatabase
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BackupWork {
    private const val LEGACY_NAME = "calendar-cloud-backup"
    internal const val NAME = "calendar-daily-cloud-backup"
    private val schedulingMutex = Mutex()
    suspend fun schedule(context: Context, replace: Boolean = false, alignNextRun: Boolean = false) = schedulingMutex.withLock {
        if (!CloudConfig.isConfigured) return@withLock
        withContext(Dispatchers.IO) {
            val manager = WorkManager.getInstance(context)
            // 升级后停止旧版“修改后 30 秒”任务，按用户选择的每日时间备份。
            manager.cancelUniqueWork(LEGACY_NAME).result.get()
            val state = LocalBackupStore(CalendarDatabase.open(context)).state()
            if (state.backupEnabled) enqueue(manager, BackupTimeSettings(context).minute(), replace, alignNextRun = alignNextRun)
            else manager.cancelUniqueWork(NAME).result.get()
        }
    }
    internal fun enqueue(manager: WorkManager, minute: Int, replace: Boolean = false, now: Instant = Instant.now(), alignNextRun: Boolean = false) {
        val delay = backupInitialDelayMillis(now, minute)
        val builder = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        // 延期执行后仍按下一次北京时间对齐，不能按实际完成时间永久偏移。
        if (alignNextRun) builder.setNextScheduleTimeOverride(now.toEpochMilli() + delay)
        val request = builder.build()
        // 普通编辑保留下一次执行时间；显式调整时间才取消旧任务并重新安排。
        manager.enqueueUniquePeriodicWork(NAME,
            when {
                replace -> ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                alignNextRun -> ExistingPeriodicWorkPolicy.UPDATE
                else -> ExistingPeriodicWorkPolicy.KEEP
            }, request).result.get()
    }
    suspend fun cancel(context: Context) = schedulingMutex.withLock {
        withContext(Dispatchers.IO) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME).result.get()
            WorkManager.getInstance(context).cancelUniqueWork(LEGACY_NAME).result.get()
        }
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val coordinator = BackupCoordinator.open(applicationContext)
        var alignNextRun = true
        val outcome = try {
            if (coordinator.upload()) Result.success()
            else if (runAttemptCount < 4) { alignNextRun = false; Result.retry() }
            else { coordinator.recordError("仍有新修改，下次定时备份会继续处理"); Result.success() }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            val permanent = error is BackupApiException && error.status in setOf(400,401,403,409,412,413,428) || error is IllegalArgumentException
            coordinator.recordError(if (error is BackupApiException || error is IllegalStateException || error is IllegalArgumentException)
                error.message ?: "备份失败，请重试" else "网络或存储异常，备份尚未完成")
            if (permanent || runAttemptCount >= 4) Result.failure() else { alignNextRun = false; Result.retry() }
        }
        if (alignNextRun) {
            try { BackupWork.schedule(applicationContext, alignNextRun = true) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                Log.e("BackupWork", "无法安排下一次备份：${error.javaClass.simpleName}")
                coordinator.recordError("下次备份时间未能更新，请打开应用后检查自动备份设置")
            }
        }
        return outcome
    }
}
