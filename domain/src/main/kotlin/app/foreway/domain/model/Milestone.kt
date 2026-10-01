package app.foreway.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One step on the way to a career: a subject choice, an exam, an interview, a medical,
 * a stretch of training.
 *
 * A milestone states facts — "the SSB interview carries 900 marks", "training is three
 * years" — so it is sourced and reviewed exactly like a [Criterion]. Unlike a criterion it
 * can never be a declared gap: a step we cannot source is a step we do not show.
 *
 * Title and detail are content, not app copy. They live in the content store so a wrong
 * sentence can be fixed without a Play Store release (CLAUDE.md, "No OTA").
 */
@Serializable
public data class Milestone(
    val id: String,
    val title: String,
    val detail: String? = null,
    val kind: MilestoneKind,
    val timing: Timing,
    /**
     * Criteria checked at this step, by id. The age window belongs to applying, colour
     * vision to the medical. A criterion attached to no step is still shown — under
     * "also checked" — never dropped.
     */
    val gates: List<String> = emptyList(),
    val review: ReviewState,
    val provenance: Provenance,
    val appliesTo: Applicability = Applicability.Everyone,
    val window: VerificationWindow = VerificationWindow.ANNUAL,
) {
    init {
        require(review != ReviewState.KNOWN_UNSOURCED) {
            "$id: a milestone states facts and cannot be a declared gap"
        }
    }

    public fun isStale(asOf: LocalDate): Boolean = provenance.isStale(asOf, window)
}

@Serializable
public enum class MilestoneKind {
    SUBJECT_CHOICE,
    APPLICATION,
    ENTRANCE_EXAM,
    SELECTION_INTERVIEW,
    MEDICAL,
    TRAINING,
}

/**
 * When a step happens, relative to school — not a calendar date.
 *
 * Dated windows (this cycle's application deadline, this cycle's exam day) are deadlines,
 * a separate record with its own source, because they change every cycle while the shape
 * of the pathway does not.
 */
@Serializable
public sealed interface Timing {

    /** During these school years, inclusive: "Class 11–12". */
    @Serializable
    @SerialName("school_years")
    public data class SchoolYears(val from: SchoolClass, val to: SchoolClass) : Timing {
        init {
            require(from <= to) { "school_years runs backwards: $from to $to" }
        }
    }

    /** Open from this school year onward: "From class 12, while appearing". */
    @Serializable
    @SerialName("from_stage")
    public data class FromStage(val stage: SchoolClass) : Timing

    /** After the previous step, with no fixed time of its own: SSB follows the written exam. */
    @Serializable
    @SerialName("follows")
    public data object Follows : Timing
}
