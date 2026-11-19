package com.mixagames666.trackerrntemp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import okio.Path.Companion.toOkioPath
import java.io.File

/** Casos límite del almacén JSON y de los serializadores tolerantes (datos de AsyncStorage o de un backup ajeno). */
class StoreRobustnessTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var dir: File
    private lateinit var repos: Repos

    @Before
    fun setUp() {
        dir = tmp.newFolder("stores")
        repos = Repos(JsonStore(dir.toOkioPath()))
    }

    private fun file(name: String) = File(dir, "$name.json")

    @Test
    fun aceptaNumerosTextosYBooleanosConOtroTipo() = runBlocking {
        file("expenses").writeText(
            """[{"id":"a","amount":"12.5","date":"2026-01-01","category":5,"detail":null,"isDeferred":"true","deferredMonths":"3","campoNuevo":{"x":1}}]""",
        )
        val e = repos.expenses.getAll().single()
        assertEquals(12.5, e.amount, 0.0)
        assertEquals("5", e.category)
        assertEquals("", e.detail)
        assertEquals(true, e.isDeferred)
        assertEquals(3, e.deferredMonths)
    }

    @Test
    fun conservaLosRegistrosIlegiblesAlGuardar() = runBlocking {
        file("expenses").writeText("""[{"id":"ok","amount":1,"date":"2026-01-01"},{"id":"raro","amount":2,"tags":{"no":"es lista"}}]""")

        assertEquals(listOf("ok"), repos.expenses.getAll().map { it.id })
        repos.expenses.add(Expense(amount = 3.0, date = "2026-01-02"))

        val saved = AppJson.parseToJsonElement(file("expenses").readText()).jsonArray
        assertEquals(3, saved.size)
        assertTrue(saved.any { it.jsonObject["id"]?.jsonPrimitive?.content == "raro" })
    }

    @Test
    fun unArchivoCorruptoSeTrataComoVacioYSePuedeVolverAEscribir() = runBlocking {
        file("expenses").writeText("esto no es json {{{")
        assertTrue(repos.expenses.getAll().isEmpty())

        repos.expenses.add(Expense(amount = 1.0, date = "2026-01-01"))
        assertEquals(1, repos.expenses.getAll().size)
    }

    @Test
    fun losAltasConcurrentesNoPierdenRegistros() = runBlocking {
        (0 until 50).map { i -> async(Dispatchers.Default) { repos.expenses.add(Expense(amount = i.toDouble(), date = "2026-01-01")) } }.awaitAll()
        assertEquals(50, repos.expenses.getAll().size)
    }

    @Test
    fun actualizarUnIdInexistenteFallaComoEnElOriginal() = runBlocking {
        try {
            repos.expenses.update(Expense(id = "no-existe"))
            fail("update debía lanzar NoSuchElementException")
        } catch (_: NoSuchElementException) {
            // esperado
        }
    }

    @Test
    fun losEnterosSeEscribenSinPuntoDecimalComoJsonStringify() = runBlocking {
        repos.expenses.add(Expense(id = "a", amount = 1500.0, date = "2026-01-01"))
        repos.expenses.add(Expense(id = "b", amount = 12.5, date = "2026-01-01"))
        val text = file("expenses").readText()
        assertTrue(text.contains("\"amount\":1500,"))
        assertFalse(text.contains("1500.0"))
        assertTrue(text.contains("\"amount\":12.5"))
    }

    @Test
    fun addAsignaIdYFechaDeCreacion() = runBlocking {
        val created = repos.expenses.add(Expense(amount = 5.0, date = "2026-01-01"))
        assertTrue(created.id.isNotEmpty())
        assertTrue(created.createdAt!!.endsWith("Z"))
        assertEquals(created, repos.expenses.get(created.id))
    }
}
