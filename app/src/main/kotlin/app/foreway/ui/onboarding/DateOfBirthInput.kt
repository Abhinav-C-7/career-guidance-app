package app.foreway.ui.onboarding

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Day, month and year as typed, checked before anything is saved.
 *
 * Three numeric fields rather than a calendar picker: a picker scrolling back fifteen years
 * is slow on a budget phone, awkward at 200% font scale, and invites a wrong tap that
 * nobody notices. A wrong birth date is worse than none — every age gate is computed from
 * it — so this refuses anything implausible rather than storing it.
 */
object DateOfBirthInput {

    sealed interface Result {
        data class Valid(val date: LocalDate) : Result
        data object Incomplete : Result
        data object NotADate : Result
        data object InTheFuture : Result

        /**
         * Outside the ages this app serves. This is input sanity, not eligibility: the
         * bounds catch "2061" for "2016", and say nothing about any career.
         */
        data object Implausible : Result
    }

    /** Youngest and oldest plausible user, in years. Classes 8–12 and early college. */
    private const val YOUNGEST = 8
    private const val OLDEST = 35

    fun parse(day: String, month: String, year: String, today: LocalDate): Result {
        if (day.isBlank() || month.isBlank() || year.length != 4) return Result.Incomplete

        val d = day.toIntOrNull()
        val m = month.toIntOrNull()
        val y = year.toIntOrNull()
        if (d == null || m == null || y == null) return Result.NotADate

        val date = try {
            LocalDate(y, m, d)
        } catch (_: IllegalArgumentException) {
            // 31 April, 29 February in a non-leap year, month 13.
            return Result.NotADate
        }

        return when {
            date > today -> Result.InTheFuture
            date > today.minus(DatePeriod(years = YOUNGEST)) -> Result.Implausible
            date < today.minus(DatePeriod(years = OLDEST)) -> Result.Implausible
            else -> Result.Valid(date)
        }
    }
}
