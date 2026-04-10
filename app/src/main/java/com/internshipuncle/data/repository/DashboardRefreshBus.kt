package com.internshipuncle.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DashboardRefreshBus @Inject constructor() {
    private val refreshSignal = MutableStateFlow(0)

    val ticks: StateFlow<Int> = refreshSignal.asStateFlow()

    fun refresh() {
        refreshSignal.update { it + 1 }
    }
}
