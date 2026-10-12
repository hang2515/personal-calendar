package com.hang2515.personalcalendar.backup

import androidx.room.withTransaction
import com.hang2515.personalcalendar.data.CalendarDatabase
import com.hang2515.personalcalendar.data.LocalState
import java.time.Instant
import java.util.UUID

class LocalBackupStore(private val database: CalendarDatabase) {
    private val states = database.localStateDao()
    private val calendars = database.calendarDao()

    suspend fun state(): LocalState = database.withTransaction {
        states.initialize(LocalState())
        checkNotNull(states.get())
    }

    suspend fun snapshot(): BackupSnapshot = database.withTransaction {
        val state = state()
        BackupSnapshot(UUID.randomUUID().toString(), state.datasetId, state.localRevision,
            Instant.now().toString(), calendars.exportCalendars(), calendars.exportChanges(), calendars.exportCheckIns())
            .also { BackupSnapshot.decode(it.encode()) }
    }

    suspend fun bind(account: String, expectedRevision: Long, etag: String?, automatic: Boolean = true) = database.withTransaction {
        val current = state()
        require(current.localRevision == expectedRevision) { "本地数据已变化，请重新确认" }
        check(states.update(current.copy(boundAccountSub = account, backupEnabled = automatic,
            lastBackedUpRevision = null, lastBackupAt = null, cloudETag = etag)) == 1)
    }

    suspend fun disable() = database.withTransaction {
        check(states.update(state().copy(backupEnabled = false)) == 1)
    }

    suspend fun markUploaded(snapshot: BackupSnapshot, account: String, etag: String, savedAt: String) = database.withTransaction {
        val current = state()
        require(current.boundAccountSub == account && current.datasetId == snapshot.datasetId) { "备份账号已变化" }
        check(states.update(current.copy(lastBackedUpRevision = snapshot.sourceRevision, cloudETag = etag, lastBackupAt = savedAt)) == 1)
    }

    suspend fun restore(body: String, account: String, etag: String, expectedRevision: Long): List<String> {
        val snapshot = BackupSnapshot.decode(body)
        return database.withTransaction {
            val current = state()
            require(current.localRevision == expectedRevision) { "本地数据已变化，请重新读取云备份" }
            val previousIds = calendars.getCalendarIds()
            // 此入口只在用户确认整份替换后调用；删除精确限定为当前本地日历 ID。
            previousIds.chunked(500).forEach { calendars.deleteCalendars(it) }
            calendars.insertCalendars(snapshot.calendars)
            calendars.insertChanges(snapshot.scheduleChanges)
            calendars.insertCheckIns(snapshot.checkIns)
            val revision = current.localRevision + 1
            check(states.update(current.copy(datasetId = snapshot.datasetId, localRevision = revision,
                boundAccountSub = account, backupEnabled = false, lastBackedUpRevision = revision,
                lastBackupAt = snapshot.createdAt, cloudETag = etag)) == 1)
            previousIds
        }
    }
}
