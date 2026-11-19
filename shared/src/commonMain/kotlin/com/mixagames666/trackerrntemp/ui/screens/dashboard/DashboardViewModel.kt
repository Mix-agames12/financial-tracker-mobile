package com.mixagames666.trackerrntemp.ui.screens.dashboard

import com.mixagames666.trackerrntemp.util.AppLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mixagames666.trackerrntemp.CoreContainer
import com.mixagames666.trackerrntemp.data.Income
import com.mixagames666.trackerrntemp.domain.DashboardData
import com.mixagames666.trackerrntemp.domain.SalaryPrompt
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.getToday
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado y acciones de DashboardScreen (loadData, handleAcceptSalary, handlePostponeSalary…). */
class DashboardViewModel(private val container: CoreContainer) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val refreshing: Boolean = false,
        val data: DashboardData? = null,
        val salaryPrompt: SalaryPrompt? = null,
        val showWelcome: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch { loadData() }
    }

    private suspend fun loadData() {
        try {
            val data = container.dashboardLoader.load()
            _state.value = _state.value.copy(
                data = data,
                showWelcome = data.needsWelcome,
                // Como en el original, un aviso ya abierto sólo se quita al responderlo.
                salaryPrompt = data.salaryPrompt ?: _state.value.salaryPrompt,
            )
        } catch (e: Exception) {
            AppLog.e("DashboardViewModel", "No se pudo cargar el inicio", e)
        } finally {
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            loadData()
            _state.value = _state.value.copy(refreshing = false)
        }
    }

    fun dismissSalaryPrompt() {
        _state.value = _state.value.copy(salaryPrompt = null)
    }

    /** "Sí, ya se acreditó": registra el sueldo como ingreso. */
    fun acceptSalary() {
        val prompt = _state.value.salaryPrompt ?: return
        viewModelScope.launch {
            container.repos.income.add(
                Income(
                    amount = prompt.amount,
                    source = "Sueldo (Automático)",
                    detail = "Acreditación confirmada desde notificador de fecha de cobro",
                    bankAccount = prompt.account,
                    bankName = prompt.account,
                    date = getToday(),
                    isSalary = true,
                ),
            )
            _state.value = _state.value.copy(salaryPrompt = null)
            loadData()
            container.dataSync.syncAfterDataChange()
        }
    }

    /** "Aún no": vuelve a preguntar en 24 horas. */
    fun postponeSalary() {
        _state.value = _state.value.copy(salaryPrompt = null)
        viewModelScope.launch {
            container.repos.settings.save { it.copy(salaryPromptPostponedUntil = Time.nowMillis() + 24 * 60 * 60 * 1000) }
        }
    }

    fun welcomeDone() {
        _state.value = _state.value.copy(showWelcome = false)
        viewModelScope.launch {
            loadData()
            container.dataSync.syncAfterDataChange()
        }
    }
}
