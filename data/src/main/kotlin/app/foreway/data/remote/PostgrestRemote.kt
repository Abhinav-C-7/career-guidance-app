package app.foreway.data.remote

import app.foreway.data.sync.ContentRemote
import app.foreway.data.sync.Cursor
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Three read-only queries against PostgREST. Deliberately not supabase-kt: see CLAUDE.md.
 *
 * Pagination is keyset on (timestamp, id), never offset and never "timestamp >= cursor".
 * A publish writes many rows in one transaction, so they share one updated_at; paging on
 * the timestamp alone would either loop forever on a full page of equal timestamps or skip
 * the rest of them.
 */
internal class PostgrestRemote(
    baseUrl: String,
    private val key: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ContentRemote {

    private val rest = baseUrl.trimEnd('/') + "/rest/v1/"

    // Row decoding is lenient: a new column on a view is not a reason to fail a sync. The
    // payload inside each row is decoded strictly, later, by RowMapper.
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun careers(after: Cursor?, limit: Int): List<CareerRow> =
        get(query(Stream.Careers, after, limit), CareerRow.serializer())

    override suspend fun criteria(after: Cursor?, limit: Int): List<CriterionRow> =
        get(query(Stream.Criteria, after, limit), CriterionRow.serializer())

    override suspend fun withdrawals(after: Cursor?, limit: Int): List<WithdrawalRow> =
        get(query(Stream.Withdrawals, after, limit), WithdrawalRow.serializer())

    internal enum class Stream(val relation: String, val at: String, val id: String) {
        Careers("published_careers", "updated_at", "id"),
        Criteria("published_criteria", "updated_at", "id"),
        Withdrawals("criteria_withdrawals", "withdrawn_at", "criterion_id"),
    }

    internal fun query(stream: Stream, after: Cursor?, limit: Int): String {
        val base = "$rest${stream.relation}?select=*" +
            "&order=${stream.at}.asc,${stream.id}.asc&limit=$limit"
        if (after == null) return base

        // Values are double-quoted because timestamps contain '.' and ':', which PostgREST
        // reserves inside logic trees. The whole tree is then URL-encoded, which matters
        // for the '+' in "+00:00" — left raw, it arrives as a space.
        val at = "\"${after.at}\""
        val id = "\"${after.id}\""
        val tree = "(${stream.at}.gt.$at,and(${stream.at}.eq.$at,${stream.id}.gt.$id))"
        return "$base&or=${URLEncoder.encode(tree, "UTF-8")}"
    }

    private suspend fun <T> get(url: String, row: KSerializer<T>): List<T> = withContext(io) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("apikey", key)
            connection.setRequestProperty("Accept", "application/json")
            // Legacy anon keys are JWTs and also go in Authorization. The newer
            // sb_publishable_ keys are not JWTs and must not.
            if (key.startsWith("eyJ")) connection.setRequestProperty("Authorization", "Bearer $key")

            val code = connection.responseCode
            if (code !in 200..299) {
                // The key travels only in headers and never appears in this message.
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Content sync failed: HTTP $code ${detail.take(300)}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString(ListSerializer(row), body)
        } finally {
            connection.disconnect()
        }
    }
}
