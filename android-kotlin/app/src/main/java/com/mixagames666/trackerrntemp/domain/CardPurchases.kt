package com.mixagames666.trackerrntemp.domain

import com.mixagames666.trackerrntemp.data.CreditCard
import com.mixagames666.trackerrntemp.data.Expense
import com.mixagames666.trackerrntemp.data.Loan
import com.mixagames666.trackerrntemp.data.PaymentMethod
import com.mixagames666.trackerrntemp.data.Repos
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.lastDateForDay
import com.mixagames666.trackerrntemp.util.nextDateForDay
import com.mixagames666.trackerrntemp.util.orZero
import com.mixagames666.trackerrntemp.util.roundMoney
import com.mixagames666.trackerrntemp.util.toLocalDateStr
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Port de src/utils/cardPurchases.ts

/** Sufijo del gasto adicional que registra los impuestos/comisiones de una compra. */
const val TAX_DETAIL_SUFFIX = " (Impuestos/Comisiones)"

// El préstamo de una compra con tarjeta se crea milisegundos después de su gasto.
private const val LEGACY_LINK_WINDOW_MS = 60_000.0

private const val DEFERRED_PREFIX = "Diferido: "

/**
 * Parte de la compra que sigue cargada en la tarjeta. Los préstamos vinculados la guardan en
 * cardOutstanding para que pagos, ediciones y borrados muevan exactamente lo que se cargó.
 */
fun loanCardOutstanding(loan: Loan): Double {
    if (loan.cardId.isNullOrEmpty()) return 0.0
    val stored = loan.cardOutstanding ?: if (loan.status == "paid") 0.0 else loan.totalAmount
    return max(0.0, roundMoney(stored.orZero()))
}

/** Lo que queda por pagar de un préstamo según sus cuotas (0 si ya está pagado). */
fun loanRemainingDebt(loan: Loan): Double {
    if (loan.status == "paid") return 0.0
    return max(0.0, roundMoney((loan.installments - loan.paidInstallments) * loan.monthlyQuota))
}

/** Nivel del cupo disponible: más de 30 % verde, entre 10 % y 30 % ámbar, menos de 10 % rojo. */
enum class AvailabilityLevel { SUCCESS, WARNING, DANGER }

fun availabilityLevel(availableRatio: Double): AvailabilityLevel = when {
    availableRatio > 0.3 -> AvailabilityLevel.SUCCESS
    availableRatio >= 0.1 -> AvailabilityLevel.WARNING
    else -> AvailabilityLevel.DANGER
}

private data class PurchasePlan(val name: String, val installments: Int, val nextPaymentDate: String)

private fun purchasePlan(expense: Expense, card: CreditCard, nextPaymentDate: String): PurchasePlan {
    val deferredMonths = if (expense.isDeferred == true) (expense.deferredMonths ?: 0) else 0
    return if (deferredMonths > 0) {
        PurchasePlan("$DEFERRED_PREFIX${expense.detail}", deferredMonths, nextPaymentDate)
    } else {
        PurchasePlan("TC ${card.name}: ${expense.detail}", 1, nextPaymentDate)
    }
}

/** Mantiene sincronizados saldo de tarjeta, préstamo y gasto de las compras con tarjeta. */
class CardPurchases(private val repos: Repos) {

    /** Suma `delta` al saldo de la tarjeta (negativo para restar), sin bajar de 0. */
    private suspend fun changeCardBalance(cardId: String?, delta: Double) {
        if (cardId.isNullOrEmpty() || delta == 0.0 || delta.isNaN()) return
        val card = repos.creditCards.get(cardId) ?: return
        val currentBalance = max(0.0, roundMoney(card.currentBalance.orZero() + delta))
        repos.creditCards.update(card.copy(currentBalance = currentBalance))
    }

