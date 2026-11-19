package com.mixagames666.trackerrntemp.domain

import com.mixagames666.trackerrntemp.data.Aggregates
import com.mixagames666.trackerrntemp.data.Category
import com.mixagames666.trackerrntemp.data.Expense
import com.mixagames666.trackerrntemp.data.Income
import com.mixagames666.trackerrntemp.data.Repos
import com.mixagames666.trackerrntemp.util.JsCompat
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.daysInMonthOf
import com.mixagames666.trackerrntemp.util.getMonthName
import com.mixagames666.trackerrntemp.util.getWeekNumber
import com.mixagames666.trackerrntemp.util.jsDate
import com.mixagames666.trackerrntemp.util.jsDayOfWeek
import com.mixagames666.trackerrntemp.util.toLocalDateStr
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

// Cálculos de AnalyticsScreen (loadData y handleComparison) sin la UI ni los apaños de chart-kit.

enum class AnalyticsPeriod { DAILY, WEEKLY, MONTHLY }

data class PieSlice(val name: String, val population: Double, val color: String)

data class AnalyticsData(
    val labels: List<String>,
    val expData: List<Double>,
    val incData: List<Double>,
    val totalSpent: Double,
    val totalIncome: Double,
    val pieCategory: List<PieSlice>,
    val pieMethod: List<PieSlice>,
    val byAccountExpense: List<Pair<String, Double>>,
    val optCategories: List<String>,
    val optMethods: List<String>,
    val optIncomeSources: List<String>,
)

/** Paleta de gráficos de Análisis para categorías/métodos sin color propio. */
val ANALYTICS_COLORS = listOf("#f43f5e", "#3b82f6", "#10b981", "#f59e0b", "#8b5cf6", "#06b6d4", "#ec4899", "#64748b")

private fun shortName(name: String): String = if (name.length > 18) name.substring(0, 16) + ".." else name

fun computeAnalytics(
    period: AnalyticsPeriod,
    allExpenses: List<Expense>,
    allIncomes: List<Income>,
    accountNames: Set<String>,
    categories: List<Category>,
    today: LocalDate = Time.today(),
): AnalyticsData {
    val year = today.year
    val month = today.monthNumber

    val labels = mutableListOf<String>()
    val expData = mutableListOf<Double>()
    val incData = mutableListOf<Double>()
    var tSpent = 0.0
    var tIncome = 0.0

    fun sumExpenses(from: String, to: String) =
        allExpenses.filter { it.date >= from && it.date <= to }.fold(0.0) { s, e -> s + e.amount }

    fun sumIncomes(from: String, to: String) =
        allIncomes.filter { it.date >= from && it.date <= to }.fold(0.0) { s, i -> s + i.amount }

    when (period) {
        AnalyticsPeriod.DAILY -> for (i in 29 downTo 0) {
            val d = today.minus(i, DateTimeUnit.DAY)
            val dateStr = toLocalDateStr(d)
            labels.add("${d.dayOfMonth}/${d.monthNumber}")
            val dayExp = allExpenses.filter { it.date == dateStr }.fold(0.0) { s, e -> s + e.amount }
            val dayInc = allIncomes.filter { it.date == dateStr }.fold(0.0) { s, inc -> s + inc.amount }
            expData.add(dayExp); incData.add(dayInc)
            tSpent += dayExp; tIncome += dayInc
        }

        AnalyticsPeriod.WEEKLY -> for (i in 11 downTo 0) {
            // setDate(getDate() - (i * 7) - getDay() + 1): en domingo la "semana" empieza mañana
            val weekStart = today.plus(-(i * 7) - today.jsDayOfWeek() + 1, DateTimeUnit.DAY)
            val weekEnd = weekStart.plus(6, DateTimeUnit.DAY)
            val startStr = toLocalDateStr(weekStart)
            val endStr = toLocalDateStr(weekEnd)
            labels.add("S${getWeekNumber(weekStart)}")
            val weekExp = sumExpenses(startStr, endStr)
            val weekInc = sumIncomes(startStr, endStr)
            expData.add(weekExp); incData.add(weekInc)
            tSpent += weekExp; tIncome += weekInc
        }

        AnalyticsPeriod.MONTHLY -> for (i in 11 downTo 0) {
            val d = jsDate(year, month - 1 - i, 1)
            val m = d.monthNumber
            val y = d.year
            labels.add(getMonthName(m).take(3))
            val mm = m.toString().padStart(2, '0')
            val start = "$y-$mm-01"
            val end = "$y-$mm-${daysInMonthOf(y, m).toString().padStart(2, '0')}"
            val monthExp = sumExpenses(start, end)
            val monthInc = sumIncomes(start, end)
            expData.add(monthExp); incData.add(monthInc)
            tSpent += monthExp; tIncome += monthInc
        }
    }

    val byCategory = LinkedHashMap<String, Double>()
    allExpenses.forEach { e ->
        val cat = e.category.ifEmpty { "Sin categoría" }
        byCategory[cat] = (byCategory[cat] ?: 0.0) + e.amount
    }
    val pieCategory = byCategory.entries
        .sortedByDescending { it.value }
        .mapIndexed { i, (name, value) ->
            PieSlice(
                name = shortName(name),
                population = JsCompat.round(value * 100) / 100,
                // Color personalizado de la categoría; la paleta sólo cubre categorías sin color propio.
                color = categories.firstOrNull { it.name == name }?.color?.takeIf { it.isNotEmpty() } ?: ANALYTICS_COLORS[i % ANALYTICS_COLORS.size],
            )
        }

    val byMethod = LinkedHashMap<String, Double>()
    allExpenses.forEach { e ->
        val m = e.paymentMethod.ifEmpty { "Otro" }
        byMethod[m] = (byMethod[m] ?: 0.0) + e.amount
    }
    val pieMethod = byMethod.entries.mapIndexed { i, (name, value) ->
        PieSlice(shortName(name), JsCompat.round(value * 100) / 100, ANALYTICS_COLORS[(i + 3) % ANALYTICS_COLORS.size])
    }

    val byAccountExp = LinkedHashMap<String, Double>()
    allExpenses.forEach { e ->
        val account = e.accountName
        if (e.paymentMethod == "Débito" && !account.isNullOrEmpty() && account in accountNames) {
            byAccountExp[account] = (byAccountExp[account] ?: 0.0) + e.amount
        }
    }

    val byIncomeSource = LinkedHashMap<String, Double>()
    allIncomes.forEach { i ->
        val src = i.source.ifEmpty { "Otro" }
        byIncomeSource[src] = (byIncomeSource[src] ?: 0.0) + i.amount
    }

    return AnalyticsData(
        labels = labels,
        expData = expData,
        incData = incData,
        totalSpent = tSpent,
        totalIncome = tIncome,
        pieCategory = pieCategory,
        pieMethod = pieMethod,
        byAccountExpense = byAccountExp.entries.map { it.key to it.value },
        optCategories = byCategory.keys.toList(),
        optMethods = byMethod.keys.toList(),
        optIncomeSources = byIncomeSource.keys.toList(),
    )
}

