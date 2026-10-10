package com.hang2515.personalcalendar.backup

import com.hang2515.personalcalendar.data.CalendarEntity
import com.hang2515.personalcalendar.data.CheckInEntity
import com.hang2515.personalcalendar.data.ScheduleChangeEntity
import com.hang2515.personalcalendar.domain.CalendarAppearance
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

data class BackupSnapshot(
    val backupId: String,
    val datasetId: String,
    val sourceRevision: Long,
    val createdAt: String,
    val calendars: List<CalendarEntity>,
    val scheduleChanges: List<ScheduleChangeEntity>,
    val checkIns: List<CheckInEntity>,
) {
    fun encode(): String = JSONObject().apply {
        put("formatVersion", 1); put("timeZone", "Asia/Shanghai")
        put("backupId", backupId); put("datasetId", datasetId)
        put("sourceRevision", sourceRevision); put("createdAt", createdAt)
        put("calendars", JSONArray(calendars.map { c -> JSONObject().apply {
            put("id", c.id); put("name", c.name); put("startDate", c.startDate)
            put("weekdayMask", c.weekdayMask); put("isPaused", c.isPaused)
            put("reminderMinute", c.reminderMinute); put("reminderEnabled", c.reminderEnabled)
            put("iconKey", c.iconKey); put("colorKey", c.colorKey)
        } }))
        put("scheduleChanges", JSONArray(scheduleChanges.map { c -> JSONObject().apply {
            put("calendarId", c.calendarId); put("effectiveDate", c.effectiveDate); put("weekdayMask", c.weekdayMask)
        } }))
        put("checkIns", JSONArray(checkIns.map { c -> JSONObject().apply {
            put("calendarId", c.calendarId); put("localDate", c.localDate)
        } }))
    }.toString()

    companion object {
        const val MAX_BYTES = 1024 * 1024
        private val identifier = Regex("[A-Za-z0-9_-]{1,128}")

        fun decode(body: String): BackupSnapshot {
            require(body.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份超过 1 MiB，无法处理" }
            val root = JSONObject(body)
            require(root.integer("formatVersion", 1, 1) == 1L && root.text("timeZone") == "Asia/Shanghai") { "不支持此备份版本或时区" }
            val calendars = root.objects("calendars", 1000).map { c ->
                CalendarEntity(c.id("id"), c.text("name").also {
                    require(it == it.trim() && it.length in 1..40) { "日历名称无效" }
                }, c.date("startDate"), c.integer("weekdayMask", 1, 127).toInt(),
                    c.boolean("isPaused"), c.integer("reminderMinute", 0, 1439).toInt(), c.boolean("reminderEnabled"),
                    c.text("iconKey").also { require(it in CalendarAppearance.iconKeys) },
                    c.text("colorKey").also { require(it in CalendarAppearance.colorKeys) })
            }
            require(calendars.map { it.id }.distinct().size == calendars.size) { "日历重复" }
            val starts = calendars.associate { it.id to it.startDate }
            val changes = root.objects("scheduleChanges", 10000).map { c ->
                ScheduleChangeEntity(c.id("calendarId"), c.date("effectiveDate"), c.integer("weekdayMask", 0, 127).toInt())
            }
            val ruleKeys = changes.map { it.calendarId to it.effectiveDate }
            require(ruleKeys.distinct().size == changes.size) { "规则重复" }
            changes.forEach { require(starts[it.calendarId]?.let { start -> it.effectiveDate >= start } == true) { "规则关联无效" } }
            calendars.forEach { require((it.id to it.startDate) in ruleKeys) { "缺少初始规则" } }
            val checks = root.objects("checkIns", 10000).map { c -> CheckInEntity(c.id("calendarId"), c.date("localDate")) }
            require(checks.distinct().size == checks.size) { "打卡重复" }
            checks.forEach { require(starts[it.calendarId]?.let { start -> it.localDate >= start } == true) { "打卡关联无效" } }
            val createdAt = root.text("createdAt")
            require(createdAt.endsWith("Z")) { "备份时间无效" }
            Instant.parse(createdAt)
            return BackupSnapshot(root.id("backupId"), root.id("datasetId"),
                root.integer("sourceRevision", 0, 9007199254740991L), createdAt, calendars, changes, checks)
        }

        private fun JSONObject.text(key: String): String = get(key).also { require(it is String) } as String
        private fun JSONObject.id(key: String): String = text(key).also { require(identifier.matches(it)) { "记录标识无效" } }
        private fun JSONObject.date(key: String): String = text(key).also {
            require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) && LocalDate.parse(it).toString() == it) { "日期无效" }
        }
        private fun JSONObject.boolean(key: String): Boolean = get(key).also { require(it is Boolean) } as Boolean
        private fun JSONObject.integer(key: String, min: Long, max: Long): Long {
            val value = get(key)
            require(value is Int || value is Long) { "数值格式无效" }
            return (value as Number).toLong().also { require(it in min..max) { "数值超出范围" } }
        }
        private fun JSONObject.objects(key: String, max: Int): List<JSONObject> {
            val array = getJSONArray(key)
            require(array.length() <= max) { "备份记录数量超过上限" }
            return (0 until array.length()).map { array.getJSONObject(it) }
        }
    }
}