    /** Carga la compra en la tarjeta y crea su préstamo, vinculado a la tarjeta y al gasto. */
    suspend fun registerCardPurchase(expense: Expense, card: CreditCard, totalCharge: Double, nextPaymentDate: String) {
        val charge = roundMoney(totalCharge)
        val plan = purchasePlan(expense, card, nextPaymentDate)
        changeCardBalance(card.id, charge)
        repos.loans.add(
            Loan(
                name = plan.name,
                totalAmount = charge,
                installments = plan.installments,
                paidInstallments = 0,
                monthlyQuota = roundMoney(charge / plan.installments),
                interestRate = 0.0,
                nextPaymentDate = plan.nextPaymentDate,
                status = "active",
                cardId = card.id,
                sourceExpenseId = expense.id,
                cardOutstanding = charge,
            ),
        )
    }

    /**
     * Cambia en `delta` el monto de una compra ya registrada (positivo si aumentó): ajusta la tarjeta y
     * recalcula las cuotas pendientes sobre lo que falta pagar del préstamo.
     */
    private suspend fun adjustPurchaseLoan(loan: Loan, delta: Double, plan: PurchasePlan? = null) {
        val pendingInstallments = max(0, loan.installments - loan.paidInstallments)
        val remainingDebt = if (loan.status == "paid") 0.0 else pendingInstallments * loan.monthlyQuota
        val nextDebt = max(0.0, roundMoney(remainingDebt + delta))
        val isPaid = nextDebt <= 0

        val outstanding = loanCardOutstanding(loan)
        val nextOutstanding = if (isPaid) 0.0 else max(0.0, roundMoney(outstanding + delta))
        changeCardBalance(loan.cardId, roundMoney(nextOutstanding - outstanding))

        val installments = max(plan?.installments ?: loan.installments, loan.paidInstallments + if (isPaid) 0 else 1)
        val keepDate = loan.paidInstallments > 0 && !loan.nextPaymentDate.isNullOrEmpty()
        repos.loans.update(
            loan.copy(
                name = plan?.name ?: loan.name,
                totalAmount = max(0.0, roundMoney(loan.totalAmount + delta)),
                installments = installments,
                monthlyQuota = if (isPaid) loan.monthlyQuota else roundMoney(nextDebt / (installments - loan.paidInstallments)),
                cardOutstanding = nextOutstanding,
                status = if (isPaid) "paid" else "active",
                // Sin cuotas pagadas, la fecha de pago se recalcula con la nueva fecha de compra.
                nextPaymentDate = if (isPaid) "" else if (keepDate) loan.nextPaymentDate else plan?.nextPaymentDate ?: loan.nextPaymentDate,
            ),
        )
    }

    /** Al pagar una cuota: la resta de la tarjeta y devuelve lo que sigue pendiente en ella. */
    suspend fun applyLoanPaymentToCard(loan: Loan, quota: Double, isFinalPayment: Boolean): Double? {
        if (loan.cardId.isNullOrEmpty()) return null
        val outstanding = loanCardOutstanding(loan)
        // La última cuota libera todo lo pendiente para no dejar centavos de redondeo en la tarjeta.
        val portion = if (isFinalPayment) outstanding else min(roundMoney(quota), outstanding)
        changeCardBalance(loan.cardId, -portion)
        return roundMoney(outstanding - portion)
    }

    /** Antes de eliminar un préstamo vinculado: quita de la tarjeta lo que seguía pendiente. */
    suspend fun releaseLoanFromCard(loan: Loan) {
        changeCardBalance(loan.cardId, -loanCardOutstanding(loan))
    }

