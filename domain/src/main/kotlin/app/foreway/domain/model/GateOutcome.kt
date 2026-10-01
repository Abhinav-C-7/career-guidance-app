package app.foreway.domain.model

import kotlinx.datetime.LocalDate

/**
 * The result of holding one criterion against one student. Four states, matching the four
 * in DESIGN.md exactly — if a fifth ever seems necessary, the design changes first.
 *
 * These carry structured facts, never sentences. The rules engine knows a student is 5 cm
 * short; it does not know how to say so, in which language, or how gently. That belongs to
 * the UI.
 *
 * The reason is localisation: a Hindi or Tamil build is impossible if the English is
 * welded into the engine. It is NOT about OTA — string resources ship inside the APK just
 * as compiled strings do, so changing wording still needs a release either way. Delivering
 * copy from the content store would fix that, and this split is the precondition for it.
 */
public sealed interface GateOutcome {

    /** The student satisfies it. Rendered quietly. */
    public data object Met : GateOutcome

    /**
     * We have not been told enough to say. The default, and not a failure —
     * [missing] is what to ask for.
     */
    public data class NotYetAssessed(val missing: MissingInput) : GateOutcome

    /**
     * Currently short, but this can still change. Height before the growth plateau, marks
     * before the exam is sat, a subject choice not yet locked.
     */
    public data class AtRisk(val reason: RiskReason) : GateOutcome

    /**
     * A closed door. This is the product working, not an error — never styled as one.
     * Callers must render it alongside what remains open (DESIGN.md, "Gate states").
     */
    public data class CannotBeMet(
        val reason: BlockReason,
        val finality: Finality,
    ) : GateOutcome
}

public enum class Finality {
    /** Will not change: colour vision, an attempt limit already spent. */
    PERMANENT,

    /** A date passed, or a choice locked. A different route may still exist. */
    WINDOW_CLOSED,
}

/** What we still need from the student, or in one case from ourselves. */
public enum class MissingInput {
    DATE_OF_BIRTH,
    CURRENT_CLASS,
    SUBJECTS_TAKEN,
    BODY_MEASUREMENT,
    EYE_TEST,
    FULL_EYE_TEST,
    COLOUR_VISION_TEST,
    MARKS,
    ATTEMPTS_USED,
    MARITAL_STATUS,
    NATIONALITY,
    HEALTH_DECLARATIONS,
    PRIOR_APPLICATIONS,

    /** We cannot tell whether the criterion even applies without more of the profile. */
    PROFILE_SCOPE,

    /** Not the student's gap but ours: no trustworthy figure exists yet. */
    VERIFIED_FIGURE,
}

/** Why something is currently short, when it can still be recovered. */
public sealed interface RiskReason {
    public data class BodyMetricShortfall(
        val metric: BodyMetricKind,
        val shortfallBy: Double,
    ) : RiskReason

    public data class BodyMetricExcess(
        val metric: BodyMetricKind,
        val excessBy: Double,
    ) : RiskReason

    /** Eligible for a later cycle, not for this one. Not a failure. */
    public data object TooYoungForCycle : RiskReason

    public data class SubjectsStillAhead(val missing: Set<Subject>) : RiskReason

    /** Not taken, but the rule allows passing them later as additional subjects. */
    public data class SubjectsCanStillBeAdded(val missing: Set<Subject>) : RiskReason

    public data class StageStillAhead(val stage: SchoolStage) : RiskReason

    public data class StageNotYetPassed(val stage: SchoolStage) : RiskReason

    public data class MarksTarget(val percentage: Double) : RiskReason

    public data object VisionBelowStandard : RiskReason
}

/** Why a door is closed. */
public sealed interface BlockReason {
    public data class BornBeforeWindow(val earliest: LocalDate) : BlockReason

    public data class BodyMetricFinal(val metric: BodyMetricKind) : BlockReason

    public data class ColourVisionBelow(val required: ColourVisionGrade) : BlockReason

    public data object RefractiveSurgeryNotPermitted : BlockReason

    public data class SubjectsLocked(val missing: Set<Subject>) : BlockReason

    public data class MarksAlreadySat(val required: Double) : BlockReason

    public data class AttemptsExhausted(val max: Int) : BlockReason

    public data object MustBeUnmarried : BlockReason

    public data class NationalityNotAccepted(val accepted: Set<String>) : BlockReason

    public data object MedicalDisqualifier : BlockReason

    public data object PriorDisqualification : BlockReason
}

/**
 * A criterion paired with its verdict and the two ways our own data can let a student down.
 *
 * [isStale] means the fact is past its re-verification window. [sourceGap] means we never
 * had a trustworthy figure in the first place. The UI must render both — a verdict shown
 * without them is a claim we have not earned.
 */
public data class AssessedCriterion(
    val criterion: Criterion,
    val outcome: GateOutcome,
    val isStale: Boolean,
    val sourceGap: Boolean = false,
)
