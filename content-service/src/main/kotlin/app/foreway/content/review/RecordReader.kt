package app.foreway.content.review

import app.foreway.domain.model.Applicability
import app.foreway.domain.model.MilestoneKind
import app.foreway.domain.model.Necessity
import app.foreway.domain.model.Requirement
import app.foreway.domain.model.Timing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

/**
 * Reads stored JSON with the app's own model, strictly, exactly as a phone will.
 *
 * This is what lets the review screen promise "approve means students see this": a payload
 * the app's decoder rejects is reported as unreadable, and ReviewPolicy refuses to approve
 * it.
 */
object RecordReader {

    private val strict = Json { ignoreUnknownKeys = false }
    private val pretty = Json { prettyPrint = true }

    sealed interface Read<out T> {
        data class Ok<T>(val value: T) : Read<T>
        data class Unreadable(val why: String) : Read<Nothing>
    }

    fun requirement(json: String?): Read<Requirement> = attempt {
        strict.decodeFromString(serializer<Requirement>(), requireNotNull(json) { "no requirement" })
    }

    fun applicability(json: String?): Read<Applicability> = attempt {
        if (json == null) Applicability.Everyone else strict.decodeFromString(Applicability.serializer(), json)
    }

    data class StepBody(
        val title: String,
        val detail: String?,
        val kind: MilestoneKind,
        val timing: Timing,
        val gates: List<String>,
        val necessity: Necessity,
    )

    private val stepKeys = setOf("title", "detail", "kind", "timing", "gates", "necessity")

    /** Mirrors the app's private MilestoneBody, including its refusal of unknown keys. */
    fun stepBody(json: String): Read<StepBody> = attempt {
        val o = strict.parseToJsonElement(json).jsonObject
        val unknown = o.keys - stepKeys
        require(unknown.isEmpty()) { "unknown keys $unknown" }
        StepBody(
            title = o.getValue("title").jsonPrimitive.content,
            detail = o["detail"]?.jsonPrimitive?.content,
            kind = MilestoneKind.valueOf(o.getValue("kind").jsonPrimitive.content),
            timing = strict.decodeFromJsonElement(serializer<Timing>(), o.getValue("timing")),
            gates = o["gates"]?.let { strict.decodeFromJsonElement(serializer<List<String>>(), it) }.orEmpty(),
            necessity = o["necessity"]?.let { Necessity.valueOf(it.jsonPrimitive.content) } ?: Necessity.REQUIRED,
        )
    }

    /** For display: the exact JSON that goes to students, indented. */
    fun pretty(json: String?): String? =
        json?.let { runCatching { pretty.encodeToString(JsonObject.serializer(), strict.parseToJsonElement(it).jsonObject) }.getOrDefault(it) }

    private fun <T> attempt(block: () -> T): Read<T> =
        try {
            Read.Ok(block())
        } catch (e: Exception) {
            Read.Unreadable(e.message?.take(300) ?: e::class.simpleName.orEmpty())
        }
}
