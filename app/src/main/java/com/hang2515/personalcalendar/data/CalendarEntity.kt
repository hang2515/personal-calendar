package com.hang2515.personalcalendar.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.LocalDate

@Entity(tableName = "calendars", primaryKeys = ["id"])
data class CalendarEntity(
    val id: String,
    val name: String,
    val startDate: String,
    val weekdayMask: Int,
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
