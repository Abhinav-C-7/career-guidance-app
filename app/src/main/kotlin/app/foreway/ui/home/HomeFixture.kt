package app.foreway.ui.home

import app.foreway.data.CareerSummary
import app.foreway.domain.engine.GateEvaluator
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
 * PREVIEWS ONLY. NOT the real NDA content, and never shown on a device.
 *
 * The running app renders home from the local store (HomeViewModel). This exists so the
 * layout can be checked in Android Studio previews with every gate state on screen at
 * once — something real content, all still unreviewed, cannot do yet. It is deliberately
 * marked VERIFIED here and nowhere else.
 *
 * The values are shaped to produce all four gate states plus a source gap, so the design
 * gets exercised rather than flattered.
 */
object HomeFixture {

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

    val ready = HomeUiState.Ready(
        career = CareerSummary("nda-officer-entry", "Armed forces officer", CareerFamily.DEFENCE),
        rail = railFor(SchoolClass.CLASS_10),
        assessed = HomeViewModel.ordered(GateEvaluator().assess(criteria, profile, today)),
    )
}
