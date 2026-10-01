package app.foreway.content

import app.foreway.content.config.ReviewerProperties
import app.foreway.content.config.SecurityConfig
import app.foreway.content.review.Career
import app.foreway.content.review.Kind
import app.foreway.content.review.Record
import app.foreway.content.review.ReviewRepository
import app.foreway.content.review.ReviewService
import app.foreway.content.web.ReviewController
import app.foreway.domain.model.ReviewState
import java.time.LocalDate
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The review page can put eligibility facts in front of students, so who may reach it is
 * tested over HTTP, not assumed from configuration.
 */
@WebMvcTest(ReviewController::class)
@Import(SecurityConfig::class)
@EnableConfigurationProperties(ReviewerProperties::class)
@TestPropertySource(
    properties = [
        "foreway.reviewer.name=Test Reviewer",
        "foreway.reviewer.password=a-long-enough-test-password",
    ],
)
class SecurityTest {

    @Autowired lateinit var mvc: MockMvc

    @MockitoBean lateinit var repo: ReviewRepository

    @MockitoBean lateinit var service: ReviewService

    @Test
    fun `the queue requires signing in`() {
        mvc.perform(get("/"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/login"))
    }

    @Test
    fun `the sign-in page is public`() {
        mvc.perform(get("/login")).andExpect(status().isOk)
    }

    @Test
    fun `a sign-off without a CSRF token is rejected, and nothing is signed`() {
        mvc.perform(
            post("/review/criterion/c1/approve").with(user("Test Reviewer").roles("REVIEWER"))
                .param("version", "v1").param("confirmed", "true"),
        ).andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `a signed-in reviewer signs under their own name`() {
        `when`(service.approve(Kind.CRITERION, "c1", "v1", true, "Test Reviewer"))
            .thenReturn(ReviewService.Outcome.Done("ok"))

        mvc.perform(
            post("/review/criterion/c1/approve").with(user("Test Reviewer").roles("REVIEWER")).with(csrf())
                .param("version", "v1").param("confirmed", "true"),
        ).andExpect(redirectedUrl("/review/criterion/c1"))

        verify(service).approve(Kind.CRITERION, "c1", "v1", true, "Test Reviewer")
    }

    @Test
    fun `a signed-in user without the reviewer role cannot sign`() {
        mvc.perform(
            post("/review/criterion/c1/approve").with(user("someone")).with(csrf())
                .param("version", "v1").param("confirmed", "true"),
        ).andExpect(status().isForbidden)
    }

    private fun record(review: ReviewState, kind: Kind = Kind.CRITERION, payload: String) = Record(
        kind = kind, id = "c1", careerId = "nda", label = "Nationality", review = review, window = "ANNUAL",
        payload = payload, lookUpAt = null, appliesTo = """{"sex":["MALE"]}""",
        sourceUrl = "https://upsc.gov.in/n.pdf", sourceAuthority = "OFFICIAL_NOTIFICATION",
        effectiveFrom = LocalDate.of(2026, 5, 20), effectiveTo = null, lastVerifiedAt = LocalDate.of(2026, 9, 17),
        verifiedBy = "automated-extraction-unreviewed", note = "Returned by A: check year", position = null, version = "v1",
    )

    private val reviewer = user("Test Reviewer").roles("REVIEWER")

    @Test
    fun `the record page renders a reviewable criterion with its sign-off form`() {
        `when`(repo.find(Kind.CRITERION, "c1"))
            .thenReturn(record(ReviewState.NEEDS_REVIEW, payload = """{"type":"nationality","accepted":["IN","NP"]}"""))
        `when`(repo.careers()).thenReturn(listOf(Career("nda", "Armed forces officer", listOf("internal note"))))

        mvc.perform(get("/review/criterion/c1").with(reviewer))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Nationality one of IN, NP")))
            .andExpect(content().string(containsString("sex MALE")))
            .andExpect(content().string(containsString("Verify and sign")))
    }

    @Test
    fun `an unreadable record renders the refusal instead of a sign-off form`() {
        `when`(repo.find(Kind.CRITERION, "c1"))
            .thenReturn(record(ReviewState.NEEDS_REVIEW, payload = """{"type":"swimming_test"}"""))
        `when`(repo.careers()).thenReturn(emptyList())

        mvc.perform(get("/review/criterion/c1").with(reviewer))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("The app cannot read this record")))
            .andExpect(content().string(not(containsString("Verify and sign"))))
    }

    @Test
    fun `a step renders with its timing and gates`() {
        `when`(repo.find(Kind.STEP, "c1")).thenReturn(
            record(
                ReviewState.VERIFIED,
                kind = Kind.STEP,
                payload = """{"title":"Medical examination","kind":"MEDICAL","timing":{"type":"follows"},"gates":["nda-height"]}""",
            ),
        )
        `when`(repo.careers()).thenReturn(emptyList())

        mvc.perform(get("/review/step/c1").with(reviewer))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Follows the previous step")))
            .andExpect(content().string(containsString("nda-height")))
            .andExpect(content().string(containsString("Withdraw from students")))
    }

    @Test
    fun `the queue renders`() {
        `when`(repo.careers()).thenReturn(listOf(Career("nda", "Armed forces officer", emptyList())))
        `when`(repo.all()).thenReturn(
            listOf(record(ReviewState.NEEDS_REVIEW, payload = """{"type":"nationality","accepted":["IN"]}""")),
        )
        mvc.perform(get("/").with(reviewer))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Armed forces officer")))
            .andExpect(content().string(containsString("/review/criterion/c1")))
    }
}