class AnalyticsLoader(private val repos: Repos) {
    suspend fun load(period: AnalyticsPeriod, today: LocalDate = Time.today()): AnalyticsData {
        val allExpenses = repos.expenses.getAll()
        val allIncomes = repos.income.getAll()
        val allAccounts = repos.accounts.getAll()
        val allCategories = repos.categories.getAll()
        return computeAnalytics(period, allExpenses, allIncomes, allAccounts.map { it.name }.toSet(), allCategories, today)
    }
}

data class ComparisonResult(
    val y1: Int,
    val m1: Int,
    val y2: Int,
    val m2: Int,
    val expenses1: Double,
    val income1: Double,
    val expenses2: Double,
    val income2: Double,
    val diff: Double,
    val diffPercent: Double,
)

sealed interface ComparisonOutcome {
    data class Result(val value: ComparisonResult) : ComparisonOutcome
    data class Invalid(val message: String) : ComparisonOutcome
}

/** `handleComparison`: compara el gasto de dos meses (YYYY-MM) con filtros por categoría, método e ingreso. */
suspend fun compareMonths(
    aggregates: Aggregates,
    period1: String,
    period2: String,
    category: String = "",
    method: String = "",
    incomeSource: String = "",
): ComparisonOutcome? {
    if (period1.isEmpty() || period2.isEmpty()) return null
    val p1 = period1.split('-').map { JsCompat.number(it) }
    val p2 = period2.split('-').map { JsCompat.number(it) }
    fun part(list: List<Double>, i: Int): Int? = list.getOrNull(i)?.takeIf { it.isFinite() && it != 0.0 }?.toInt()
    val y1 = part(p1, 0)
    val m1 = part(p1, 1)
    val y2 = part(p2, 0)
    val m2 = part(p2, 1)
    if (y1 == null || m1 == null || y2 == null || m2 == null) {
        return ComparisonOutcome.Invalid("Usa el formato YYYY-MM para comparar")
    }

    val s1 = aggregates.getMonthlySummary(y1, m1)
    val s2 = aggregates.getMonthlySummary(y2, m2)

    fun expenses(list: List<Expense>) = list.filter {
        (category.isEmpty() || it.category.lowercase().contains(category.lowercase())) &&
            (method.isEmpty() || it.paymentMethod.lowercase().contains(method.lowercase()))
    }

    fun incomes(list: List<Income>) = list.filter { incomeSource.isEmpty() || it.source.lowercase().contains(incomeSource.lowercase()) }

    val s1Exp = expenses(s1.expenses).fold(0.0) { s, e -> s + e.amount }
    val s2Exp = expenses(s2.expenses).fold(0.0) { s, e -> s + e.amount }
    val s1Inc = incomes(s1.incomes).fold(0.0) { s, i -> s + i.amount }
    val s2Inc = incomes(s2.incomes).fold(0.0) { s, i -> s + i.amount }

    val diff = s1Exp - s2Exp
    val diffPercent = if (s2Exp > 0) JsCompat.parseFloat(JsCompat.toFixed((diff / s2Exp) * 100, 1)) else 0.0

    return ComparisonOutcome.Result(ComparisonResult(y1, m1, y2, m2, s1Exp, s1Inc, s2Exp, s2Inc, diff, diffPercent))
}
