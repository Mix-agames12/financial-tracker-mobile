package com.mixagames666.trackerrntemp.domain

import com.mixagames666.trackerrntemp.data.Repos
import com.mixagames666.trackerrntemp.util.JsCompat
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.daysUntil
import com.mixagames666.trackerrntemp.util.nextDateForDay
import com.mixagames666.trackerrntemp.util.nextDateForMonthDay
import com.mixagames666.trackerrntemp.util.orZero
import com.mixagames666.trackerrntemp.util.roundMoney
import java.time.LocalDate

// Port de src/utils/paymentReminders.ts

enum class UpcomingPaymentKind(val wireName: String) {
    LOAN("loan"),
    CARD("card"),
    RECURRING("recurring"),
}

data class UpcomingPayment(
    val id: String,
    val kind: UpcomingPaymentKind,
    val title: String,
    val amount: Double,
    val dueDate: String, // 'YYYY-MM-DD'
    val daysLeft: Int, // negativo = vencido
)

/**
 * Pagos pendientes dentro de `horizonDays` días, ordenados por fecha.
 * Incluye préstamos vencidos (daysLeft < 0) para poder mostrarlos en la app.
 */
suspend fun getUpcomingPayments(repos: Repos, horizonDays: Int = 30, today: LocalDate = Time.today()): List<UpcomingPayment> {
    val allLoans = repos.loans.getAll()
    val cards = repos.creditCards.getAll()
    val expenses = repos.expenses.getAll()
    val payments = mutableListOf<UpcomingPayment>()

    allLoans.filter { it.status != "paid" }.forEach { loan ->
        val due = loan.nextPaymentDate
        if (due.isNullOrEmpty() || loan.paidInstallments >= loan.installments) return@forEach
        val daysLeft = daysUntil(due, today) ?: return@forEach // fecha inválida: nunca pasa el filtro
        payments.add(
            UpcomingPayment(
                id = "loan-${loan.id}",
                kind = UpcomingPaymentKind.LOAN,
                title = loan.name,
                amount = loan.monthlyQuota.orZero(),
                dueDate = due,
                daysLeft = daysLeft,
            ),
        )
    }

    // Lo que cargaron las compras registradas en la app ya tiene su aviso como préstamo vinculado
    // (cardId); la tarjeta sólo avisa del saldo ingresado manualmente.
    cards.forEach { card ->
        if (card.paymentDueDay == 0) return@forEach
        val tracked = allLoans.filter { it.cardId == card.id }.fold(0.0) { sum, l -> sum + loanCardOutstanding(l) }
        val manualBalance = roundMoney(card.currentBalance.orZero() - tracked)
        if (manualBalance <= 0) return@forEach
        val dueDate = nextDateForDay(card.paymentDueDay, today)
        payments.add(
            UpcomingPayment(
                id = "card-${card.id}",
                kind = UpcomingPaymentKind.CARD,
                title = "Tarjeta ${card.name}",
                amount = manualBalance,
                dueDate = dueDate,
                daysLeft = daysUntil(dueDate, today) ?: return@forEach,
            ),
        )
    }

    // Un gasto recurrente se registra varias veces (una por mes): se toma el más reciente de cada uno.
    val seen = HashSet<String>()
    expenses
        .filter { it.isRecurring == true && it.parentExpenseId.isNullOrEmpty() && !it.detail.endsWith(TAX_DETAIL_SUFFIX) }
        .sortedWith { a, b -> b.date.compareTo(a.date) }
        .forEach { exp ->
            val parts = exp.date.split('-').map { JsCompat.number(it) }
            val purchaseMonth = parts.getOrElse(1) { Double.NaN }
            val purchaseDay = parts.getOrElse(2) { Double.NaN }
            val yearly = exp.recurringFrequency == "yearly"
            val recurringDay = exp.recurringDay ?: 0
            val day: Double = if ((exp.recurringFrequency == "specific" || yearly) && recurringDay != 0) recurringDay.toDouble() else purchaseDay
            val month: Double = if (yearly) (exp.recurringMonth?.takeIf { it != 0 }?.toDouble() ?: purchaseMonth) else 0.0
            if (day.isNaN() || day == 0.0 || (yearly && (month.isNaN() || month == 0.0))) return@forEach
            val key = "${exp.detail}|${exp.category}|${month.toInt()}|${day.toInt()}".lowercase()
            if (!seen.add(key)) return@forEach
            val dueDate = if (yearly) nextDateForMonthDay(month.toInt(), day.toInt(), today) else nextDateForDay(day.toInt(), today)
            payments.add(
                UpcomingPayment(
                    id = "recurring-${exp.id}",
                    kind = UpcomingPaymentKind.RECURRING,
                    title = exp.detail.ifEmpty { exp.category },
                    amount = exp.amount.orZero(),
                    dueDate = dueDate,
                    daysLeft = daysUntil(dueDate, today) ?: return@forEach,
                ),
            )
        }

    return payments
        .filter { it.daysLeft <= horizonDays }
        .sortedWith { a, b -> a.dueDate.compareTo(b.dueDate) }
}