    /**
     * Edición de un préstamo vinculado desde Préstamos: si cambian montos o cuotas, la tarjeta pasa a
     * reflejar lo que queda por pagar. Devuelve el nuevo pendiente, o null si no hay que tocarlo.
     */
    suspend fun syncLoanEditWithCard(before: Loan, after: Loan): Double? {
        if (before.cardId.isNullOrEmpty()) return null
        val unchanged = before.totalAmount == after.totalAmount &&
            before.interestRate == after.interestRate &&
            before.installments == after.installments &&
            before.paidInstallments == after.paidInstallments
        if (unchanged) return null

        val outstanding = loanCardOutstanding(before)
        val next = if (after.status == "paid") {
            0.0
        } else {
            max(0.0, roundMoney((after.installments - after.paidInstallments) * after.monthlyQuota))
        }
        changeCardBalance(before.cardId, roundMoney(next - outstanding))
        return next
    }

    /**
     * Mantiene tarjeta y préstamo en sincronía al editar un gasto. Con la misma tarjeta ajusta la
     * diferencia; si cambió la tarjeta o el método de pago, deshace la compra anterior y registra la nueva.
     */
    suspend fun updateCardPurchase(before: Expense, after: Expense, card: CreditCard?, nextPaymentDate: String) {
        val loans = repos.loans.getAll()
        val expenses = repos.expenses.getAll()
        val loan = loans.firstOrNull { it.sourceExpenseId == before.id }
        val taxRows = expenses.filter { it.parentExpenseId == before.id }
        val delta = roundMoney(after.amount.orZero() - before.amount.orZero())

        if (loan != null && card != null && loan.cardId == card.id) {
            adjustPurchaseLoan(loan, delta, purchasePlan(after, card, nextPaymentDate))
        } else {
            if (loan != null) {
                releaseLoanFromCard(loan)
                repos.loans.delete(loan.id)
            }
            if (card != null) {
                if (loan == null && before.paymentMethod == PaymentMethod.CREDIT && before.cardName == card.name) {
                    // Compra anterior a este cambio, sin préstamo vinculado: sólo se ajusta la diferencia.
                    changeCardBalance(card.id, delta)
                } else {
                    // Los cargos de impuestos ya registrados siguen siendo parte de la compra.
                    val taxes = taxRows.fold(0.0) { sum, e -> sum + e.amount.orZero() }
                    registerCardPurchase(after, card, after.amount.orZero() + taxes, nextPaymentDate)
                }
            }
        }

        // Los cargos de impuestos acompañan al gasto principal si cambió su método de pago.
        val methodChanged = before.paymentMethod != after.paymentMethod ||
            before.cardName != after.cardName ||
            before.accountName != after.accountName
        if (methodChanged) {
            for (row in taxRows) {
                repos.expenses.update(row.copy(paymentMethod = after.paymentMethod, cardName = after.cardName, accountName = after.accountName))
            }
        }
    }

    /**
     * Elimina un gasto manteniendo la tarjeta al día: una compra con tarjeta libera su saldo pendiente
     * y se lleva su préstamo y su cargo de impuestos; un cargo de impuestos se descuenta de su compra.
     */
    suspend fun deleteExpenseWithCardSync(expenseId: String) {
        val expense = repos.expenses.get(expenseId) ?: return
        val loans = repos.loans.getAll()

        val loan = loans.firstOrNull { it.sourceExpenseId == expense.id }
        if (loan != null) {
            releaseLoanFromCard(loan)
            repos.loans.delete(loan.id)
            val taxRows = repos.expenses.getAll().filter { it.parentExpenseId == expense.id }
            for (row in taxRows) repos.expenses.delete(row.id)
        } else if (!expense.parentExpenseId.isNullOrEmpty()) {
            val parentLoan = loans.firstOrNull { it.sourceExpenseId == expense.parentExpenseId }
            if (parentLoan != null) adjustPurchaseLoan(parentLoan, -expense.amount.orZero())
        }

        repos.expenses.delete(expense.id)
    }

