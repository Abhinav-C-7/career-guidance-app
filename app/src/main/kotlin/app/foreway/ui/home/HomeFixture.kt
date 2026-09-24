package app.foreway.ui.home

import app.foreway.domain.engine.GateEvaluator
import app.foreway.domain.model.AssessedCriterion
import app.foreway.domain.model.BodyMetricKind
import app.foreway.domain.model.CareerFamily
import app.foreway.domain.model.ColourVisionGrade
import app.foreway.domain.model.Criterion
import app.foreway.domain.model.LookupPointer
import app.foreway.domain.model.Provenance
import app.foreway.domain.model.Requirement
import app.foreway.domain.model.ReviewState
import app.foreway.domain.model.SchoolClass
import app.foreway.domain.model.SchoolStage
import app.foreway.domain.model.SourceAuthority
import app.foreway.domain.model.Sourced
import app.foreway.domain.model.StudentProfile
import app.foreway.domain.model.VerificationWindow
import kotlinx.datetime.LocalDate

/**
 * Development fixture. NOT the real NDA content.
 *
 * The records in `content/careers/nda-officer-entry.json` are still unreviewed, and by
 * design nothing unreviewed reaches a screen. So this file stands in until the real
 * content is signed off and syncing — it exists to exercise the layout, and it is
 * deliberately marked VERIFIED here and nowhere else.
 *
 * Delete this the day real content flows from Supabase.
 *
 * The values are shaped to produce all four gate states plus a source gap, so the design
 * gets exercised rather than flattered.
 */
object HomeFixture {

    private val today = LocalDate(2026, 9, 20)

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

    private val criteria = listOf(
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

    val assessed: List<AssessedCriterion> = GateEvaluator().assess(criteria, profile, today)

    val careerTitle = "Armed forces officer"
    val familyLabel = "Defence and uniformed services"
    val family = CareerFamily.DEFENCE
    val stages = listOf("Class 9", "Class 10", "Class 11", "NDA")
    val currentStageIndex = 1
}
