package com.hang2515.personalcalendar.data

import androidx.room.Room
import com.hang2515.personalcalendar.backup.BackupSnapshot
import com.hang2515.personalcalendar.backup.LocalBackupStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalBackupStoreTest {
    @Test
    fun failedInsertDuringRestoreRollsBackDeletedCalendarsAndBackupState() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CalendarDatabase::class.java).build()
        try {
            val dao = database.calendarDao()
            dao.insertCalendar(CalendarEntity("one", "保留的日历", "2026-10-01", 127))
            dao.upsertScheduleChange(ScheduleChangeEntity("one", "2026-10-01", 127))
            dao.insertCheckIn(CheckInEntity("one", "2026-10-01"))
            val store = LocalBackupStore(database)
            val snapshot = store.snapshot()
            database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restore BEFORE INSERT ON check_ins BEGIN SELECT RAISE(ABORT, 'restore test'); END")
            val attempt = runCatching { store.restore(snapshot.encode(), "account", "tag", 0) }
            assertTrue(attempt.isFailure)
            assertEquals("保留的日历", dao.getCalendar("one")?.name)
            assertTrue(dao.hasCheckIn("one", "2026-10-01"))
            assertEquals(0L, store.state().localRevision)
            assertNull(store.state().boundAccountSub)
        } finally { database.close() }
    }

    @Test
    fun restoreRejectsInvalidAndStalePreviewWithoutDeletingLocalData() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CalendarDatabase::class.java).build()
        try {
            val dao = database.calendarDao()
            dao.insertCalendar(CalendarEntity("one", "阅读", "2026-10-01", 127))
            dao.upsertScheduleChange(ScheduleChangeEntity("one", "2026-10-01", 127))
            dao.insertCheckIn(CheckInEntity("one", "2026-10-01"))
            val store = LocalBackupStore(database)
            val snapshot = store.snapshot()
            assertEquals("阅读", BackupSnapshot.decode(snapshot.encode()).calendars.single().name)
            try { store.restore(snapshot.encode().replace("2026-10-01", "2026-02-30"), "account", "tag", 0); fail() }
            catch (_: java.time.DateTimeException) { }
            database.localStateDao().recordChange()
            try { store.restore(snapshot.encode(), "account", "tag", 0); fail() }
            catch (_: IllegalArgumentException) { }
            assertNotNull(dao.getCalendar("one"))
            assertTrue(dao.hasCheckIn("one", "2026-10-01"))
            store.restore(snapshot.encode(), "account", "tag", 1)
            assertEquals(2L, store.state().localRevision)
            assertFalse(store.state().backupEnabled)
        } finally { database.close() }
    }

    @Test
    fun uploadCompletionDoesNotMarkEditsDuringUploadAsBackedUp() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CalendarDatabase::class.java).build()
        try {
            val store = LocalBackupStore(database)
            store.bind("account", 0, null)
            val snapshot = store.snapshot()
            database.localStateDao().recordChange()
            store.markUploaded(snapshot, "account", "etag", "2026-10-02T00:00:00Z")
            assertEquals(0L, store.state().lastBackedUpRevision)
            assertEquals(1L, store.state().localRevision)
            store.disable()
            assertFalse(store.state().backupEnabled)
            assertEquals("account", store.state().boundAccountSub)
        } finally { database.close() }
    }
}
