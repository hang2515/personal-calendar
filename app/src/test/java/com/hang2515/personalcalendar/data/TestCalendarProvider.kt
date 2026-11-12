package com.hang2515.personalcalendar.data

import android.content.ContentProvider
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.CalendarContract
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowContentResolver

/** 用真实 SQLite 校验选择条件、外键和批处理回滚，不模拟成功返回值。 */
class TestCalendarProvider : ContentProvider() {
    lateinit var database: SQLiteDatabase
    var mutations = 0
    var failNextReminder = false
    var failQuery = false

    override fun onCreate(): Boolean {
        database = SQLiteDatabase.create(null)
        database.setForeignKeyConstraintsEnabled(true)
        database.execSQL("CREATE TABLE calendars (_id INTEGER PRIMARY KEY, account_name TEXT, account_type TEXT, name TEXT, calendar_displayName TEXT, ownerAccount TEXT, calendar_color INTEGER, calendar_access_level INTEGER, visible INTEGER, sync_events INTEGER, calendar_timezone TEXT, allowedReminders TEXT, maxReminders INTEGER)")
        database.execSQL("CREATE TABLE events (_id INTEGER PRIMARY KEY, calendar_id INTEGER REFERENCES calendars(_id) ON DELETE CASCADE, title TEXT, description TEXT, dtstart INTEGER, dtend INTEGER, duration TEXT, rrule TEXT, eventTimezone TEXT, hasAlarm INTEGER, customAppPackage TEXT, customAppUri TEXT)")
        database.execSQL("CREATE TABLE reminders (_id INTEGER PRIMARY KEY, event_id INTEGER REFERENCES events(_id) ON DELETE CASCADE, minutes INTEGER, method INTEGER)")
        database.execSQL("CREATE TABLE calendar_alerts (_id INTEGER PRIMARY KEY, event_id INTEGER REFERENCES events(_id) ON DELETE CASCADE, begin INTEGER, state INTEGER)")
        return true
    }

    private fun table(uri: Uri): String = uri.pathSegments.first().also {
        require(it in setOf("calendars", "events", "reminders", "calendar_alerts"))
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        if (failQuery) return null
        return database.query(table(uri), projection, selection, selectionArgs, null, null, sortOrder)
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri {
        if (failNextReminder && table(uri) == "reminders") { failNextReminder = false; error("测试提醒写入失败") }
        val id = database.insertOrThrow(table(uri), null, values)
        mutations++
        return ContentUris.withAppendedId(uri, id)
    }
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        require(!selection.isNullOrBlank())
        mutations++
        return database.update(table(uri), values, selection, selectionArgs)
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        require(!selection.isNullOrBlank())
        mutations++
        return database.delete(table(uri), selection, selectionArgs)
    }
    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/" + table(uri)
    override fun applyBatch(operations: ArrayList<ContentProviderOperation>): Array<ContentProviderResult> {
        database.beginTransaction()
        try {
            return super.applyBatch(operations).also { database.setTransactionSuccessful() }
        } finally { database.endTransaction() }
    }

    companion object {
        fun install(): TestCalendarProvider = Robolectric.buildContentProvider(TestCalendarProvider::class.java).create().get().also {
            ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, it)
            val context = org.robolectric.RuntimeEnvironment.getApplication()
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_APP_CALENDAR)
            val info = android.content.pm.ResolveInfo().apply {
                activityInfo = android.content.pm.ActivityInfo().apply {
                    packageName = "test.calendar"; name = "test.calendar.CalendarActivity"
                    applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = "test.calendar" }
                }
            }
            org.robolectric.Shadows.shadowOf(context.packageManager).addResolveInfoForIntent(intent, info)
        }
    }
}
