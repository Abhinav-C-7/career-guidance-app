package app.foreway.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.foreway.R
import app.foreway.domain.model.BlockReason
import app.foreway.domain.model.BodyMetricKind
import app.foreway.domain.model.GateOutcome
import app.foreway.domain.model.MissingInput
import app.foreway.domain.model.RiskReason
import app.foreway.domain.model.Subject

/**
 * Turns a structured outcome into a sentence.
 *
 * This is the only place in the app that decides how an eligibility verdict is worded. The
 * rules engine reports that a student is 5 cm short; it has no opinion on how to say so.
 *
 * What this buys: translation becomes a matter of adding values-hi/strings.xml, and the
 * engine can be tested on facts rather than prose. What it does not buy: these strings are
 * still in the APK, so changing wording still needs a release.
 */
@Composable
fun GateOutcome.describe(): String? = when (this) {
    is GateOutcome.Met -> null
    is GateOutcome.NotYetAssessed -> missing.describe()
    is GateOutcome.AtRisk -> reason.describe()
    is GateOutcome.CannotBeMet -> reason.describe()
}

@Composable
private fun MissingInput.describe(): String = stringResource(
    when (this) {
        MissingInput.DATE_OF_BIRTH -> R.string.need_date_of_birth
        MissingInput.CURRENT_CLASS -> R.string.need_current_class
        MissingInput.SUBJECTS_TAKEN -> R.string.need_subjects
        MissingInput.BODY_MEASUREMENT -> R.string.need_body_measurement
        MissingInput.EYE_TEST -> R.string.need_eye_test
        MissingInput.FULL_EYE_TEST -> R.string.need_full_eye_test
        MissingInput.COLOUR_VISION_TEST -> R.string.need_colour_vision_test
        MissingInput.MARKS -> R.string.need_marks
        MissingInput.ATTEMPTS_USED -> R.string.need_attempts
        MissingInput.MARITAL_STATUS -> R.string.need_marital_status
        MissingInput.NATIONALITY -> R.string.need_nationality
        MissingInput.HEALTH_DECLARATIONS -> R.string.need_health_declarations
        MissingInput.PRIOR_APPLICATIONS -> R.string.need_prior_applications
        MissingInput.PROFILE_SCOPE -> R.string.need_profile_scope
        MissingInput.VERIFIED_FIGURE -> R.string.need_verified_figure
    },
)

@Composable
private fun RiskReason.describe(): String = when (this) {
    is RiskReason.BodyMetricShortfall ->
        stringResource(R.string.risk_metric_short, shortfallBy.trimmed(), metric.unit())

    is RiskReason.BodyMetricExcess ->
        stringResource(R.string.risk_metric_over, excessBy.trimmed(), metric.unit())

    is RiskReason.TooYoungForCycle -> stringResource(R.string.risk_too_young)

    is RiskReason.SubjectsStillAhead ->
        stringResource(R.string.risk_subjects_ahead, missing.readable())

    is RiskReason.StageStillAhead -> stringResource(R.string.risk_stage_ahead)

    is RiskReason.StageNotYetPassed -> stringResource(R.string.risk_stage_not_passed)

    is RiskReason.MarksTarget -> stringResource(R.string.risk_marks_target, percentage.trimmed())

    is RiskReason.VisionBelowStandard -> stringResource(R.string.risk_vision)
}

@Composable
private fun BlockReason.describe(): String = when (this) {
    is BlockReason.BornBeforeWindow ->
        stringResource(R.string.block_born_before, earliest.shortDate())

    is BlockReason.BodyMetricFinal -> stringResource(R.string.block_metric_final)

    is BlockReason.ColourVisionBelow ->
        stringResource(R.string.block_colour_vision, required.name)

    is BlockReason.RefractiveSurgeryNotPermitted ->
        stringResource(R.string.block_refractive_surgery)

    is BlockReason.SubjectsLocked ->
        stringResource(R.string.block_subjects_locked, missing.readable())

    is BlockReason.MarksAlreadySat ->
        stringResource(R.string.block_marks_sat, required.trimmed())

    is BlockReason.AttemptsExhausted -> stringResource(R.string.block_attempts, max)

    is BlockReason.MustBeUnmarried -> stringResource(R.string.block_unmarried)

    is BlockReason.NationalityNotAccepted ->
        stringResource(R.string.block_nationality, accepted.joinToString(", "))

    is BlockReason.MedicalDisqualifier -> stringResource(R.string.block_medical)

    is BlockReason.PriorDisqualification -> stringResource(R.string.block_prior_disqualification)
}

@Composable
private fun BodyMetricKind.unit(): String = stringResource(
    if (this == BodyMetricKind.WEIGHT_KG) R.string.unit_kg else R.string.unit_cm,
)

private fun Set<Subject>.readable(): String =
    joinToString(", ") { it.name.lowercase().replace('_', ' ') }

private fun Double.trimmed(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()
