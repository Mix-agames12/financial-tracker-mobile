package com.mixagames666.trackerrntemp.ui.screens.expenses

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mixagames666.trackerrntemp.AppContainer
import com.mixagames666.trackerrntemp.data.Account
import com.mixagames666.trackerrntemp.data.Category
import com.mixagames666.trackerrntemp.data.CreditCard
import com.mixagames666.trackerrntemp.data.Expense
import com.mixagames666.trackerrntemp.data.PaymentMethod
import com.mixagames666.trackerrntemp.data.TaxesConfig
import com.mixagames666.trackerrntemp.domain.ExpenseForm
import com.mixagames666.trackerrntemp.domain.ServiceResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Port de la lógica de datos de screens/ExpensesScreen.tsx

/** Criterios de filtrado de la lista de gastos: cada uno es opcional. */
data class ExpenseFilters(
    val from: String = "",
    val to: String = "",
    val categories: List<String> = emptyList(),
    val method: String = "",
) {
    val isActive: Boolean get() = from.isNotEmpty() || to.isNotEmpty() || categories.isNotEmpty() || method.isNotEmpty()
}

/** Rango de fechas abierto, varias categorías y método de pago. */
fun applyExpenseFilters(expenses: List<Expense>, f: ExpenseFilters): List<Expense> = expenses.filter { e ->
    (f.from.isEmpty() || e.date >= f.from) &&
        (f.to.isEmpty() || e.date <= f.to) &&
        (f.categories.isEmpty() || f.categories.contains(e.category)) &&
        (f.method.isEmpty() || e.paymentMethod == f.method)
}

/** Heuristic H7 (memoria de estado): lo último elegido se reutiliza en el siguiente gasto durante el proceso. */
object ExpenseFormMemory {
    var paymentMethod: String = PaymentMethod.DEBIT
    var debitAccount: String = ""
    var creditAccount: String = ""
}

class ExpensesViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val expenses: List<Expense> = emptyList(),
        val categories: List<Category> = emptyList(),
        val accounts: List<Account> = emptyList(),
        val creditCards: List<CreditCard> = emptyList(),
        val taxesConfig: TaxesConfig? = null,
        val expenseCategoryNames: List<String> = emptyList(),
        val appliedFilters: ExpenseFilters = ExpenseFilters(),
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
            var data = repos.expenses.getAll()

            // Categorías presentes en los gastos (incluye "Préstamo" o "Inversión", que genera la app).
            val names = data.map { it.category }.filter { it.isNotEmpty() }.distinct()
            data = applyExpenseFilters(data, _state.value.appliedFilters)
            data = data.sortedWith { a, b -> b.date.compareTo(a.date) }

            repos.categories.seedDefaults()
            val cats = repos.categories.getAll().filter { it.type == "expense" }
            _state.value = _state.value.copy(
                expenses = data,
                expenseCategoryNames = names,
                categories = cats,
                accounts = repos.accounts.getAll(),
                creditCards = repos.creditCards.getAll(),
                taxesConfig = repos.settings.get().taxes,
            )
        } catch (e: Exception) {
            Log.e("ExpensesViewModel", "No se pudieron cargar los gastos", e)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            loadData()
            _state.value = _state.value.copy(refreshing = false)
        }
    }

    fun setAppliedFilters(filters: ExpenseFilters) {
        _state.value = _state.value.copy(appliedFilters = filters)
        load()
    }

    /** Guarda (alta o edición). Si termina bien memoriza las elecciones, recarga y sincroniza. */
    suspend fun save(form: ExpenseForm, editingId: String?): ServiceResult {
        val result = container.expenseService.save(form, editingId, _state.value.taxesConfig)
        if (result is ServiceResult.Done) {
            // Memorize choices for the next entry
            ExpenseFormMemory.paymentMethod = form.paymentMethod
            val account = if (form.paymentMethod == PaymentMethod.DEBIT || form.paymentMethod == PaymentMethod.CASH) form.selectedDebit else ""
            val card = if (form.paymentMethod == PaymentMethod.CREDIT) form.selectedCredit else ""
            if (account.isNotEmpty()) ExpenseFormMemory.debitAccount = account
            if (card.isNotEmpty()) ExpenseFormMemory.creditAccount = card
            loadData()
            container.dataSync.syncAfterDataChange()
        }
        return result
    }

    fun delete(expenseId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            container.cardPurchases.deleteExpenseWithCardSync(expenseId)
            onDone()
            loadData()
            container.dataSync.syncAfterDataChange()
        }
    }
}
