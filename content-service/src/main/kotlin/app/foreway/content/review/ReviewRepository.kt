package app.foreway.content.review

import app.foreway.domain.model.ReviewState
import java.sql.ResultSet
import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

enum class Kind(val table: String, val path: String) {
    CRITERION("criteria", "criterion"),
    STEP("milestones", "step");

    companion object {
        fun fromPath(path: String): Kind? = entries.firstOrNull { it.path == path }
    }
}

/**
 * One record as the review screen needs it. [payload] is the requirement JSON for a
 * criterion and the body JSON for a step. [version] is updated_at as text: an opaque
 * token, compared exactly, so a sign-off only lands on the row the reviewer actually saw.
 */
data class Record(
    val kind: Kind,
    val id: String,
    val careerId: String,
    val label: String,
    val review: ReviewState,
    val window: String,
    val payload: String?,
    val lookUpAt: String?,
    val appliesTo: String?,
    val sourceUrl: String?,
    val sourceAuthority: String?,
    val effectiveFrom: LocalDate?,
    val effectiveTo: LocalDate?,
    val lastVerifiedAt: LocalDate?,
    val verifiedBy: String?,
    val note: String?,
    val position: Int?,
    val version: String,
)

data class Career(val id: String, val title: String, val notes: List<String>)

/**
 * The review service's only access to content. It connects as content_service, which the
 * database lets sign and return records but never change a value — so nothing here could
 * alter an eligibility number even if it tried.
 *
 * Plain SQL through JdbcClient rather than an ORM: the schema belongs to the migrations,
 * these are a handful of queries, and Postgres enums and jsonb are clearer as explicit
 * casts than as ORM type mappings.
 */
@Repository
class ReviewRepository(private val jdbc: JdbcClient) {

    private val notesJson = Json { ignoreUnknownKeys = true }

    fun careers(): List<Career> =
        jdbc.sql("select id, title, notes::text as notes from careers order by title")
            .query { rs, _ ->
                Career(
                    id = rs.getString("id"),
                    title = rs.getString("title"),
                    notes = notesJson.decodeFromString(ListSerializer(String.serializer()), rs.getString("notes")),
                )
            }
            .list()

    fun all(): List<Record> = criteria(null) + steps(null)

    fun find(kind: Kind, id: String): Record? = when (kind) {
        Kind.CRITERION -> criteria(id)
        Kind.STEP -> steps(id)
    }.singleOrNull()

    private fun criteria(id: String?): List<Record> =
        jdbc.sql(
            """
            select id, career_id, label, review::text as review, verification_window::text as window,
                   requirement::text as payload, look_up_at::text as look_up_at, applies_to::text as applies_to,
                   source_url, source_authority::text as source_authority, effective_from, effective_to,
                   last_verified_at, verified_by, review_note, null::int as position, updated_at::text as version
            from criteria
            where (cast(:id as text) is null or id = :id)
            order by career_id, id
            """,
        ).param("id", id).query { rs, _ -> record(Kind.CRITERION, rs) }.list()

    private fun steps(id: String?): List<Record> =
        jdbc.sql(
            """
            select id, career_id, body->>'title' as label, review::text as review,
                   verification_window::text as window, body::text as payload, null::text as look_up_at,
                   applies_to::text as applies_to, source_url, source_authority::text as source_authority,
                   effective_from, effective_to, last_verified_at, verified_by, review_note, position,
                   updated_at::text as version
            from milestones
            where (cast(:id as text) is null or id = :id)
            order by career_id, position
            """,
        ).param("id", id).query { rs, _ -> record(Kind.STEP, rs) }.list()

    /** NEEDS_REVIEW -> VERIFIED, signed by [reviewer]. False if the row moved on since it was read. */
    fun sign(kind: Kind, id: String, version: String, reviewer: String, today: LocalDate): Boolean =
        update(
            kind,
            "review = 'VERIFIED', verified_by = :reviewer, last_verified_at = :today, review_note = null",
            from = ReviewState.NEEDS_REVIEW,
            id = id,
            version = version,
            params = mapOf("reviewer" to reviewer, "today" to today),
        )

    /** NEEDS_REVIEW -> DRAFT, with the reason. The value is fixed in the repo and republished. */
    fun returnForCorrection(kind: Kind, id: String, version: String, note: String): Boolean =
        update(kind, "review = 'DRAFT', review_note = :note", ReviewState.NEEDS_REVIEW, id, version, mapOf("note" to note))

    /** VERIFIED -> NEEDS_REVIEW. The withdrawal trigger then takes it off phones. */
    fun withdraw(kind: Kind, id: String, version: String, note: String): Boolean =
        update(kind, "review = 'NEEDS_REVIEW', review_note = :note", ReviewState.VERIFIED, id, version, mapOf("note" to note))

    private fun update(
        kind: Kind,
        set: String,
        from: ReviewState,
        id: String,
        version: String,
        params: Map<String, Any>,
    ): Boolean {
        // kind.table is one of two constants from the enum, never user input.
        val sql = "update ${kind.table} set $set where id = :id and review::text = :from and updated_at::text = :version"
        return jdbc.sql(sql)
            .param("id", id)
            .param("from", from.name)
            .param("version", version)
            .params(params)
            .update() == 1
    }

    private fun record(kind: Kind, rs: ResultSet) = Record(
        kind = kind,
        id = rs.getString("id"),
        careerId = rs.getString("career_id"),
        label = rs.getString("label") ?: rs.getString("id"),
        review = ReviewState.valueOf(rs.getString("review")),
        window = rs.getString("window"),
        payload = rs.getString("payload"),
        lookUpAt = rs.getString("look_up_at"),
        appliesTo = rs.getString("applies_to"),
        sourceUrl = rs.getString("source_url"),
        sourceAuthority = rs.getString("source_authority"),
        effectiveFrom = rs.getObject("effective_from", LocalDate::class.java),
        effectiveTo = rs.getObject("effective_to", LocalDate::class.java),
        lastVerifiedAt = rs.getObject("last_verified_at", LocalDate::class.java),
        verifiedBy = rs.getString("verified_by"),
        note = rs.getString("review_note"),
        position = rs.getObject("position") as Int?,
        version = rs.getString("version"),
    )
}
