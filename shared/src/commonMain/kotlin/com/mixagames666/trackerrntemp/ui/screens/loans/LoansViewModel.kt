package com.mixagames666.trackerrntemp.ui.screens.loans

import com.mixagames666.trackerrntemp.util.AppLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mixagames666.trackerrntemp.CoreContainer
import com.mixagames666.trackerrntemp.data.Account
import com.mixagames666.trackerrntemp.data.CreditCard
import com.mixagames666.trackerrntemp.data.Expense
import com.mixagames666.trackerrntemp.data.Loan
import com.mixagames666.trackerrntemp.data.TaxesConfig
import com.mixagames666.trackerrntemp.domain.LoanForm
import com.mixagames666.trackerrntemp.domain.ServiceResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Datos y acciones de LoansScreen (loadData, handleSaveLoan, handleDelete, handlePayInstallment). */
class LoansViewModel(private val container: CoreContainer) : ViewModel() {

    data class UiState(
        val loans: List<Loan> = emptyList(),
        val accounts: List<Account> = emptyList(),
        val accountBalances: Map<String, Double> = emptyMap(),
        val creditCards: List<CreditCard> = emptyList(),
        val expensesById: Map<String, Expense> = emptyMap(),
        val taxesConfig: TaxesConfig? = null,
        val refreshing: Boolean = false,
    )

    private val repos = container.repos
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch { loadData() }
    }

    private suspend fun loadData() {
        try {
            // Los pagados al final; el resto por fecha de próximo pago.
            val data = repos.loans.getAll().sortedWith { a, b ->
                val aPaid = a.status == "paid"
                val bPaid = b.status == "paid"
                when {
                    aPaid && !bPaid -> 1
                    !aPaid && bPaid -> -1
                    else -> a.nextPaymentDate.orEmpty().compareTo(b.nextPaymentDate.orEmpty())
                }
            }
            _state.value = _state.value.copy(
                loans = data,
                accounts = repos.accounts.getAll(),
                creditCards = repos.creditCards.getAll(),
                expensesById = repos.expenses.getAll().associateBy { it.id },
                accountBalances = container.aggregates.getAccountBalances(),
                taxesConfig = repos.settings.get().taxes,
            )
        } catch (e: Exception) {
            AppLog.e("LoansViewModel", "No se pudieron cargar los préstamos", e)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            loadData()
            _state.value = _state.value.copy(refreshing = false)
        }
    }

    suspend fun saveLoan(form: LoanForm, editing: Loan?): ServiceResult {
        val result = container.loanService.save(form, editing)
        if (result is ServiceResult.Done) {
            loadData()
            container.dataSync.syncAfterDataChange()
        }
        return result
    }

    fun deleteLoan(loan: Loan, onDone: () -> Unit) {
        viewModelScope.launch {
            container.loanService.delete(loan)
            onDone()
            loadData()
            container.dataSync.syncAfterDataChange()
        }
    }

    suspend fun payInstallment(loan: Loan, paymentAccount: String): ServiceResult =
        container.loanService.payInstallment(loan, paymentAccount, _state.value.taxesConfig)

    /** Tras pagar: recarga y sincroniza (el aviso de éxito se muestra antes, como en el original). */
    fun reloadAfterPayment() {
        viewModelScope.launch {
            loadData()
            container.dataSync.syncAfterDataChange()
        }
    }
}
