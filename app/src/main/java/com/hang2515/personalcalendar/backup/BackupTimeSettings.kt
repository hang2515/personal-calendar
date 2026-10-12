package com.hang2515.personalcalendar.backup

import android.content.Context
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class BackupTimeSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("cloud-backup-time", Context.MODE_PRIVATE)
    fun minute(): Int = preferences.getInt("daily-minute", DEFAULT_MINUTE).takeIf { it in 0..1439 } ?: DEFAULT_MINUTE
    fun save(minute: Int) {
        require(minute in 0..1439) { "备份时间无效" }
        check(preferences.edit().putInt("daily-minute", minute).commit()) { "备份时间未保存，请重试" }
    }
    companion object { const val DEFAULT_MINUTE = 22 * 60 }
}

internal fun backupInitialDelayMillis(now: Instant, minute: Int): Long {
    require(minute in 0..1439) { "备份时间无效" }
    val localNow = now.atZone(ZoneId.of("Asia/Shanghai"))
    var next = localNow.toLocalDate().atStartOfDay(localNow.zone).plusMinutes(minute.toLong())
    if (!next.isAfter(localNow)) next = next.plusDays(1)
    return Duration.between(now, next.toInstant()).toMillis()
}
