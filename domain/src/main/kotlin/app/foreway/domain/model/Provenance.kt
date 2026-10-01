package app.foreway.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.Serializable

/**
 * Where a fact came from and when we last checked it.
 *
 * Every eligibility number in this app carries one of these. See CLAUDE.md, "facts come
 * from the database, not the model".
 */
@Serializable
public data class Provenance(
    val sourceUrl: String,
    val authority: SourceAuthority,
    /** First day this rule was in force. */
    val effectiveFrom: LocalDate,
    /** Last day this rule was in force, or null if it still is. */
    val effectiveTo: LocalDate? = null,
    val lastVerifiedAt: LocalDate,
    /** Who checked it. A person or a named automated job, never "system". */
    val verifiedBy: String,
) {
    public fun isInForce(on: LocalDate): Boolean =
        on >= effectiveFrom && (effectiveTo == null || on <= effectiveTo)

    /**
     * True once we can no longer vouch for this fact. A stale criterion is shown flagged,
     * never silently served, and never quietly dropped.
     */
    public fun isStale(asOf: LocalDate, window: VerificationWindow): Boolean =
        lastVerifiedAt.daysUntil(asOf) > window.days

    public companion object {
        /**
         * What the extraction pass writes into [verifiedBy]. It is a signature that means
         * "nobody has signed". The database refuses VERIFIED with this value (constraint
         * verified_requires_a_human), and so does the publish tool.
         */
        public const val UNREVIEWED: String = "automated-extraction-unreviewed"

        /**
         * What a synced record carries in [verifiedBy]. The server withholds the reviewer's
         * name from clients (DESIGN.md shows the source and date, not the person). That a
         * person signed is still guaranteed: the database refuses VERIFIED with [UNREVIEWED].
         */
        public const val WITHHELD: String = "signed-server-side"
    }
}

/**
 * How much weight a source carries. Ordered weakest to strongest so comparisons work:
 * when two records disagree, the higher authority wins.
 */
@Serializable
public enum class SourceAuthority {
    /** A coaching site, news article, or aggregator. Always shown as such. */
    SECONDARY,

    /** A school board circular or university handbook. */
    BOARD_CIRCULAR,

    /** A government press release or ministry page. */
    GOVERNMENT_RELEASE,

    /** The exam notification or service regulation itself. The only real answer. */
    OFFICIAL_NOTIFICATION,
}

/**
 * How long a fact of a given kind stays trustworthy without a re-check.
 *
 * Exam notifications move yearly; physical standards move rarely. Both still expire,
 * because "we checked in 2024" is not a claim we make to a student in 2027.
 */
@Serializable
public enum class VerificationWindow(public val days: Int) {
    /** Registration windows, exam dates, fees. */
    VOLATILE(90),

    /** Age limits, attempt limits, subject prerequisites. */
    ANNUAL(365),

    /** Physical and medical standards, which change on a scale of years. */
    SLOW(730),
}

/**
 * How far a record has got through review.
 *
 * Content is written by people and by extraction jobs; only a person moves a record to
 * [VERIFIED]. Nothing below that threshold is ever shown as a figure to a student.
 */
@Serializable
public enum class ReviewState {
    /** Being authored. Never leaves the repository. */
    DRAFT,

    /**
     * Extracted from a source but not yet checked by a person. Never rendered — an
     * unreviewed number that looks verified is worse than no number at all.
     */
    NEEDS_REVIEW,

    /**
     * We know this gate exists and we do not have a trustworthy figure for it.
     *
     * This is shown to the student, deliberately. Telling someone "there is a height
     * standard here and we cannot yet tell you what it is, check this source" serves them.
     * Hiding the requirement because we lack the number does not.
     */
    KNOWN_UNSOURCED,

    /** A person checked the value against the cited source and signed for it. */
    VERIFIED,
}

/**
 * A value that cannot exist without its receipt.
 *
 * This is deliberate: the type system, not code review, is what stops an unsourced number
 * reaching a student. If you find yourself wanting to unwrap this early, that is the bug.
 */
@Serializable
public data class Sourced<out T>(
    val value: T,
    val provenance: Provenance,
)
