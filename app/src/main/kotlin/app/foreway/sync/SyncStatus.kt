package app.foreway.sync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.datetime.Instant

/**
 * When this phone last finished checking the server for content. A device fact, not
 * content, so it lives in preferences rather than the content store, and never syncs.
 */
class SyncStatus(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("sync_status", Context.MODE_PRIVATE)

    fun markChecked(at: Instant) {
        prefs.edit().putLong(KEY_LAST_CHECKED, at.toEpochMilliseconds()).apply()
    }

    /** Null until the first successful check. */
    val lastChecked: Flow<Instant?> = callbackFlow {
        fun read() = prefs.getLong(KEY_LAST_CHECKED, 0L).takeIf { it > 0 }?.let(Instant::fromEpochMilliseconds)
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LAST_CHECKED) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val KEY_LAST_CHECKED = "last_checked"
    }
}
