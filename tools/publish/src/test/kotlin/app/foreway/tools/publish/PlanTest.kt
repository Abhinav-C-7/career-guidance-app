package app.foreway.tools.publish

import app.foreway.domain.content.ContentLoader
import app.foreway.domain.model.CareerContent
import app.foreway.domain.model.ReviewState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class PlanTest {

    private val nda: CareerContent =
        ContentLoader.parse(File("../../content/careers/nda-officer-entry.json").readText())

    private fun rowsOf(content: CareerContent) = content.criteria.map { Rows.criterion(content.careerId, it) }

    @Test
    fun `every row carries every column, so bulk upserts can clear a value`() {
        rowsOf(nda).forEach { assertEquals(Rows.criterionColumns.toSet(), it.keys) }
    }

    @Test
    fun `a declared gap is written with no requirement and a pointer`() {
        val gap = rowsOf(nda).single { it["id"] == JsonPrimitive("nda-physical-standards") }
        assertEquals(JsonNull, gap["requirement"])
        assertTrue(gap["look_up_at"] is JsonObject)
    }

    @Test
    fun `requirement payload keeps the discriminator the app decodes on`() {
        val height = rowsOf(nda).single { it["id"] == JsonPrimitive("nda-height-male-cadet") }
        assertEquals(JsonPrimitive("body_metric"), (height["requirement"] as JsonObject)["type"])
    }

    @Test
    fun `first publish of NDA exposes only the declared gap`() {
        val plan = plan(listOf(nda), emptyList(), emptyList())
        assertEquals(nda.criteria.size, plan.criteria.upserts.size)
        assertEquals(listOf("nda-physical-standards"), plan.criteria.becomingVisible)
    }

    private fun stepsOf(content: CareerContent) =
        content.milestones.mapIndexed { i, m -> Rows.milestone(content.careerId, i + 1, m) }

    @Test
    fun `republishing unchanged content writes nothing`() {
        val plan = plan(listOf(nda), listOf(Rows.career(nda)), rowsOf(nda), stepsOf(nda))
        assertTrue(plan.isEmpty, "a no-op publish would force every phone to resync")
    }

    @Test
    fun `a demotion is written and reported as leaving student view`() {
        val server = rowsOf(nda).map {
            if (it["id"] == JsonPrimitive("nda-nationality")) {
                JsonObject(it + ("review" to JsonPrimitive("VERIFIED")) + ("verified_by" to JsonPrimitive("a-reviewer")))
            } else {
                it
            }
        }
        val plan = plan(listOf(nda), listOf(Rows.career(nda)), server)
        assertEquals(listOf("nda-nationality"), plan.criteria.upserts.map { (it["id"] as JsonPrimitive).content })
        assertEquals(listOf("nda-nationality"), plan.criteria.leavingVisible)
    }

    @Test
    fun `a criterion removed from the repo is planned for deletion`() {
        val orphan = JsonObject(rowsOf(nda).first() + ("id" to JsonPrimitive("nda-retired-rule")))
        val plan = plan(listOf(nda), listOf(Rows.career(nda)), rowsOf(nda) + orphan)
        assertEquals(listOf("nda-retired-rule"), plan.criteria.deletions)
    }

    @Test
    fun `a machine cannot sign for a VERIFIED value`() {
        val selfSigned = nda.copy(
            criteria = nda.criteria.map {
                if (it.id == "nda-nationality") it.copy(review = ReviewState.VERIFIED) else it
            },
        )
        assertFailsWith<ContentRejected> { validate(mapOf("nda-officer-entry.json" to selfSigned)) }
    }

    @Test
    fun `file name must match careerId`() {
        assertFailsWith<ContentRejected> { validate(mapOf("nda.json" to nda)) }
    }

    @Test
    fun `milestones are written in file order and none is visible until reviewed`() {
        val plan = plan(listOf(nda), emptyList(), emptyList())
        val positions = plan.milestones.upserts.map { (it["position"] as JsonPrimitive).content.toInt() }
        assertEquals((1..nda.milestones.size).toList(), positions)
        assertTrue(plan.milestones.becomingVisible.isEmpty(), "NEEDS_REVIEW steps must not publish")
    }

    @Test
    fun `milestone body carries the timing discriminator the app decodes on`() {
        val body = stepsOf(nda).first()["body"] as JsonObject
        assertEquals(JsonPrimitive("school_years"), (body["timing"] as JsonObject)["type"])
    }

    @Test
    fun `a machine cannot sign a VERIFIED milestone either`() {
        val selfSigned = nda.copy(
            milestones = nda.milestones.mapIndexed { i, m -> if (i == 0) m.copy(review = ReviewState.VERIFIED) else m },
        )
        assertFailsWith<ContentRejected> { validate(mapOf("nda-officer-entry.json" to selfSigned)) }
    }
}
