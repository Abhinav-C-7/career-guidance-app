package app.foreway.ui.format

import app.foreway.domain.model.BodyMetricKind
import app.foreway.domain.model.MaritalState
import app.foreway.domain.model.Requirement
import app.foreway.domain.model.SchoolStage
import kotlinx.datetime.LocalDate

/**
 * Turns a requirement into the short string shown beside its label.
 *
 * Every value here is read out of the record. Nothing in this file may introduce a number
 * of its own — see CLAUDE.md, "a literal age, height, mark or cutoff in a .kt file is a
 * bug, not a shortcut". Formatting a value is fine; inventing one is not.
 */
fun Requirement.displayValue(): String = when (this) {
    is Requirement.BornBetween ->
        "${earliest.shortDate()} – ${latest.shortDate()}"

    is Requirement.BodyMetric -> {
        val unit = if (metric == BodyMetricKind.WEIGHT_KG) "kg" else "cm"
        // Local copies: properties from another module cannot be smart-cast.
        val lo = atLeast
        val hi = atMost
        when {
            lo != null && hi != null -> "${lo.trim()}–${hi.trim()} $unit"
            lo != null -> "${lo.trim()} $unit minimum"
            hi != null -> "${hi.trim()} $unit maximum"
            else -> ""
        }
    }

    is Requirement.VisualStandard ->
        "6/$uncorrectedBetterEye uncorrected, 6/$bestCorrectedEachEye corrected"

    is Requirement.ColourVision -> atLeast.name

    is Requirement.CompletedStage ->
        stageLabel(stage) + if (appearingAccepted) ", or appearing" else ""

    is Requirement.SubjectsTaken ->
        subjects.joinToString(", ") { it.name.lowercase().replaceFirstChar(Char::uppercase) }

    is Requirement.MinimumMarks -> "${percentage.trim()}%"

    is Requirement.AttemptLimit ->
        if (maxAttempts == 1) "1 attempt" else "$maxAttempts attempts"

    is Requirement.MaritalStatus ->
        if (mustBe == MaritalState.UNMARRIED) "Unmarried" else "Any"

    is Requirement.Nationality -> accepted.joinToString(", ")

    is Requirement.NoPriorDisqualification -> "Must not apply"

    is Requirement.MedicalDisqualifier -> "Restricted"
}

private fun stageLabel(stage: SchoolStage): String = when (stage) {
    SchoolStage.CLASS_10 -> "Class 10 pass"
    SchoolStage.CLASS_12 -> "Class 12 pass"
    SchoolStage.UNDERGRADUATE -> "Degree"
}

/** Drops a trailing .0 so 157.0 reads as 157, which is how a notification writes it. */
private fun Double.trim(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()

private val months = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

fun LocalDate.shortDate(): String = "$dayOfMonth ${months[monthNumber - 1]} $year"

fun LocalDate.monthAndYear(): String = "${months[monthNumber - 1]} $year"
