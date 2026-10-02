package app.foreway.ui.goal

import app.foreway.data.CareerSummary
import app.foreway.data.Steps
import app.foreway.data.profile.SavedProfile
import app.foreway.domain.model.BodyMetricKind
import app.foreway.domain.model.CareerFamily
import app.foreway.domain.model.ColourVisionGrade
import app.foreway.domain.model.Criterion
import app.foreway.domain.model.LookupPointer
import app.foreway.domain.model.Milestone
import app.foreway.domain.model.MilestoneKind
import app.foreway.domain.model.Provenance
import app.foreway.domain.model.Requirement
import app.foreway.domain.model.ReviewState
import app.foreway.domain.model.SchoolClass
import app.foreway.domain.model.SchoolStage
import app.foreway.domain.model.SourceAuthority
import app.foreway.domain.model.Sourced
import app.foreway.domain.model.StudentProfile
import app.foreway.domain.model.Timing
import app.foreway.domain.model.VerificationWindow
import kotlinx.datetime.LocalDate

/**
 * PREVIEWS ONLY. NOT the real NDA content, and never shown on a device.
 *
 * The running app renders from the local store (GoalPathway). This exists so layouts can be
 * checked in Android Studio previews with every gate state and step position on screen at
 * once. It is deliberately marked VERIFIED here and nowhere else.
 */
object GoalFixture {

    val today = LocalDate(2026, 9, 20)

    private fun provenance(verified: LocalDate = LocalDate(2026, 5, 20)) = Provenance(
        sourceUrl = "https://www.upsc.gov.in/",
        authority = SourceAuthority.OFFICIAL_NOTIFICATION,
        effectiveFrom = LocalDate(2026, 1, 1),
        lastVerifiedAt = verified,
        verifiedBy = "fixture",
    )

    private fun verified(
        id: String,
        label: String,
        requirement: Requirement,
        window: VerificationWindow = VerificationWindow.ANNUAL,
        verifiedAt: LocalDate = LocalDate(2026, 5, 20),
    ) = Criterion(
        id = id,
        label = label,
        review = ReviewState.VERIFIED,
        requirement = Sourced(requirement, provenance(verifiedAt)),
        window = window,
    )

    /** A class 10 student, 16 years old, partway through telling us about themselves. */
    val profile = StudentProfile(
        dateOfBirth = LocalDate(2010, 3, 14),
        currentClass = SchoolClass.CLASS_10,
        nationality = "IN",
        bodyMetrics = mapOf(BodyMetricKind.HEIGHT_CM to 150.0),
        declaredConditions = setOf("TATTOO_OUTSIDE_PERMITTED_AREA"),
    )

    val criteria = listOf(
        verified(
            id = "nationality",
            label = "Nationality",
            requirement = Requirement.Nationality(accepted = setOf("IN", "NP")),
        ),
        verified(
            id = "class-12",
            label = "Class 12",
            requirement = Requirement.CompletedStage(SchoolStage.CLASS_12, appearingAccepted = true),
        ),
        verified(
            id = "height",
            label = "Minimum height",
            requirement = Requirement.BodyMetric(BodyMetricKind.HEIGHT_CM, atLeast = 157.0),
            window = VerificationWindow.SLOW,
        ),
        verified(
            id = "colour-vision",
            label = "Colour vision",
            requirement = Requirement.ColourVision(atLeast = ColourVisionGrade.CP2),
            window = VerificationWindow.SLOW,
        ),
        verified(
            id = "tattoo",
            label = "Permanent tattoo placement",
            requirement = Requirement.MedicalDisqualifier("TATTOO_OUTSIDE_PERMITTED_AREA"),
            window = VerificationWindow.SLOW,
            // Deliberately old, so the stale treatment gets exercised too.
            verifiedAt = LocalDate(2023, 1, 10),
        ),
        Criterion(
            id = "physical-standards",
            label = "Remaining physical standards",
            review = ReviewState.KNOWN_UNSOURCED,
            lookUpAt = LookupPointer(
                describedAs = "Joint Order Manual of Medical Standards",
                citedBy = "https://www.upsc.gov.in/",
            ),
            window = VerificationWindow.SLOW,
        ),
    )

    private fun step(id: String, kind: MilestoneKind, timing: Timing, title: String, detail: String, gates: List<String>) =
        Milestone(id, title, detail, kind, timing, gates, ReviewState.VERIFIED, provenance())

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

    val career = CareerSummary(
        "nda-officer-entry", "Armed forces officer (NDA entry)", CareerFamily.DEFENCE,
        summary = "Joins the Army, Navy or Air Force as an officer, entering straight after class 12.",
    )

    fun ready(current: SchoolClass?): Goal.Ready = GoalPathway.assemble(
        saved = SavedProfile(profile.copy(currentClass = current), goalCareerId = career.id),
        careers = listOf(career),
        criteria = criteria,
        steps = Steps(milestones, unreadable = 0),
        asOf = today,
    ) as Goal.Ready
}
