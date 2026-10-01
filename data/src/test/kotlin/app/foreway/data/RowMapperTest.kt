package app.foreway.data

import app.foreway.data.remote.CriterionRow
import app.foreway.data.remote.RowMapper
import app.foreway.domain.model.Applicability
import app.foreway.domain.model.LookupPointer
import app.foreway.domain.model.Provenance
import app.foreway.domain.model.Requirement
import app.foreway.domain.model.ReviewState
import app.foreway.domain.model.Sex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class RowMapperTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private fun verified(
        requirement: String = """{"type":"body_metric","metric":"HEIGHT_CM","atLeast":157.0}""",
        appliesTo: String = """{"sex":["MALE"]}""",
    ) = CriterionRow(
        id = "h",
        careerId = "c",
        label = "Height",
        review = "VERIFIED",
        verificationWindow = "SLOW",
        requirement = json(requirement),
        sourceUrl = "https://example.gov.in/n.pdf",
        sourceAuthority = "OFFICIAL_NOTIFICATION",
        effectiveFrom = "2026-05-20",
        lastVerifiedAt = "2026-09-20",
        appliesTo = json(appliesTo),
        updatedAt = "2026-09-25T10:00:00.123456+00:00",
    )

    @Test
    fun `a verified row becomes a sourced criterion`() {
        val c = RowMapper.criterion(verified())
        assertEquals(ReviewState.VERIFIED, c.review)
        assertEquals(157.0, (c.requirement!!.value as Requirement.BodyMetric).atLeast)
        assertEquals(Applicability(sex = setOf(Sex.MALE)), c.appliesTo)
        assertFalse(RowMapper.isUnreadable(c))
    }

    @Test
    fun `the signer is withheld, never invented`() {
        assertEquals(Provenance.WITHHELD, RowMapper.criterion(verified()).requirement!!.provenance.verifiedBy)
    }

    @Test
    fun `a declared gap keeps its pointer and has no value`() {
        val c = RowMapper.criterion(
            CriterionRow(
                id = "g",
                careerId = "c",
                label = "Medical standards",
                review = "KNOWN_UNSOURCED",
                verificationWindow = "SLOW",
                lookUpAt = json("""{"describedAs":"Joint Order Manual","citedBy":"https://x.gov.in"}"""),
                updatedAt = "2026-09-25T10:00:00+00:00",
            ),
        )
        assertNull(c.requirement)
        assertEquals("Joint Order Manual", c.lookUpAt!!.describedAs)
    }

    @Test
    fun `a requirement kind added after this build shipped becomes a visible gap`() {
        val c = RowMapper.criterion(verified(requirement = """{"type":"swimming_test","metres":100}"""))
        assertTrue(RowMapper.isUnreadable(c))
        assertEquals(ReviewState.KNOWN_UNSOURCED, c.review)
        assertEquals("Height", c.label)
        assertEquals("https://example.gov.in/n.pdf", c.lookUpAt!!.url)
    }

    @Test
    fun `a new field on a known requirement is not silently ignored`() {
        // An old build that dropped "atMostAge" would evaluate without the bound.
        val c = RowMapper.criterion(
            verified(requirement = """{"type":"body_metric","metric":"HEIGHT_CM","atLeast":157.0,"atMostAge":21}"""),
        )
        assertTrue(RowMapper.isUnreadable(c))
    }

    @Test
    fun `an unknown scoping dimension makes the row a gap shown to everyone`() {
        val c = RowMapper.criterion(verified(appliesTo = """{"communities":["GORKHA"]}"""))
        assertTrue(RowMapper.isUnreadable(c))
        assertEquals(Applicability.Everyone, c.appliesTo)
    }

    @Test
    fun `an unknown review state does not crash the mapper`() {
        val c = RowMapper.criterion(verified().copy(review = "PROVISIONAL"))
        assertEquals(LookupPointer.UNREADABLE_BY_THIS_VERSION, c.lookUpAt!!.describedAs)
    }
}
