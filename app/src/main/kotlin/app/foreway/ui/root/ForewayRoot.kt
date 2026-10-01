package app.foreway.ui.root

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.foreway.ForewayApp
import app.foreway.data.profile.ProfileRepository
import app.foreway.ui.HomeRoute
import app.foreway.ui.PathwayRoute
import app.foreway.ui.home.HomeScreen
import app.foreway.ui.pathway.PathwayScreen
import app.foreway.ui.onboarding.OnboardingScreen
import app.foreway.ui.theme.ForewayColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class RootState { LOADING, ONBOARDING, HOME }

/**
 * The saved profile decides where the app opens. Onboarding never navigates to home; it
 * saves, and this flips. One source of truth, so the two can never disagree.
 */
class RootViewModel(profiles: ProfileRepository) : ViewModel() {
    val state: StateFlow<RootState> = profiles.saved
        .map { if (it == null) RootState.ONBOARDING else RootState.HOME }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RootState.LOADING)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RootViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ForewayApp).data.profile)
            }
        }
    }
}

@Composable
fun ForewayRoot(vm: RootViewModel = viewModel(factory = RootViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (state) {
        // A blank canvas for the few milliseconds Room takes, rather than a flash of
        // onboarding for a student who has already onboarded.
        RootState.LOADING -> Box(Modifier.fillMaxSize().background(ForewayColors.Canvas))
        RootState.ONBOARDING -> OnboardingScreen()
        RootState.HOME -> ForewayNavHost()
    }
}

/**
 * Everything after onboarding. Transitions are the horizontal push DESIGN.md specifies,
 * 220ms ease-out, and the system back gesture pops as expected.
 */
@Composable
private fun ForewayNavHost() {
    val nav = rememberNavController()
    val spec = tween<IntOffset>(220, easing = LinearOutSlowInEasing)
    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        enterTransition = { slideInHorizontally(spec) { it } },
        exitTransition = { slideOutHorizontally(spec) { -it / 3 } },
        popEnterTransition = { slideInHorizontally(spec) { -it / 3 } },
        popExitTransition = { slideOutHorizontally(spec) { it } },
    ) {
        composable<HomeRoute> {
            HomeScreen(onOpenPathway = { id -> nav.navigate(PathwayRoute(id)) })
        }
        composable<PathwayRoute> {
            PathwayScreen(onBack = { nav.popBackStack() })
        }
    }
}
