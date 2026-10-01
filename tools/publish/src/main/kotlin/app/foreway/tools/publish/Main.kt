package app.foreway.tools.publish

import app.foreway.domain.content.ContentLoader
import app.foreway.domain.model.CareerContent
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.system.exitProcess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Publishes reviewed content from the repository to Supabase.
 *
 * Dry run by default. Nothing is written without --apply, and deleting a criterion that is
 * gone from the repo additionally needs --allow-delete.
 *
 * Reads SUPABASE_URL and SUPABASE_SECRET_KEY from the environment, or from a gitignored
 * .env at the repo root. The key is never accepted as an argument, because arguments end
 * up in shell history.
 */
fun main(args: Array<String>) {
    val apply = "--apply" in args
    val allowDelete = "--allow-delete" in args
    val dir = File(args.firstOrNull { !it.startsWith("--") } ?: "content/careers")

    try {
        run(dir, apply, allowDelete)
    } catch (e: ContentRejected) {
        System.err.println("Refusing to publish:\n${e.message}")
        exitProcess(1)
    } catch (e: PublishFailed) {
        System.err.println(e.message)
        exitProcess(2)
    }
}

private fun run(dir: File, apply: Boolean, allowDelete: Boolean) {
    val files = dir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }
        ?: throw ContentRejected("No such directory: ${dir.absolutePath}")

    // Parsed by the same loader the app uses. Unknown keys, a missing source on a stated
    // value, a gap without a pointer: all fail here, before any network call.
    val content: Map<String, CareerContent> = files.associate { f ->
        f.name to try {
            ContentLoader.parse(f.readText())
        } catch (e: Exception) {
            throw ContentRejected("${f.name}: ${e.message}")
        }
    }
    validate(content)

    val api = Rest.fromEnvironment()
    val careerIds = content.values.map { it.careerId }
    val existingCareers = api.select("careers", Rows.careerColumns, "id", careerIds)
    // review_note is read so a row a reviewer has returned is recognised as reviewed.
    val existingCriteria = api.select("criteria", Rows.criterionColumns + "review_note", "career_id", careerIds)
    val existingMilestones = api.select("milestones", Rows.milestoneColumns + "review_note", "career_id", careerIds)

    val plan = plan(content.values, existingCareers, existingCriteria, existingMilestones)
    report(plan)

    if (plan.isEmpty) return
    if (!apply) {
        println("\nDry run. Nothing written. Re-run with --apply to publish.")
        return
    }
    val deletes = plan.criteria.deletions.isNotEmpty() || plan.milestones.deletions.isNotEmpty()
    if (deletes && !allowDelete) {
        throw PublishFailed(
            "\nThe plan deletes rows. Nothing written. Re-run with --apply --allow-delete " +
                "if that is intended; the audit history keeps the old rows either way.",
        )
    }

    // Careers first: criteria and milestones reference them.
    if (plan.careerUpserts.isNotEmpty()) api.upsert("careers", plan.careerUpserts)

    if (plan.criteria.upserts.isNotEmpty()) api.upsert("criteria", plan.criteria.upserts)
    if (plan.criteria.deletions.isNotEmpty()) api.delete("criteria", plan.criteria.deletions)

    // Milestones delete first: a new step taking a removed step's position would otherwise
    // collide on (career_id, position) when the upsert commits.
    if (plan.milestones.deletions.isNotEmpty()) api.delete("milestones", plan.milestones.deletions)
    if (plan.milestones.upserts.isNotEmpty()) api.upsert("milestones", plan.milestones.upserts)

    println("\nPublished.")
}

private fun report(plan: Plan) {
    if (plan.isEmpty) {
        println("Up to date. Nothing to publish.")
        return
    }
    println("Careers to write: ${plan.careerUpserts.size}")
    report("Criteria", plan.criteria)
    report("Milestones", plan.milestones)
}

private fun report(name: String, t: TableChanges) {
    println()
    println("$name to write:  ${t.upserts.size}")
    println("$name to delete: ${t.deletions.size}")
    t.deletions.forEach { println("  - $it") }
    if (t.keptAsReviewed.isNotEmpty()) {
        println("$name kept as reviewed on the server (${t.keptAsReviewed.size}):")
        t.keptAsReviewed.forEach { println("  = $it") }
    }

    // The only lines that change what a student sees. Read these every time.
    println("Students WILL START seeing (${t.becomingVisible.size}):")
    t.becomingVisible.forEach { println("  + $it") }
    println("Students will STOP seeing (${t.leavingVisible.size}):")
    t.leavingVisible.forEach { println("  - $it") }
}

internal class PublishFailed(message: String) : Exception(message)

/** Just enough PostgREST. The JDK client keeps this tool dependency-free. */
private class Rest(private val baseUrl: String, private val key: String) {

    private val http = HttpClient.newHttpClient()

    fun select(table: String, columns: List<String>, column: String, values: List<String>): List<JsonObject> {
        if (values.isEmpty()) return emptyList()
        val url = "$baseUrl/rest/v1/$table?select=${columns.joinToString(",")}" +
            "&$column=in.(${values.joinToString(",")})"
        val body = send(request(url).GET())
        return Json.parseToJsonElement(body).let { it as JsonArray }.map { it.jsonObject }
    }

    fun upsert(table: String, rows: List<JsonObject>) {
        send(
            request("$baseUrl/rest/v1/$table?on_conflict=id")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(JsonArray(rows).toString())),
        )
    }

    fun delete(table: String, ids: List<String>) {
        val filter = URLEncoder.encode("in.(${ids.joinToString(",")})", Charsets.UTF_8)
        send(request("$baseUrl/rest/v1/$table?id=$filter").DELETE())
    }

    private fun request(url: String): HttpRequest.Builder {
        val b = HttpRequest.newBuilder(URI.create(url)).header("apikey", key)
        // Legacy service_role keys are JWTs and also go in Authorization. The newer
        // sb_secret_ keys are not JWTs and must not.
        if (key.startsWith("eyJ")) b.header("Authorization", "Bearer $key")
        return b
    }

    private fun send(builder: HttpRequest.Builder): String {
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            // The body is PostgREST's error, e.g. a check-constraint name. It never
            // contains the key, which only travels in request headers.
            throw PublishFailed("${response.request().method()} failed: ${response.statusCode()} ${response.body()}")
        }
        return response.body()
    }

    companion object {
        fun fromEnvironment(): Rest {
            val dotenv = readDotEnv(File(".env"))
            fun setting(vararg names: String): String? =
                names.firstNotNullOfOrNull { System.getenv(it) ?: dotenv[it] }?.takeIf { it.isNotBlank() }

            val url = setting("SUPABASE_URL")
                ?: throw PublishFailed("SUPABASE_URL is not set (environment or .env).")
            val key = setting("SUPABASE_SECRET_KEY", "SUPABASE_SERVICE_ROLE_KEY")
                ?: throw PublishFailed("SUPABASE_SECRET_KEY is not set (environment or .env).")
            return Rest(url.trimEnd('/'), key)
        }

        private fun readDotEnv(file: File): Map<String, String> =
            if (!file.exists()) {
                emptyMap()
            } else {
                file.readLines()
                    .map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
                    .associate { line ->
                        line.substringBefore('=').trim() to line.substringAfter('=').trim().trim('"')
                    }
            }
    }
}
