package com.mixagames666.trackerrntemp.ui.screens.income

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mixagames666.trackerrntemp.AppContainer
import com.mixagames666.trackerrntemp.data.Account
import com.mixagames666.trackerrntemp.data.CreditCard
import com.mixagames666.trackerrntemp.data.Income
import com.mixagames666.trackerrntemp.data.Investment
import com.mixagames666.trackerrntemp.data.Salary
import com.mixagames666.trackerrntemp.data.TaxesConfig
import com.mixagames666.trackerrntemp.domain.CreditCardForm
import com.mixagames666.trackerrntemp.domain.ServiceResult
import com.mixagames666.trackerrntemp.util.JsCompat
import com.mixagames666.trackerrntemp.util.getToday
import com.mixagames666.trackerrntemp.util.orZero
import com.mixagames666.trackerrntemp.util.roundMoney
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Datos y acciones de IncomeScreen (loadData, los handleSave… y handleDelete del original). */
class IncomeViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val salary: Salary? = null,
        val accounts: List<Account> = emptyList(),
        val creditCards: List<CreditCard> = emptyList(),
        val investments: List<Investment> = emptyList(),
        val incomes: List<Income> = emptyList(),
        val accountBalances: Map<String, Double> = emptyMap(),
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
            _state.value = _state.value.copy(
                salary = repos.salary.getAll().firstOrNull(),
                accounts = repos.accounts.getAll(),
                creditCards = repos.creditCards.getAll(),
                investments = repos.investments.getAll(),
                incomes = repos.income.getAll().sortedWith { a, b -> b.date.compareTo(a.date) },
                accountBalances = container.aggregates.getAccountBalances(),
                taxesConfig = repos.settings.get().taxes,
            )
        } catch (e: Exception) {
            Log.e("IncomeViewModel", "No se pudieron cargar los ingresos", e)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true)
            loadData()
            _state.value = _state.value.copy(refreshing = false)
        }
    }

    private suspend fun afterSave() {
        loadData()
        container.dataSync.syncAfterDataChange()
    }

    // ----- SAVE HANDLERS -----

    data class SalaryForm(val amount: String, val payDateType: String, val payDate: String, val recurrence: String, val account: String)

    suspend fun saveSalary(form: SalaryForm, editing: Salary?): ServiceResult {
        val amt = JsCompat.parseFloat(form.amount.replace(',', '.'))
        if (amt.isNaN() || amt <= 0) return ServiceResult.Alert("Error", "Ingresa un monto válido")
        repos.salary.save { existing ->
            (existing ?: Salary()).copy(
                id = editing?.id?.takeIf { it.isNotEmpty() } ?: "salary-1",
                amount = amt,
                payDateType = form.payDateType,
                payDate = if (form.payDateType == "specific") form.payDate.ifEmpty { "1" } else "",
                recurrence = form.recurrence,
                bankAccount = form.account,
            )
        }
        afterSave()
        return ServiceResult.Done()
    }

    data class AccountForm(val name: String, val bankName: String, val referenceNumber: String, val accountType: String, val initialBalance: String)

    suspend fun saveAccount(form: AccountForm, editing: Account?): ServiceResult {
        if (form.name.trim().isEmpty() || form.bankName.trim().isEmpty()) return ServiceResult.Alert("Error", "Nombre y banco son obligatorios")
        val base = editing ?: Account()
        val payload = base.copy(
            name = form.name.trim(),
            bankName = form.bankName.trim(),
            referenceNumber = form.referenceNumber.trim(),
            accountType = form.accountType,
            initialBalance = JsCompat.parseFloat(form.initialBalance.replace(',', '.')).orZero(),
        )
        if (editing != null) repos.accounts.update(payload) else repos.accounts.add(payload)
        afterSave()
        return ServiceResult.Done()
    }

    suspend fun saveCreditCard(form: CreditCardForm, editing: CreditCard?): ServiceResult {
        val result = container.creditCardService.save(form, editing)
        if (result is ServiceResult.Done) afterSave()
        return result
    }

    data class InvestmentForm(
        val entity: String,
        val monthlyDeposit: String,
        val totalMonths: String,
        val remainingMonths: String,
        val detail: String,
        val totalDeposited: String,
    )

    suspend fun saveInvestment(form: InvestmentForm, editing: Investment?): ServiceResult {
        val amt = JsCompat.parseFloat(form.monthlyDeposit.replace(',', '.'))
        if (form.entity.trim().isEmpty() || amt.isNaN() || amt <= 0) return ServiceResult.Alert("Error", "Entidad y depósito válido son obligatorios")
        val termMonths = JsCompat.parseIntOrZero(form.totalMonths)
        val monthsLeft = JsCompat.parseIntOrZero(form.remainingMonths)
        if (termMonths > 0 && monthsLeft > termMonths) {
            return ServiceResult.Alert("Error", "Los meses restantes no pueden superar el plazo total")
        }
        val payload = (editing ?: Investment()).copy(
            entity = form.entity.trim(),
            monthlyDeposit = amt,
            remainingMonths = monthsLeft,
            totalMonths = termMonths,
            detail = form.detail.trim(),
            totalDeposited = roundMoney(JsCompat.parseFloat(form.totalDeposited).orZero()),
        )
        if (editing != null) repos.investments.update(payload) else repos.investments.add(payload)
        afterSave()
        return ServiceResult.Done()
    }

    data class IncomeForm(val amount: String, val source: String, val detail: String, val account: String, val date: String)

    suspend fun saveIncome(form: IncomeForm, editing: Income?): ServiceResult {
        val amt = JsCompat.parseFloat(form.amount.replace(',', '.'))
        if (amt.isNaN() || amt <= 0) return ServiceResult.Alert("Error", "Monto válido obligatorio")
        if (form.detail.trim().isEmpty() || form.account.isEmpty()) return ServiceResult.Alert("Error", "Detalle y cuenta son obligatorios")
        val payload = (editing ?: Income()).copy(
            amount = amt,
            source = form.source.trim(),
            detail = form.detail.trim(),
            bankAccount = form.account,
            bankName = form.account.split(" - ").getOrNull(1).orEmpty(),
            date = form.date.ifEmpty { getToday() },
        )
        if (editing != null) repos.income.update(payload) else repos.income.add(payload)
        afterSave()
        return ServiceResult.Done()
    }

    // ----- DELETE / DEPOSIT -----

    fun deleteAccount(id: String, onDone: () -> Unit) = delete(onDone) { repos.accounts.delete(id) }
    fun deleteCreditCard(id: String, onDone: () -> Unit) = delete(onDone) { repos.creditCards.delete(id) }
    fun deleteInvestment(id: String, onDone: () -> Unit) = delete(onDone) { repos.investments.delete(id) }
    fun deleteIncome(id: String, onDone: () -> Unit) = delete(onDone) { repos.income.delete(id) }

    private fun delete(onDone: () -> Unit, action: suspend () -> Unit) {
        viewModelScope.launch {
            action()
            onDone()
            afterSave()
        }
    }

    suspend fun investmentDeposit(inv: Investment, sourceAccount: String): ServiceResult {
        val result = container.investmentService.deposit(inv, sourceAccount, _state.value.taxesConfig)
        if (result is ServiceResult.Done) afterSave()
        return result
    }
}
