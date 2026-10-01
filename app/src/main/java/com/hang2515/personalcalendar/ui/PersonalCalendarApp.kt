package com.hang2515.personalcalendar.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hang2515.personalcalendar.ui.screens.CalendarScreen
import com.hang2515.personalcalendar.ui.screens.ProfileScreen
import com.hang2515.personalcalendar.ui.screens.TodayScreen

private enum class MainTab(val title: String) {
    TODAY("今天"),
    CALENDARS("日历"),
    PROFILE("我的"),
}

@Composable
fun PersonalCalendarApp() {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.TODAY) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(if (selectedTab == tab) "●" else "○") },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { contentPadding ->
        val modifier = Modifier.padding(contentPadding)
        when (selectedTab) {
            MainTab.TODAY -> TodayScreen(modifier)
            MainTab.CALENDARS -> CalendarScreen(modifier)
            MainTab.PROFILE -> ProfileScreen(modifier)
        }
    }
}
