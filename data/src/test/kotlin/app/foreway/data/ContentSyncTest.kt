package app.foreway.data

import app.foreway.data.remote.CareerRow
import app.foreway.data.remote.CriterionRow
import app.foreway.data.remote.MilestoneRow
import app.foreway.data.remote.MilestoneWithdrawalRow
import app.foreway.data.remote.PostgrestRemote
import app.foreway.data.remote.WithdrawalRow
import app.foreway.data.sync.ContentRemote
import app.foreway.data.sync.ContentStore
import app.foreway.data.sync.ContentSync
import app.foreway.data.sync.Cursor
import app.foreway.data.sync.Stream
import app.foreway.data.sync.SyncUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ContentSyncTest {

    /** Behaves like PostgREST's keyset query: rows strictly after (at, id), in order. */
    private class FakeRemote : ContentRemote {
        val careers = mutableListOf<CareerRow>()
        val criteria = mutableListOf<CriterionRow>()
        val withdrawals = mutableListOf<WithdrawalRow>()
        val milestones = mutableListOf<MilestoneRow>()
        val milestoneWithdrawals = mutableListOf<MilestoneWithdrawalRow>()
        var calls = 0

        private fun <T> page(rows: List<T>, after: Cursor?, limit: Int, key: (T) -> Pair<String, String>): List<T> {
            calls++
            return rows.sortedWith(compareBy({ key(it).first }, { key(it).second }))
                .filter { after == null || key(it).first > after.at || (key(it).first == after.at && key(it).second > after.id) }
                .take(limit)
        }

        override suspend fun careers(after: Cursor?, limit: Int) = page(careers, after, limit) { it.updatedAt to it.id }
        override suspend fun criteria(after: Cursor?, limit: Int) = page(criteria, after, limit) { it.updatedAt to it.id }
        override suspend fun withdrawals(after: Cursor?, limit: Int) =
            page(withdrawals, after, limit) { it.withdrawnAt to it.criterionId }
        override suspend fun milestones(after: Cursor?, limit: Int) =
            page(milestones, after, limit) { it.updatedAt to it.id }
        override suspend fun milestoneWithdrawals(after: Cursor?, limit: Int) =
            page(milestoneWithdrawals, after, limit) { it.withdrawnAt to it.milestoneId }
    }

    private class FakeStore : ContentStore {
        val criteria = mutableMapOf<String, CriterionRow>()
        val milestones = mutableMapOf<String, MilestoneRow>()
        val cursors = mutableMapOf<Stream, Cursor>()
        val applied = mutableListOf<SyncUpdate>()

        override suspend fun cursor(stream: Stream) = cursors[stream]
        override suspend fun apply(update: SyncUpdate) {
            applied += update
            if (update.replaceAll) criteria.clear()
            update.criteria.forEach { criteria[it.id] = it }
            update.withdrawnIds.forEach { criteria.remove(it) }
            if (update.replaceAll) milestones.clear()
            update.milestones.forEach { milestones[it.id] = it }
            update.withdrawnMilestoneIds.forEach { milestones.remove(it) }
            cursors += update.cursors
        }
    }

    private fun row(id: String, at: String) = CriterionRow(
        id = id, careerId = "c", label = id, review = "KNOWN_UNSOURCED", verificationWindow = "SLOW",
        lookUpAt = kotlinx.serialization.json.Json.parseToJsonElement("""{"describedAs":"x","citedBy":"y"}"""),
        updatedAt = at,
    )

    @Test
    fun `pages through rows that share one timestamp without looping or skipping`() = runTest {
        // A single publish stamps every row with the same updated_at.
        val remote = FakeRemote().apply { repeat(7) { criteria += row("r$it", "T1") } }
        val store = FakeStore()

        ContentSync(remote, store, pageSize = 3).run()

        assertEquals(7, store.criteria.size)
        assertEquals(Cursor("T1", "r6"), store.cursors[Stream.CRITERIA])
    }

    @Test
    fun `incremental sync fetches only what changed since the cursor`() = runTest {
        val remote = FakeRemote().apply { criteria += row("a", "T1") }
        val store = FakeStore()
        val sync = ContentSync(remote, store)

        sync.run()
        remote.criteria += row("b", "T2")
        val second = sync.run()

        assertEquals(1, second.criteria)
        assertEquals(setOf("a", "b"), store.criteria.keys)
    }

    @Test
    fun `a withdrawal removes the retracted criterion from the phone`() = runTest {
        val remote = FakeRemote().apply { criteria += row("a", "T1"); criteria += row("b", "T1") }
        val store = FakeStore()
        val sync = ContentSync(remote, store)
        sync.run()

        remote.criteria.removeAll { it.id == "a" }
        remote.withdrawals += WithdrawalRow("a", "c", "T2")
        sync.run()

        assertEquals(setOf("b"), store.criteria.keys)
    }

    @Test
    fun `a full resync replaces local content wholesale`() = runTest {
        val remote = FakeRemote().apply { criteria += row("a", "T1") }
        val store = FakeStore().apply { criteria["orphan"] = row("orphan", "T0") }

        ContentSync(remote, store).run(full = true)

        assertEquals(setOf("a"), store.criteria.keys)
    }

    @Test
    fun `everything is applied in one write, after all fetching`() = runTest {
        val remote = FakeRemote().apply { repeat(5) { criteria += row("r$it", "T$it") } }
        val store = FakeStore()

        ContentSync(remote, store, pageSize = 2).run()

        assertEquals(1, store.applied.size)
    }

    @Test
    fun `a fetch failure writes nothing`() = runTest {
        val failing = object : ContentRemote by FakeRemote() {
            override suspend fun withdrawals(after: Cursor?, limit: Int): List<WithdrawalRow> =
                throw java.io.IOException("offline")
        }
        val store = FakeStore()

        assertFailsWith<java.io.IOException> { ContentSync(failing, store).run() }
        assertTrue(store.applied.isEmpty())
    }

    @Test
    fun `a server that ignores the cursor cannot trap sync in a loop`() = runTest {
        val stuck = object : ContentRemote by FakeRemote() {
            override suspend fun criteria(after: Cursor?, limit: Int) = List(limit) { row("same", "T1") }
        }
        assertFailsWith<IllegalStateException> { ContentSync(stuck, FakeStore(), pageSize = 2).run() }
    }

    @Test
    fun `cursor query quotes reserved characters and encodes the timezone plus`() {
        val url = PostgrestRemote("https://x.supabase.co/", "k").query(
            PostgrestRemote.Stream.Criteria,
            Cursor("2026-09-25T10:00:00.5+00:00", "nda-dob"),
            500,
        )
        assertTrue(url.startsWith("https://x.supabase.co/rest/v1/published_criteria?select=*"))
        assertTrue("order=updated_at.asc,id.asc" in url)
        assertTrue("%2B00%3A00" in url, url)
        assertTrue("%22nda-dob%22" in url, url)
        assertTrue(" " !in url && "+00" !in url, url)
    }

    private fun step(id: String, at: String) = MilestoneRow(
        id = id, careerId = "c", position = 1, review = "VERIFIED", verificationWindow = "ANNUAL",
        body = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"title":"t","kind":"TRAINING","timing":{"type":"follows"}}""",
        ),
        sourceUrl = "https://x.gov.in", sourceAuthority = "OFFICIAL_NOTIFICATION",
        effectiveFrom = "2026-05-20", lastVerifiedAt = "2026-09-01", updatedAt = at,
    )

    @Test
    fun `a withdrawn step leaves the phone like a withdrawn criterion`() = runTest {
        val remote = FakeRemote().apply { milestones += step("s1", "T1"); milestones += step("s2", "T1") }
        val store = FakeStore()
        val sync = ContentSync(remote, store)
        assertEquals(2, sync.run().milestones)

        remote.milestones.removeAll { it.id == "s1" }
        remote.milestoneWithdrawals += MilestoneWithdrawalRow("s1", "c", "T2")
        sync.run()

        assertEquals(setOf("s2"), store.milestones.keys)
    }

    @Test
    fun `a full resync replaces steps too`() = runTest {
        val remote = FakeRemote().apply { milestones += step("s1", "T1") }
        val store = FakeStore().apply { milestones["stale"] = step("stale", "T0") }
        ContentSync(remote, store).run(full = true)
        assertEquals(setOf("s1"), store.milestones.keys)
    }
}
