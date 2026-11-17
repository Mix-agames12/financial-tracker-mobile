package com.mixagames666.trackerrntemp.domain

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Port de src/utils/dataSync.ts: tras cualquier cambio en los datos se recalculan los recordatorios de
 * pago, la alerta de presupuesto y el widget. Las tareas las registra la plataforma (notificaciones y
 * widget) para que este módulo no dependa de Android.
 */
class DataSync(private val scope: CoroutineScope) {
    private val tasks = mutableListOf<Pair<String, suspend () -> Unit>>()

    fun register(name: String, task: suspend () -> Unit) {
        tasks.add(name to task)
    }

    /** Mantiene al día recordatorios, alerta de presupuesto y widget tras cualquier cambio en los datos. */
    fun syncAfterDataChange() {
        tasks.forEach { (name, task) ->
            scope.launch {
                try {
                    task()
                } catch (e: Exception) {
                    Log.d(TAG, "Error en $name", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "DataSync"
    }
}
