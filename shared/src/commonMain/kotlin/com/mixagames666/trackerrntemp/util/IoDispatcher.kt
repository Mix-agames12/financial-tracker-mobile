package com.mixagames666.trackerrntemp.util

import kotlinx.coroutines.CoroutineDispatcher

/** `Dispatchers.IO` no está en el código común de coroutines: cada plataforma entrega el suyo. */
expect val ioDispatcher: CoroutineDispatcher
