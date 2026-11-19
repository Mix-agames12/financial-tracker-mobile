package com.mixagames666.trackerrntemp.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import okio.Path.Companion.toOkioPath
import java.io.File

class LegacyAsyncStorageImporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun importer(dir: File) = LegacyAsyncStorageImporter(File(tmp.root, "no-existe/RKStorage"), dir, JsonStore(dir.toOkioPath()))

    @Test
    fun copiaLasColeccionesConocidasTalCual() = runBlocking {
        val dir = tmp.newFolder("stores")
        val expenses = """[{"id":"e1","amount":12.5,"category":"Comida"}]"""
        val settings = """[{"id":"default","monthlyGoal":800,"theme":"light"}]"""
        val imported = importer(dir).importRows(
            mapOf(
                "@tracker_gastos_expenses" to expenses,
                "@tracker_gastos_settings" to settings,
            ),
        )

        assertEquals(2, imported)
        assertEquals(expenses, File(dir, "expenses.json").readText())
        assertEquals(settings, File(dir, "settings.json").readText())
        assertFalse(File(dir, "loans.json").exists())
    }

    @Test
    fun ignoraClavesAjenasYValoresQueNoSonArreglos() = runBlocking {
        val dir = tmp.newFolder("stores")
        val imported = importer(dir).importRows(
            mapOf(
                "@otra_app_expenses" to "[1]", // clave de otra librería
                "@tracker_gastos_unknown" to "[1]", // colección que no existe
                "@tracker_gastos_loans" to """{"no":"arreglo"}""",
                "@tracker_gastos_income" to "esto no es json",
                "@tracker_gastos_accounts" to "[]",
            ),
        )

        assertEquals(1, imported)
        assertEquals("[]", File(dir, "accounts.json").readText())
        assertNull(dir.listFiles { f -> f.name == "loans.json" || f.name == "income.json" || f.name == "unknown.json" }?.firstOrNull())
    }

    @Test
    fun sinBaseDeDatosNoHaceNada() = runBlocking {
        val dir = tmp.newFolder("stores")
        assertEquals(0, importer(dir).importIfNeeded())
        assertFalse(File(dir, "expenses.json").exists())
    }

    @Test
    fun noPisaDatosYaGuardadosPorEstaApp() = runBlocking {
        val dir = tmp.newFolder("stores")
        File(dir, "settings.json").writeText("""[{"id":"default"}]""")
        // Aunque la base exista, el almacén propio ya tiene datos: no se importa nada.
        val db = tmp.newFile("RKStorage")
        val result = LegacyAsyncStorageImporter(db, dir, JsonStore(dir.toOkioPath())).importIfNeeded()
        assertEquals(0, result)
    }
}
