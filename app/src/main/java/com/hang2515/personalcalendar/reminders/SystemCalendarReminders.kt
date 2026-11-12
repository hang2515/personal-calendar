package com.hang2515.personalcalendar.reminders

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import android.provider.CalendarContract.CalendarAlerts

data class DelegatedCalendarEvent(
    val calendarId: String, val ruleKey: String, val title: String,
    val startsAtMillis: Long, val recurrence: String?, val isTest: Boolean = false,
)

/** 仅管理本应用创建的本地日历及带本应用标记的提醒，不读取其他日历的事项。调用方在 IO 线程串行执行。 */
class SystemCalendarReminders(context: Context) {
    private val context = context.applicationContext
    private val resolver = this.context.contentResolver
    private val account = this.context.packageName + ".reminders"
    private val settings = this.context.getSharedPreferences("system-calendar-projection", Context.MODE_PRIVATE)

    fun hasPermission(): Boolean = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun hasProjection(): Boolean = settings.getBoolean("has_projection", false)

    fun reconcile(desired: List<DelegatedCalendarEvent>, scopeIds: Set<String>? = null) = applyDesired(desired, scopeIds, false)

    fun replaceTest(token: String, expectedAt: Long) = applyDesired(listOf(
        DelegatedCalendarEvent("test", token, "个人日历：提醒测试", expectedAt, null, true)), null, true)

    fun cancelTest() {
        if (!hasPermission() && !hasProjection()) return
        applyDesired(emptyList(), null, true)
    }

    fun clearManaged() {
        if (!hasPermission() && !hasProjection()) return
        applyDesired(emptyList(), null, null)
    }

    fun dismissAlerts(sourceIds: Set<String>, startsAt: Long, endsAt: Long) {
        if (sourceIds.isEmpty()) return
        requireAccess()
        require(startsAt < endsAt)
        managedEvents(calendarIds()).filter { !it.isTest && it.sourceId in sourceIds }.forEach { saved ->
            val operations = arrayListOf(
                ContentProviderOperation.newAssertQuery(Events.CONTENT_URI)
                    .withSelection(ownedSelection(), ownedArgs(saved)).withExpectedCount(1).build(),
                ContentProviderOperation.newUpdate(CalendarAlerts.CONTENT_URI)
                    .withValue(CalendarAlerts.STATE, CalendarAlerts.STATE_DISMISSED)
                    .withSelection("${CalendarAlerts.EVENT_ID}=? AND ${CalendarAlerts.BEGIN}>=? AND ${CalendarAlerts.BEGIN}<? AND ${CalendarAlerts.STATE}!=?",
                        arrayOf(saved.id.toString(), startsAt.toString(), endsAt.toString(), CalendarAlerts.STATE_DISMISSED.toString())).build(),
            )
            resolver.applyBatch(CalendarContract.AUTHORITY, operations)
        }
    }

    private fun requireAccess() = check(hasPermission()) { "请允许读写系统日历后，再更新或关闭代管提醒" }

