package app.foreway.ui

import app.foreway.domain.model.IndianStates
import app.foreway.domain.model.SchoolClass
import app.foreway.ui.home.RailStop
import app.foreway.ui.home.railFor
import app.foreway.ui.onboarding.DateOfBirthInput
import app.foreway.ui.onboarding.DateOfBirthInput.Result
import app.foreway.ui.onboarding.IndianStateNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class OnboardingLogicTest {

    private val today = LocalDate(2026, 10, 1)

    private fun parse(d: String, m: String, y: String) = DateOfBirthInput.parse(d, m, y, today)

    @Test
    fun `a real date in range is accepted`() {
        assertEquals(Result.Valid(LocalDate(2011, 3, 14)), parse("14", "3", "2011"))
    }

    @Test
    fun `dates that do not exist are refused`() {
        assertEquals(Result.NotADate, parse("31", "4", "2011"))
        assertEquals(Result.NotADate, parse("29", "2", "2011"))
        assertEquals(Result.NotADate, parse("1", "13", "2011"))
    }

    @Test
    fun `a leap day in a leap year is fine`() {
        assertEquals(Result.Valid(LocalDate(2012, 2, 29)), parse("29", "02", "2012"))
    }

    @Test
    fun `a typo that makes the student a toddler or a pensioner is caught`() {
        assertEquals(Result.InTheFuture, parse("14", "3", "2061"))
        assertEquals(Result.Implausible, parse("14", "3", "2021"))
        assertEquals(Result.Implausible, parse("14", "3", "1961"))
    }

    @Test
    fun `a two-digit year is incomplete, not 0011`() {
        assertEquals(Result.Incomplete, parse("14", "3", "11"))
    }

    @Test
    fun `rail shows one stop behind and what is ahead`() {
        val rail = railFor(SchoolClass.CLASS_10)
        assertEquals(
            listOf(SchoolClass.CLASS_9, SchoolClass.CLASS_10, SchoolClass.CLASS_11, SchoolClass.CLASS_12)
                .map { RailStop.InClass(it) },
            rail.stops,
        )
        assertEquals(1, rail.currentIndex)
    }

    @Test
    fun `rail starts at the first stop for class 8`() {
        val rail = railFor(SchoolClass.CLASS_8)
        assertEquals(0, rail.currentIndex)
        assertEquals(4, rail.stops.size)
    }

    @Test
    fun `rail ends after school for a class 12 pass`() {
        val rail = railFor(SchoolClass.PASSED_12)
        assertEquals(RailStop.AfterSchool, rail.stops[rail.currentIndex])
        assertEquals(listOf(RailStop.InClass(SchoolClass.CLASS_12), RailStop.AfterSchool), rail.stops)
    }

    @Test
    fun `every state code has a name, and every name a known code`() {
        assertEquals(IndianStates.codes, IndianStateNames.all.map { it.first }.toSet())
        assertEquals(IndianStates.codes.size, IndianStateNames.all.size)
    }
}
