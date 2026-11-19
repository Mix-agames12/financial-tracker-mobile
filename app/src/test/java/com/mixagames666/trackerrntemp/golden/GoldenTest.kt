package com.mixagames666.trackerrntemp.golden

import com.mixagames666.trackerrntemp.data.AppJson
import com.mixagames666.trackerrntemp.util.Time
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.Clock

/**
 * Pruebas diferenciales: cada escenario de src/test/resources/golden se generó ejecutando la lógica
 * TypeScript original (tools/golden). Aquí se repite contra Kotlin y se exige el mismo resultado en cada
 * paso y el mismo estado final de las colecciones.
 */
@RunWith(Parameterized::class)
class GoldenTest(private val file: File) {

    @get:Rule
    val tmp = TemporaryFolder()

    @After
    fun resetClock() {
        Time.nowProvider = { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
        Time.zoneProvider = { TimeZone.currentSystemDefault() }
    }

    @Test
    fun matchesOriginal() = runBlocking {
        val scenario = AppJson.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
        val name = scenario["name"]!!.jsonPrimitive.content

        val runner = GoldenRunner(tmp.newFolder("stores"))
        val output = runner.run(scenario)

        val problems = mutableListOf<String>()
        val steps = scenario["steps"]!!.jsonArray
        steps.forEachIndexed { i, step ->
            val expected = step.jsonObject["result"]
            val diffs = JsonDiff.diff(expected, output.results[i], "paso $i (${step.jsonObject["op"]!!.jsonPrimitive.content})")
            problems.addAll(diffs)
        }
        problems.addAll(JsonDiff.diff(scenario["final"], output.finalState, "final"))

        if (problems.isNotEmpty()) {
            fail("$name: ${problems.size} diferencias con el original\n" + problems.take(40).joinToString("\n"))
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun scenarios(): List<File> {
            val dir = File(GoldenTest::class.java.classLoader!!.getResource("golden")!!.toURI())
            return dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        }
    }
}
