package com.hang2515.personalcalendar.data

import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.hang2515.personalcalendar.backup.BackupWork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupSchedulingTest {
    @Test
    fun editLeavesSuccessorEvenWhenEarlierBackupAlreadyExists() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val manager = WorkManager.getInstance(context)
        BackupWork.enqueue(manager, followUp = true)
        manager.getWorkInfosForUniqueWork("calendar-cloud-backup").get()
        BackupWork.enqueue(manager, followUp = true)
        val jobs = manager.getWorkInfosForUniqueWork("calendar-cloud-backup").get()
        assertEquals(2, jobs.size)
        assertTrue(jobs.any { it.state == WorkInfo.State.BLOCKED })
        manager.cancelAllWork().result.get()
    }
}
