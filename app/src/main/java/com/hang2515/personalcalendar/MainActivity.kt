package com.hang2515.personalcalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hang2515.personalcalendar.ui.PersonalCalendarApp
import com.hang2515.personalcalendar.ui.theme.PersonalCalendarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PersonalCalendarTheme {
                PersonalCalendarApp()
            }
        }
    }
}
