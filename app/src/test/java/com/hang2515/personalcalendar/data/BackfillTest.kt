package com.hang2515.personalcalendar.data

import androidx.room.Room
import com.hang2515.personalcalendar.backup.LocalBackupStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackfillTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-12T03:00:00Z"), ZoneId.of("Asia/Shanghai"))

    @Test fun historicalCheckInIsIdempotentAndPreservedByBackupRestore() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CalendarDatabase::class.java).build()
        try {
            val dao = database.calendarDao()
            dao.insertCalendar(CalendarEntity("one", "阅读", "2026-09-01", 127))
            dao.upsertScheduleChange(ScheduleChangeEntity("one", "2026-09-01", 127))
            val repository = CalendarRepository(database, clock)
            val date = LocalDate.of(2026, 9, 5)
            assertTrue(repository.backfill("one", date))
            assertFalse(repository.backfill("one", date))
            assertEquals(1L, LocalBackupStore(database).state().localRevision)
            assertEquals(1, repository.observeCheckInsInMonth("one", YearMonth.of(2026, 9)).first().size)
            val store = LocalBackupStore(database)
            val snapshot = store.snapshot()
            store.restore(snapshot.encode(), "test-account", "test-etag", 1)
            assertTrue(dao.hasCheckIn("one", "2026-09-05"))
            assertTrue(repository.undoHistoricalCheckIn("one", date))
            assertFalse(repository.undoHistoricalCheckIn("one", date))
            assertFalse(dao.hasCheckIn("one", "2026-09-05"))
        } finally { database.close() }
    }

    @Test fun backfillRejectsFutureTodayBeforeCreationAndPausedDatesButUsesHistoricalRule() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CalendarDatabase::class.java).build()
        try {
            val dao = database.calendarDao()
            dao.insertCalendar(CalendarEntity("one", "阅读", "2026-10-01", 1, isPaused = true))
            dao.upsertScheduleChange(ScheduleChangeEntity("one", "2026-10-01", 127))
            dao.upsertScheduleChange(ScheduleChangeEntity("one", "2026-10-03", 0))
            dao.upsertScheduleChange(ScheduleChangeEntity("one", "2026-10-05", 1))
            val repository = CalendarRepository(database, clock)
            assertTrue(repository.backfill("one", LocalDate.of(2026, 10, 2)))
            assertTrue(repository.backfill("one", LocalDate.of(2026, 10, 5)))
            for (date in listOf("2026-09-30", "2026-10-03", "2026-10-06", "2026-10-12", "2026-10-13")) {
                try { repository.backfill("one", LocalDate.parse(date)); fail("应拒绝 $date") }
                catch (_: IllegalArgumentException) { }
            }
            assertEquals(2L, LocalBackupStore(database).state().localRevision)
            assertEquals(2, repository.observeMonthlyProgress(LocalDate.of(2026, 10, 12)).first().getValue("one").completedDays)
            assertEquals(3, dao.getChangesInRange("one", "2026-10-01", "2026-10-31").size)
            try { repository.backfill("missing", LocalDate.of(2026, 10, 2)); fail() }
            catch (_: IllegalArgumentException) { }
        } finally { database.close() }
    }
}
