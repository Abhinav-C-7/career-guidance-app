package app.foreway.tools.publish

import app.foreway.domain.model.Applicability
import app.foreway.domain.model.CareerContent
import app.foreway.domain.model.Criterion
import app.foreway.domain.model.LookupPointer
import app.foreway.domain.model.Milestone
import app.foreway.domain.model.Necessity
import app.foreway.domain.model.Requirement
import app.foreway.domain.model.Timing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.serializer

/**
 * Domain objects to database rows, column for column with 0001_content_store.sql.
 *
 * Every column is always present, nulls included. PostgREST bulk upserts require every
 * object to carry the same keys, and an omitted key would leave a stale value in place
 * rather than clearing it.
 */
internal object Rows {

    // Must encode exactly as ContentLoader decodes: same discriminator, no defaults, so
    // the JSONB payload is what the app will read back.
    private val json = Json { encodeDefaults = false }

    val criterionColumns: List<String> = listOf(
        "id", "career_id", "label", "review", "verification_window", "requirement",
        "source_url", "source_authority", "effective_from", "effective_to",
        "last_verified_at", "verified_by", "look_up_at", "applies_to",
    )

    val careerColumns: List<String> = listOf("id", "title", "family", "notes", "parent_id", "summary", "regulated")

    val milestoneColumns: List<String> = listOf(
        "id", "career_id", "position", "review", "verification_window", "body",
        "source_url", "source_authority", "effective_from", "effective_to",
        "last_verified_at", "verified_by", "applies_to",
    )

    fun career(content: CareerContent): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(content.careerId))
        put("title", JsonPrimitive(content.title))
        put("family", JsonPrimitive(content.family.name))
        put("notes", json.encodeToJsonElement(serializer<List<String>>(), content.notes))
        put("parent_id", content.parentId.orNull())
        put("summary", content.summary.orNull())
        put("regulated", JsonPrimitive(content.regulated))
    }

    fun criterion(careerId: String, c: Criterion): JsonObject {
        val sourced = c.requirement
        val p = sourced?.provenance
        return buildJsonObject {
            put("id", JsonPrimitive(c.id))
            put("career_id", JsonPrimitive(careerId))
            put("label", JsonPrimitive(c.label))
            put("review", JsonPrimitive(c.review.name))
            put("verification_window", JsonPrimitive(c.window.name))
            put(
                "requirement",
                sourced?.let { json.encodeToJsonElement(serializer<Requirement>(), it.value) }
                    ?: JsonNull,
            )
            put("source_url", p?.sourceUrl.orNull())
            put("source_authority", p?.authority?.name.orNull())
            put("effective_from", p?.effectiveFrom?.toString().orNull())
            put("effective_to", p?.effectiveTo?.toString().orNull())
            put("last_verified_at", p?.lastVerifiedAt?.toString().orNull())
            put("verified_by", p?.verifiedBy.orNull())
            put(
                "look_up_at",
                c.lookUpAt?.let { json.encodeToJsonElement(LookupPointer.serializer(), it) }
                    ?: JsonNull,
            )
            put("applies_to", json.encodeToJsonElement(Applicability.serializer(), c.appliesTo))
        }
    }

    /** [position] is the milestone's index in the authored list, so file order is pathway order. */
    fun milestone(careerId: String, position: Int, m: Milestone): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(m.id))
        put("career_id", JsonPrimitive(careerId))
        put("position", JsonPrimitive(position))
        put("review", JsonPrimitive(m.review.name))
        put("verification_window", JsonPrimitive(m.window.name))
        put(
            "body",
            buildJsonObject {
                put("title", JsonPrimitive(m.title))
                m.detail?.let { put("detail", JsonPrimitive(it)) }
                put("kind", JsonPrimitive(m.kind.name))
                put("timing", json.encodeToJsonElement(serializer<Timing>(), m.timing))
                put("gates", json.encodeToJsonElement(serializer<List<String>>(), m.gates))
                // Written only when it differs from the default, so a REQUIRED step stays
                // readable by app builds from before necessity existed.
                if (m.necessity != Necessity.REQUIRED) put("necessity", JsonPrimitive(m.necessity.name))
            },
        )
        put("source_url", JsonPrimitive(m.provenance.sourceUrl))
        put("source_authority", JsonPrimitive(m.provenance.authority.name))
        put("effective_from", JsonPrimitive(m.provenance.effectiveFrom.toString()))
        put("effective_to", m.provenance.effectiveTo?.toString().orNull())
        put("last_verified_at", JsonPrimitive(m.provenance.lastVerifiedAt.toString()))
        put("verified_by", JsonPrimitive(m.provenance.verifiedBy))
        put("applies_to", json.encodeToJsonElement(Applicability.serializer(), m.appliesTo))
    }

    private fun String?.orNull(): JsonElement = if (this == null) JsonNull else JsonPrimitive(this)
}
