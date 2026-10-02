package app.foreway.ui

import app.foreway.data.CareerSummary
import app.foreway.data.Steps
import app.foreway.data.profile.SavedProfile
import app.foreway.domain.engine.Position
import app.foreway.domain.model.CareerFamily
import app.foreway.domain.model.GateOutcome
import app.foreway.domain.model.SchoolClass
import app.foreway.domain.model.StudentProfile
import app.foreway.ui.goal.Goal
import app.foreway.ui.goal.GoalFixture
import app.foreway.ui.goal.GoalPathway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class GoalPathwayTest {

    private val today = LocalDate(2026, 10, 1)
    private val doctor = CareerSummary("doctor", "Doctor", CareerFamily.MEDICAL)
    private val specialist = CareerSummary("specialist", "Specialist", CareerFamily.MEDICAL, parentId = "doctor")
    private val surgeon = CareerSummary("surgeon", "Surgeon", CareerFamily.MEDICAL, parentId = "specialist")
    private val neuro = CareerSummary("neuro", "Neurosurgeon", CareerFamily.MEDICAL, parentId = "surgeon")
    private val careers = listOf(doctor, specialist, surgeon, neuro)

    private fun assemble(saved: SavedProfile?, all: List<CareerSummary> = careers) =
        GoalPathway.assemble(saved, all, emptyList(), Steps(emptyList(), 0), today)

    private fun saved(goal: String?) = SavedProfile(StudentProfile(currentClass = SchoolClass.CLASS_10), goal)

    @Test
    fun `no saved profile is still loading, not an empty goal`() {
        assertEquals(Goal.Loading, assemble(null))
    }

    @Test
    fun `no goal offers only the top-level careers`() {
        val goal = assertIs<Goal.None>(assemble(saved(null)))
        assertEquals(listOf(doctor), goal.roots)
    }

    @Test
    fun `a goal that has not synced yet says so rather than showing nothing`() {
        assertEquals(Goal.NotDownloaded, assemble(saved("neuro"), all = listOf(doctor)))
    }

    @Test
    fun `a specialisation knows every career it builds on, root first, and what it leads to`() {
        val ready = assertIs<Goal.Ready>(assemble(saved("specialist")))
        assertEquals(listOf(doctor), ready.buildsOn)
        assertEquals(listOf(surgeon), ready.specialisations)

        val deepest = assertIs<Goal.Ready>(assemble(saved("neuro")))
        assertEquals(listOf(doctor, specialist, surgeon), deepest.buildsOn)
        assertTrue(deepest.specialisations.isEmpty())
    }

    @Test
    fun `closed doors and risks come before anything already met`() {
        val gates = GoalFixture.ready(SchoolClass.CLASS_10).gates
        val rank = gates.map {
            when (it.outcome) {
                is GateOutcome.CannotBeMet -> 0
                is GateOutcome.AtRisk -> 1
                is GateOutcome.NotYetAssessed -> 2
                is GateOutcome.Met -> 3
            }
        }
        assertEquals(rank.sorted(), rank)
        assertIs<GateOutcome.CannotBeMet>(gates.first().outcome)
    }

    @Test
    fun `the next step is the one under way, else the first ahead`() {
        // Class 12: the written exam opens from class 12, so it is under way.
        val class12 = GoalFixture.ready(SchoolClass.CLASS_12)
        assertEquals(Position.NOW, class12.nextStep?.position)

        // Class 8: nothing has started yet, so the next step is the first one ahead.
        val class8 = GoalFixture.ready(SchoolClass.CLASS_8)
        assertEquals(Position.AHEAD, class8.nextStep?.position)
        assertEquals(class8.pathway.steps.first { it.position == Position.AHEAD }, class8.nextStep)
    }

    @Test
    fun `with no class we do not place the student, but still show a step`() {
        val unplaced = GoalFixture.ready(null)
        assertEquals(Position.UNPLACED, unplaced.nextStep?.position)
    }

    @Test
    fun `no steps means no next step, not a guess`() {
        val ready = assertIs<Goal.Ready>(assemble(saved("doctor")))
        assertNull(ready.nextStep)
    }
}
