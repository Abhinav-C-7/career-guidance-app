package app.foreway.domain.engine

import app.foreway.domain.model.Applies
import app.foreway.domain.model.AssessedCriterion
import app.foreway.domain.model.BlockReason
import app.foreway.domain.model.BodyMetricKind
import app.foreway.domain.model.Criterion
import app.foreway.domain.model.Finality
import app.foreway.domain.model.GateOutcome
import app.foreway.domain.model.MaritalState
import app.foreway.domain.model.MissingInput
import app.foreway.domain.model.Requirement
import app.foreway.domain.model.ReviewState
import app.foreway.domain.model.RiskReason
import app.foreway.domain.model.SchoolStage
import app.foreway.domain.model.StudentProfile
import kotlinx.datetime.LocalDate

/**
 * Tunables that are biology or policy rather than eligibility rules, injected so that no
 * number is baked into a branch. Supplied from config, overridable in tests.
 */
public data class EvaluationPolicy(
    /**
     * Age past which we stop telling a student their height might still change. Below it a
     * shortfall is [GateOutcome.AtRisk]; at or above it, it is a closed door. Being wrong
     * in the optimistic direction here wastes years, so this is deliberately a knob.
     */
    val growthPlateauAgeYears: Int = 18,

    /**
     * Centimetres credited to a candidate who has not yet reached [growthPlateauAgeYears].
     *
     * Not invented: the Army DGMS manual on medical examination states that "an allowance
     * for growth of 02 cm will be made for candidates below 18 yrs at the time of
     * examination". The 18 above comes from the same sentence.
     *
     * KNOWN LIMITATION: this is one global figure. Services differ, and when a second
     * career needs a different allowance this belongs on the career, not on the policy.
     */
    val growthAllowanceCm: Double = 2.0,
) {
    public companion object {
        public val Default: EvaluationPolicy = EvaluationPolicy()
    }
}

/**
 * Holds criteria against a student profile.
 *
 * Pure and deterministic: [asOf] is always passed in and never read from a system clock,
 * so a pathway generated today can be reproduced and diffed tomorrow.
 *
 * Nothing in this file produces a sentence. Outcomes carry structured facts and the UI
 * decides the wording — see the note on [GateOutcome].
 */
