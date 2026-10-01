package com.hang2515.personalcalendar.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.hang2515.personalcalendar.data.CalendarRepository
import com.hang2515.personalcalendar.ui.screens.CalendarScreen
import com.hang2515.personalcalendar.ui.screens.ProfileScreen
import com.hang2515.personalcalendar.ui.screens.TodayScreen
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

private enum class MainTab(val title: String) {
    TODAY("今天"),
    CALENDARS("日历"),
    PROFILE("我的"),
}

@Composable
fun PersonalCalendarApp(openCalendarId: String?, openRequest: Int, permissionRevision: Int) {
    var selectedTab by rememberSaveable { mutableStateOf(if (openCalendarId == null) MainTab.TODAY else MainTab.CALENDARS) }
    val context = LocalContext.current
    val repository = remember { CalendarRepository.open(context) }
    val today by produceState(initialValue = LocalDate.now(ZoneId.of("Asia/Shanghai"))) {
        while (true) {
            value = LocalDate.now(ZoneId.of("Asia/Shanghai"))
            delay(30_000)
        }
    }
    LaunchedEffect(openRequest) {
        if (openCalendarId != null) selectedTab = MainTab.CALENDARS
    }

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
            MainTab.TODAY -> TodayScreen(repository, today, modifier)
            MainTab.CALENDARS -> CalendarScreen(repository, today, openCalendarId, openRequest, permissionRevision, modifier)
            MainTab.PROFILE -> ProfileScreen(permissionRevision, modifier)
        }
    }
}
