package com.hang2515.personalcalendar.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.ColumnInfo
import java.time.LocalDate

@Entity(tableName = "calendars", primaryKeys = ["id"])
data class CalendarEntity(
    val id: String,
    val name: String,
    val startDate: String,
    val weekdayMask: Int,
    @ColumnInfo(defaultValue = "0") val isPaused: Boolean = false,
    @ColumnInfo(defaultValue = "480") val reminderMinute: Int = 480,
    @ColumnInfo(defaultValue = "0") val reminderEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "'leaf'") val iconKey: String = "leaf",
    @ColumnInfo(defaultValue = "'sage'") val colorKey: String = "sage",
) {
    fun startLocalDate(): LocalDate = LocalDate.parse(startDate)
}

@Entity(
    tableName = "check_ins",
    primaryKeys = ["calendarId", "localDate"],
    foreignKeys = [
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("localDate")],
)
data class CheckInEntity(
    val calendarId: String,
    val localDate: String,
)

@Entity(
    tableName = "schedule_changes",
    primaryKeys = ["calendarId", "effectiveDate"],
    foreignKeys = [
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ScheduleChangeEntity(
    val calendarId: String,
    val effectiveDate: String,
    val weekdayMask: Int,
)
