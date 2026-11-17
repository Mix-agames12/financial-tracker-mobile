package com.mixagames666.trackerrntemp.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Publica un recordatorio de pago a la hora programada (09:00 del día anterior o del mismo día). */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val body = inputData.getString(KEY_BODY).orEmpty()
        val id = inputData.getString(KEY_ID) ?: title
        PaymentNotifications.showNotification(applicationContext, id, title, body)
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_ID = "id"
    }
}
