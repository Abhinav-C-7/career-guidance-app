package app.foreway.data.profile

import app.foreway.data.local.ProfileDao
import app.foreway.data.local.ProfileEntity
import app.foreway.domain.model.StudentProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

public data class SavedProfile(
    val profile: StudentProfile,
    /** The career the student is working towards, if they have picked one. */
    val goalCareerId: String?,
)

/**
 * The student's own answers. Stored on the device only — there is no account in v1, and
 * nothing here is ever sent to the server (CLAUDE.md, PII).
 */
public class ProfileRepository internal constructor(private val dao: ProfileDao) {

    // Lenient on read so a field removed in a later version does not lose the whole
    // profile. Defaults in StudentProfile mean "not told", so a missing field is safe.
    private val json = Json { ignoreUnknownKeys = true }

    /** Null until onboarding has saved something. */
    public val saved: Flow<SavedProfile?> = dao.observe().map { entity ->
        entity?.let {
            val profile = runCatching {
                json.decodeFromString(StudentProfile.serializer(), it.payload)
            }.getOrNull()
            // Unreadable means ask again, not crash, and not guess.
            profile?.let { p -> SavedProfile(p, it.goalCareerId) }
        }
    }

    /** "Start over": forget every answer. The app returns to onboarding. */
    public suspend fun clear() {
        dao.clear()
    }

    public suspend fun save(saved: SavedProfile) {
        dao.save(
            ProfileEntity(
                payload = json.encodeToString(StudentProfile.serializer(), saved.profile),
                goalCareerId = saved.goalCareerId,
            ),
        )
    }
}
