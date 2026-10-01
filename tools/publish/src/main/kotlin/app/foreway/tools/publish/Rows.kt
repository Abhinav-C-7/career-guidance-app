package app.foreway.tools.publish

import app.foreway.domain.model.Applicability
import app.foreway.domain.model.CareerContent
import app.foreway.domain.model.Criterion
import app.foreway.domain.model.LookupPointer
import app.foreway.domain.model.Requirement
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

    val careerColumns: List<String> = listOf("id", "title", "family", "notes")

    fun career(content: CareerContent): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(content.careerId))
        put("title", JsonPrimitive(content.title))
        put("family", JsonPrimitive(content.family.name))
        put("notes", json.encodeToJsonElement(serializer<List<String>>(), content.notes))
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

    private fun String?.orNull(): JsonElement = if (this == null) JsonNull else JsonPrimitive(this)
}
