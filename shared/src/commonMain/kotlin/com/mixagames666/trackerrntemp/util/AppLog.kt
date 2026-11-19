package com.mixagames666.trackerrntemp.util

/** Registro mínimo multiplataforma: Android usa android.util.Log y iOS NSLog. */
expect object AppLog {
    fun d(tag: String, message: String, throwable: Throwable? = null)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
