package app.foreway.domain.engine

import app.foreway.domain.model.GateOutcome
import app.foreway.domain.model.Lineage
import app.foreway.domain.model.Requirement
import app.foreway.domain.model.RiskReason
import app.foreway.domain.model.SchoolClass
import app.foreway.domain.model.SchoolStage
import app.foreway.domain.model.StudentProfile
import app.foreway.domain.model.Subject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.datetime.LocalDate

class CareerTreeTest {

    private val today = LocalDate(2026, 10, 2)
    private val evaluator = GateEvaluator()

    private fun eval(r: Requirement, p: StudentProfile) = evaluator.evaluate(r, p, today)

    @Test
    fun `lineage runs root first`() {
        val parents = mapOf("neurosurgeon" to "surgeon", "surgeon" to "specialist", "specialist" to "doctor")
        assertEquals(listOf("doctor", "specialist", "surgeon", "neurosurgeon"), Lineage.chain("neurosurgeon") { parents[it] })
    }

    @Test
    fun `a cycle in bad data stops the walk instead of looping`() {
        val parents = mapOf("a" to "b", "b" to "a")
        assertEquals(listOf("b", "a"), Lineage.chain("a") { parents[it] })
    }

    @Test
    fun `a minimum age with no upper limit never closes a door`() {
        val rule = Requirement.BornOnOrBefore(LocalDate(2009, 12, 31))
        assertIs<GateOutcome.Met>(eval(rule, StudentProfile(dateOfBirth = LocalDate(1990, 1, 1))))
        assertEquals(
            GateOutcome.AtRisk(RiskReason.TooYoungForCycle),
            eval(rule, StudentProfile(dateOfBirth = LocalDate(2011, 3, 14))),
        )
        assertIs<GateOutcome.NotYetAssessed>(eval(rule, StudentProfile()))
    }

    @Test
    fun `subjects that can be added after class 12 are a risk, not a closed door`() {
        val pcbe = setOf(Subject.PHYSICS, Subject.CHEMISTRY, Subject.BIOLOGY, Subject.ENGLISH)
        val passedWithPcm = StudentProfile(
            currentClass = SchoolClass.PASSED_12,
            subjectsTaken = setOf(Subject.PHYSICS, Subject.CHEMISTRY, Subject.MATHEMATICS, Subject.ENGLISH),
        )
        assertEquals(
            GateOutcome.AtRisk(RiskReason.SubjectsCanStillBeAdded(setOf(Subject.BIOLOGY))),
            eval(Requirement.SubjectsTaken(pcbe, SchoolStage.CLASS_12, addableAfterwards = true), passedWithPcm),
        )
        // Without the flag, the same student is shut out, as before.
        assertIs<GateOutcome.CannotBeMet>(eval(Requirement.SubjectsTaken(pcbe, SchoolStage.CLASS_12), passedWithPcm))
    }
}
