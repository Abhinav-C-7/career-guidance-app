package app.foreway.domain.content

import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Cross-references inside each content file. A milestone gate naming a criterion that does
 * not exist would silently attach nothing — the medical step would show no standards, and
 * nothing would say why.
 */
class ContentIntegrityTest {

    private val careers = File("../content/careers").listFiles { f -> f.extension == "json" }.orEmpty()
        .map { it.name to ContentLoader.parse(it.readText()) }

    @Test
    fun `every milestone gate names a criterion in the same career`() {
        val problems = careers.flatMap { (file, career) ->
            val ids = career.criteria.map { it.id }.toSet()
            career.milestones.flatMap { m ->
                m.gates.filter { it !in ids }.map { "$file ${m.id}: gate '$it' is not a criterion here" }
            }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    @Test
    fun `ids are unique within a career`() {
        val problems = careers.flatMap { (file, career) ->
            (career.criteria.map { it.id } + career.milestones.map { it.id })
                .groupingBy { it }.eachCount().filterValues { it > 1 }.keys
                .map { "$file: duplicate id '$it'" }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
