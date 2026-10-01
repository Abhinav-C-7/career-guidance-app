package app.foreway.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * The device's source of truth. Screens read only from here; sync only writes into it.
 *
 * Criteria are stored as the server row, verbatim, and mapped to the domain on read. That
 * way a row this build could not understand is kept rather than discarded, and becomes
 * readable the moment the app is updated — no resync needed, and nothing silently lost.
 */

@Entity(tableName = "careers")
internal data class CareerEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** Raw, so an unknown family from a newer server is stored rather than rejected. */
    val family: String,
    val updatedAt: String,
)

@Entity(tableName = "criteria", indices = [Index("careerId")])
internal data class CriterionEntity(
    @PrimaryKey val id: String,
    val careerId: String,
    /** The CriterionRow JSON as received. */
    val row: String,
    val updatedAt: String,
)

@Entity(tableName = "sync_cursors")
internal data class SyncCursorEntity(
    @PrimaryKey val stream: String,
    val at: String,
    val id: String,
)

/** One row. The app has exactly one student. */
@Entity(tableName = "profile")
internal data class ProfileEntity(
    @PrimaryKey val slot: Int = 0,
    /** StudentProfile JSON. */
    val payload: String,
    val goalCareerId: String?,
)

@Dao
internal interface ContentDao {
    @Upsert
    suspend fun upsertCareers(rows: List<CareerEntity>)

    @Upsert
    suspend fun upsertCriteria(rows: List<CriterionEntity>)

    @Query("DELETE FROM criteria WHERE id IN (:ids)")
    suspend fun deleteCriteria(ids: List<String>)

    @Query("DELETE FROM criteria")
    suspend fun clearCriteria()

    @Query("DELETE FROM careers")
    suspend fun clearCareers()

    @Query("SELECT * FROM careers ORDER BY title")
    fun careers(): Flow<List<CareerEntity>>

    @Query("SELECT * FROM criteria WHERE careerId = :careerId ORDER BY id")
    fun criteriaFor(careerId: String): Flow<List<CriterionEntity>>

    @Query("SELECT * FROM sync_cursors WHERE stream = :stream")
    suspend fun cursor(stream: String): SyncCursorEntity?

    @Upsert
    suspend fun saveCursor(cursor: SyncCursorEntity)
}

@Dao
internal interface ProfileDao {
    @Query("SELECT * FROM profile WHERE slot = 0")
    fun observe(): Flow<ProfileEntity?>

    @Upsert
    suspend fun save(profile: ProfileEntity)
}

@Database(
    entities = [CareerEntity::class, CriterionEntity::class, SyncCursorEntity::class, ProfileEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class ForewayDatabase : RoomDatabase() {
    abstract fun content(): ContentDao
    abstract fun profile(): ProfileDao
}
