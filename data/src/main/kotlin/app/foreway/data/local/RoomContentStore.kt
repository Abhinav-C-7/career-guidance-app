package app.foreway.data.local

import androidx.room.withTransaction
import app.foreway.data.remote.CriterionRow
import app.foreway.data.sync.ContentStore
import app.foreway.data.sync.Cursor
import app.foreway.data.sync.Stream
import app.foreway.data.sync.SyncUpdate
import kotlinx.serialization.json.Json

internal class RoomContentStore(private val db: ForewayDatabase) : ContentStore {

    private val dao = db.content()

    override suspend fun cursor(stream: Stream): Cursor? =
        dao.cursor(stream.name)?.let { Cursor(it.at, it.id) }

    override suspend fun apply(update: SyncUpdate) {
        db.withTransaction {
            if (update.replaceAll) {
                dao.clearCriteria()
                dao.clearCareers()
            }
            dao.upsertCareers(
                update.careers.map { CareerEntity(it.id, it.title, it.family, it.updatedAt) },
            )
            dao.upsertCriteria(
                update.criteria.map {
                    CriterionEntity(
                        id = it.id,
                        careerId = it.careerId,
                        row = Json.encodeToString(CriterionRow.serializer(), it),
                        updatedAt = it.updatedAt,
                    )
                },
            )
            // Older Android builds cap a statement at 999 bound variables.
            update.withdrawnIds.chunked(500).forEach { dao.deleteCriteria(it) }

            update.cursors.forEach { (stream, c) ->
                dao.saveCursor(SyncCursorEntity(stream.name, c.at, c.id))
            }
        }
    }
}
