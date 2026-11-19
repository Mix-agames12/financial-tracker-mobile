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
import com.mixagames666.trackerrntemp.domain.pendingBudgetAlert
import com.mixagames666.trackerrntemp.domain.planPaymentReminders
import com.mixagames666.trackerrntemp.platform.NotificationService
import com.mixagames666.trackerrntemp.util.Time
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.toInstant
import java.util.concurrent.TimeUnit

// Port de src/hooks/useNotifications.ts (+ localNotifications.ts)

private const val CHANNEL_ID = "payments"
private const val REMINDER_KIND = "payment-reminder"

/** Recordatorios de pagos próximos y alerta de presupuesto, como notificaciones locales. */
class PaymentNotifications(private val context: Context, private val repos: Repos) : NotificationService {

    // Ejecuta las llamadas una detrás de otra para que dos reprogramaciones no se intercalen.
    private val queue = Mutex()

    /** En Android 13+ el canal debe existir antes de pedir el permiso para que se muestre el diálogo. */
    override fun ensureChannel() {
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
    override fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Reprograma los recordatorios de pagos próximos: préstamos, tarjetas y gastos recurrentes. */
    suspend fun schedulePaymentReminders() = queue.withLock {
        if (!hasPermission()) return@withLock

        val work = WorkManager.getInstance(context)
        work.cancelAllWorkByTag(REMINDER_KIND)

        for (reminder in planPaymentReminders(repos)) {
            val delay = reminder.fireAt.toInstant(Time.zone()).toEpochMilliseconds() - Time.nowMillis()
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .addTag(REMINDER_KIND)
                .setInputData(
                    Data.Builder()
                        .putString(ReminderWorker.KEY_TITLE, reminder.title)
                        .putString(ReminderWorker.KEY_BODY, reminder.body)
                        .putString(ReminderWorker.KEY_ID, reminder.id)
                        .build(),
                )
                .build()
            work.enqueueUniqueWork(reminder.id, ExistingWorkPolicy.REPLACE, request)
        }
    }

    /** Avisa una sola vez por mes y nivel cuando el gasto del mes llega al 90 % del límite de gastos mensual o lo supera. */
    suspend fun checkBudgetAlert() = queue.withLock {
        if (!hasPermission()) return@withLock
        val alert = pendingBudgetAlert(repos) ?: return@withLock
        showNotification(context = context, id = alert.id, title = alert.title, body = alert.body)
        repos.settings.save { it.copy(lastBudgetAlert = alert.marker) }
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