    /**
     * Vincula los préstamos de compras con tarjeta creados antes de existir cardId (idempotente). No
     * cambia saldos: el código anterior nunca descontaba de la tarjeta, así que toda la compra sigue
     * cargada en ella (cardOutstanding = totalAmount) y se libera al pagar la última cuota o al borrarla.
     */
    suspend fun linkLegacyCardLoans() {
        val loans = repos.loans.getAll()
        val cards = repos.creditCards.getAll()
        val expenses = repos.expenses.getAll()
        val legacy = loans.filter { it.cardId.isNullOrEmpty() && (it.name.startsWith("TC ") || it.name.startsWith(DEFERRED_PREFIX)) }
        if (legacy.isEmpty()) return

        val linkedExpenseIds = loans.mapNotNull { it.sourceExpenseId?.takeIf { id -> id.isNotEmpty() } }.toMutableSet()
        val purchases = expenses.filter {
            it.paymentMethod == PaymentMethod.CREDIT && !it.cardName.isNullOrEmpty() && it.parentExpenseId.isNullOrEmpty() &&
                !it.detail.endsWith(TAX_DETAIL_SUFFIX)
        }

        for (loan in legacy) {
            val deferred = loan.name.startsWith(DEFERRED_PREFIX)
            val namedCard = if (deferred) null else cards.firstOrNull { loan.name.startsWith("TC ${it.name}: ") }
            if (!deferred && namedCard == null) continue
            val detail = if (namedCard != null) loan.name.substring("TC ${namedCard.name}: ".length) else loan.name.substring(DEFERRED_PREFIX.length)

            val candidates = purchases.filter {
                it.id !in linkedExpenseIds &&
                    it.detail == detail &&
                    (it.isDeferred == true) == deferred &&
                    (namedCard == null || it.cardName == namedCard.name)
            }
            val loanTime = createdAtMs(loan.createdAt)
            val closest = candidates
                .map { it to abs(createdAtMs(it.createdAt) - loanTime) }
                .filter { (_, gap) -> gap <= LEGACY_LINK_WINDOW_MS }
                .sortedBy { (_, gap) -> gap }
                .firstOrNull()?.first
            val match = closest ?: if (candidates.size == 1) candidates[0] else null

            val card = namedCard ?: cards.firstOrNull { it.name == match?.cardName }
            if (card == null) continue

            repos.loans.update(loan.copy(cardId = card.id, sourceExpenseId = match?.id, cardOutstanding = roundMoney(loan.totalAmount)))
            if (match == null) continue

            linkedExpenseIds.add(match.id)
            val taxRows = expenses.filter {
                it.parentExpenseId.isNullOrEmpty() &&
                    it.date == match.date &&
                    it.cardName == match.cardName &&
                    it.detail == "${match.detail}$TAX_DETAIL_SUFFIX"
            }
            for (row in taxRows) repos.expenses.update(row.copy(parentExpenseId = match.id))
        }
    }

    private fun createdAtMs(createdAt: String?): Double = Time.parseIsoMillis(createdAt)
}

data class CardPurchaseItem(
    val loan: Loan,
    val expense: Expense?,
    val title: String,
    val pending: Double,
    val isPaid: Boolean,
)

data class CardCycleSummary(
    val lastCutOff: String?, // último corte (null si la tarjeta no tiene día de corte)
    val nextDueDate: String?, // próximo día de pago de la tarjeta
    val dueAmount: Double, // vence en el próximo pago: compras hasta el último corte y cuotas atrasadas
    val activeDebt: Double, // resto pendiente: compras después del corte y cuotas siguientes
    val totalPending: Double,
    val available: Double?, // cupo disponible (null si la tarjeta no tiene límite)
    val hasOverdue: Boolean,
    val purchases: List<CardPurchaseItem>, // pendientes por vencimiento, luego las pagadas
)

private fun purchaseTitle(loan: Loan, card: CreditCard, expense: Expense?): String {
    if (!expense?.detail.isNullOrEmpty()) return expense!!.detail
    val prefix = if (loan.name.startsWith(DEFERRED_PREFIX)) DEFERRED_PREFIX else "TC ${card.name}: "
    return if (loan.name.startsWith(prefix)) loan.name.substring(prefix.length) else loan.name
}

