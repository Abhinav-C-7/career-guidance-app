package app.foreway.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.foreway.R
import app.foreway.domain.model.SchoolClass
import app.foreway.domain.model.Timing

/** "Class 11–12", "From class 12", "After school", "Next". */
@Composable
fun timingLabel(t: Timing): String = when (t) {
    is Timing.SchoolYears -> when {
        t.from == SchoolClass.PASSED_12 -> stringResource(R.string.timing_after_school)
        t.from == t.to -> stringResource(R.string.timing_one_year, t.from.number)
        t.to == SchoolClass.PASSED_12 -> stringResource(R.string.timing_from_stage, t.from.number)
        else -> stringResource(R.string.timing_school_years, t.from.number, t.to.number)
    }
    is Timing.FromStage ->
        if (t.stage == SchoolClass.PASSED_12) {
            stringResource(R.string.timing_after_school)
        } else {
            stringResource(R.string.timing_from_stage, t.stage.number)
        }
    Timing.Follows -> stringResource(R.string.timing_follows)
}
