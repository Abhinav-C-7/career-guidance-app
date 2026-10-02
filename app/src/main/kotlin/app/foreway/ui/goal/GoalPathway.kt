package app.foreway.ui.goal

import app.foreway.data.CareerSummary
import app.foreway.data.ContentRepository
import app.foreway.data.Steps
import app.foreway.data.profile.ProfileRepository
import app.foreway.data.profile.SavedProfile
import app.foreway.domain.engine.GateEvaluator
import app.foreway.domain.engine.Pathway
import app.foreway.domain.engine.PathwayBuilder
import app.foreway.domain.engine.PathwayStep
import app.foreway.domain.engine.Position
import app.foreway.domain.model.AssessedCriterion
import app.foreway.domain.model.Criterion
import app.foreway.domain.model.GateOutcome
import app.foreway.domain.model.Lineage
import app.foreway.domain.model.StudentProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate

/** The student's goal, assembled. Today and Pathway both read this, so they cannot disagree. */
sealed interface Goal {
    data object Loading : Goal

    /** No goal yet. [roots] are the top-level careers, for an empty state that leads somewhere. */
    data class None(val roots: List<CareerSummary>) : Goal

    /** A goal is saved but its career has not reached this phone yet. */
    data object NotDownloaded : Goal

    data class Ready(
        val career: CareerSummary,
        /** Broader careers this one builds on, root first. */
        val buildsOn: List<CareerSummary>,
        /** The next level down. */
        val specialisations: List<CareerSummary>,
        val profile: StudentProfile,
        /** Every gate in the chain, closed doors and risks first. */
        val gates: List<AssessedCriterion>,
        val pathway: Pathway,
        val unreadableSteps: Int,
    ) : Goal {
        /** The step under way, else the first one ahead, else the first we could not place. */
        val nextStep: PathwayStep?
            get() = pathway.steps.firstOrNull { it.position == Position.NOW }
                ?: pathway.steps.firstOrNull { it.position == Position.AHEAD }
                ?: pathway.steps.firstOrNull { it.position == Position.UNPLACED }
    }
}

/**
 * The goal's pathway for this student, from the local store only: the chain of careers
 * (a neurosurgeon's starts at MBBS), GateEvaluator over every criterion in it, and
 * PathwayBuilder laying the steps against the student's school years. Deterministic and
 * offline, with no model anywhere in the path (ARCHITECTURE.md).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalPathway(
    private val content: ContentRepository,
    private val profiles: ProfileRepository,
    private val today: () -> LocalDate,
    private val evaluator: GateEvaluator = GateEvaluator(),
    private val builder: PathwayBuilder = PathwayBuilder(),
) {
    val state: Flow<Goal> = combine(profiles.saved, content.careers()) { saved, careers -> saved to careers }
        .flatMapLatest { (saved, careers) ->
            val goal = saved?.goalCareerId
            val byId = careers.associateBy { it.id }
            when {
                saved == null || goal == null || byId[goal] == null ->
                    flowOf(assemble(saved, careers, emptyList(), Steps(emptyList(), 0), today(), evaluator, builder))
                else -> {
                    val chain = Lineage.chain(goal) { byId[it]?.parentId }
                    combine(content.criteriaFor(chain), content.milestonesFor(chain)) { criteria, steps ->
                        assemble(saved, careers, criteria, steps, today(), evaluator, builder)
                    }
                }
            }
        }

    companion object {
        /** Pure, so the whole assembly is tested without a database. */
        fun assemble(
            saved: SavedProfile?,
            careers: List<CareerSummary>,
            criteria: List<Criterion>,
            steps: Steps,
            asOf: LocalDate,
            evaluator: GateEvaluator = GateEvaluator(),
            builder: PathwayBuilder = PathwayBuilder(),
        ): Goal {
            if (saved == null) return Goal.Loading
            val goal = saved.goalCareerId ?: return Goal.None(careers.filter { it.parentId == null })
            val byId = careers.associateBy { it.id }
            val career = byId[goal] ?: return Goal.NotDownloaded
            val chain = Lineage.chain(goal) { byId[it]?.parentId }
            val assessed = evaluator.assess(criteria, saved.profile, asOf)
            return Goal.Ready(
                career = career,
                buildsOn = chain.dropLast(1).mapNotNull { byId[it] },
                specialisations = careers.filter { it.parentId == goal },
                profile = saved.profile,
                gates = ordered(assessed),
                pathway = builder.build(steps.milestones, assessed, saved.profile, asOf),
                unreadableSteps = steps.unreadable,
            )
        }

        /**
         * Closed doors and risks first. Principle 4: surfacing a disqualifier early is the
         * product, so it must not sit below a list of things already met. Stable within each
         * group, so the order is reproducible.
         */
        fun ordered(assessed: List<AssessedCriterion>): List<AssessedCriterion> =
            assessed.sortedBy {
                when (it.outcome) {
                    is GateOutcome.CannotBeMet -> 0
                    is GateOutcome.AtRisk -> 1
                    is GateOutcome.NotYetAssessed -> 2
                    is GateOutcome.Met -> 3
                }
            }
    }
}
