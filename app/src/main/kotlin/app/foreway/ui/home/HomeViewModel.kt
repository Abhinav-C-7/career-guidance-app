package app.foreway.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.foreway.ForewayApp
import app.foreway.data.CareerSummary
import app.foreway.data.ContentRepository
import app.foreway.data.profile.ProfileRepository
import app.foreway.domain.engine.GateEvaluator
import app.foreway.domain.model.AssessedCriterion
import app.foreway.domain.model.GateOutcome
import app.foreway.domain.model.Lineage
import app.foreway.ui.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** They chose to browse first, or nothing had downloaded when they onboarded. */
    data class NoGoal(val careers: List<CareerSummary>) : HomeUiState

    /** A goal is saved but its content has not reached this phone yet. */
    data object NotDownloaded : HomeUiState

    data class Ready(
        val career: CareerSummary,
        /** Null when we were not told the class. We do not guess a position in time. */
        val rail: Rail?,
        /** Every gate in the career's chain: a neurosurgeon's include a doctor's. */
        val assessed: List<AssessedCriterion>,
        /** The next level down, offered as "go further". */
        val specialisations: List<CareerSummary> = emptyList(),
    ) : HomeUiState
}

/**
 * Home, from the local store only: the saved profile, the synced criteria for the goal,
 * and the rules engine between them. No network call renders this screen, and no model
 * call ever will (ARCHITECTURE.md).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    content: ContentRepository,
    profiles: ProfileRepository,
    today: () -> LocalDate,
    evaluator: GateEvaluator = GateEvaluator(),
) : ViewModel() {

    val state: StateFlow<HomeUiState> = profiles.saved
        .flatMapLatest { saved ->
            val goal = saved?.goalCareerId
            when {
                saved == null -> flowOf(HomeUiState.Loading)
                goal == null -> content.careers().map { all -> HomeUiState.NoGoal(all.filter { it.parentId == null }) }
                else -> content.careers().flatMapLatest { careers ->
                    val career = careers.firstOrNull { it.id == goal }
                    if (career == null) {
                        flowOf(HomeUiState.NotDownloaded)
                    } else {
                        val byId = careers.associateBy { it.id }
                        val chain = Lineage.chain(goal) { byId[it]?.parentId }
                        content.criteriaFor(chain).map { criteria ->
                            HomeUiState.Ready(
                                career = career,
                                rail = saved.profile.currentClass?.let(::railFor),
                                assessed = ordered(evaluator.assess(criteria, saved.profile, today())),
                                specialisations = careers.filter { it.parentId == goal },
                            )
                        }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val data = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ForewayApp).data
                HomeViewModel(data.content, data.profile, ::today)
            }
        }

        /**
         * Closed doors and risks first. Principle 4: surfacing a disqualifier early is the
         * product, so it must not sit below the fold under a list of things already met.
         * Stable within each group, so the order is reproducible.
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
