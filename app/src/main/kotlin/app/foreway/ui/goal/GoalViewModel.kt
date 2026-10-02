package app.foreway.ui.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.foreway.ForewayApp
import app.foreway.ui.today
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Today, Pathway and All gates each hold one of these over the same [GoalPathway]. */
class GoalViewModel(goal: GoalPathway) : ViewModel() {

    val state: StateFlow<Goal> = goal.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Goal.Loading)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val data = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ForewayApp).data
                GoalViewModel(GoalPathway(data.content, data.profile, ::today))
            }
        }
    }
}
