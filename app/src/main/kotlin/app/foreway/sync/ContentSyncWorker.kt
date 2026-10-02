package app.foreway.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.foreway.ForewayApp
import java.io.IOException
import kotlinx.datetime.Clock
import java.util.concurrent.TimeUnit

/**
 * Keeps the local content store current. Inexact by design (CLAUDE.md, stack): content
 * changes at most daily, and a sync an hour late costs nothing while a wakeup on a budget
 * phone costs battery.
 */
class ContentSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sync = (applicationContext as ForewayApp).data.sync
            ?: return Result.success() // No server configured; the local store is all there is.

        return try {
            val result = sync.run(full = inputData.getBoolean(KEY_FULL, false))
            SyncStatus(applicationContext).markChecked(Clock.System.now())
            if (result.unreadable > 0) {
                Log.w(TAG, "${result.unreadable} criteria need a newer app version to read")
            }
            Result.success()
        } catch (e: IOException) {
            // Offline, timeout, server hiccup. WorkManager backs off and tries again.
            Log.i(TAG, "Sync deferred: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ContentSync"
        private const val KEY_FULL = "full"

        /** Unique name of the student-requested check, so the screen can watch it. */
        const val CHECK_NOW = "content-sync-now"

        /** "Check for updates". Waits for a connection rather than failing without one. */
        fun checkNow(context: Context) {
            val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                CHECK_NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ContentSyncWorker>().setConstraints(online).build(),
            )
        }

        fun schedule(context: Context) {
            val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val work = WorkManager.getInstance(context)

            // On every launch, so a student opening the app sees today's content.
            work.enqueueUniqueWork(
                "content-sync-launch",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ContentSyncWorker>().setConstraints(online).build(),
            )

            // Daily, for students who keep the app installed but rarely open it. This is
            // what lets deadline reminders fire on current dates.
            work.enqueueUniquePeriodicWork(
                "content-sync-daily",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ContentSyncWorker>(1, TimeUnit.DAYS).setConstraints(online).build(),
            )

            // Weekly full resync: the safety net for anything incremental sync can miss.
            work.enqueueUniquePeriodicWork(
                "content-sync-full",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ContentSyncWorker>(7, TimeUnit.DAYS)
                    .setConstraints(online)
                    .setInputData(workDataOf(KEY_FULL to true))
                    .build(),
            )
        }
    }
}
