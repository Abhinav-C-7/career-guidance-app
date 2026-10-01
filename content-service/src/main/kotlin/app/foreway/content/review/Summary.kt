package app.foreway.content.review

import app.foreway.domain.model.Applicability
import app.foreway.domain.model.Requirement
import app.foreway.domain.model.Timing

/**
 * One line a reviewer can check against the PDF at a glance. The full JSON is shown under
 * it — this is a reading aid, not a substitute.
 *
 * Every field is printed. A reviewer who sees "157 cm minimum" but not that it was scoped
 * to one sex has not reviewed the record.
 */
object Summary {

    fun of(r: Requirement): String = when (r) {
        is Requirement.BornBetween -> "Born between ${r.earliest} and ${r.latest} (both inclusive)"
        is Requirement.BornOnOrBefore -> "Born on or before ${r.latest}; no upper age limit"
        is Requirement.BodyMetric -> buildString {
            append(r.metric.name.lowercase().replace('_', ' '))
            r.atLeast?.let { append(", at least $it") }
            r.atMost?.let { append(", at most $it") }
        }
        is Requirement.VisualStandard ->
            "Uncorrected better eye 6/${r.uncorrectedBetterEye}, worse eye 6/${r.uncorrectedWorseEye}; " +
                "best corrected each eye 6/${r.bestCorrectedEachEye}; " +
                "myopia max ${r.maxMyopiaDSph ?: "not stated"} D; hypermetropia max ${r.maxHypermetropiaDSph ?: "not stated"} D; " +
                "refractive surgery ${if (r.refractiveSurgeryPermitted) "permitted" else "not permitted"}"
        is Requirement.ColourVision -> "Colour vision at least ${r.atLeast}"
        is Requirement.CompletedStage ->
            "Completed ${r.stage}" + if (r.appearingAccepted) ", or appearing" else ", appearing NOT accepted"
        is Requirement.SubjectsTaken -> "Subjects ${r.subjects.joinToString()} at ${r.atStage}" +
            if (r.addableAfterwards) "; may be added afterwards as additional subjects" else "; locks once the stage is passed"
        is Requirement.MinimumMarks -> "At least ${r.percentage}% (${r.scope}) at ${r.atStage}"
        is Requirement.AttemptLimit -> "At most ${r.maxAttempts} attempts"
        is Requirement.MaritalStatus -> "Marital status: ${r.mustBe}"
        is Requirement.Nationality -> "Nationality one of ${r.accepted.joinToString()}"
        is Requirement.NoPriorDisqualification -> "Not previously disqualified: ${r.code}"
        is Requirement.MedicalDisqualifier -> "Disqualifying condition: ${r.conditionCode}"
    }

    fun of(a: Applicability): String {
        if (a == Applicability.Everyone) return "Everyone"
        return listOfNotNull(
            a.sex?.let { "sex ${it.joinToString()}" },
            a.states?.let { "states ${it.joinToString()}" },
            a.boards?.let { "boards ${it.joinToString()}" },
        ).joinToString("; ")
    }

    fun of(t: Timing): String = when (t) {
        is Timing.SchoolYears -> "During ${t.from} to ${t.to}"
        is Timing.FromStage -> "From ${t.stage} onward"
        Timing.Follows -> "Follows the previous step"
    }
}
