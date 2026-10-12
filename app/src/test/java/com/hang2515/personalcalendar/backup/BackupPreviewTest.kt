package com.hang2515.personalcalendar.backup

import org.junit.Assert.*
import org.junit.Test

class BackupPreviewTest {
    @Test fun restoringRejectsCloudVersionChangedSinceReading() {
        val selected = BackupResponse("validated-snapshot", "version-one")
        val preview = BackupPreview("test-account", 12, selected)
        assertSame(selected, preview.requireUnchanged(BackupResponse("validated-snapshot", "version-one")))
        assertThrows(IllegalArgumentException::class.java) {
            preview.requireUnchanged(BackupResponse("new-snapshot", "version-two"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupPreview("test-account", 12, null).requireUnchanged(selected)
        }
    }
}
