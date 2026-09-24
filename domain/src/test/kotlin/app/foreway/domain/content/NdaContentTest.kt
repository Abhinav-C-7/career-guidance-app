package app.foreway.domain.content

import app.foreway.domain.engine.GateEvaluator
import app.foreway.domain.model.CareerFamily
import app.foreway.domain.model.ReviewState
import app.foreway.domain.model.StudentProfile
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs against the real authored file in content/, not a fixture. If a content edit breaks
 * an invariant, this fails in CI before the file reaches a student.
 */
class NdaContentTest {

    private val content = ContentLoader.parse(
        File("../content/careers/nda-officer-entry.json").readText(),
    )

    private val evaluator = GateEvaluator()
    private val today = LocalDate(2026, 9, 17)

    @Test
    fun `the authored NDA file parses`() {
        assertEquals("nda-officer-entry", content.careerId)
        assertEquals(CareerFamily.DEFENCE, content.family)
        assertTrue(content.criteria.isNotEmpty())
    }

    @Test
    fun `every criterion carries either a source or a declared gap`() {
        content.criteria.forEach { criterion ->
            when (criterion.review) {
                ReviewState.KNOWN_UNSOURCED -> {
                    val pointer = assertNotNull(criterion.lookUpAt, criterion.id)
                    assertTrue(pointer.citedBy.isNotBlank(), "${criterion.id} gap is itself unsourced")
                }
                else -> {
                    val sourced = assertNotNull(criterion.requirement, criterion.id)
                    assertTrue(sourced.provenance.sourceUrl.isNotBlank(), criterion.id)
                    assertTrue(sourced.provenance.verifiedBy.isNotBlank(), criterion.id)
                }
            }
        }
    }

    /**
     * The whole file was extracted automatically. Until a person signs for each value,
     * a student must see none of the figures.
     */
    @Test
    fun `nothing in this file is verified yet, so no figure reaches a student`() {
        assertTrue(
            content.criteria.none { it.review == ReviewState.VERIFIED },
            "A criterion is marked VERIFIED — a person must sign for that, not an extraction job",
        )

        val assessed = evaluator.assess(content.criteria, StudentProfile(), today)

        assertEquals(1, assessed.size, "Only the declared gap should surface")
        assertTrue(assessed.single().sourceGap)
        assertEquals("nda-physical-standards", assessed.single().criterion.id)
    }

    /**
     * The physical standards are the reason this app exists, and we cannot state them.
     * Surfacing the gate with a pointer is the honest behaviour; hiding it is not.
     */
    @Test
    fun `the physical standards gate is surfaced even though we have no figures`() {
        val physical = content.criteria.single { it.id == "nda-physical-standards" }

        assertEquals(ReviewState.KNOWN_UNSOURCED, physical.review)
        val pointer = assertNotNull(physical.lookUpAt)
        assertTrue(pointer.describedAs.contains("Joint Order Manual"))
        assertTrue(pointer.citedBy.contains("upsc.gov.in"))
    }

    /**
     * NDA-II 2026 sat on 13 September 2026. Its date-of-birth window is cycle specific and
     * expired four days before this reference date, so it must not be applied to anyone.
     */
    @Test
    fun `an expired cycle's date of birth window is out of force`() {
        val dobWindow = content.criteria.single { it.id == "nda-dob-window-ii-2026" }
        val provenance = assertNotNull(dobWindow.requirement).provenance

        assertTrue(provenance.isInForce(LocalDate(2026, 6, 1)))
        assertTrue(!provenance.isInForce(today))
    }

    /**
     * Guards the note in the content file. Coaching sites widely assert an NDA attempt
     * limit; the notification imposes none. Anyone adding one must come with a source.
     */
    @Test
    fun `no attempt limit is asserted for NDA`() {
        assertTrue(
            content.notes.any { it.contains("attempt", ignoreCase = true) },
            "The deliberate absence of an attempt limit must stay documented",
        )
    }
}
