package app.foreway.ui.pathway

import app.foreway.domain.engine.GateEvaluator
import app.foreway.domain.engine.PathwayBuilder
import app.foreway.domain.model.Milestone
import app.foreway.domain.model.MilestoneKind
import app.foreway.domain.model.Provenance
import app.foreway.domain.model.ReviewState
import app.foreway.domain.model.SchoolClass
import app.foreway.domain.model.SourceAuthority
import app.foreway.domain.model.Timing
import app.foreway.ui.home.HomeFixture
import kotlinx.datetime.LocalDate

/**
 * PREVIEWS ONLY. Shaped like the NDA steps in content/, but marked VERIFIED here and
 * nowhere else, so every position and gate state can be seen in Android Studio. The
 * device renders only what is in the local store.
 */
object PathwayFixture {

    private val provenance = Provenance(
        sourceUrl = "https://www.upsc.gov.in/",
        authority = SourceAuthority.OFFICIAL_NOTIFICATION,
        effectiveFrom = LocalDate(2026, 5, 20),
        lastVerifiedAt = LocalDate(2026, 9, 1),
        verifiedBy = "fixture",
    )

    private fun step(id: String, kind: MilestoneKind, timing: Timing, title: String, detail: String, gates: List<String>) =
        Milestone(id, title, detail, kind, timing, gates, ReviewState.VERIFIED, provenance)

    private val milestones = listOf(
        step(
            "pcm", MilestoneKind.SUBJECT_CHOICE, Timing.SchoolYears(SchoolClass.CLASS_11, SchoolClass.CLASS_12),
            "Physics, Chemistry and Maths in class 11 and 12, for the Air Force and Navy wings",
            "The Army wing accepts a class 12 pass in any stream.",
            emptyList(),
        ),
        step(
            "written", MilestoneKind.ENTRANCE_EXAM, Timing.FromStage(SchoolClass.CLASS_12),
            "Apply, and sit the NDA written exam",
            "You can apply while appearing in class 12. Two papers: Mathematics (300) and the General Ability Test (600).",
            listOf("class-12", "nationality"),
        ),
        step(
            "ssb", MilestoneKind.SELECTION_INTERVIEW, Timing.Follows,
            "Services Selection Board interview",
            "An intelligence and personality test worth 900 marks, in two stages.",
            emptyList(),
        ),
        step(
            "medical", MilestoneKind.MEDICAL, Timing.Follows,
            "Medical examination",
            "Only candidates declared fit by the Medical Board are admitted.",
            listOf("height", "colour-vision", "tattoo", "physical-standards"),
        ),
        step(
            "academy", MilestoneKind.TRAINING, Timing.Follows,
            "Three years at the National Defence Academy",
            "Common to all three wings for the first two and a half years.",
            emptyList(),
        ),
    )

    fun forClass(current: SchoolClass): PathwayUiState.Ready {
        val profile = HomeFixture.profile.copy(currentClass = current)
        val assessed = GateEvaluator().assess(HomeFixture.criteria, profile, HomeFixture.today)
        return PathwayUiState.Ready(
            career = HomeFixture.ready.career,
            studentClass = current,
            pathway = PathwayBuilder().build(milestones, assessed, profile, HomeFixture.today),
            unreadableSteps = 0,
        )
    }
}
