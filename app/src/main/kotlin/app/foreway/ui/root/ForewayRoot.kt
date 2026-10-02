package app.foreway.ui.root

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.foreway.ForewayApp
import app.foreway.data.ContentRepository
import app.foreway.data.profile.ProfileRepository
import app.foreway.domain.model.CareerFamily
import app.foreway.ui.AboutRoute
import app.foreway.ui.CareerRoute
import app.foreway.ui.EditAnswerRoute
import app.foreway.ui.ExploreGraph
import app.foreway.ui.ExploreRoute
import app.foreway.ui.GatesRoute
import app.foreway.ui.PathwayGraph
import app.foreway.ui.PathwayRoute
import app.foreway.ui.TodayGraph
import app.foreway.ui.TodayRoute
import app.foreway.ui.YouGraph
import app.foreway.ui.YouRoute
import app.foreway.ui.browse.CareerScreen
import app.foreway.ui.browse.ExploreScreen
import app.foreway.ui.onboarding.OnboardingScreen
import app.foreway.ui.pathway.PathwayScreen
import app.foreway.ui.shell.ForewayBottomBar
import app.foreway.ui.shell.Tab
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTheme
import app.foreway.ui.today.GatesScreen
import app.foreway.ui.today.TodayActions
import app.foreway.ui.today.TodayScreen
import app.foreway.ui.you.AboutScreen
import app.foreway.ui.you.EditAnswerScreen
import app.foreway.ui.you.ProfileQuestion
import app.foreway.ui.you.YouScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class RootState { LOADING, ONBOARDING, HOME }

/**
 * The saved profile decides where the app opens. Onboarding never navigates to home; it
 * saves, and this flips. One source of truth, so the two can never disagree.
 */
class RootViewModel(profiles: ProfileRepository, content: ContentRepository) : ViewModel() {
    val state: StateFlow<RootState> = profiles.saved
        .map { if (it == null) RootState.ONBOARDING else RootState.HOME }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RootState.LOADING)

    /** The goal's field, so the shell can wear its colour. Null with no goal, or before it syncs. */
    val goalFamily: StateFlow<CareerFamily?> = combine(profiles.saved, content.careers()) { saved, careers ->
        saved?.goalCareerId?.let { id -> careers.firstOrNull { it.id == id }?.family }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val data = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ForewayApp).data
                RootViewModel(data.profile, data.content)
            }
        }
    }
}