/**
 * Resume las compras de una tarjeta por ciclo de facturación. La fecha de pago de cada compra ya
 * refleja el corte (las posteriores al corte vencen un mes después), así que lo que vence hasta el
 * próximo día de pago es "por pagar" y el resto es deuda activa.
 */
fun getCardCycleSummary(
    card: CreditCard,
    loans: List<Loan>,
    expensesById: Map<String, Expense>,
    today: LocalDate = Time.today(),
): CardCycleSummary {
    val nextDueDate = if (card.paymentDueDay != 0) nextDateForDay(card.paymentDueDay, today) else null
    val todayStr = toLocalDateStr(today)
    var dueAmount = 0.0
    var totalPending = 0.0
    var hasOverdue = false

    val purchases = loans
        .filter { it.cardId == card.id }
        .map { loan ->
            val expense = loan.sourceExpenseId?.takeIf { it.isNotEmpty() }?.let { expensesById[it] }
            val pending = loanRemainingDebt(loan)
            val isPaid = pending <= 0
            if (!isPaid) {
                totalPending += pending
                val due = loan.nextPaymentDate
                if (!due.isNullOrEmpty() && due < todayStr) hasOverdue = true
                if (!due.isNullOrEmpty() && nextDueDate != null && due <= nextDueDate) dueAmount += min(roundMoney(loan.monthlyQuota), pending)
            }
            CardPurchaseItem(loan, expense, purchaseTitle(loan, card, expense), pending, isPaid)
        }
        .sortedWith { a, b ->
            when {
                a.isPaid != b.isPaid -> if (a.isPaid) 1 else -1
                !a.isPaid -> (a.loan.nextPaymentDate.orEmpty().ifEmpty { "9999" }).compareTo(b.loan.nextPaymentDate.orEmpty().ifEmpty { "9999" })
                else -> (b.expense?.date.orEmpty()).compareTo(a.expense?.date.orEmpty())
            }
        }

    return CardCycleSummary(
        lastCutOff = if (card.cutOffDay != 0) lastDateForDay(card.cutOffDay, today) else null,
        nextDueDate = nextDueDate,
        dueAmount = roundMoney(dueAmount),
        activeDebt = roundMoney(totalPending - dueAmount),
        totalPending = roundMoney(totalPending),
        available = if (card.creditLimit > 0) max(0.0, roundMoney(card.creditLimit - card.currentBalance.orZero())) else null,
        hasOverdue = hasOverdue,
        purchases = purchases,
    )
}

/**
 * Fecha del próximo pago de una compra con tarjeta (copia de calculateCreditCardNextPaymentDate de
 * ExpensesScreen): el mes siguiente a la compra, o el subsiguiente si cae después del corte.
 */
fun calculateCreditCardNextPaymentDate(expenseDate: String, cutOffDay: Int, paymentDueDay: Int): String {
    if (expenseDate.isEmpty() || cutOffDay == 0 || paymentDueDay == 0) return ""
    val date = try {
        LocalDate.parse(expenseDate)
    } catch (_: Exception) {
        return ""
    }
    val expDay = date.dayOfMonth
    var dueMonth = date.monthValue - 1 + 1 // getUTCMonth() + 1
    var dueYear = date.year

    if (expDay > cutOffDay) dueMonth += 1
    if (dueMonth > 11) {
        dueMonth -= 12
        dueYear += 1
    }

    val mStr = (dueMonth + 1).toString().padStart(2, '0')
    // El día de pago no puede pasar del último día del mes (p. ej. 31 en noviembre).
    val lastDayOfDueMonth = com.mixagames666.trackerrntemp.util.daysInMonthOf(dueYear, dueMonth + 1)
    val dStr = min(paymentDueDay, lastDayOfDueMonth).toString().padStart(2, '0')
    return "$dueYear-$mStr-$dStr"
}
