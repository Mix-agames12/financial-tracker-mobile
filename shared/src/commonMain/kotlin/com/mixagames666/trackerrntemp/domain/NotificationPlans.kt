package com.mixagames666.trackerrntemp.domain

import com.mixagames666.trackerrntemp.data.Repos
import com.mixagames666.trackerrntemp.util.JsCompat
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.formatCurrency
import com.mixagames666.trackerrntemp.util.formatDate
import com.mixagames666.trackerrntemp.util.formatPercentLocalized
import com.mixagames666.trackerrntemp.util.jsDate
import com.mixagames666.trackerrntemp.util.orZero
import com.mixagames666.trackerrntemp.util.toLocalDateStr
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime

// Qué avisar y cuándo (port de src/hooks/useNotifications.ts). La entrega (WorkManager en Android,
// UNUserNotificationCenter en iOS) es de cada plataforma; este módulo sólo decide el contenido y la hora.

const val REMINDER_ID_PREFIX = "payment-reminder"
const val REMINDER_HOUR = 9
const val REMINDER_HORIZON_DAYS = 60
const val MAX_SCHEDULED_REMINDERS = 50

/** Un recordatorio de pago listo para programar a la hora local [fireAt]. */
data class PaymentReminder(
    /** Identificador estable: `payment-reminder-<id del pago>`. */
    val id: String,
    val title: String,
    val body: String,
    val fireAt: LocalDateTime,
)

/** El día anterior a las 09:00; si ya pasó, el mismo día a las 09:00; si también pasó, ninguno. */
private fun reminderDateFor(payment: UpcomingPayment, now: LocalDateTime): Pair<LocalDateTime, Boolean>? {
    val parts = payment.dueDate.split('-').map { JsCompat.number(it) }
    val y = parts.getOrNull(0)?.takeIf { it.isFinite() && it != 0.0 }?.toInt() ?: return null
    val m = parts.getOrNull(1)?.takeIf { it.isFinite() && it != 0.0 }?.toInt() ?: return null
    val d = parts.getOrNull(2)?.takeIf { it.isFinite() && it != 0.0 }?.toInt() ?: return null
    val dayBefore = jsDate(y, m - 1, d - 1).atTime(REMINDER_HOUR, 0)
    if (dayBefore > now) return dayBefore to false
    val dueDay = jsDate(y, m - 1, d).atTime(REMINDER_HOUR, 0)
    if (dueDay > now) return dueDay to true
    return null
}

/** Recordatorios de pagos próximos (préstamos, tarjetas y gastos recurrentes), como máximo [MAX_SCHEDULED_REMINDERS]. */
suspend fun planPaymentReminders(repos: Repos, now: LocalDateTime = Time.now()): List<PaymentReminder> {
    val payments = getUpcomingPayments(repos, REMINDER_HORIZON_DAYS).filter { it.daysLeft >= 0 }
    val plan = mutableListOf<PaymentReminder>()
    for (payment in payments) {
        if (plan.size >= MAX_SCHEDULED_REMINDERS) break
        val (date, isDueDay) = reminderDateFor(payment, now) ?: continue
        plan.add(
            PaymentReminder(
                id = "$REMINDER_ID_PREFIX-${payment.id}",
                title = if (isDueDay) "Hoy vence un pago" else "Pago próximo",
                body = "${if (isDueDay) "Hoy" else "Mañana"} vence ${payment.title}: ${formatCurrency(payment.amount)} (${formatDate(payment.dueDate)}).",
                fireAt = date,
            ),
        )
    }
    return plan
}

/** Aviso del límite de gastos mensual; [marker] (`YYYY-MM:nivel`) se guarda en los ajustes para avisar una sola vez. */
data class BudgetAlert(val id: String, val marker: String, val title: String, val body: String)

/**
 * Aviso pendiente cuando el gasto del mes llega al 90 % del límite de gastos mensual o lo supera; `null` si no hay
 * límite, no se alcanza o ya se avisó de ese nivel este mes. Quien lo entrega debe guardar `lastBudgetAlert = marker`.
 */
suspend fun pendingBudgetAlert(repos: Repos): BudgetAlert? {
    val settings = repos.settings.get()
    val goal = settings.monthlyGoal.orZero()
    if (goal <= 0) return null

    val now = Time.today()
    val start = toLocalDateStr(jsDate(now.year, now.monthNumber - 1, 1))
    val end = toLocalDateStr(jsDate(now.year, now.monthNumber, 0))
    val expenses = repos.expenses.getByDateRange(start, end)
    val totalSpent = expenses.fold(0.0) { sum, e -> sum + e.amount.orZero() }

    val level = if (totalSpent > goal) "over" else if (totalSpent >= goal * 0.9) "near" else null
    if (level == null) return null
    val marker = "${start.substring(0, 7)}:$level"
    if (settings.lastBudgetAlert == marker) return null

    return BudgetAlert(
        id = "budget-$marker",
        marker = marker,
        title = if (level == "over") "¡Superaste tu límite de gastos!" else "Cerca de tu límite de gastos",
        body = if (level == "over") {
            "Llevas ${formatCurrency(totalSpent)} gastados este mes y superaste tu límite mensual de ${formatCurrency(goal)}."
        } else {
            "Has usado el ${formatPercentLocalized(totalSpent / goal)} de tu límite de gastos mensual (${formatCurrency(goal)})."
        },
    )
}
