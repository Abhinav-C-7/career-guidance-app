package app.foreway.data

import app.foreway.data.local.ContentDao
import app.foreway.data.remote.CriterionRow
import app.foreway.data.remote.MilestoneRow
import app.foreway.data.remote.RowMapper
import app.foreway.domain.model.CareerFamily
import app.foreway.domain.model.Criterion
import app.foreway.domain.model.Milestone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

public data class CareerSummary(
    val id: String,
    val title: String,
    /** Null when the server sent a family this build does not know. Render neutrally. */
    val family: CareerFamily?,
)

public data class Steps(
    /** In pathway order. */
    val milestones: List<Milestone>,
    /** Steps this build could not read. Shown as "update the app", never silently skipped. */
    val unreadable: Int,
)

/** Read side of the content store. Everything here works offline. */
public class ContentRepository internal constructor(private val dao: ContentDao) {

    private val json = Json { ignoreUnknownKeys = true }

    public fun careers(): Flow<List<CareerSummary>> = dao.careers().map { rows ->
        rows.map { row ->
            CareerSummary(
                id = row.id,
                title = row.title,
                family = CareerFamily.entries.firstOrNull { it.name == row.family },
            )
        }
    }

    /**
     * Every synced criterion for a career, including ones this build cannot read — those
     * arrive as declared gaps (see RowMapper). Filtering by review state and applicability
     * is GateEvaluator's job, not this one's.
     */
    public fun criteriaFor(careerId: String): Flow<List<Criterion>> =
        dao.criteriaFor(careerId).map { rows ->
            rows.map { RowMapper.criterion(json.decodeFromString(CriterionRow.serializer(), it.row)) }
        }

    /** Every synced step for a career, in order. Filtering is PathwayBuilder's job. */
    public fun milestonesFor(careerId: String): Flow<Steps> =
        dao.milestonesFor(careerId).map { rows ->
            val mapped = rows.map { RowMapper.milestone(json.decodeFromString(MilestoneRow.serializer(), it.row)) }
            Steps(milestones = mapped.filterNotNull(), unreadable = mapped.count { it == null })
        }
}
