package app.foreway.content

import app.foreway.content.review.Kind
import app.foreway.content.review.Record
import app.foreway.content.review.RecordReader
import app.foreway.content.review.ReviewPolicy
import app.foreway.content.review.ReviewRepository
import app.foreway.content.review.ReviewService
import app.foreway.domain.model.ReviewState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.Mockito
import org.springframework.jdbc.core.simple.JdbcClient

class ReviewServiceTest {

    // 1 Oct 2026, 23:30 UTC is already 2 Oct in India. The signature must carry the Indian date.
    private val clock = Clock.fixed(Instant.parse("2026-10-01T23:30:00Z"), ZoneId.of("Asia/Kolkata"))

    private fun record(
        review: ReviewState = ReviewState.NEEDS_REVIEW,
        payload: String? = """{"type":"nationality","accepted":["IN"]}""",
    ) = Record(
        kind = Kind.CRITERION, id = "c1", careerId = "nda", label = "Nationality", review = review,
        window = "ANNUAL", payload = payload, lookUpAt = null, appliesTo = "{}",
        sourceUrl = "https://upsc.gov.in/n.pdf", sourceAuthority = "OFFICIAL_NOTIFICATION",
        effectiveFrom = LocalDate.of(2026, 5, 20), effectiveTo = null, lastVerifiedAt = LocalDate.of(2026, 9, 17),
        verifiedBy = "automated-extraction-unreviewed", note = null, position = null, version = "v1",
    )

    /** Records what the service tried to write, without a database. */
    private class FakeRepo(var current: Record?) : ReviewRepository(Mockito.mock(JdbcClient::class.java)) {
        var signedAs: Pair<String, LocalDate>? = null
        var returnedWith: String? = null

        override fun find(kind: Kind, id: String): Record? = current

        override fun sign(kind: Kind, id: String, version: String, reviewer: String, today: LocalDate): Boolean {
            if (version != current?.version) return false
            signedAs = reviewer to today
            return true
        }

        override fun returnForCorrection(kind: Kind, id: String, version: String, note: String): Boolean {
            returnedWith = note
            return true
        }
    }

    @Test
    fun `nothing is signed without the confirmation box`() {
        val repo = FakeRepo(record())
        val outcome = ReviewService(repo, clock).approve(Kind.CRITERION, "c1", "v1", confirmed = false, reviewer = "Abhinav C")
        assertIs<ReviewService.Outcome.Refused>(outcome)
        assertNull(repo.signedAs)
    }

    @Test
    fun `a confirmed approval is signed by the reviewer with the Indian date`() {
        val repo = FakeRepo(record())
        val outcome = ReviewService(repo, clock).approve(Kind.CRITERION, "c1", "v1", confirmed = true, reviewer = "Abhinav C")
        assertIs<ReviewService.Outcome.Done>(outcome)
        assertEquals("Abhinav C" to LocalDate.of(2026, 10, 2), repo.signedAs)
    }

    @Test
    fun `a record changed since the page was opened is not signed`() {
        val repo = FakeRepo(record())
        val outcome = ReviewService(repo, clock).approve(Kind.CRITERION, "c1", "v0-stale", confirmed = true, reviewer = "A")
        assertIs<ReviewService.Outcome.Refused>(outcome)
        assertNull(repo.signedAs)
    }

    @Test
    fun `a draft cannot be approved, even by a crafted POST`() {
        val repo = FakeRepo(record(review = ReviewState.DRAFT))
        assertIs<ReviewService.Outcome.Refused>(
            ReviewService(repo, clock).approve(Kind.CRITERION, "c1", "v1", confirmed = true, reviewer = "A"),
        )
        assertNull(repo.signedAs)
    }

    @Test
    fun `a payload the app cannot read cannot be approved`() {
        val repo = FakeRepo(record(payload = """{"type":"swimming_test","metres":100}"""))
        assertIs<ReviewService.Outcome.Refused>(
            ReviewService(repo, clock).approve(Kind.CRITERION, "c1", "v1", confirmed = true, reviewer = "A"),
        )
        assertNull(repo.signedAs)
    }

    @Test
    fun `returning needs a reason, and the reason is signed`() {
        val repo = FakeRepo(record())
        val service = ReviewService(repo, clock)
        assertIs<ReviewService.Outcome.Refused>(service.returnForCorrection(Kind.CRITERION, "c1", "v1", "  ", "A"))

        service.returnForCorrection(Kind.CRITERION, "c1", "v1", "Year should be 2011", "Abhinav C")
        assertEquals("Returned by Abhinav C on 2026-10-02: Year should be 2011", repo.returnedWith)
    }

    @Test
    fun `policy refuses a declared gap and a verified record`() {
        assertIs<ReviewPolicy.Decision.Refused>(ReviewPolicy.approve(ReviewState.KNOWN_UNSOURCED, true))
        assertIs<ReviewPolicy.Decision.Refused>(ReviewPolicy.approve(ReviewState.VERIFIED, true))
        assertIs<ReviewPolicy.Decision.Allowed>(ReviewPolicy.withdraw(ReviewState.VERIFIED))
        assertIs<ReviewPolicy.Decision.Refused>(ReviewPolicy.withdraw(ReviewState.NEEDS_REVIEW))
    }

    @Test
    fun `the reader is as strict as the app`() {
        assertIs<RecordReader.Read.Ok<*>>(RecordReader.requirement("""{"type":"attempt_limit","maxAttempts":2}"""))
        // A field the app does not know would be silently dropped by a lenient reader.
        assertIs<RecordReader.Read.Unreadable>(RecordReader.requirement("""{"type":"attempt_limit","maxAttempts":2,"perYear":1}"""))
        assertIs<RecordReader.Read.Ok<*>>(
            RecordReader.stepBody("""{"title":"t","kind":"MEDICAL","timing":{"type":"follows"},"gates":["g"]}"""),
        )
        assertIs<RecordReader.Read.Unreadable>(
            RecordReader.stepBody("""{"title":"t","kind":"MEDICAL","timing":{"type":"follows"},"colour":"red"}"""),
        )
        assertTrue(ReviewService.isReadableByApp(record()))
    }
}
