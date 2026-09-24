package app.foreway.domain.engine

import app.foreway.domain.model.Applicability
import app.foreway.domain.model.BodyMetricKind
import app.foreway.domain.model.ColourVisionGrade
import app.foreway.domain.model.Criterion
import app.foreway.domain.model.Finality
import app.foreway.domain.model.GateOutcome
import app.foreway.domain.model.MeasuredVision
import app.foreway.domain.model.Provenance
import app.foreway.domain.model.Requirement
import app.foreway.domain.model.RiskReason
import app.foreway.domain.model.ReviewState
import app.foreway.domain.model.SchoolClass
import app.foreway.domain.model.SchoolStage
import app.foreway.domain.model.Sex
import app.foreway.domain.model.SourceAuthority
import app.foreway.domain.model.Sourced
import app.foreway.domain.model.StudentProfile
import app.foreway.domain.model.Subject
import app.foreway.domain.model.VerificationWindow
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GateEvaluatorTest {

    private val evaluator = GateEvaluator()
    private val today = LocalDate(2026, 9, 17)

    private fun provenance(
        from: LocalDate = LocalDate(2026, 1, 1),
        to: LocalDate? = null,
        verified: LocalDate = LocalDate(2026, 8, 1),
    ) = Provenance(
        sourceUrl = "https://example.gov.in/notification",
        authority = SourceAuthority.OFFICIAL_NOTIFICATION,
        effectiveFrom = from,
        effectiveTo = to,
        lastVerifiedAt = verified,
        verifiedBy = "test",
    )

    private fun criterion(
        requirement: Requirement,
        appliesTo: Applicability = Applicability.Everyone,
        window: VerificationWindow = VerificationWindow.ANNUAL,
        provenance: Provenance = provenance(),
        review: ReviewState = ReviewState.VERIFIED,
    ) = Criterion(
        id = "c1",
        label = "test criterion",
        review = review,
        requirement = Sourced(requirement, provenance),
        appliesTo = appliesTo,
        window = window,
    )

    // --- The flagship case: a permanent gate, surfaced plainly. ---

    @Test
    fun `colour vision below the standard is a permanent closed door`() {
        val outcome = evaluator.evaluate(
            Requirement.ColourVision(atLeast = ColourVisionGrade.CP1),
            StudentProfile(colourVision = ColourVisionGrade.CP3),
            today,
        )
        val blocked = assertIs<GateOutcome.CannotBeMet>(outcome)
        assertEquals(Finality.PERMANENT, blocked.finality)
    }

    @Test
    fun `colour vision at or above the standard passes`() {
        val outcome = evaluator.evaluate(
            Requirement.ColourVision(atLeast = ColourVisionGrade.CP2),
            StudentProfile(colourVision = ColourVisionGrade.CP1),
            today,
        )
        assertEquals(GateOutcome.Met, outcome)
    }

    // --- Never assert something we were not told. ---

    @Test
    fun `an empty profile yields no closed doors, only unanswered questions`() {
        val criteria = listOf(
            criterion(Requirement.ColourVision(atLeast = ColourVisionGrade.CP1)),
            criterion(Requirement.BodyMetric(BodyMetricKind.HEIGHT_CM, atLeast = 157.0)),
            criterion(Requirement.AttemptLimit(maxAttempts = 2)),
            criterion(Requirement.Nationality(accepted = setOf("IN"))),
        )
        val assessed = evaluator.assess(criteria, StudentProfile(), today)

        assertEquals(4, assessed.size)
        assertTrue(assessed.none { it.outcome is GateOutcome.CannotBeMet })
        assertTrue(assessed.all { it.outcome is GateOutcome.NotYetAssessed })
    }

    // --- Things that can still change must not be reported as final. ---

    @Test
    fun `height below standard is recoverable before the growth plateau`() {
        val outcome = evaluator.evaluate(
            Requirement.BodyMetric(BodyMetricKind.HEIGHT_CM, atLeast = 157.0),
            StudentProfile(
                dateOfBirth = LocalDate(2012, 5, 1), // 14 on the reference date
                bodyMetrics = mapOf(BodyMetricKind.HEIGHT_CM to 150.0),
            ),
            today,
        )
        assertIs<GateOutcome.AtRisk>(outcome)
    }

    @Test
    fun `the same height shortfall is final once growth has plateaued`() {
        val outcome = evaluator.evaluate(
            Requirement.BodyMetric(BodyMetricKind.HEIGHT_CM, atLeast = 157.0),
            StudentProfile(
                dateOfBirth = LocalDate(2006, 5, 1), // 20 on the reference date
                bodyMetrics = mapOf(BodyMetricKind.HEIGHT_CM to 150.0),
            ),
            today,
        )
        val blocked = assertIs<GateOutcome.CannotBeMet>(outcome)
        assertEquals(Finality.PERMANENT, blocked.finality)
    }

    @Test
    fun `the growth plateau age is a policy knob, not a constant`() {
        val profile = StudentProfile(
            dateOfBirth = LocalDate(2010, 5, 1), // 16 on the reference date
            bodyMetrics = mapOf(BodyMetricKind.HEIGHT_CM to 150.0),
        )
        val requirement = Requirement.BodyMetric(BodyMetricKind.HEIGHT_CM, atLeast = 157.0)

        assertIs<GateOutcome.AtRisk>(evaluator.evaluate(requirement, profile, today))
        assertIs<GateOutcome.CannotBeMet>(
            GateEvaluator(EvaluationPolicy(growthPlateauAgeYears = 15))
                .evaluate(requirement, profile, today),
        )
    }

    // --- Date-of-birth windows, stated the way notifications state them. ---

    @Test
    fun `born before the window opens is a closed window, not a permanent block`() {
        val outcome = evaluator.evaluate(
            Requirement.BornBetween(LocalDate(2007, 1, 2), LocalDate(2010, 1, 1)),
            StudentProfile(dateOfBirth = LocalDate(2005, 6, 1)),
            today,
        )
        val blocked = assertIs<GateOutcome.CannotBeMet>(outcome)
        assertEquals(Finality.WINDOW_CLOSED, blocked.finality)
    }

    @Test
    fun `being too young for a cycle is not a failure`() {
        val outcome = evaluator.evaluate(
            Requirement.BornBetween(LocalDate(2007, 1, 2), LocalDate(2010, 1, 1)),
            StudentProfile(dateOfBirth = LocalDate(2013, 6, 1)),
            today,
        )
        assertIs<GateOutcome.AtRisk>(outcome)
    }

    // --- Subject choices: open while ahead of you, closed once behind. ---

    @Test
    fun `a missing subject is recoverable while the choice is still ahead`() {
        val outcome = evaluator.evaluate(
            Requirement.SubjectsTaken(setOf(Subject.BIOLOGY), SchoolStage.CLASS_12),
            StudentProfile(currentClass = SchoolClass.CLASS_9, subjectsTaken = emptySet()),
            today,
        )
        assertIs<GateOutcome.AtRisk>(outcome)
    }

    @Test
    fun `a missing subject is a closed window once school is behind you`() {
        val outcome = evaluator.evaluate(
            Requirement.SubjectsTaken(setOf(Subject.BIOLOGY), SchoolStage.CLASS_12),
            StudentProfile(
                currentClass = SchoolClass.PASSED_12,
                subjectsTaken = setOf(Subject.PHYSICS, Subject.CHEMISTRY, Subject.MATHEMATICS),
            ),
            today,
        )
        val blocked = assertIs<GateOutcome.CannotBeMet>(outcome)
        assertEquals(Finality.WINDOW_CLOSED, blocked.finality)
    }

    // --- Applicability is tri-state, so we never silently drop a gate. ---

    @Test
    fun `a criterion scoped to a sex we were not told is surfaced, not dropped`() {
        val criteria = listOf(
            criterion(
                Requirement.BodyMetric(BodyMetricKind.HEIGHT_CM, atLeast = 157.0),
                appliesTo = Applicability(sex = setOf(Sex.MALE)),
            ),
        )
        val assessed = evaluator.assess(criteria, StudentProfile(), today)

        assertEquals(1, assessed.size)
        assertIs<GateOutcome.NotYetAssessed>(assessed.single().outcome)
    }

    @Test
    fun `a criterion that provably does not apply is dropped`() {
        val criteria = listOf(
            criterion(
                Requirement.BodyMetric(BodyMetricKind.HEIGHT_CM, atLeast = 157.0),
                appliesTo = Applicability(sex = setOf(Sex.MALE)),
            ),
        )
        val assessed = evaluator.assess(criteria, StudentProfile(sex = Sex.FEMALE), today)

        assertTrue(assessed.isEmpty())
    }

    // --- Provenance governs what we are even allowed to apply. ---

    @Test
    fun `a rule no longer in force is not applied`() {
        val criteria = listOf(
            criterion(
                Requirement.Nationality(accepted = setOf("IN")),
                provenance = provenance(
                    from = LocalDate(2020, 1, 1),
                    to = LocalDate(2024, 12, 31),
                ),
            ),
        )
        assertTrue(evaluator.assess(criteria, StudentProfile(nationality = "IN"), today).isEmpty())
    }

    @Test
    fun `a fact past its re-verification window is flagged, not hidden`() {
        val criteria = listOf(
            criterion(
                Requirement.Nationality(accepted = setOf("IN")),
                window = VerificationWindow.VOLATILE,
                provenance = provenance(verified = LocalDate(2025, 1, 1)),
            ),
        )
        val assessed = evaluator.assess(criteria, StudentProfile(nationality = "IN"), today)

        assertEquals(1, assessed.size)
        assertTrue(assessed.single().isStale)
        assertEquals(GateOutcome.Met, assessed.single().outcome)
    }

    @Test
    fun `evaluation does not depend on the system clock`() {
        val requirement = Requirement.BornBetween(LocalDate(2007, 1, 2), LocalDate(2010, 1, 1))
        val profile = StudentProfile(dateOfBirth = LocalDate(2008, 6, 1))

        assertEquals(
            evaluator.evaluate(requirement, profile, LocalDate(2026, 1, 1)),
            evaluator.evaluate(requirement, profile, LocalDate(2030, 1, 1)),
        )
    }

    // --- Physical standards, against figures taken from the Army DGMS manual. ---

    @Test
    fun `an under-18 candidate is credited the growth allowance a medical board would credit`() {
        val requirement = Requirement.BodyMetric(BodyMetricKind.HEIGHT_CM, atLeast = 157.0)
        val profile = StudentProfile(
            dateOfBirth = LocalDate(2010, 5, 1), // 16 on the reference date
            bodyMetrics = mapOf(BodyMetricKind.HEIGHT_CM to 155.0),
        )

        // 155 + 2 clears 157, so this is Met rather than a shortfall to worry them about.
        assertEquals(GateOutcome.Met, evaluator.evaluate(requirement, profile, today))
    }

    @Test
    fun `the growth allowance is not credited once the candidate is past the plateau`() {
        val requirement = Requirement.BodyMetric(BodyMetricKind.HEIGHT_CM, atLeast = 157.0)
        val profile = StudentProfile(
            dateOfBirth = LocalDate(2004, 5, 1), // 22 on the reference date
            bodyMetrics = mapOf(BodyMetricKind.HEIGHT_CM to 155.0),
        )

        assertIs<GateOutcome.CannotBeMet>(evaluator.evaluate(requirement, profile, today))
    }

    @Test
    fun `refractive surgery is a permanent bar where the standard forbids it`() {
        val outcome = evaluator.evaluate(
            ndaVisualStandard,
            StudentProfile(
                distantVision = MeasuredVision(
                    uncorrectedBetterEye = 6,
                    uncorrectedWorseEye = 6,
                    bestCorrectedEachEye = 6,
                    myopiaDSph = 0.0,
                    hadRefractiveSurgery = true,
                ),
            ),
            today,
        )
        val blocked = assertIs<GateOutcome.CannotBeMet>(outcome)
        assertEquals(Finality.PERMANENT, blocked.finality)
    }

    @Test
    fun `good acuity does not pass the standard while the prescription is unmeasured`() {
        val outcome = evaluator.evaluate(
            ndaVisualStandard,
            StudentProfile(
                distantVision = MeasuredVision(uncorrectedBetterEye = 6, uncorrectedWorseEye = 6),
            ),
            today,
        )
        assertIs<GateOutcome.NotYetAssessed>(outcome)
    }

    @Test
    fun `myopia beyond the dioptre ceiling is flagged even when acuity looks fine`() {
        val outcome = evaluator.evaluate(
            ndaVisualStandard,
            StudentProfile(
                distantVision = MeasuredVision(
                    uncorrectedBetterEye = 36,
                    uncorrectedWorseEye = 36,
                    bestCorrectedEachEye = 6,
                    myopiaDSph = -4.0,
                    hadRefractiveSurgery = false,
                ),
            ),
            today,
        )
        assertIs<GateOutcome.AtRisk>(outcome)
    }

    /** The NDA Army standard, per section (m) of the DGMS manual. */
    private val ndaVisualStandard = Requirement.VisualStandard(
        uncorrectedBetterEye = 36,
        uncorrectedWorseEye = 36,
        bestCorrectedEachEye = 6,
        maxMyopiaDSph = -2.5,
        maxHypermetropiaDSph = 2.5,
        refractiveSurgeryPermitted = false,
    )

    // --- Outcomes carry facts, not sentences. ---

    @Test
    fun `a shortfall reports the gap as a number the UI can phrase however it likes`() {
        val outcome = evaluator.evaluate(
            Requirement.BodyMetric(BodyMetricKind.HEIGHT_CM, atLeast = 157.0),
            StudentProfile(
                dateOfBirth = LocalDate(2010, 5, 1), // 16 on the reference date
                bodyMetrics = mapOf(BodyMetricKind.HEIGHT_CM to 150.0),
            ),
            today,
        )

        val risk = assertIs<GateOutcome.AtRisk>(outcome)
        val shortfall = assertIs<RiskReason.BodyMetricShortfall>(risk.reason)

        // 150 measured, plus the 2 cm growth allowance, against a 157 standard.
        assertEquals(5.0, shortfall.shortfallBy)
        assertEquals(BodyMetricKind.HEIGHT_CM, shortfall.metric)
    }
}
