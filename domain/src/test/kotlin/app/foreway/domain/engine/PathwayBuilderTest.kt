package app.foreway.domain.engine

import app.foreway.domain.model.Applicability
import app.foreway.domain.model.Criterion
import app.foreway.domain.model.Milestone
import app.foreway.domain.model.MilestoneKind
import app.foreway.domain.model.Provenance
import app.foreway.domain.model.Requirement
import app.foreway.domain.model.ReviewState
import app.foreway.domain.model.SchoolClass
import app.foreway.domain.model.Sex
import app.foreway.domain.model.SourceAuthority
import app.foreway.domain.model.Sourced
import app.foreway.domain.model.StudentProfile
import app.foreway.domain.model.Timing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class PathwayBuilderTest {

    private val today = LocalDate(2026, 10, 2)

    private val provenance = Provenance(
        sourceUrl = "https://example.gov.in/n.pdf",
        authority = SourceAuthority.OFFICIAL_NOTIFICATION,
        effectiveFrom = LocalDate(2026, 5, 20),
        lastVerifiedAt = LocalDate(2026, 9, 1),
        verifiedBy = "a-reviewer",
    )

    private fun step(
        id: String,
        timing: Timing,
        gates: List<String> = emptyList(),
        review: ReviewState = ReviewState.VERIFIED,
        appliesTo: Applicability = Applicability.Everyone,
    ) = Milestone(id, id, null, MilestoneKind.TRAINING, timing, gates, review, provenance, appliesTo)

    private val milestones = listOf(
        step("pcm", Timing.SchoolYears(SchoolClass.CLASS_11, SchoolClass.CLASS_12), gates = listOf("pcm-gate")),
        step("written", Timing.FromStage(SchoolClass.CLASS_12), gates = listOf("age-gate")),
        step("ssb", Timing.Follows),
    )

    private val criteria = listOf("pcm-gate", "age-gate", "loose-gate").map {
        Criterion(
            id = it,
            label = it,
            review = ReviewState.VERIFIED,
            requirement = Sourced(Requirement.Nationality(setOf("IN")), provenance),
        )
    }

    private fun build(current: SchoolClass?, ms: List<Milestone> = milestones): Pathway {
        val profile = StudentProfile(currentClass = current)
        val assessed = GateEvaluator().assess(criteria, profile, today)
        return PathwayBuilder().build(ms, assessed, profile, today)
    }

    private fun Pathway.positions() = steps.map { it.milestone.id to it.position }

    @Test
    fun `class 10 has everything ahead`() {
        assertEquals(
            listOf("pcm" to Position.AHEAD, "written" to Position.AHEAD, "ssb" to Position.AHEAD),
            build(SchoolClass.CLASS_10).positions(),
        )
    }

    @Test
    fun `class 12 is inside the subject years and the exam is open`() {
        assertEquals(
            listOf("pcm" to Position.NOW, "written" to Position.NOW, "ssb" to Position.AHEAD),
            build(SchoolClass.CLASS_12).positions(),
        )
    }

    @Test
    fun `after school the subject years are past but nothing is claimed done`() {
        val p = build(SchoolClass.PASSED_12)
        assertEquals(Position.PAST, p.steps[0].position)
        // The exam stays open, and the SSB stays ahead: we were never told they sat anything.
        assertEquals(Position.NOW, p.steps[1].position)
        assertEquals(Position.AHEAD, p.steps[2].position)
    }

    @Test
    fun `without a class nothing is placed in time`() {
        assertTrue(build(null).steps.all { it.position == Position.UNPLACED })
    }

    @Test
    fun `gates hang on their step, and unattached gates are kept, not dropped`() {
        val p = build(SchoolClass.CLASS_10)
        assertEquals(listOf("pcm-gate"), p.steps[0].gates.map { it.criterion.id })
        assertEquals(listOf("age-gate"), p.steps[1].gates.map { it.criterion.id })
        assertEquals(listOf("loose-gate"), p.otherGates.map { it.criterion.id })
    }

    @Test
    fun `an unreviewed step is not shown, and its gates fall through to other gates`() {
        val ms = milestones.map { if (it.id == "pcm") it.copy(review = ReviewState.NEEDS_REVIEW) else it }
        val p = build(SchoolClass.CLASS_10, ms)
        assertEquals(listOf("written", "ssb"), p.steps.map { it.milestone.id })
        assertTrue("pcm-gate" in p.otherGates.map { it.criterion.id })
    }

    @Test
    fun `a step scoped to another sex is dropped, an unknown sex keeps it`() {
        val ms = milestones + step("women-only", Timing.Follows, appliesTo = Applicability(sex = setOf(Sex.FEMALE)))
        val male = StudentProfile(currentClass = SchoolClass.CLASS_10, sex = Sex.MALE)
        val unknown = StudentProfile(currentClass = SchoolClass.CLASS_10)
        assertTrue(PathwayBuilder().build(ms, emptyList(), male, today).steps.none { it.milestone.id == "women-only" })
        assertTrue(PathwayBuilder().build(ms, emptyList(), unknown, today).steps.any { it.milestone.id == "women-only" })
    }

    @Test
    fun `a step past its verification window is flagged stale`() {
        val old = step("old", Timing.Follows).copy(provenance = provenance.copy(lastVerifiedAt = LocalDate(2024, 1, 1)))
        assertTrue(build(SchoolClass.CLASS_10, listOf(old)).steps.single().isStale)
    }

    @Test
    fun `a milestone cannot be a declared gap`() {
        assertFailsWith<IllegalArgumentException> {
            step("gap", Timing.Follows, review = ReviewState.KNOWN_UNSOURCED)
        }
    }
}
