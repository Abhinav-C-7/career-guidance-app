package app.foreway.data

import android.content.Context
import androidx.room.Room
import app.foreway.data.local.ForewayDatabase
import app.foreway.data.local.RoomContentStore
import app.foreway.data.profile.ProfileRepository
import app.foreway.data.remote.PostgrestRemote
import app.foreway.data.sync.ContentSync

/**
 * Everything the app needs from the data layer, built once per process.
 *
 * [sync] is null when no server is configured (a fresh checkout without local.properties).
 * The app still runs — on whatever is already in the local store.
 */
public class ForewayData private constructor(
    public val content: ContentRepository,
    public val profile: ProfileRepository,
    public val sync: ContentSync?,
) {
    public companion object {
        /**
         * [publishableKey] is the anon/publishable key. It is public by design — it ships
         * in the APK — and RLS is what protects the data. Never pass the secret key here.
         */
        public fun create(context: Context, serverUrl: String?, publishableKey: String?): ForewayData {
            val db = Room.databaseBuilder(context.applicationContext, ForewayDatabase::class.java, "foreway.db")
                .build()

            val sync = if (!serverUrl.isNullOrBlank() && !publishableKey.isNullOrBlank()) {
                ContentSync(PostgrestRemote(serverUrl, publishableKey), RoomContentStore(db))
            } else {
                null
            }

            return ForewayData(
                content = ContentRepository(db.content()),
                profile = ProfileRepository(db.profile()),
                sync = sync,
            )
        }
    }
}
