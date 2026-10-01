package app.foreway.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The closed set of things a career can require of a student.
 *
 * The *shape* of a requirement lives here in code; the *numbers* live in the content
 * store. Adding a new kind of requirement needs a Play Store release. Changing any height,
 * age, mark or date does not. Given we have no OTA channel (CLAUDE.md, "No OTA"), keep
 * that line where it is: never encode a specific threshold as a branch in this file.
 */
@Serializable
public sealed interface Requirement {

    /**
     * A date-of-birth window, not an age. Indian exam notifications almost always express
     * this as "born not earlier than X and not later than Y" relative to a course start,
     * and restating it as "age 16 to 19" quietly loses months at both ends.
     */
    @Serializable
    @SerialName("born_between")
    public data class BornBetween(
        val earliest: LocalDate,
        val latest: LocalDate,
    ) : Requirement

    /**
     * A minimum age with no upper limit, written as a latest birth date for one exam cycle.
     * NEET (UG) is the case: 17 by 31 December of the exam year, and no upper age limit.
     * A BornBetween with an invented earliest date would put a number in front of a student
     * that no source states.
     */
    @Serializable
    @SerialName("born_on_or_before")
    public data class BornOnOrBefore(
        val latest: LocalDate,
    ) : Requirement

    @Serializable
    @SerialName("body_metric")
    public data class BodyMetric(
        val metric: BodyMetricKind,
        val atLeast: Double? = null,
        val atMost: Double? = null,
    ) : Requirement {
        init {
            require(atLeast != null || atMost != null) {
                "A body metric requirement with neither bound is not a requirement"
            }
        }
    }

    /**
     * A visual standard as the services actually write one.
     *
     * An earlier version of this modelled a single acuity threshold. Real standards do not
     * work that way: the NDA standard sets an uncorrected acuity, a separate best-corrected
     * acuity, dioptre ceilings for myopia and hypermetropia, and a rule on refractive
     * surgery — and a candidate must satisfy all of them. A model that stores only one
     * number would silently pass someone the standard rejects.
     *
     * Acuities are Snellen denominators: 6 means 6/6, and lower is better.
     */
    @Serializable
    @SerialName("visual_standard")
    public data class VisualStandard(
        val uncorrectedBetterEye: Int,
        val uncorrectedWorseEye: Int,
        val bestCorrectedEachEye: Int,
        val maxMyopiaDSph: Double? = null,
        val maxHypermetropiaDSph: Double? = null,
        val refractiveSurgeryPermitted: Boolean,
    ) : Requirement

    @Serializable
    @SerialName("colour_vision")
    public data class ColourVision(
        val atLeast: ColourVisionGrade,
    ) : Requirement

    /**
     * A stage of education that must be completed.
     *
     * [appearingAccepted] matters more than it looks: most Indian entrance exams let a
     * student apply while still sitting the qualifying exam, with proof due by a later
     * date. Modelling this as a plain "must have passed" would wrongly tell a class 12
     * student they are ineligible for the exam they are entitled to sit.
     */
    @Serializable
    @SerialName("completed_stage")
    public data class CompletedStage(
        val stage: SchoolStage,
        val appearingAccepted: Boolean,
    ) : Requirement

    /**
     * An administrative bar rather than a medical one — debarment by a ministry, or having
     * resigned from a training academy on disciplinary grounds. Students almost never
     * think to check these and they are absolute.
     */
    @Serializable
    @SerialName("no_prior_disqualification")
    public data class NoPriorDisqualification(
        val code: String,
    ) : Requirement

    /** Subjects that must be taken, at a given stage of school. */
    @Serializable
    @SerialName("subjects_taken")
    public data class SubjectsTaken(
        val subjects: Set<Subject>,
        val atStage: SchoolStage,
        /**
         * True when the rule lets missing subjects be studied after the stage — NMC's
         * NEP-era position for NEET (UG) is that Physics, Chemistry, Biology and English may
         * be passed as additional subjects even after class 12. Then a missing subject is a
         * risk, never a closed door.
         */
        val addableAfterwards: Boolean = false,
    ) : Requirement

    @Serializable
    @SerialName("minimum_marks")
    public data class MinimumMarks(
        val percentage: Double,
        val scope: MarksScope,
        val atStage: SchoolStage,
    ) : Requirement

    @Serializable
    @SerialName("attempt_limit")
    public data class AttemptLimit(
        val maxAttempts: Int,
    ) : Requirement

    /**
     * Several services still require candidates to be unmarried, and some bar marriage
     * during training. Students discover this late and it is rarely listed prominently.
     */
    @Serializable
    @SerialName("marital_status")
    public data class MaritalStatus(
        val mustBe: MaritalState,
    ) : Requirement

