package app.foreway.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * Rows exactly as the published views return them. Column names and types mirror
 * supabase/migrations/20260925000001_published_views.sql; change both together.
 *
 * Enum-typed columns are kept as strings here on purpose. A value this build does not
 * know (a new career family, a new review state) must reach RowMapper, which can turn it
 * into a visible gap — not fail the whole sync inside the decoder.
 *
 * Timestamps are kept as the exact strings the server sent. They are sync cursors, sent
 * back verbatim in the next query; parsing and re-formatting them risks losing the
 * microseconds Postgres compares on.
 */

@Serializable
internal data class CareerRow(
    val id: String,
    val title: String,
    val family: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
internal data class CriterionRow(
    val id: String,
    @SerialName("career_id") val careerId: String,
    val label: String,
    val review: String,
    @SerialName("verification_window") val verificationWindow: String,
    val requirement: JsonElement? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("source_authority") val sourceAuthority: String? = null,
    @SerialName("effective_from") val effectiveFrom: String? = null,
    @SerialName("effective_to") val effectiveTo: String? = null,
    @SerialName("last_verified_at") val lastVerifiedAt: String? = null,
    @SerialName("look_up_at") val lookUpAt: JsonElement? = null,
    @SerialName("applies_to") val appliesTo: JsonElement? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
internal data class WithdrawalRow(
    @SerialName("criterion_id") val criterionId: String,
    @SerialName("career_id") val careerId: String,
    @SerialName("withdrawn_at") val withdrawnAt: String,
)
