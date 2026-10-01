package app.foreway.ui.browse

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
import app.foreway.domain.model.CareerFamily
import app.foreway.domain.model.Lineage
import app.foreway.ui.CareerRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

data class CareerNode(val career: CareerSummary, val specialisations: Int)

sealed interface BrowseUiState {
    data object Loading : BrowseUiState

    /** Top-level careers, grouped by family. Empty before the first sync. */
    data class Fields(val groups: List<Pair<CareerFamily?, List<CareerNode>>>) : BrowseUiState

    data class Career(
        val career: CareerSummary,
        /** Broader careers this builds on, root first. "Includes the steps to become…" */
        val buildsOn: List<CareerSummary>,
        val specialisations: List<CareerNode>,
        val isGoal: Boolean,
    ) : BrowseUiState

    data object NotFound : BrowseUiState
}

/**
 * Browsing the career tree, from fields down to the narrowest specialisation. Any level
 * can be the goal: a student sure of "doctor" but not yet of "neurosurgeon" picks doctor,
 * and home offers the next level when they are ready.
 */
class CareerBrowserViewModel(
    private val careerId: String?,
    content: ContentRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    val state: StateFlow<BrowseUiState> = combine(content.careers(), profiles.saved) { careers, saved ->
        fun node(c: CareerSummary) = CareerNode(c, careers.count { it.parentId == c.id })

        if (careerId == null) {
            BrowseUiState.Fields(
                careers.filter { it.parentId == null }
                    .groupBy { it.family }
                    .entries
                    .sortedBy { it.key?.ordinal ?: Int.MAX_VALUE }
                    .map { (family, list) -> family to list.map(::node) },
            )
        } else {
            val career = careers.firstOrNull { it.id == careerId }
            if (career == null) {
                BrowseUiState.NotFound
            } else {
                val byId = careers.associateBy { it.id }
                val chain = Lineage.chain(careerId) { byId[it]?.parentId }
                BrowseUiState.Career(
                    career = career,
                    buildsOn = chain.dropLast(1).mapNotNull { byId[it] },
                    specialisations = careers.filter { it.parentId == careerId }.map(::node),
                    isGoal = saved?.goalCareerId == careerId,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState.Loading)

    /** Saves the goal; the caller navigates home once this returns. */
    suspend fun makeGoal() {
        val id = careerId ?: return
        val saved = profiles.saved.first() ?: return
        profiles.save(saved.copy(goalCareerId = id))
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val data = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ForewayApp).data
                CareerBrowserViewModel(createSavedStateHandle().toRoute<CareerRoute>().careerId, data.content, data.profile)
            }
        }
    }
}
