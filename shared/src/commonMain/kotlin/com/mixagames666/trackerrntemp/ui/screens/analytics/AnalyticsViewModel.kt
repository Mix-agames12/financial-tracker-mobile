package com.mixagames666.trackerrntemp.ui.screens.analytics

import com.mixagames666.trackerrntemp.util.AppLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mixagames666.trackerrntemp.CoreContainer
import com.mixagames666.trackerrntemp.domain.AnalyticsData
import com.mixagames666.trackerrntemp.domain.AnalyticsPeriod
import com.mixagames666.trackerrntemp.domain.ComparisonOutcome
import com.mixagames666.trackerrntemp.domain.compareMonths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AnalyticsViewModel(private val container: CoreContainer) : ViewModel() {

    data class UiState(
        val period: AnalyticsPeriod = AnalyticsPeriod.DAILY,
        val data: AnalyticsData? = null,
        val refreshing: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch { loadData() }
    }

    private suspend fun loadData() {
        try {
            _state.value = _state.value.copy(data = container.analyticsLoader.load(_state.value.period))
        } catch (e: Exception) {
            AppLog.e("AnalyticsViewModel", "No se pudo cargar el análisis", e)
        }
    }

    fun setPeriod(period: AnalyticsPeriod) {
        _state.value = _state.value.copy(period = period)
        load()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            loadData()
            _state.value = _state.value.copy(refreshing = false)
        }
    }

    suspend fun compare(p1: String, p2: String, category: String, method: String, incomeSource: String): ComparisonOutcome? =
        compareMonths(container.aggregates, p1, p2, category, method, incomeSource)
}
