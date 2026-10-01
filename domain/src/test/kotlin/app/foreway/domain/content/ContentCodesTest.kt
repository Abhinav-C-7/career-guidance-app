package app.foreway.domain.content

import app.foreway.domain.model.BoardCodes
import app.foreway.domain.model.IndianStates
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every board and state code in authored content must be one the app can store. A typo
 * here does not fail loudly anywhere else — the criterion just never applies to anyone.
 */
class ContentCodesTest {

    private val files = File("../content/careers").listFiles { f -> f.extension == "json" }.orEmpty()

    @Test
    fun `content directory is found`() {
        assertTrue(files.isNotEmpty(), "no content files found from ${File(".").absolutePath}")
    }

    @Test
    fun `applicability uses only known board and state codes`() {
        val problems = files.flatMap { file ->
            ContentLoader.parse(file.readText()).criteria.flatMap { c ->
                c.appliesTo.boards.orEmpty().filter { it !in BoardCodes.all }
                    .map { "${file.name} ${c.id}: unknown board '$it'" } +
                    c.appliesTo.states.orEmpty().filter { it !in IndianStates.codes }
                        .map { "${file.name} ${c.id}: unknown state '$it'" }
            }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
