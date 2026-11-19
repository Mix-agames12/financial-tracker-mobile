package com.mixagames666.trackerrntemp.util

/** Candado de exclusión mutua bloqueante para secciones críticas muy cortas (sin suspender dentro del bloque). */
expect class SynchronizedLock() {
    fun <T> withLock(block: () -> T): T
}
