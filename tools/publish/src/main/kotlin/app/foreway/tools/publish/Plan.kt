package app.foreway.tools.publish

import app.foreway.domain.model.CareerContent
import app.foreway.domain.model.Provenance
import app.foreway.domain.model.ReviewState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The changes to one table. */
internal data class TableChanges(
    val upserts: List<JsonObject>,
    val deletions: List<String>,
    /** Rows a student will start seeing after this publish. The lines to read carefully. */
    val becomingVisible: List<String>,
    val leavingVisible: List<String>,
) {
    val isEmpty: Boolean get() = upserts.isEmpty() && deletions.isEmpty()
}

/**
 * What a publish would change, computed before anything is written.
 *
 * Every review state is written, not just the published ones. The database is where a
 * demotion has to land — a criterion that goes VERIFIED -> NEEDS_REVIEW in the repo must
 * go the same way on the server, or phones keep the retracted figure. Unreviewed rows are
 * harmless there: RLS and the published views never return them.
 *
 * Unchanged rows are not sent. Each write bumps updated_at, and every phone re-downloads
 * whatever has a newer updated_at, so a no-op republish would cost every student a sync.
 */
internal data class Plan(
    val careerUpserts: List<JsonObject>,
    val criteria: TableChanges,
    val milestones: TableChanges,
) {
    val isEmpty: Boolean get() = careerUpserts.isEmpty() && criteria.isEmpty && milestones.isEmpty
}

internal class ContentRejected(message: String) : Exception(message)

private val slug = Regex("^[a-z0-9][a-z0-9-]*$")

/** What each table lets a student see. Must match the RLS policies exactly. */
private val criteriaPublished = setOf(ReviewState.VERIFIED.name, ReviewState.KNOWN_UNSOURCED.name)
private val milestonesPublished = setOf(ReviewState.VERIFIED.name)

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

        val signed = content.criteria.map { Triple(it.id, it.review, it.requirement?.provenance) } +
            content.milestones.map { Triple(it.id, it.review, it.provenance) }

        for ((id, review, provenance) in signed) {
            if (!slug.matches(id)) problems += "$id: id is not a slug"
            seen.put(id, fileName)?.let { problems += "$id: appears in both $it and $fileName" }
            if (review == ReviewState.VERIFIED && provenance?.verifiedBy == Provenance.UNREVIEWED) {
                problems += "$id: VERIFIED but signed '${Provenance.UNREVIEWED}' — a person must sign"
            }
        }
    }

    if (problems.isNotEmpty()) throw ContentRejected(problems.joinToString("\n"))
}

/**
 * The existing rows are the current server state, as the service role sees it, restricted
 * to the careers being published. Careers absent from the repo are left alone — publishing
 * one file must never delete another career.
 */
internal fun plan(
    files: Collection<CareerContent>,
    existingCareers: List<JsonObject>,
    existingCriteria: List<JsonObject>,
    existingMilestones: List<JsonObject> = emptyList(),
): Plan {
    val careersById = existingCareers.associateBy { it.id() }
    return Plan(
        careerUpserts = files.map(Rows::career).filter { careersById[it.id()] != it },
        criteria = diff(
            desired = files.flatMap { f -> f.criteria.map { Rows.criterion(f.careerId, it) } },
            existing = existingCriteria,
            published = criteriaPublished,
        ),
        milestones = diff(
            desired = files.flatMap { f ->
                f.milestones.mapIndexed { i, m -> Rows.milestone(f.careerId, i + 1, m) }
            },
            existing = existingMilestones,
            published = milestonesPublished,
        ),
    )
}

private fun diff(desired: List<JsonObject>, existing: List<JsonObject>, published: Set<String>): TableChanges {
    val existingById = existing.associateBy { it.id() }
    val desiredIds = desired.map { it.id() }.toSet()

    val wasVisible = existing.filter { it.review() in published }.map { it.id() }.toSet()
    val willBeVisible = desired.filter { it.review() in published }.map { it.id() }.toSet()

    return TableChanges(
        upserts = desired.filter { existingById[it.id()] != it },
        deletions = existing.map { it.id() }.filter { it !in desiredIds }.sorted(),
        becomingVisible = (willBeVisible - wasVisible).sorted(),
        leavingVisible = (wasVisible - willBeVisible).sorted(),
    )
}

private fun JsonObject.id(): String = getValue("id").jsonPrimitive.content

private fun JsonObject.review(): String? = get("review")?.jsonPrimitive?.content
