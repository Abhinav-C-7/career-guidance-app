package app.foreway.data

import app.foreway.data.local.ContentDao
import app.foreway.data.remote.CriterionRow
import app.foreway.data.remote.RowMapper
import app.foreway.domain.model.CareerFamily
import app.foreway.domain.model.Criterion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

public data class CareerSummary(
    val id: String,
    val title: String,
    /** Null when the server sent a family this build does not know. Render neutrally. */
    val family: CareerFamily?,
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
}
