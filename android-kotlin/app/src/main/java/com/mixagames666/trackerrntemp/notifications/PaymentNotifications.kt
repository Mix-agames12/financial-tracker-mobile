package com.mixagames666.trackerrntemp.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mixagames666.trackerrntemp.MainActivity
import com.mixagames666.trackerrntemp.R
import com.mixagames666.trackerrntemp.data.Repos
import com.mixagames666.trackerrntemp.domain.UpcomingPayment
import com.mixagames666.trackerrntemp.domain.getUpcomingPayments
import com.mixagames666.trackerrntemp.util.JsCompat
import com.mixagames666.trackerrntemp.util.Time
import com.mixagames666.trackerrntemp.util.formatCurrency
import com.mixagames666.trackerrntemp.util.formatDate
import com.mixagames666.trackerrntemp.util.jsDate
import com.mixagames666.trackerrntemp.util.orZero
import com.mixagames666.trackerrntemp.util.toLocalDateStr
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

// Port de src/hooks/useNotifications.ts (+ localNotifications.ts)

private const val CHANNEL_ID = "payments"
private const val REMINDER_KIND = "payment-reminder"
private const val REMINDER_HOUR = 9
private const val REMINDER_HORIZON_DAYS = 60
private const val MAX_SCHEDULED = 50

/** Recordatorios de pagos próximos y alerta de presupuesto, como notificaciones locales. */
class PaymentNotifications(private val context: Context, private val repos: Repos) {

    // Ejecuta las llamadas una detrás de otra para que dos reprogramaciones no se intercalen.
    private val queue = Mutex()

    /** En Android 13+ el canal debe existir antes de pedir el permiso para que se muestre el diálogo. */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            vibrationPattern = longArrayOf(0, 250, 250, 250)
            enableVibration(true)
            enableLights(true)
            lightColor = Color.parseColor("#6366f1")
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** `getPermissionsAsync().status === 'granted'` */
    fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** El día anterior a las 09:00; si ya pasó, el mismo día a las 09:00; si también pasó, ninguno. */
    private fun reminderDateFor(payment: UpcomingPayment): Pair<LocalDateTime, Boolean>? {
        val parts = payment.dueDate.split('-').map { JsCompat.number(it) }
        val y = parts.getOrNull(0)?.takeIf { it.isFinite() && it != 0.0 }?.toInt() ?: return null
        val m = parts.getOrNull(1)?.takeIf { it.isFinite() && it != 0.0 }?.toInt() ?: return null
        val d = parts.getOrNull(2)?.takeIf { it.isFinite() && it != 0.0 }?.toInt() ?: return null
        val now = Time.now()
        val dayBefore = LocalDateTime.of(jsDate(y, m - 1, d - 1), LocalTime.of(REMINDER_HOUR, 0))
        if (dayBefore.isAfter(now)) return dayBefore to false
        val dueDay = LocalDateTime.of(jsDate(y, m - 1, d), LocalTime.of(REMINDER_HOUR, 0))
        if (dueDay.isAfter(now)) return dueDay to true
        return null
    }

    /** Reprograma los recordatorios de pagos próximos: préstamos, tarjetas y gastos recurrentes. */
    suspend fun schedulePaymentReminders() = queue.withLock {
        if (!hasPermission()) return@withLock

        val work = WorkManager.getInstance(context)
        work.cancelAllWorkByTag(REMINDER_KIND)

        val payments = getUpcomingPayments(repos, REMINDER_HORIZON_DAYS).filter { it.daysLeft >= 0 }
        var count = 0
        for (payment in payments) {
            if (count >= MAX_SCHEDULED) break
            val (date, isDueDay) = reminderDateFor(payment) ?: continue
            val delay = date.atZone(Time.zone()).toInstant().toEpochMilli() - Time.nowMillis()
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .addTag(REMINDER_KIND)
                .setInputData(
                    Data.Builder()
                        .putString(ReminderWorker.KEY_TITLE, if (isDueDay) "Hoy vence un pago" else "Pago próximo")
                        .putString(
                            ReminderWorker.KEY_BODY,
                            "${if (isDueDay) "Hoy" else "Mañana"} vence ${payment.title}: ${formatCurrency(payment.amount)} (${formatDate(payment.dueDate)}).",
                        )
                        .putString(ReminderWorker.KEY_ID, "$REMINDER_KIND-${payment.id}")
                        .build(),
                )
                .build()
            work.enqueueUniqueWork("$REMINDER_KIND-${payment.id}", ExistingWorkPolicy.REPLACE, request)
            count++
        }
    }

    /** Avisa una sola vez por mes y nivel cuando el gasto del mes llega al 90 % de la meta o la supera. */
    suspend fun checkBudgetAlert() = queue.withLock {
        val settings = repos.settings.get()
        val goal = settings.monthlyGoal.orZero()
        if (goal <= 0 || !hasPermission()) return@withLock

        val now = Time.today()
        val start = toLocalDateStr(jsDate(now.year, now.monthValue - 1, 1))
        val end = toLocalDateStr(jsDate(now.year, now.monthValue, 0))
        val expenses = repos.expenses.getByDateRange(start, end)
        val totalSpent = expenses.fold(0.0) { sum, e -> sum + e.amount.orZero() }

        val level = if (totalSpent > goal) "over" else if (totalSpent >= goal * 0.9) "near" else null
        if (level == null) return@withLock
        val marker = "${start.substring(0, 7)}:$level"
        if (settings.lastBudgetAlert == marker) return@withLock

        showNotification(
            context = context,
            id = "budget-$marker",
            title = if (level == "over") "¡Presupuesto superado!" else "Atención al presupuesto",
            body = if (level == "over") {
                "Has gastado ${formatCurrency(totalSpent)} y superado tu límite de ${formatCurrency(goal)}."
            } else {
                "Has gastado el ${JsCompat.toFixed(totalSpent / goal * 100, 1)}% de tu límite de ${formatCurrency(goal)}."
            },
        )
        repos.settings.save { it.copy(lastBudgetAlert = marker) }
    }

    companion object {
        /** Publica una notificación en el canal de pagos (también la usa el Worker de recordatorios). */
        @Suppress("MissingPermission")
        fun showNotification(context: Context, id: String, title: String, body: String) {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setColor(Color.parseColor("#6366f1"))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(longArrayOf(0, 250, 250, 250))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            manager.notify(id.hashCode(), notification)
        }
    }
}
