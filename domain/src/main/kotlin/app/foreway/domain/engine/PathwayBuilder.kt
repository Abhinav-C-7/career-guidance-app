package app.foreway.domain.engine

import app.foreway.domain.model.Applies
import app.foreway.domain.model.AssessedCriterion
import app.foreway.domain.model.Milestone
import app.foreway.domain.model.ReviewState
import app.foreway.domain.model.SchoolClass
import app.foreway.domain.model.StudentProfile
import app.foreway.domain.model.Timing
import kotlinx.datetime.LocalDate

/** Where a step sits relative to the student today. Position in time, never completion. */
public enum class Position {
    /** Its school years are behind the student. Says nothing about whether they did it. */
    PAST,

    /** The student is in the school years this step covers, or it is now open to them. */
    NOW,

    AHEAD,

    /** We were not told the student's class, so we do not guess where they are. */
    UNPLACED,
}

public data class PathwayStep(
    val milestone: Milestone,
    val position: Position,
    /** The assessed gates checked at this step, in the order the milestone lists them. */
    val gates: List<AssessedCriterion>,
    val isStale: Boolean,
)

public data class Pathway(
    val steps: List<PathwayStep>,
    /** Assessed gates not attached to any shown step. Rendered, never dropped. */
    val otherGates: List<AssessedCriterion>,
)

/**
 * Lays a career's milestones against one student's timeline and hangs each assessed gate
 * on the step where it is checked.
 *
 * Pure and deterministic like [GateEvaluator]: same inputs, same pathway, so a pathway can
 * be reproduced and diffed in a test.
 *
 * The app never claims a student has done a step. It cannot see exam portals, and was not
 * told. So positions are about time only: PAST means "those school years are behind you",
 * not "done".
 */
public class PathwayBuilder {

    public fun build(
        milestones: List<Milestone>,
        assessed: List<AssessedCriterion>,
        profile: StudentProfile,
        asOf: LocalDate,
    ): Pathway {
        val shown = milestones.filter {
            // The same gate as GateEvaluator: nothing unreviewed reaches a student, even
            // if it somehow reached the device.
            it.review == ReviewState.VERIFIED &&
                it.provenance.isInForce(asOf) &&
                it.appliesTo.appliesTo(profile) != Applies.NO
        }

        val byId = assessed.associateBy { it.criterion.id }
        val attached = mutableSetOf<String>()

        val steps = shown.map { m ->
            val position = position(m.timing, profile.currentClass)
            val gates = m.gates.mapNotNull { id -> byId[id]?.also { attached += id } }
            PathwayStep(m, position, gates, m.isStale(asOf))
        }

        return Pathway(steps, assessed.filter { it.criterion.id !in attached })
    }

    private fun position(timing: Timing, current: SchoolClass?): Position {
        if (current == null) return Position.UNPLACED
        return when (timing) {
            is Timing.SchoolYears -> when {
                current < timing.from -> Position.AHEAD
                current > timing.to -> Position.PAST
                else -> Position.NOW
            }
            // Open-ended: once open it stays open. We do not know if they have taken it.
            is Timing.FromStage -> if (current < timing.stage) Position.AHEAD else Position.NOW
            // Whatever came before, we cannot know they got past it. Ahead is the honest
            // default; it never claims progress we were not told about.
            Timing.Follows -> Position.AHEAD
        }
    }
}