    @Serializable
    @SerialName("nationality")
    public data class Nationality(
        val accepted: Set<String>,
    ) : Requirement

    /**
     * Named conditions that disqualify — knock knees and flat feet for several services,
     * for instance. Stored as codes so the display copy stays in the content store.
     */
    @Serializable
    @SerialName("medical_disqualifier")
    public data class MedicalDisqualifier(
        val conditionCode: String,
    ) : Requirement
}

@Serializable
public enum class BodyMetricKind { HEIGHT_CM, WEIGHT_KG, CHEST_EXPANDED_CM, CHEST_EXPANSION_CM }

/** Ordered worst to best, so [ColourVisionGrade] comparisons mean what they read like. */
@Serializable
public enum class ColourVisionGrade { CP3, CP2, CP1 }

@Serializable
public enum class Subject { PHYSICS, CHEMISTRY, BIOLOGY, MATHEMATICS, ENGLISH, COMPUTER_SCIENCE, ACCOUNTANCY, ECONOMICS }

@Serializable
public enum class SchoolStage { CLASS_10, CLASS_12, UNDERGRADUATE }

@Serializable
public enum class MarksScope { AGGREGATE, EACH_SUBJECT, PCB_AGGREGATE, PCM_AGGREGATE }

@Serializable
public enum class MaritalState { UNMARRIED, ANY }

@Serializable
public enum class Sex { MALE, FEMALE }

/**
 * Narrows who a criterion applies to. A null field means "everyone".
 *
 * This exists so that sex-specific height standards, state domicile quotas and
 * board-specific stream rules are all one mechanism driven by data, rather than three
 * special cases branching in Kotlin.
 */
@Serializable
public data class Applicability(
    val sex: Set<Sex>? = null,
    val states: Set<String>? = null,
    val boards: Set<String>? = null,
) {
    /**
     * Tri-state on purpose. If we have not been told the student's sex, a sex-scoped
     * height standard is [Applies.UNKNOWN], never [Applies.NO] — silently dropping a
     * criterion we cannot scope would hide a gate, which is the one thing principle 4
     * forbids.
     */
    public fun appliesTo(profile: StudentProfile): Applies {
        var anyUnknown = false
        sex?.let {
            when {
                profile.sex == null -> anyUnknown = true
                profile.sex !in it -> return Applies.NO
            }
        }
        states?.let {
            when {
                profile.state == null -> anyUnknown = true
                profile.state !in it -> return Applies.NO
            }
        }
        boards?.let {
            when {
                profile.board == null -> anyUnknown = true
                profile.board !in it -> return Applies.NO
            }
        }
        return if (anyUnknown) Applies.UNKNOWN else Applies.YES
    }

    public companion object {
        public val Everyone: Applicability = Applicability()
    }
}

public enum class Applies { YES, NO, UNKNOWN }

/**
 * Where an authoritative figure lives when we cannot state it ourselves.
 *
 * [citedBy] is the source that told us to look there, so even an admission of ignorance
 * carries a receipt.
 */
@Serializable
public data class LookupPointer(
    val describedAs: String,
    val url: String? = null,
    val citedBy: String,
) {
    public companion object {
        /**
         * [describedAs] for a record this build of the app cannot read — a requirement kind
         * or applicability dimension added after it shipped. The criterion is shown as a
         * gap pointing at its source, never dropped: hiding a gate because the app is old
         * would break principle 4. The UI renders this as "update the app".
         */
        public const val UNREADABLE_BY_THIS_VERSION: String = "unreadable-by-this-version"
    }
}

/**
 * One requirement, sourced, scoped, and given a stability class for re-verification.
 *
 * The [review] state and [requirement] are tied together by the init block: a criterion is
 * either something we can state and stand behind, or an openly declared gap with a pointer
 * to where the real answer lives. There is no third option where we quietly show a number
 * nobody checked.
 */
@Serializable
public data class Criterion(
    val id: String,
    val label: String,
    val review: ReviewState,
    val requirement: Sourced<Requirement>? = null,
    val lookUpAt: LookupPointer? = null,
    val appliesTo: Applicability = Applicability.Everyone,
    val window: VerificationWindow = VerificationWindow.ANNUAL,
) {
    init {
        when (review) {
            ReviewState.KNOWN_UNSOURCED -> {
                require(requirement == null) {
                    "$id is marked KNOWN_UNSOURCED but carries a requirement value"
                }
                require(lookUpAt != null) {
                    "$id admits a gap but does not say where the real figure lives"
                }
            }
            else -> require(requirement != null) {
                "$id has review state $review but no requirement"
            }
        }
    }
}
