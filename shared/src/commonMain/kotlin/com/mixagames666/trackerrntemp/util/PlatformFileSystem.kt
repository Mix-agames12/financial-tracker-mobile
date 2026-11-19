package com.mixagames666.trackerrntemp.util

import okio.FileSystem

/** `FileSystem.SYSTEM` no está en el código común de okio: cada plataforma entrega el suyo. */
expect val platformFileSystem: FileSystem
