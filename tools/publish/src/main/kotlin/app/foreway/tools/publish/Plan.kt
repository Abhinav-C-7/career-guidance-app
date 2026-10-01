package app.foreway.tools.publish

import app.foreway.domain.model.CareerContent
import app.foreway.domain.model.Provenance
import app.foreway.domain.model.ReviewState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What a publish would change, computed before anything is written.
 *
 * Every review state is written, not just the published ones. The database is where a
 * demotion has to land — a criterion that goes VERIFIED -> NEEDS_REVIEW in the repo must
 * go the same way on the server, or phones keep the retracted figure. Unreviewed rows are
 * harmless there: RLS and published_criteria never return them.
 *
 * Unchanged rows are not sent. Each write bumps updated_at, and every phone re-downloads
 * whatever has a newer updated_at, so a no-op republish would cost every student a sync.
 */
internal data class Plan(
    val careerUpserts: List<JsonObject>,
    val criterionUpserts: List<JsonObject>,
    val deletions: List<String>,
    /** Criteria a student will start seeing after this publish. The line to read carefully. */
    val becomingVisible: List<String>,
    val leavingVisible: List<String>,
) {
    val isEmpty: Boolean
        get() = careerUpserts.isEmpty() && criterionUpserts.isEmpty() && deletions.isEmpty()
}

internal class ContentRejected(message: String) : Exception(message)

private val slug = Regex("^[a-z0-9][a-z0-9-]*$")
private val publishedStates = setOf(ReviewState.VERIFIED.name, ReviewState.KNOWN_UNSOURCED.name)

/**
 * The publish tool's own gate, independent of the database's. Checks what the schema
 * cannot see: ids are safe to put in a URL filter, nothing is duplicated across files,
 * and no machine has signed for a VERIFIED value.
 */
internal fun validate(files: Map<String, CareerContent>) {
    val problems = mutableListOf<String>()
    val seen = mutableMapOf<String, String>()

    for ((fileName, content) in files) {
        if (fileName.removeSuffix(".json") != content.careerId) {
            problems += "$fileName: careerId '${content.careerId}' does not match the file name"
        }
        if (!slug.matches(content.careerId)) problems += "$fileName: careerId is not a slug"

        for (c in content.criteria) {
            if (!slug.matches(c.id)) problems += "${c.id}: id is not a slug"
            seen.put(c.id, fileName)?.let { problems += "${c.id}: appears in both $it and $fileName" }

            if (c.review == ReviewState.VERIFIED &&
                c.requirement?.provenance?.verifiedBy == Provenance.UNREVIEWED
            ) {
                problems += "${c.id}: VERIFIED but signed '${Provenance.UNREVIEWED}' — a person must sign"
            }
        }
    }

    if (problems.isNotEmpty()) throw ContentRejected(problems.joinToString("\n"))
}

/**
 * [existingCareers] and [existingCriteria] are the current rows, as the service role sees
 * them, restricted to the careers being published. Careers absent from the repo are left
 * alone — publishing one file must never delete another career.
 */
internal fun plan(
    files: Collection<CareerContent>,
    existingCareers: List<JsonObject>,
    existingCriteria: List<JsonObject>,
): Plan {
    val careersById = existingCareers.associateBy { it.id() }
    val criteriaById = existingCriteria.associateBy { it.id() }

    val careerUpserts = files.map(Rows::career).filter { careersById[it.id()] != it }

    val desired = files.flatMap { f -> f.criteria.map { Rows.criterion(f.careerId, it) } }
    val criterionUpserts = desired.filter { criteriaById[it.id()] != it }

    val desiredIds = desired.map { it.id() }.toSet()
    val deletions = existingCriteria.map { it.id() }.filter { it !in desiredIds }.sorted()

    val wasVisible = existingCriteria.filter { it.isPublished() }.map { it.id() }.toSet()
    val willBeVisible = desired.filter { it.isPublished() }.map { it.id() }.toSet()

    return Plan(
        careerUpserts = careerUpserts,
        criterionUpserts = criterionUpserts,
        deletions = deletions,
        becomingVisible = (willBeVisible - wasVisible).sorted(),
        leavingVisible = (wasVisible - willBeVisible).sorted(),
    )
}

private fun JsonObject.id(): String = getValue("id").jsonPrimitive.content

private fun JsonObject.isPublished(): Boolean =
    get("review")?.jsonPrimitive?.content in publishedStates
