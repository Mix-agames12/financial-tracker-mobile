package com.mixagames666.trackerrntemp.data

import com.mixagames666.trackerrntemp.util.daysInMonthOf
import com.mixagames666.trackerrntemp.util.orZero

// Port de getTotalBalance, getAccountBalances y getMonthlySummary de src/db/storage.ts.

data class TotalBalance(
    val totalIncome: Double,
    val totalExpenses: Double,
    val balance: Double,
    val salaryAmount: Double,
    val extraIncome: Double,
)

data class MonthlySummary(
    val totalExpenses: Double,
    val totalIncome: Double,
    val salaryAmount: Double,
    val byCategory: Map<String, Double>,
    val byPaymentMethod: Map<String, Double>,
    val byAccount: Map<String, Double>,
    val incomeByAccount: Map<String, Double>,
    val expenses: List<Expense>,
    val incomes: List<Income>,
)

class Aggregates(private val repos: Repos) {

    suspend fun getTotalBalance(): TotalBalance {
        val incomes = repos.income.getAll()
        val expenses = repos.expenses.getAll()
        val accounts = repos.accounts.getAll()
        val salaries = repos.salary.getAll()

        val totalIncome = incomes.fold(0.0) { sum, i -> sum + i.amount.orZero() }
        val totalExpenses = expenses.fold(0.0) { sum, e -> sum + e.amount.orZero() }
        val totalInitial = accounts.fold(0.0) { sum, a -> sum + (a.initialBalance ?: 0.0).orZero() }
        val configuredSalary = (salaries.firstOrNull()?.amount ?: 0.0).orZero()

        return TotalBalance(
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            balance = totalIncome + totalInitial - totalExpenses,
            salaryAmount = configuredSalary,
            extraIncome = totalIncome, // idéntico al total de ingresos, como en el original
        )
    }

    /** Saldo de cada cuenta (por id): inicial + ingresos depositados − gastos pagados con ella. */
    suspend fun getAccountBalances(): Map<String, Double> {
        val accounts = repos.accounts.getAll()
        val incomes = repos.income.getAll()
        val expenses = repos.expenses.getAll()

        val balances = LinkedHashMap<String, Double>()
        accounts.forEach { a ->
            var bal = (a.initialBalance ?: 0.0).orZero()
            incomes.filter { it.bankAccount.isNotEmpty() && it.bankAccount.startsWith(a.name) }.forEach { bal += it.amount.orZero() }
            // Todo gasto en débito que apunte a esta cuenta se descuenta de ella
            expenses.filter { it.accountName == a.name }.forEach { bal -= it.amount.orZero() }
            balances[a.id] = bal
        }
        return balances
    }

    suspend fun getMonthlySummary(year: Int, month: Int): MonthlySummary {
        val mm = month.toString().padStart(2, '0')
        val start = "$year-$mm-01"
        val lastDay = daysInMonthOf(year, month)
        val end = "$year-$mm-$lastDay"

        val expenses = repos.expenses.getByDateRange(start, end)
        val incomes = repos.income.getByDateRange(start, end)
        val salaries = repos.salary.getAll()

        val totalExp = expenses.fold(0.0) { s, e -> s + e.amount }
        val totalInc = incomes.fold(0.0) { s, i -> s + i.amount }
        val configuredSalary = (salaries.firstOrNull()?.amount ?: 0.0).orZero()

        val byCategory = LinkedHashMap<String, Double>()
        expenses.forEach { e ->
            val cat = e.category.ifEmpty { "Sin categoría" }
            byCategory[cat] = (byCategory[cat] ?: 0.0) + e.amount
        }

        val byPaymentMethod = LinkedHashMap<String, Double>()
        expenses.forEach { e ->
            val m = e.paymentMethod.ifEmpty { "Otro" }
            byPaymentMethod[m] = (byPaymentMethod[m] ?: 0.0) + e.amount
        }

        val byAccount = LinkedHashMap<String, Double>()
        expenses.forEach { e ->
            val acc = e.accountName?.takeIf { it.isNotEmpty() } ?: e.cardName?.takeIf { it.isNotEmpty() } ?: "Sin cuenta"
            byAccount[acc] = (byAccount[acc] ?: 0.0) + e.amount
        }

        val incomeByAccount = LinkedHashMap<String, Double>()
        incomes.forEach { i ->
            val acc = i.bankAccount.ifEmpty { "Sin cuenta" }
            incomeByAccount[acc] = (incomeByAccount[acc] ?: 0.0) + i.amount
        }

        return MonthlySummary(
            totalExpenses = totalExp,
            totalIncome = totalInc,
            salaryAmount = configuredSalary,
            byCategory = byCategory,
            byPaymentMethod = byPaymentMethod,
            byAccount = byAccount,
            incomeByAccount = incomeByAccount,
            expenses = expenses,
            incomes = incomes,
        )
    }
}
