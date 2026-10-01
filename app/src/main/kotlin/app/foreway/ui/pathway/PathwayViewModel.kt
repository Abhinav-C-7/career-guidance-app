package app.foreway.ui.pathway

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import app.foreway.ForewayApp
import app.foreway.data.CareerSummary
import app.foreway.data.ContentRepository
import app.foreway.data.profile.ProfileRepository
import app.foreway.domain.engine.GateEvaluator
import app.foreway.domain.engine.Pathway
import app.foreway.domain.engine.PathwayBuilder
import app.foreway.domain.model.SchoolClass
import app.foreway.ui.PathwayRoute
import app.foreway.ui.today
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

sealed interface PathwayUiState {
    data object Loading : PathwayUiState
    data object NotDownloaded : PathwayUiState

    data class Ready(
        val career: CareerSummary,
        /** Null when we were not told. The screen then places nothing in time. */
        val studentClass: SchoolClass?,
        val pathway: Pathway,
        val unreadableSteps: Int,
    ) : PathwayUiState
}

/**
 * One career's pathway for this student, assembled on the device from the local store:
 * GateEvaluator judges each criterion, PathwayBuilder lays the steps against the
 * student's school years and hangs each gate on its step. Deterministic, offline, and
 * with no model anywhere in the path (ARCHITECTURE.md).
 */
class PathwayViewModel(
    careerId: String,
    content: ContentRepository,
    profiles: ProfileRepository,
    today: () -> LocalDate,
    evaluator: GateEvaluator = GateEvaluator(),
    builder: PathwayBuilder = PathwayBuilder(),
) : ViewModel() {

    val state: StateFlow<PathwayUiState> = combine(
        profiles.saved,
        content.careers(),
        content.criteriaFor(careerId),
        content.milestonesFor(careerId),
    ) { saved, careers, criteria, steps ->
        val career = careers.firstOrNull { it.id == careerId }
        if (saved == null || career == null) {
            if (saved == null) PathwayUiState.Loading else PathwayUiState.NotDownloaded
        } else {
            val asOf = today()
            val assessed = evaluator.assess(criteria, saved.profile, asOf)
            PathwayUiState.Ready(
                career = career,
                studentClass = saved.profile.currentClass,
                pathway = builder.build(steps.milestones, assessed, saved.profile, asOf),
                unreadableSteps = steps.unreadable,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PathwayUiState.Loading)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle: SavedStateHandle = createSavedStateHandle()
                val data = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ForewayApp).data
                PathwayViewModel(handle.toRoute<PathwayRoute>().careerId, data.content, data.profile, ::today)
            }
        }
    }
}