public class GateEvaluator(
    private val policy: EvaluationPolicy = EvaluationPolicy.Default,
) {

    public fun assess(
        criteria: List<Criterion>,
        profile: StudentProfile,
        asOf: LocalDate,
    ): List<AssessedCriterion> = criteria.mapNotNull { criterion ->
        when (criterion.review) {
            // Unreviewed content never reaches a student. A number nobody checked, shown
            // with the same confidence as one that was, is the failure mode this whole
            // architecture exists to prevent.
            ReviewState.DRAFT, ReviewState.NEEDS_REVIEW -> null

            // An openly declared gap. Shown, because a student needs to know the gate
            // exists even when we cannot tell them the figure.
            ReviewState.KNOWN_UNSOURCED -> AssessedCriterion(
                criterion = criterion,
                outcome = GateOutcome.NotYetAssessed(MissingInput.VERIFIED_FIGURE),
                isStale = false,
                sourceGap = true,
            )

            ReviewState.VERIFIED -> {
                val sourced = requireNotNull(criterion.requirement)
                if (!sourced.provenance.isInForce(asOf)) {
                    null
                } else {
                    when (criterion.appliesTo.appliesTo(profile)) {
                        Applies.NO -> null
                        Applies.UNKNOWN -> AssessedCriterion(
                            criterion = criterion,
                            outcome = GateOutcome.NotYetAssessed(MissingInput.PROFILE_SCOPE),
                            isStale = criterion.isStale(asOf),
                        )
                        Applies.YES -> AssessedCriterion(
                            criterion = criterion,
                            outcome = evaluate(sourced.value, profile, asOf),
                            isStale = criterion.isStale(asOf),
                        )
                    }
                }
            }
        }
    }

    public fun evaluate(
        requirement: Requirement,
        profile: StudentProfile,
        asOf: LocalDate,
    ): GateOutcome = when (requirement) {
        is Requirement.BornBetween -> evaluateBornBetween(requirement, profile)
        is Requirement.BodyMetric -> evaluateBodyMetric(requirement, profile, asOf)
        is Requirement.ColourVision -> evaluateColourVision(requirement, profile)
        is Requirement.VisualStandard -> evaluateVisualStandard(requirement, profile)
        is Requirement.CompletedStage -> evaluateCompletedStage(requirement, profile)
        is Requirement.NoPriorDisqualification -> evaluateDisqualification(requirement, profile)
        is Requirement.SubjectsTaken -> evaluateSubjects(requirement, profile)
        is Requirement.MinimumMarks -> evaluateMarks(requirement, profile)
        is Requirement.AttemptLimit -> evaluateAttempts(requirement, profile)
        is Requirement.MaritalStatus -> evaluateMaritalStatus(requirement, profile)
        is Requirement.Nationality -> evaluateNationality(requirement, profile)
        is Requirement.MedicalDisqualifier -> evaluateMedical(requirement, profile)
    }

    private fun evaluateBornBetween(
        requirement: Requirement.BornBetween,
        profile: StudentProfile,
    ): GateOutcome {
        val dob = profile.dateOfBirth
            ?: return GateOutcome.NotYetAssessed(MissingInput.DATE_OF_BIRTH)
        return when {
            dob < requirement.earliest -> GateOutcome.CannotBeMet(
                reason = BlockReason.BornBeforeWindow(requirement.earliest),
                finality = Finality.WINDOW_CLOSED,
            )
            dob > requirement.latest -> GateOutcome.AtRisk(RiskReason.TooYoungForCycle)
            else -> GateOutcome.Met
        }
    }

    private fun evaluateBodyMetric(
        requirement: Requirement.BodyMetric,
        profile: StudentProfile,
        asOf: LocalDate,
    ): GateOutcome {
        val measured = profile.bodyMetrics[requirement.metric]
            ?: return GateOutcome.NotYetAssessed(MissingInput.BODY_MEASUREMENT)

        val stillChanging = when (requirement.metric) {
            BodyMetricKind.HEIGHT_CM ->
                ageInYears(profile, asOf)?.let { it < policy.growthPlateauAgeYears } ?: true
            // Weight and chest respond to training at any age, so never a closed door.
            else -> true
        }

        // A candidate below the plateau age is credited the growth allowance, exactly as
        // the recruiting medical board would credit it.
        val credited = if (requirement.metric == BodyMetricKind.HEIGHT_CM && stillChanging) {
            measured + policy.growthAllowanceCm
        } else {
            measured
        }

        val atLeast = requirement.atLeast
        val atMost = requirement.atMost
        val short = atLeast != null && credited < atLeast
        val over = atMost != null && credited > atMost
        if (!short && !over) return GateOutcome.Met

        if (!stillChanging) {
            return GateOutcome.CannotBeMet(
                reason = BlockReason.BodyMetricFinal(requirement.metric),
                finality = Finality.PERMANENT,
            )
        }

        return GateOutcome.AtRisk(
            when {
                short ->
                    RiskReason.BodyMetricShortfall(requirement.metric, atLeast - credited)
                atMost != null ->
                    RiskReason.BodyMetricExcess(requirement.metric, credited - atMost)
                else ->
                    RiskReason.BodyMetricShortfall(requirement.metric, 0.0)
            },
        )
    }

    private fun evaluateColourVision(
        requirement: Requirement.ColourVision,
        profile: StudentProfile,
    ): GateOutcome {
        val grade = profile.colourVision
            ?: return GateOutcome.NotYetAssessed(MissingInput.COLOUR_VISION_TEST)
        return if (grade >= requirement.atLeast) {
            GateOutcome.Met
        } else {
            GateOutcome.CannotBeMet(
                reason = BlockReason.ColourVisionBelow(requirement.atLeast),
                finality = Finality.PERMANENT,
            )
        }
    }

    private fun evaluateVisualStandard(
        requirement: Requirement.VisualStandard,
        profile: StudentProfile,
    ): GateOutcome {
        val vision = profile.distantVision
            ?: return GateOutcome.NotYetAssessed(MissingInput.EYE_TEST)

        if (!requirement.refractiveSurgeryPermitted && vision.hadRefractiveSurgery == true) {
            return GateOutcome.CannotBeMet(
                reason = BlockReason.RefractiveSurgeryNotPermitted,
                finality = Finality.PERMANENT,
            )
        }

        // Snellen denominators: lower is better.
        val acuityShort = vision.uncorrectedBetterEye > requirement.uncorrectedBetterEye ||
            vision.uncorrectedWorseEye > requirement.uncorrectedWorseEye
        val correctedShort = vision.bestCorrectedEachEye?.let { it > requirement.bestCorrectedEachEye }
        val myopiaOver = overDioptreLimit(vision.myopiaDSph, requirement.maxMyopiaDSph)
        val hypermetropiaOver = overDioptreLimit(vision.hypermetropiaDSph, requirement.maxHypermetropiaDSph)

        // Anything we were not measured for stays a question, not a pass.
        val unmeasured = (requirement.maxMyopiaDSph != null && vision.myopiaDSph == null) ||
            vision.bestCorrectedEachEye == null
        if (!acuityShort && !myopiaOver && !hypermetropiaOver && correctedShort != true && unmeasured) {
            return GateOutcome.NotYetAssessed(MissingInput.FULL_EYE_TEST)
        }

        if (!acuityShort && correctedShort != true && !myopiaOver && !hypermetropiaOver) {
            return GateOutcome.Met
        }

        // Correctable shortfalls are not closed doors: prescriptions change, and a student
        // who learns early can be monitored rather than written off.
        return GateOutcome.AtRisk(RiskReason.VisionBelowStandard)
    }

    private fun overDioptreLimit(measured: Double?, limit: Double?): Boolean {
        if (measured == null || limit == null) return false
        // Limits are written as magnitudes with a sign convention; compare absolute values.
        return kotlin.math.abs(measured) > kotlin.math.abs(limit)
    }

    private fun evaluateCompletedStage(
        requirement: Requirement.CompletedStage,
        profile: StudentProfile,
    ): GateOutcome {
        val current = profile.currentClass?.number
            ?: return GateOutcome.NotYetAssessed(MissingInput.CURRENT_CLASS)
        val endsAt = stageEndsAtClass(requirement.stage)
        return when {
            current > endsAt -> GateOutcome.Met
            current == endsAt && requirement.appearingAccepted -> GateOutcome.Met
            current == endsAt -> GateOutcome.AtRisk(RiskReason.StageNotYetPassed(requirement.stage))
            else -> GateOutcome.AtRisk(RiskReason.StageStillAhead(requirement.stage))
        }
    }

    private fun evaluateDisqualification(
        requirement: Requirement.NoPriorDisqualification,
        profile: StudentProfile,
    ): GateOutcome {
        val declared = profile.declaredDisqualifications
            ?: return GateOutcome.NotYetAssessed(MissingInput.PRIOR_APPLICATIONS)
        return if (requirement.code in declared) {
            GateOutcome.CannotBeMet(
                reason = BlockReason.PriorDisqualification,
                finality = Finality.PERMANENT,
            )
        } else {
            GateOutcome.Met
        }
    }

    private fun evaluateSubjects(
        requirement: Requirement.SubjectsTaken,
        profile: StudentProfile,
    ): GateOutcome {
        val taken = profile.subjectsTaken
            ?: return GateOutcome.NotYetAssessed(MissingInput.SUBJECTS_TAKEN)
        val missing = requirement.subjects - taken
        if (missing.isEmpty()) return GateOutcome.Met
        return if (hasPassed(profile, requirement.atStage)) {
            GateOutcome.CannotBeMet(
                reason = BlockReason.SubjectsLocked(missing),
                finality = Finality.WINDOW_CLOSED,
            )
        } else {
            GateOutcome.AtRisk(RiskReason.SubjectsStillAhead(missing))
        }
    }

    private fun evaluateMarks(
        requirement: Requirement.MinimumMarks,
        profile: StudentProfile,
    ): GateOutcome {
        val achieved = profile.marksByStage[requirement.atStage]
            ?: return GateOutcome.NotYetAssessed(MissingInput.MARKS)
        if (achieved >= requirement.percentage) return GateOutcome.Met
        return if (hasPassed(profile, requirement.atStage)) {
            GateOutcome.CannotBeMet(
                reason = BlockReason.MarksAlreadySat(requirement.percentage),
                finality = Finality.WINDOW_CLOSED,
            )
        } else {
            GateOutcome.AtRisk(RiskReason.MarksTarget(requirement.percentage))
        }
    }

    private fun evaluateAttempts(
        requirement: Requirement.AttemptLimit,
        profile: StudentProfile,
    ): GateOutcome {
        // Keyed by exam elsewhere; here we take the highest recorded count as the guard.
        val used = profile.attemptsUsed.values.maxOrNull()
            ?: return GateOutcome.NotYetAssessed(MissingInput.ATTEMPTS_USED)
        return when {
            used < requirement.maxAttempts -> GateOutcome.Met
            else -> GateOutcome.CannotBeMet(
                reason = BlockReason.AttemptsExhausted(requirement.maxAttempts),
                finality = Finality.PERMANENT,
            )
        }
    }

    private fun evaluateMaritalStatus(
        requirement: Requirement.MaritalStatus,
        profile: StudentProfile,
    ): GateOutcome {
        if (requirement.mustBe == MaritalState.ANY) return GateOutcome.Met
        val state = profile.maritalState
            ?: return GateOutcome.NotYetAssessed(MissingInput.MARITAL_STATUS)
        return if (state == requirement.mustBe) {
            GateOutcome.Met
        } else {
            GateOutcome.CannotBeMet(
                reason = BlockReason.MustBeUnmarried,
                finality = Finality.WINDOW_CLOSED,
            )
        }
    }

    private fun evaluateNationality(
        requirement: Requirement.Nationality,
        profile: StudentProfile,
    ): GateOutcome {
        val nationality = profile.nationality
            ?: return GateOutcome.NotYetAssessed(MissingInput.NATIONALITY)
        return if (nationality in requirement.accepted) {
            GateOutcome.Met
        } else {
            GateOutcome.CannotBeMet(
                reason = BlockReason.NationalityNotAccepted(requirement.accepted),
                finality = Finality.PERMANENT,
            )
        }
    }

    private fun evaluateMedical(
        requirement: Requirement.MedicalDisqualifier,
        profile: StudentProfile,
    ): GateOutcome {
        val declared = profile.declaredConditions
            ?: return GateOutcome.NotYetAssessed(MissingInput.HEALTH_DECLARATIONS)
        return if (requirement.conditionCode in declared) {
            GateOutcome.CannotBeMet(
                reason = BlockReason.MedicalDisqualifier,
                finality = Finality.PERMANENT,
            )
        } else {
            GateOutcome.Met
        }
    }

    private fun hasPassed(profile: StudentProfile, stage: SchoolStage): Boolean {
        val current = profile.currentClass?.number ?: return false
        return current > stageEndsAtClass(stage)
    }

    private fun stageEndsAtClass(stage: SchoolStage): Int = when (stage) {
        SchoolStage.CLASS_10 -> 10
        SchoolStage.CLASS_12 -> 12
        SchoolStage.UNDERGRADUATE -> 13
    }

    private fun ageInYears(profile: StudentProfile, asOf: LocalDate): Int? {
        val dob = profile.dateOfBirth ?: return null
        var years = asOf.year - dob.year
        val hadBirthday = asOf.monthNumber > dob.monthNumber ||
            (asOf.monthNumber == dob.monthNumber && asOf.dayOfMonth >= dob.dayOfMonth)
        if (!hadBirthday) years -= 1
        return years
    }
}

private fun Criterion.isStale(asOf: LocalDate): Boolean =
    requirement?.provenance?.isStale(asOf, window) == true
