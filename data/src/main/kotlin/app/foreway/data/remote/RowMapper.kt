package app.foreway.data.remote

import app.foreway.domain.model.Applicability
import app.foreway.domain.model.Criterion
import app.foreway.domain.model.LookupPointer
import app.foreway.domain.model.Milestone
import app.foreway.domain.model.MilestoneKind
import app.foreway.domain.model.Provenance
import app.foreway.domain.model.Requirement
import app.foreway.domain.model.ReviewState
import app.foreway.domain.model.SourceAuthority
import app.foreway.domain.model.Sourced
import app.foreway.domain.model.Timing
import app.foreway.domain.model.VerificationWindow
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.serializer

/**
 * Server rows to domain criteria.
 *
 * Payloads decode strictly — unknown keys fail. On the client that is the safe direction:
 * if the server adds a field to a requirement (say a new upper bound), an old build that
 * ignored it would evaluate the criterion without the constraint and could tell a student
 * they meet something they do not.
 *
 * A row that fails to decode is never dropped. It becomes a declared gap pointing at its
 * source, marked [LookupPointer.UNREADABLE_BY_THIS_VERSION]. The student still sees that
 * the gate exists; they are told to update rather than shown nothing.
 */
internal object RowMapper {

    private val strict = Json { ignoreUnknownKeys = false }

    fun criterion(row: CriterionRow): Criterion =
        try {
            decode(row)
        } catch (_: Exception) {
            // Covers SerializationException, an unknown enum name, a malformed date, and
            // the Criterion init block refusing an inconsistent row.
            unreadable(row)
        }

    fun isUnreadable(c: Criterion): Boolean =
        c.lookUpAt?.describedAs == LookupPointer.UNREADABLE_BY_THIS_VERSION

    private fun decode(row: CriterionRow): Criterion {
        val review = ReviewState.valueOf(row.review)

        val requirement = row.requirement.present()?.let { payload ->
            Sourced(
                value = strict.decodeFromJsonElement(serializer<Requirement>(), payload),
                provenance = Provenance(
                    sourceUrl = requireNotNull(row.sourceUrl),
                    authority = SourceAuthority.valueOf(requireNotNull(row.sourceAuthority)),
                    effectiveFrom = LocalDate.parse(requireNotNull(row.effectiveFrom)),
                    effectiveTo = row.effectiveTo?.let(LocalDate::parse),
                    lastVerifiedAt = LocalDate.parse(requireNotNull(row.lastVerifiedAt)),
                    verifiedBy = Provenance.WITHHELD,
                ),
            )
        }

        return Criterion(
            id = row.id,
            label = row.label,
            review = review,
            requirement = requirement,
            lookUpAt = row.lookUpAt.present()?.let {
                strict.decodeFromJsonElement(LookupPointer.serializer(), it)
            },
            appliesTo = row.appliesTo.present()?.let {
                strict.decodeFromJsonElement(Applicability.serializer(), it)
            } ?: Applicability.Everyone,
            window = VerificationWindow.valueOf(row.verificationWindow),
        )
    }

    /**
     * Scoped to everyone, deliberately. We could not read who it applies to, and showing a
     * gap to someone it does not concern costs far less than hiding it from someone it does.
     */
    private fun unreadable(row: CriterionRow): Criterion = Criterion(
        id = row.id,
        label = row.label,
        review = ReviewState.KNOWN_UNSOURCED,
        requirement = null,
        lookUpAt = LookupPointer(
            describedAs = LookupPointer.UNREADABLE_BY_THIS_VERSION,
            url = row.sourceUrl,
            citedBy = row.sourceUrl ?: row.id,
        ),
        appliesTo = Applicability.Everyone,
        window = VerificationWindow.VOLATILE,
    )

    /**
     * A step this build cannot read is returned as null, and counted by the caller.
     * Unlike a criterion it cannot become a gap — a milestone is not itself a gate, and
     * every gate it would have carried still arrives as a criterion with its own fallback.
     */
    fun milestone(row: MilestoneRow): Milestone? =
        try {
            val body = strict.decodeFromJsonElement(MilestoneBody.serializer(), row.body)
            Milestone(
                id = row.id,
                title = body.title,
                detail = body.detail,
                kind = body.kind,
                timing = body.timing,
                gates = body.gates,
                review = ReviewState.valueOf(row.review),
                provenance = Provenance(
                    sourceUrl = row.sourceUrl,
                    authority = SourceAuthority.valueOf(row.sourceAuthority),
                    effectiveFrom = LocalDate.parse(row.effectiveFrom),
                    effectiveTo = row.effectiveTo?.let(LocalDate::parse),
                    lastVerifiedAt = LocalDate.parse(row.lastVerifiedAt),
                    verifiedBy = Provenance.WITHHELD,
                ),
                appliesTo = row.appliesTo.present()?.let {
                    strict.decodeFromJsonElement(Applicability.serializer(), it)
                } ?: Applicability.Everyone,
                window = VerificationWindow.valueOf(row.verificationWindow),
            )
        } catch (_: Exception) {
            null
        }

    /** The milestones.body column, as the publish tool writes it. */
    @Serializable
    private data class MilestoneBody(
        val title: String,
        val detail: String? = null,
        val kind: MilestoneKind,
        val timing: Timing,
        val gates: List<String> = emptyList(),
    )

    private fun JsonElement?.present(): JsonElement? = takeUnless { it == null || it is JsonNull }
}
