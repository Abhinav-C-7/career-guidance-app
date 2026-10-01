package app.foreway.data.sync

import app.foreway.data.remote.CareerRow
import app.foreway.data.remote.CriterionRow
import app.foreway.data.remote.RowMapper
import app.foreway.data.remote.WithdrawalRow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A position in one stream: the last (timestamp, id) pair received, exactly as sent. */
internal data class Cursor(val at: String, val id: String)

internal enum class Stream { CAREERS, CRITERIA, WITHDRAWALS }

internal interface ContentRemote {
    suspend fun careers(after: Cursor?, limit: Int): List<CareerRow>
    suspend fun criteria(after: Cursor?, limit: Int): List<CriterionRow>
    suspend fun withdrawals(after: Cursor?, limit: Int): List<WithdrawalRow>
}

internal interface ContentStore {
    suspend fun cursor(stream: Stream): Cursor?

    /** Applies everything in one transaction, or nothing. */
    suspend fun apply(update: SyncUpdate)
}

internal data class SyncUpdate(
    /** Drop all local content first. Set on a full resync. */
    val replaceAll: Boolean,
    val careers: List<CareerRow>,
    val criteria: List<CriterionRow>,
    val withdrawnIds: List<String>,
    val cursors: Map<Stream, Cursor>,
)

public data class SyncResult(
    val careers: Int,
    val criteria: Int,
    val withdrawn: Int,
    /** Rows this build could not read. Non-zero means students on this version should update. */
    val unreadable: Int,
)

/**
 * Pulls published content into the local store.
 *
 * Incremental runs fetch only what changed since each stream's cursor, then drop anything
 * withdrawn since. A full run replaces the local store wholesale; it is the safety net for
 * anything incremental sync can miss (a row committed late by an overlapping writer, a
 * tombstone lost server-side), and should run every few days.
 *
 * Everything is fetched before anything is written, then applied in a single transaction.
 * A sync that fails halfway leaves the phone exactly as it was — never with half of a
 * career's criteria updated and half not.
 *
 * Cursors are always the server's own values, never the device clock, which on budget
 * phones is routinely wrong by hours or years.
 */
public class ContentSync internal constructor(
    private val remote: ContentRemote,
    private val store: ContentStore,
    private val pageSize: Int = 500,
) {

    // Runs are serialised. A startup sync overlapping the weekly full resync could
    // otherwise apply a stale fetch after a newer one and resurrect a withdrawn row.
    private val lock = Mutex()

    public suspend fun run(full: Boolean = false): SyncResult = lock.withLock { runLocked(full) }

    private suspend fun runLocked(full: Boolean): SyncResult {
        val careerFrom = if (full) null else store.cursor(Stream.CAREERS)
        val criteriaFrom = if (full) null else store.cursor(Stream.CRITERIA)
        // Withdrawals keep their cursor even on a full run: old tombstones refer only to
        // criteria that are already absent from the full set, so replaying them is waste.
        val withdrawalsFrom = store.cursor(Stream.WITHDRAWALS)

        val careers = drain(careerFrom, remote::careers) { Cursor(it.updatedAt, it.id) }
        val criteria = drain(criteriaFrom, remote::criteria) { Cursor(it.updatedAt, it.id) }
        val withdrawals = drain(withdrawalsFrom, remote::withdrawals) { Cursor(it.withdrawnAt, it.criterionId) }

        val cursors = buildMap {
            careers.lastOrNull()?.let { put(Stream.CAREERS, Cursor(it.updatedAt, it.id)) }
            criteria.lastOrNull()?.let { put(Stream.CRITERIA, Cursor(it.updatedAt, it.id)) }
            withdrawals.lastOrNull()?.let { put(Stream.WITHDRAWALS, Cursor(it.withdrawnAt, it.criterionId)) }
        }

        // A criterion cannot be both published and withdrawn at once — the server trigger
        // deletes the tombstone on republish — so applying upserts then withdrawals is safe.
        store.apply(
            SyncUpdate(
                replaceAll = full,
                careers = careers,
                criteria = criteria,
                withdrawnIds = withdrawals.map { it.criterionId },
                cursors = cursors,
            ),
        )

        return SyncResult(
            careers = careers.size,
            criteria = criteria.size,
            withdrawn = withdrawals.size,
            unreadable = criteria.count { RowMapper.isUnreadable(RowMapper.criterion(it)) },
        )
    }

    private suspend fun <T> drain(
        from: Cursor?,
        fetch: suspend (Cursor?, Int) -> List<T>,
        key: (T) -> Cursor,
    ): List<T> {
        val all = mutableListOf<T>()
        var after = from
        while (true) {
            val page = fetch(after, pageSize)
            all += page
            if (page.size < pageSize) return all

            val next = key(page.last())
            // A server that ignores the cursor would otherwise loop forever.
            check(next != after) { "Sync cursor did not advance past $next" }
            after = next
        }
    }
}
