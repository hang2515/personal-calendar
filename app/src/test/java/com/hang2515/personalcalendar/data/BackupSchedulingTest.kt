package com.hang2515.personalcalendar.data

import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.WorkManagerTestInitHelper
import com.hang2515.personalcalendar.backup.BackupWork
import com.hang2515.personalcalendar.backup.BackupTimeSettings
import com.hang2515.personalcalendar.backup.backupInitialDelayMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupSchedulingTest {
    @Test
    fun dailyBackupKeepsTimeAcrossEditsAndChangingTimeReplacesOldJob() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val manager = WorkManager.getInstance(context)
        try {
            val now = Instant.parse("2026-10-12T03:00:00Z")
            BackupWork.enqueue(manager, 22 * 60, now = now)
            val first = manager.getWorkInfosForUniqueWork(BackupWork.NAME).get().single()
            BackupWork.enqueue(manager, 22 * 60, now = now.plusSeconds(60))
            assertEquals(first.id, manager.getWorkInfosForUniqueWork(BackupWork.NAME).get().single().id)
            val actualManager = manager as WorkManagerImpl
            val spec = actualManager.workDatabase.workSpecDao().getWorkSpec(first.id.toString())!!
            assertEquals(TimeUnit.HOURS.toMillis(11), spec.initialDelay)
            assertEquals(TimeUnit.HOURS.toMillis(24), spec.intervalDuration)
            assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
            BackupWork.enqueue(manager, 12 * 60, replace = true, now = now)
            val active = manager.getWorkInfosForUniqueWork(BackupWork.NAME).get().filter { !it.state.isFinished }.single()
            assertNotEquals(first.id, active.id)
            val replaced = actualManager.workDatabase.workSpecDao().getWorkSpec(active.id.toString())!!
            assertEquals(TimeUnit.HOURS.toMillis(1), replaced.initialDelay)
            BackupWork.cancel(context)
            assertTrue(manager.getWorkInfosForUniqueWork(BackupWork.NAME).get().all { it.state.isFinished })
        } finally { manager.cancelAllWork().result.get() }
    }

    @Test fun chosenTimePersistsAndInvalidTimeDoesNotOverwriteIt() {
        val context = RuntimeEnvironment.getApplication()
        val settings = BackupTimeSettings(context)
        settings.save(7 * 60 + 30)
        assertEquals(450, BackupTimeSettings(context).minute())
        for (value in listOf(-1, 1440)) {
            try { settings.save(value); fail() } catch (_: IllegalArgumentException) { }
        }
        assertEquals(450, settings.minute())
    }

    @Test fun delayedExecutionRealignsToChosenTimeWithoutReplacingThePeriodicJob() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val manager = WorkManager.getInstance(context)
        try {
            BackupWork.enqueue(manager, 1320, now = Instant.parse("2026-10-12T03:00:00Z"))
            val original = manager.getWorkInfosForUniqueWork(BackupWork.NAME).get().single()
            val actualManager = manager as WorkManagerImpl
            val oldGeneration = actualManager.workDatabase.workSpecDao().getWorkSpec(original.id.toString())!!.nextScheduleTimeOverrideGeneration
            BackupWork.enqueue(manager, 1320, now = Instant.parse("2026-10-13T01:00:00Z"), alignNextRun = true)
            val updated = manager.getWorkInfosForUniqueWork(BackupWork.NAME).get().single()
            assertEquals(original.id, updated.id)
            val spec = actualManager.workDatabase.workSpecDao().getWorkSpec(updated.id.toString())!!
            assertEquals(Instant.parse("2026-10-13T14:00:00Z").toEpochMilli(), spec.nextScheduleTimeOverride)
            assertTrue(spec.nextScheduleTimeOverrideGeneration > oldGeneration)
            actualManager.workDatabase.workSpecDao().resetWorkSpecNextScheduleTimeOverride(updated.id.toString(), oldGeneration)
            val afterCompletion = actualManager.workDatabase.workSpecDao().getWorkSpec(updated.id.toString())!!
            assertEquals(spec.nextScheduleTimeOverride, afterCompletion.nextScheduleTimeOverride)
        } finally { manager.cancelAllWork().result.get() }
    }

    @Test fun timeCalculationUsesBeijingDayAndMovesEqualOrPassedTimeToTomorrow() {
        val now = Instant.parse("2026-10-12T16:00:00Z")
        assertEquals(TimeUnit.HOURS.toMillis(24), backupInitialDelayMillis(now, 0))
        assertEquals(TimeUnit.MINUTES.toMillis(1), backupInitialDelayMillis(now, 1))
        assertEquals(TimeUnit.MINUTES.toMillis(1439), backupInitialDelayMillis(now, 1439))
        assertEquals(TimeUnit.HOURS.toMillis(23), backupInitialDelayMillis(Instant.parse("2026-10-12T15:00:00Z"), 22 * 60))
    }
}
