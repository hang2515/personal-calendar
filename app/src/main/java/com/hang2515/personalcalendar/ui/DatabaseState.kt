package com.hang2515.personalcalendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

sealed interface DatabaseState<out T> {
    data object Loading : DatabaseState<Nothing>
    data class Ready<T>(val value: T) : DatabaseState<T>
    data object Failed : DatabaseState<Nothing>
}

@Composable
fun <T> databaseState(flow: Flow<T>): State<DatabaseState<T>> = produceState<DatabaseState<T>>(
    initialValue = DatabaseState.Loading,
    key1 = flow,
) {
    try {
        flow.collect { value = DatabaseState.Ready(it) }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        value = DatabaseState.Failed
    }
}
