package app.foreway.domain.content

import app.foreway.domain.model.CareerContent
import app.foreway.domain.model.Lineage
import app.foreway.domain.model.Necessity
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Cross-references across all content. Each of these failures is silent everywhere else:
 * a gate naming a missing criterion attaches nothing, a parent that does not exist orphans
 * a specialisation, and a "required" step on an unregulated career tells a student a door
 * is shut when it is not.
 */
class ContentIntegrityTest {

    private val careers: Map<String, CareerContent> =
        File("../content/careers").listFiles { f -> f.extension == "json" }.orEmpty()
            .map { ContentLoader.parse(it.readText()) }
            .associateBy { it.careerId }

    private fun chainOf(id: String) = Lineage.chain(id) { careers[it]?.parentId }

    private fun check(problems: List<String>) {
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    @Test
    fun `every milestone gate names a criterion in the career or one of its ancestors`() = check(
        careers.values.flatMap { career ->
            val visible = chainOf(career.careerId).flatMap { careers[it]?.criteria.orEmpty() }.map { it.id }.toSet()
            career.milestones.flatMap { m ->
                m.gates.filter { it !in visible }.map { "${career.careerId} ${m.id}: gate '$it' is not in its chain" }
            }
        },
    )

    @Test
    fun `ids are unique across all content`() = check(
        careers.values.flatMap { c -> c.criteria.map { it.id } + c.milestones.map { it.id } }
            .groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            .map { "duplicate id '$it'" },
    )

    @Test
    fun `every parent exists and no chain loops`() = check(
        careers.values.mapNotNull { c ->
            val parent = c.parentId ?: return@mapNotNull null
            when {
                parent !in careers -> "${c.careerId}: parent '$parent' does not exist"
                chainOf(c.careerId).first() == c.careerId -> "${c.careerId}: its chain loops back on itself"
                else -> null
            }
        },
    )

    @Test
    fun `a specialisation stays in its parent's family`() = check(
        careers.values.mapNotNull { c ->
            val parent = careers[c.parentId ?: return@mapNotNull null] ?: return@mapNotNull null
            if (parent.family != c.family) "${c.careerId}: family ${c.family} differs from parent ${parent.family}" else null
        },
    )

    @Test
    fun `an unregulated career has only common routes, never rules`() = check(
        careers.values.filter { !it.regulated }.flatMap { c ->
            c.milestones.filter { it.necessity == Necessity.REQUIRED }
                .map { "${c.careerId} ${it.id}: REQUIRED step on an unregulated career" } +
                c.criteria.map { "${c.careerId} ${it.id}: a criterion is a rule, but this career has none" }
        },
    )
}
