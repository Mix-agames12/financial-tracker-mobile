package com.mixagames666.trackerrntemp.platform

import com.mixagames666.trackerrntemp.data.Repos
import com.mixagames666.trackerrntemp.domain.PaymentReminder
import com.mixagames666.trackerrntemp.domain.REMINDER_ID_PREFIX
import com.mixagames666.trackerrntemp.domain.pendingBudgetAlert
import com.mixagames666.trackerrntemp.domain.planPaymentReminders
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNNotificationTrigger
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * Último estado conocido del permiso de notificaciones. UIKit sólo lo da de forma asíncrona y
 * [NotificationService.hasPermission] es síncrono: se refresca al arrancar, al pedir el permiso y antes de programar.
 */
internal object IosNotificationState {
    @Volatile
    var authorized: Boolean = false
}

/** Sin delegado, iOS no muestra notificaciones mientras la app está abierta (p. ej. la alerta del límite de gastos). */
private class ForegroundPresenter : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(
            UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound,
        )
    }
}

// `UNUserNotificationCenter.delegate` es débil: se conserva aquí durante todo el proceso.
private val foregroundPresenter = ForegroundPresenter()

/**
 * Recordatorios de pagos próximos y alerta del límite de gastos como notificaciones locales de iOS.
 * Qué avisar y cuándo lo decide el código común (`NotificationPlans.kt`); aquí sólo la entrega.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosNotifications(private val repos: Repos) : NotificationService {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    // Ejecuta las llamadas una detrás de otra para que dos reprogramaciones no se intercalen.
    private val queue = Mutex()

    /** Registra el delegado (avisos con la app abierta) y lee el permiso actual. */
    fun start(scope: CoroutineScope) {
        ensureChannel()
        scope.launch { refreshAuthorization() }
    }

    /** iOS no tiene canales: sólo se registra el delegado de primer plano (idempotente). */
    override fun ensureChannel() {
        center.delegate = foregroundPresenter
    }

    override fun hasPermission(): Boolean = IosNotificationState.authorized

    private suspend fun refreshAuthorization(): Boolean {
        val granted = suspendCoroutine<Boolean> { cont ->
            center.getNotificationSettingsWithCompletionHandler { settings ->
                val status = settings?.authorizationStatus
                cont.resume(status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional)
            }
        }
        IosNotificationState.authorized = granted
        return granted
    }

    /** Identificadores de recordatorios de pago ya programados (no toca otras notificaciones pendientes). */
    private suspend fun pendingReminderIds(): List<String> = suspendCoroutine { cont ->
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            cont.resume(
                requests.orEmpty()
                    .mapNotNull { (it as? UNNotificationRequest)?.identifier }
                    .filter { it.startsWith(REMINDER_ID_PREFIX) },
            )
        }
    }

    private suspend fun add(id: String, title: String, body: String, trigger: UNNotificationTrigger) {
        val content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = UNNotificationSound.defaultSound
        val request = UNNotificationRequest.requestWithIdentifier(id, content = content, trigger = trigger)
        suspendCoroutine<Unit> { cont ->
            center.addNotificationRequest(request) { _ -> cont.resume(Unit) }
        }
    }

    /** Reprograma los recordatorios de pagos próximos: préstamos, tarjetas y gastos recurrentes. */
    suspend fun schedulePaymentReminders() = queue.withLock {
        if (!refreshAuthorization()) return@withLock

        center.removePendingNotificationRequestsWithIdentifiers(pendingReminderIds())
        for (reminder in planPaymentReminders(repos)) {
            add(reminder.id, reminder.title, reminder.body, calendarTrigger(reminder))
        }
    }

    private fun calendarTrigger(reminder: PaymentReminder): UNCalendarNotificationTrigger {
        val parts = NSDateComponents()
        parts.year = reminder.fireAt.year.toLong()
        parts.month = reminder.fireAt.monthNumber.toLong()
        parts.day = reminder.fireAt.dayOfMonth.toLong()
        parts.hour = reminder.fireAt.hour.toLong()
        parts.minute = reminder.fireAt.minute.toLong()
        return UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(parts, repeats = false)
    }

    /** Avisa una sola vez por mes y nivel cuando el gasto del mes llega al 90 % del límite de gastos mensual o lo supera. */
    suspend fun checkBudgetAlert() = queue.withLock {
        if (!refreshAuthorization()) return@withLock
        val alert = pendingBudgetAlert(repos) ?: return@withLock
        // Un disparador de 1 s (el sistema exige >= 1 s) en lugar de "ahora".
        add(alert.id, alert.title, alert.body, UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, repeats = false))
        repos.settings.save { it.copy(lastBudgetAlert = alert.marker) }
    }
}