    private fun calendarIds(): List<Long> {
        val rows = checkNotNull(resolver.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=? AND ${Calendars.NAME}=?",
            arrayOf(account, CalendarContract.ACCOUNT_TYPE_LOCAL, account), Calendars._ID)) { "无法读取系统日历，请确认手机支持日历代管" }
        return rows.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }
    }

    private fun createCalendar(): Long {
        val uri = Calendars.CONTENT_URI.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, account)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, account); put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, account); put(Calendars.OWNER_ACCOUNT, account)
            put(Calendars.CALENDAR_DISPLAY_NAME, "个人日历提醒")
            put(Calendars.CALENDAR_COLOR, 0xff628878.toInt()); put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.VISIBLE, 1); put(Calendars.SYNC_EVENTS, 1); put(Calendars.CALENDAR_TIME_ZONE, "Asia/Shanghai")
            put(Calendars.ALLOWED_REMINDERS, Reminders.METHOD_ALERT.toString()); put(Calendars.MAX_REMINDERS, 1)
        }
        return ContentUris.parseId(checkNotNull(resolver.insert(uri, values)) { "系统日历未创建，请重试" })
    }

    private data class SavedEvent(val id: Long, val owner: Long, val uri: String, val values: ContentValues) {
        val sourceId: String? get() = Uri.parse(uri).pathSegments.firstOrNull()
        val isTest: Boolean get() = Uri.parse(uri).authority == TEST_AUTHORITY
    }

    private fun managedEvents(ids: List<Long>): List<SavedEvent> = ids.chunked(100).flatMap { group ->
        val projection = arrayOf(Events._ID, Events.CALENDAR_ID, Events.CUSTOM_APP_URI, Events.TITLE, Events.DTSTART,
            Events.DTEND, Events.DURATION, Events.RRULE, Events.EVENT_TIMEZONE, Events.HAS_ALARM, Events.DESCRIPTION)
        val rows = checkNotNull(resolver.query(Events.CONTENT_URI, projection,
            "${Events.CALENDAR_ID} IN (${group.joinToString(",") { "?" }}) AND ${Events.CUSTOM_APP_PACKAGE}=?",
            (group.map(Long::toString) + context.packageName).toTypedArray(), Events._ID)) { "无法读取已代管提醒" }
        rows.use { cursor -> buildList {
            while (cursor.moveToNext()) {
                val rawUri = cursor.getString(2) ?: continue
                val parsed = Uri.parse(rawUri)
                if (parsed.scheme != "personal-calendar" || parsed.authority !in setOf(EVENT_AUTHORITY, TEST_AUTHORITY)) continue
                val values = ContentValues()
                for (index in 3 until projection.size) {
                    if (cursor.isNull(index)) values.putNull(projection[index])
                    else if (index in setOf(4, 5, 9)) values.put(projection[index], cursor.getLong(index))
                    else values.put(projection[index], cursor.getString(index))
                }
                add(SavedEvent(cursor.getLong(0), cursor.getLong(1), rawUri, values))
            }
        } }
    }

    private fun eventUri(event: DelegatedCalendarEvent): String = Uri.Builder().scheme("personal-calendar")
        .authority(if (event.isTest) TEST_AUTHORITY else EVENT_AUTHORITY)
        .appendPath(event.calendarId).appendPath(event.ruleKey).build().toString()

    private fun values(event: DelegatedCalendarEvent): ContentValues = ContentValues().apply {
        put(Events.TITLE, event.title); put(Events.DTSTART, event.startsAtMillis)
        put(Events.EVENT_TIMEZONE, "Asia/Shanghai"); put(Events.HAS_ALARM, 1L)
        put(Events.DESCRIPTION, "由个人日历管理，请在个人日历中修改或关闭提醒。")
        if (event.recurrence == null) {
            put(Events.DTEND, event.startsAtMillis + 60_000); putNull(Events.DURATION); putNull(Events.RRULE)
        } else {
            putNull(Events.DTEND); put(Events.DURATION, "PT1M"); put(Events.RRULE, event.recurrence)
        }
    }

    private fun ownedSelection() = "${Events._ID}=? AND ${Events.CALENDAR_ID}=? AND ${Events.CUSTOM_APP_PACKAGE}=?"
    private fun ownedArgs(event: SavedEvent) = arrayOf(event.id.toString(), event.owner.toString(), context.packageName)

    private fun applyDesired(desired: List<DelegatedCalendarEvent>, scope: Set<String>?, testOnly: Boolean?) {
        requireAccess()
        require(desired.map(::eventUri).distinct().size == desired.size) { "提醒标识重复" }
        require(desired.all { scope == null || it.calendarId in scope }) { "提醒范围不匹配" }
        val ids = calendarIds()
        val all = managedEvents(ids)
        val existing = all.filter { (testOnly == null || it.isTest == testOnly) && (scope == null || it.sourceId in scope) }
        if (desired.isEmpty() && existing.isEmpty()) return
        // 先留下保守标记；写入中断后也不能在权限撤回时误报“已取消”。
        check(settings.edit().putBoolean("has_projection", true).commit()) { "代管状态未保存" }
        val targetCalendar = ids.firstOrNull() ?: createCalendar()
        val reminders = mutableMapOf<Long, MutableList<Pair<Int, Int>>>()
        existing.map { it.id }.chunked(100).forEach { group ->
            val rows = checkNotNull(resolver.query(Reminders.CONTENT_URI,
                arrayOf(Reminders.EVENT_ID, Reminders.MINUTES, Reminders.METHOD),
                "${Reminders.EVENT_ID} IN (${group.joinToString(",") { "?" }})", group.map(Long::toString).toTypedArray(), null)) { "无法读取系统日历提醒方式" }
            rows.use { cursor -> while (cursor.moveToNext()) reminders.getOrPut(cursor.getLong(0)) { mutableListOf() }
                .add(cursor.getInt(1) to cursor.getInt(2)) }
        }
        val byUri = existing.groupBy { it.uri }
        val desiredUris = desired.map(::eventUri).toSet()
        val operations = arrayListOf<ContentProviderOperation>()
        fun flush() {
            if (operations.isNotEmpty()) { resolver.applyBatch(CalendarContract.AUTHORITY, operations); operations.clear() }
        }
        desired.forEach { event ->
            if (operations.size > 95) flush()
            val identity = eventUri(event)
            val saved = byUri[identity]?.firstOrNull()
            val fields = values(event)
            if (saved == null) {
                val position = operations.size
                fields.put(Events.CALENDAR_ID, targetCalendar); fields.put(Events.CUSTOM_APP_PACKAGE, context.packageName)
                fields.put(Events.CUSTOM_APP_URI, identity)
                operations.add(ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(fields).build())
                operations.add(ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                    .withValueBackReference(Reminders.EVENT_ID, position).withValue(Reminders.MINUTES, 0)
                    .withValue(Reminders.METHOD, Reminders.METHOD_ALERT).build())
            } else {
                val fieldsChanged = fields.keySet().any { fields.get(it)?.toString() != saved.values.get(it)?.toString() }
                val reminderChanged = reminders[saved.id] != listOf(0 to Reminders.METHOD_ALERT)
                if (fieldsChanged || reminderChanged) {
                    // 与后续写入处于同一 provider 事务，用户若已把事件移出专属日历则拒绝修改。
                    operations.add(ContentProviderOperation.newAssertQuery(Events.CONTENT_URI)
                        .withSelection(ownedSelection(), ownedArgs(saved)).withExpectedCount(1).build())
                    if (fieldsChanged) operations.add(ContentProviderOperation.newUpdate(Events.CONTENT_URI).withValues(fields)
                        .withSelection(ownedSelection(), ownedArgs(saved)).withExpectedCount(1).build())
                    if (reminderChanged) {
                        operations.add(ContentProviderOperation.newDelete(Reminders.CONTENT_URI)
                            .withSelection("${Reminders.EVENT_ID}=?", arrayOf(saved.id.toString())).build())
                        operations.add(ContentProviderOperation.newInsert(Reminders.CONTENT_URI)
                            .withValue(Reminders.EVENT_ID, saved.id).withValue(Reminders.MINUTES, 0)
                            .withValue(Reminders.METHOD, Reminders.METHOD_ALERT).build())
                    }
                }
            }
        }
        val obsolete = existing.filter { it.uri !in desiredUris || byUri[it.uri]?.first()?.id != it.id }
        obsolete.forEach { saved ->
            if (operations.size >= 100) flush()
            operations.add(ContentProviderOperation.newDelete(Events.CONTENT_URI)
                .withSelection(ownedSelection(), ownedArgs(saved)).withExpectedCount(1).build())
        }
        flush()
        val remaining = all.size - obsolete.size + desired.count { byUri[eventUri(it)].isNullOrEmpty() }
        check(settings.edit().putBoolean("has_projection", remaining > 0).commit()) { "代管结果未保存" }
    }

    companion object {
        private const val EVENT_AUTHORITY = "managed-reminder"
        private const val TEST_AUTHORITY = "managed-reminder-test"
    }
}
