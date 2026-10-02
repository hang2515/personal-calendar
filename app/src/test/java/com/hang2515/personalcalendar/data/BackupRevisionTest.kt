package com.hang2515.personalcalendar.data

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRevisionTest {
    @Test
    fun onlySuccessfulBusinessChangesAdvanceBackupRevision() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CalendarDatabase::class.java).build()
        val today = LocalDate.of(2026, 10, 1)
        val clock = Clock.fixed(Instant.parse("2026-10-01T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
        try {
            val repository = CalendarRepository(database, clock)
            repository.createCalendar("阅读", 127, today)
            val id = repository.observeCalendars().first().single().id
            repository.checkIn(id, today)
            repository.checkIn(id, today)
            database.openHelper.readableDatabase.query("SELECT localRevision FROM local_state WHERE id = 1").use {
                it.moveToFirst()
                assertEquals(2L, it.getLong(0))
            }
        } finally {
            database.close()
        }
    }
}
