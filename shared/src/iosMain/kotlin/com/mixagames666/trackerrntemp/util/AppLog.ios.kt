package com.mixagames666.trackerrntemp.util

actual object AppLog {
    actual fun d(tag: String, message: String, throwable: Throwable?) = log("D", tag, message, throwable)
    actual fun w(tag: String, message: String, throwable: Throwable?) = log("W", tag, message, throwable)
    actual fun e(tag: String, message: String, throwable: Throwable?) = log("E", tag, message, throwable)

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val text = if (throwable == null) message else "$message: $throwable"
        // `NSLog` es variádico (no se puede llamar desde Kotlin/Native con argumentos de formato): `println` va a
        // la consola de Xcode y a Console.app sin interpretar '%' del mensaje.
        println("$level/$tag: $text")
    }
}
