package app.foreway.tools.publish

import app.foreway.domain.model.CareerContent
import app.foreway.domain.model.Lineage
import app.foreway.domain.model.Necessity
import app.foreway.domain.model.Provenance
import app.foreway.domain.model.ReviewState
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The changes to one table. */
internal data class TableChanges(
    val upserts: List<JsonObject>,
    val deletions: List<String>,
    /**
     * Rows whose values match the repo but which a person has signed or returned on the
     * server. Their review state is kept: the repo copy must never quietly undo a review.
     */
    val keptAsReviewed: List<String> = emptyList(),
    /** Rows a student will start seeing after this publish. The lines to read carefully. */
    val becomingVisible: List<String>,
    val leavingVisible: List<String>,
) {
    val isEmpty: Boolean get() = upserts.isEmpty() && deletions.isEmpty()
}

/**
 * What a publish would change, computed before anything is written.
 *
 * Every review state is written, not just the published ones: unreviewed rows are the
 * review queue, and RLS and the published views never return them.
 *
 * The database is the record of review. A row a person has signed or returned on the
 * server keeps that state unless the repo changes its value — the repo copy saying
 * NEEDS_REVIEW is just the state it was authored in, not a decision to undo a review.
 * A changed value does go through, and drops back to the repo's state, because the old
 * signature was for a value that no longer exists.
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

    // The career tree: every parent published alongside, no loops, and an unregulated
    // career never claims a rule.
    val byId = files.values.associateBy { it.careerId }
    for (c in files.values) {
        val parent = c.parentId
        if (parent != null && parent !in byId) problems += "${c.careerId}: parent '$parent' is not in the content being published"
        if (parent != null && Lineage.chain(c.careerId) { byId[it]?.parentId }.first() == c.careerId) {
            problems += "${c.careerId}: its parent chain loops"
        }
        if (!c.regulated) {
            c.milestones.filter { it.necessity == Necessity.REQUIRED }
                .forEach { problems += "${it.id}: REQUIRED step on unregulated career ${c.careerId}" }
            c.criteria.forEach { problems += "${it.id}: criterion on unregulated career ${c.careerId}" }
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
    val byId = files.associateBy { it.careerId }
    // Parents before children, so a reader of the write order never sees a dangling parent.
    val depthFirst = files.sortedBy { c -> Lineage.chain(c.careerId) { byId[it]?.parentId }.size }
    return Plan(
        careerUpserts = depthFirst.map(Rows::career).filter { careersById[it.id()] != it },
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

/**
 * The columns a review touches. Everything else is the value itself — what the student is
 * told — and only the repo may change that.
 */
private val signature = setOf("review", "verified_by", "last_verified_at", "review_note")

private fun diff(desired: List<JsonObject>, existing: List<JsonObject>, published: Set<String>): TableChanges {
    val existingById = existing.associateBy { it.id() }
    val desiredIds = desired.map { it.id() }.toSet()

    val upserts = mutableListOf<JsonObject>()
    val kept = mutableListOf<String>()
    val finalReview = mutableMapOf<String, String?>()

    for (row in desired) {
        val server = existingById[row.id()]
        when {
            server == null -> upserts += row.clearingNote()

            server.value() == row.value() && server.reviewedByAPerson() -> {
                // Same value, and a person has acted on it since it was published. The
                // server's state stands.
                if (server.withoutNote() != row) kept += row.id()
                finalReview[row.id()] = server.review()
                continue
            }

            server.withoutNote() == row -> Unit // Nothing changed at all.

            // The value changed (or an unreviewed row's state did). Any old signature was
            // for a value that no longer exists, so the repo's state replaces it.
            else -> upserts += row.clearingNote()
        }
        finalReview[row.id()] = row.review()
    }

    val wasVisible = existing.filter { it.review() in published }.map { it.id() }.toSet()
    val willBeVisible = finalReview.filterValues { it in published }.keys

    return TableChanges(
        upserts = upserts,
        deletions = existing.map { it.id() }.filter { it !in desiredIds }.sorted(),
        keptAsReviewed = kept.sorted(),
        becomingVisible = (willBeVisible - wasVisible).sorted(),
        leavingVisible = (wasVisible - willBeVisible).sorted(),
    )
}

private fun JsonObject.value(): JsonObject = JsonObject(filterKeys { it !in signature })

private fun JsonObject.withoutNote(): JsonObject = JsonObject(filterKeys { it != "review_note" })

/** Upserts always carry review_note, so a stale note never outlives the value it was about. */
private fun JsonObject.clearingNote(): JsonObject = JsonObject(this + ("review_note" to JsonNull))

private fun JsonObject.reviewedByAPerson(): Boolean {
    val signer = get("verified_by")?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
    val note = get("review_note")?.takeUnless { it is JsonNull }
    return (signer != null && signer != Provenance.UNREVIEWED) || note != null
}

private fun JsonObject.id(): String = getValue("id").jsonPrimitive.content

private fun JsonObject.review(): String? = get("review")?.jsonPrimitive?.content
