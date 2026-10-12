package com.hang2515.personalcalendar.backup

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import com.hang2515.personalcalendar.data.CalendarDatabase
import com.hang2515.personalcalendar.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class BackupPreview(val account: String, val revision: Long, val backup: BackupResponse?) {
    internal fun requireUnchanged(current: BackupResponse): BackupResponse {
        val selected = requireNotNull(backup) { "此账号尚无云备份" }
        require(current.etag == selected.etag) { "云备份已有更新，请重新读取后恢复" }
        return selected
    }
}

class BackupCoordinator private constructor(private val context: Context) {
    // 用户确认后的上传/恢复不因底栏切页而取消，避免事务提交后遗漏提醒重排。
    val operationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val database = CalendarDatabase.open(context)
    val local = LocalBackupStore(database)
    val auth = CloudAuth(context)
    val timeSettings = BackupTimeSettings(context)
    private val api = BackupApi()
    private val mutex = Mutex()
    private val pending = AtomicFile(File(context.noBackupFilesDir, "pending-cloud-backup.json"))
    private val preferences = context.getSharedPreferences("cloud-backup-status", Context.MODE_PRIVATE)
    fun lastError(): String? = preferences.getString("error", null)
    fun recordError(message: String?) { preferences.edit().putString("error", message).apply() }

    suspend fun finishLogin(intent: Intent) = mutex.withLock {
        local.disable()
        BackupWork.cancel(context)
        pending.delete()
        auth.finishLogin(intent)
    }

    suspend fun preview(): BackupPreview = mutex.withLock {
        val token = auth.token()
        val state = local.state()
        val backup = try { api.request(token, "GET", snapshot = true) }
        catch (error: BackupApiException) { if (error.status == 404) null else throw error }
        backup?.let { BackupSnapshot.decode(it.body) }
        BackupPreview(auth.account(token), state.localRevision, backup)
    }

    suspend fun enable(preview: BackupPreview) = mutex.withLock {
        val account = auth.account(auth.token())
        require(account == preview.account) { "账号已变化，请重新读取云备份" }
        // 覆盖使用用户预览时的 ETag，确认后云端再次更新仍会产生冲突。
        local.bind(account, preview.revision, preview.backup?.etag)
        pending.delete()
        recordError(null)
        BackupWork.schedule(context, replace = true)
    }

    suspend fun setBackupTime(minute: Int) = mutex.withLock {
        withContext(Dispatchers.IO) { timeSettings.save(minute) }
        BackupWork.schedule(context, replace = true)
    }

    suspend fun backupNow(preview: BackupPreview): Boolean = mutex.withLock {
        check(CloudConfig.isConfigured) { "此安装包尚未配置云服务" }
        val account = auth.account(auth.token())
        require(account == preview.account) { "账号已变化，请重新读取云备份" }
        val current = local.state()
        val keepAutomatic = current.backupEnabled && current.boundAccountSub == account
        local.bind(account, preview.revision, preview.backup?.etag, automatic = keepAutomatic)
        pending.delete()
        recordError(null)
        BackupWork.schedule(context)
        uploadLocked(force = true)
    }

    suspend fun restore(preview: BackupPreview) = mutex.withLock {
        val account = auth.account(auth.token())
        require(account == preview.account) { "账号已变化，请重新读取云备份" }
        requireNotNull(preview.backup) { "此账号尚无云备份" }
        val backup = preview.requireUnchanged(api.request(auth.token(), "GET", snapshot = true))
        val previousIds = local.restore(backup.body, account, backup.etag, preview.revision)
        BackupWork.cancel(context); pending.delete(); recordError(null)
        val reminders = ReminderScheduler.open(context)
        previousIds.forEach { reminders.dismissNotification(it); reminders.refresh(it) }
        reminders.refreshAll()
    }

    suspend fun stop(signOut: Boolean) = mutex.withLock {
        local.disable(); BackupWork.cancel(context); pending.delete(); recordError(null)
        if (signOut) auth.clear()
    }

    /** 返回 false 表示上传期间仍有新修改，后台继续处理。 */
    suspend fun upload(): Boolean = mutex.withLock { uploadLocked(force = false) }

    private suspend fun uploadLocked(force: Boolean): Boolean {
        if (!CloudConfig.isConfigured) return true
        val state = local.state()
        if (!force && !state.backupEnabled) return true
        val token = auth.token()
        val account = auth.account(token)
        require(account == state.boundAccountSub) { "本地记录属于另一备份账号，请重新绑定" }
        val envelope = withContext(Dispatchers.IO) {
            if (pending.baseFile.exists()) JSONObject(String(pending.readFully(), Charsets.UTF_8)) else null
        }
        if (state.localRevision == state.lastBackedUpRevision && envelope == null) return true
        val body: String
        val expectedETag: String?
        if (envelope != null) {
            require(envelope.getString("account") == account && envelope.getString("dataset") == state.datasetId) { "待上传备份账号不一致，请重新绑定" }
            body = envelope.getString("body")
            expectedETag = if (envelope.isNull("etag")) null else envelope.getString("etag")
        } else {
            body = local.snapshot().encode()
            expectedETag = state.cloudETag
            val saved = JSONObject().put("account", account).put("dataset", state.datasetId)
                .put("etag", expectedETag ?: JSONObject.NULL).put("body", body).toString().toByteArray(Charsets.UTF_8)
            withContext(Dispatchers.IO) {
                val stream = pending.startWrite()
                try { stream.write(saved); pending.finishWrite(stream) }
                catch (error: Exception) { pending.failWrite(stream); throw error }
            }
        }
        val snapshot = BackupSnapshot.decode(body)
        val response = api.request(token, "PUT", snapshot = true, body = body, etag = expectedETag)
        val receipt = JSONObject(response.body)
        require(receipt.getString("accountSub") == account && receipt.getString("backupId") == snapshot.backupId) { "云备份结果不一致" }
        local.markUploaded(snapshot, account, response.etag, receipt.getString("savedAt"))
        pending.delete(); recordError(null)
        val current = local.state()
        return current.localRevision == current.lastBackedUpRevision
    }

    companion object {
        @Volatile private var instance: BackupCoordinator? = null
        fun open(context: Context): BackupCoordinator = instance ?: synchronized(this) {
            instance ?: BackupCoordinator(context.applicationContext).also { instance = it }
        }
    }
}