@Composable
fun ForewayRoot(vm: RootViewModel = viewModel(factory = RootViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val goalFamily by vm.goalFamily.collectAsStateWithLifecycle()
    when (state) {
        // A blank canvas for the few milliseconds Room takes, rather than a flash of
        // onboarding for a student who has already onboarded.
        RootState.LOADING -> Box(Modifier.fillMaxSize().background(ForewayColors.Canvas))
        RootState.ONBOARDING -> OnboardingScreen()
        RootState.HOME -> AppShell(goalFamily)
    }
}

/**
 * Everything after onboarding: four tabs, each with its own back stack, and one bottom bar.
 *
 * Moving between tabs slides the whole screen the way the bar reads: a tab to the right comes
 * in from the right. Pushing a screen within a tab is DESIGN.md's horizontal push, and the
 * bar slides away beneath it. System back pops within the tab, then returns to Today, then
 * leaves the app — never a loop.
 */
@Composable
private fun AppShell(goalFamily: CareerFamily?) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val selectTab: (Tab) -> Unit = { tab -> nav.selectTab(tab) }
    val push = tween<IntOffset>(220, easing = LinearOutSlowInEasing)
    val slide = tween<IntOffset>(300, easing = FastOutSlowInEasing)

    Box(Modifier.fillMaxSize().background(ForewayColors.Surface)) {
        NavHost(
            navController = nav,
            startDestination = TodayGraph,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                when (val d = tabDirection()) {
                    0 -> slideInHorizontally(push) { it }
                    else -> slideInHorizontally(slide) { it * d }
                }
            },
            exitTransition = {
                when (val d = tabDirection()) {
                    0 -> slideOutHorizontally(push) { -it / 3 }
                    else -> slideOutHorizontally(slide) { -it * d }
                }
            },
            popEnterTransition = {
                when (val d = tabDirection()) {
                    0 -> slideInHorizontally(push) { -it / 3 }
                    else -> slideInHorizontally(slide) { it * d }
                }
            },
            popExitTransition = {
                when (val d = tabDirection()) {
                    0 -> slideOutHorizontally(push) { it }
                    else -> slideOutHorizontally(slide) { -it * d }
                }
            },
        ) {
            navigation<TodayGraph>(startDestination = TodayRoute) {
                composable<TodayRoute> {
                    TodayScreen(
                        TodayActions(
                            selectTab = selectTab,
                            // Careers live in Explore, so a specialisation opens there.
                            openCareer = { id -> nav.selectTab(Tab.EXPLORE); nav.navigate(CareerRoute(id)) },
                            openGates = { nav.navigate(GatesRoute) },
                        ),
                    )
                }
                composable<GatesRoute> { GatesScreen(onBack = { nav.popBackStack() }) }
            }
            navigation<PathwayGraph>(startDestination = PathwayRoute) {
                composable<PathwayRoute> { PathwayScreen(onSelectTab = selectTab) }
            }
            navigation<ExploreGraph>(startDestination = ExploreRoute) {
                composable<ExploreRoute> {
                    ExploreScreen(onSelectTab = selectTab, onOpen = { id -> nav.navigate(CareerRoute(id)) })
                }
                composable<CareerRoute> { backStackEntry ->
                    CareerScreen(
                        careerId = backStackEntry.toRoute<CareerRoute>().careerId,
                        onOpen = { id -> nav.navigate(CareerRoute(id)) },
                        // The new goal is Today's subject now: clear the browsing trail and go there.
                        onGoalChosen = {
                            nav.popBackStack(ExploreRoute, inclusive = false)
                            nav.selectTab(Tab.TODAY)
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            navigation<YouGraph>(startDestination = YouRoute) {
                composable<YouRoute> {
                    YouScreen(
                        onSelectTab = selectTab,
                        onEdit = { q -> nav.navigate(EditAnswerRoute(q.name)) },
                        onOpenAbout = { nav.navigate(AboutRoute) },
                    )
                }
                composable<EditAnswerRoute> { backStackEntry ->
                    EditAnswerScreen(
                        question = ProfileQuestion.valueOf(backStackEntry.toRoute<EditAnswerRoute>().question),
                        onDone = { nav.popBackStack() },
                    )
                }
                composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }) }
            }
        }

        // Shown on tab roots only. A pushed screen covers the whole height, so the bar
        // slides down out of its way rather than the screen resizing around it.
        val atRoot = entry?.destination?.let { d -> Tab.entries.any { d.hasRoute(it.root::class) } } ?: true
        AnimatedVisibility(
            visible = atRoot,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(push) { it },
            exit = slideOutVertically(push) { it },
        ) {
            ForewayTheme(family = goalFamily) {
                ForewayBottomBar(current = entry?.tab() ?: Tab.TODAY, onSelect = selectTab)
            }
        }
    }
}

/** The standard multi-back-stack switch: each tab keeps its own history. */
private fun NavController.selectTab(tab: Tab) {
    navigate(tab.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavBackStackEntry.tab(): Tab? =
    Tab.entries.firstOrNull { t -> destination.hierarchy.any { it.hasRoute(t.graph::class) } }

/**
 * Which way a transition moves along the bar: 1 to a tab on the right, -1 to one on the
 * left, 0 within a tab (a push or a pop).
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): Int {
    val from = initialState.tab() ?: return 0
    val to = targetState.tab() ?: return 0
    return to.ordinal.compareTo(from.ordinal)
}
