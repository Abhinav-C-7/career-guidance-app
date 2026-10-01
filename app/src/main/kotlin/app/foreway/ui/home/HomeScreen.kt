package app.foreway.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.foreway.R
import app.foreway.data.CareerSummary
import app.foreway.domain.model.SchoolClass
import app.foreway.ui.components.CareerCard
import app.foreway.ui.components.CriterionRow
import app.foreway.ui.components.Gutter
import app.foreway.ui.components.PrimaryCta
import app.foreway.ui.components.BackAction
import app.foreway.ui.components.SecondaryAction
import app.foreway.ui.components.UnregulatedNote
import app.foreway.ui.components.TimelineRail
import app.foreway.ui.format.familyLabel
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTheme
import app.foreway.ui.theme.ForewayTypography
import app.foreway.ui.theme.LocalAccent

@Composable
fun HomeScreen(
    onOpenPathway: (careerId: String) -> Unit,
    onOpenCareer: (careerId: String) -> Unit,
    onBrowse: () -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    HomeContent(state, onOpenCareer = onOpenCareer, onBrowse = onBrowse, onOpenPathway = onOpenPathway)
}

/**
 * Home answers one question in two seconds: where am I, and is anything live?
 *
 * It reads only from the local store, so it opens with no network. There is no streak, no
 * percentage, no badge (DESIGN.md, anti-patterns).
 *
 * The one CTA opens the full pathway, and only appears when there is a career to open.
 */
@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenCareer: (String) -> Unit,
    onBrowse: () -> Unit = {},
    onOpenPathway: (String) -> Unit = {},
) {
    val family = (state as? HomeUiState.Ready)?.career?.family
    ForewayTheme(family = family) {
        val ready = state as? HomeUiState.Ready
        Column(
            Modifier
                .fillMaxSize()
                .background(ForewayColors.Canvas)
                // Inset padding must sit OUTSIDE the scroll, or it scrolls away and
                // content slides under the status bar.
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            // The CTA sits below the scrolling content rather than over it, so the last
            // criterion can never hide behind the button.
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                if (ready != null) {
                    // Top left, where every other screen keeps its way out. Home is the root,
                    // so this opens the career list rather than popping the stack.
                    BackAction(stringResource(R.string.home_all_careers), onBrowse, Modifier.padding(horizontal = 16.dp))
                } else {
                    Spacer(Modifier.height(24.dp))
                }
                when (state) {
                    HomeUiState.Loading -> Unit
                    is HomeUiState.Ready -> Ready(state, onOpenCareer, onBrowse)
                    is HomeUiState.NoGoal -> NoGoal(state.careers, onOpenCareer, onBrowse)
                    HomeUiState.NotDownloaded -> Message(
                        stringResource(R.string.home_not_downloaded_title),
                        stringResource(R.string.home_not_downloaded_body),
                    )
                }
            }

            if (ready != null) {
                PrimaryCta(
                    label = stringResource(R.string.home_view_pathway),
                    onClick = { onOpenPathway(ready.career.id) },
                )
            } else {
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun Ready(state: HomeUiState.Ready, onOpenCareer: (String) -> Unit, onBrowse: () -> Unit) {
    val accent = LocalAccent.current

    Text(
        text = stringResource(familyLabel(state.career.family)).uppercase(),
        style = ForewayTypography.labelMedium,
        color = accent,
        modifier = Modifier.padding(horizontal = Gutter),
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = state.career.title,
        style = ForewayTypography.headlineLarge,
        color = ForewayColors.Ink,
        modifier = Modifier.padding(horizontal = Gutter),
    )

    if (!state.career.regulated) {
        Spacer(Modifier.height(8.dp))
        UnregulatedNote(Modifier.padding(horizontal = Gutter))
    }

    state.rail?.let { rail ->
        Spacer(Modifier.height(24.dp))
        // Position in time, on the recessed surface DESIGN.md specifies.
        Box(
            Modifier
                .padding(horizontal = Gutter)
                .fillMaxWidth()
                .background(ForewayColors.Surface, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            TimelineRail(
                stages = rail.stops.map { railLabel(it) },
                currentIndex = rail.currentIndex,
                accent = accent,
            )
        }
    }

    Spacer(Modifier.height(28.dp))
    Text(
        text = stringResource(R.string.home_live_title),
        style = ForewayTypography.titleLarge,
        color = ForewayColors.Ink,
        modifier = Modifier.padding(horizontal = Gutter),
    )
    Spacer(Modifier.height(4.dp))

    if (state.assessed.isEmpty()) {
        Text(
            // An unregulated career has no gates by nature; a regulated one with none shown
            // has gates we have not yet verified. The two must never read the same.
            text = stringResource(if (state.career.regulated) R.string.home_no_criteria else R.string.home_no_gates_unregulated),
            style = ForewayTypography.bodyLarge,
            color = ForewayColors.InkMuted,
            modifier = Modifier.padding(horizontal = Gutter),
        )
    } else {
        Text(
            text = stringResource(R.string.home_live_body),
            style = ForewayTypography.bodyLarge,
            color = ForewayColors.InkMuted,
            modifier = Modifier.padding(horizontal = Gutter),
        )
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .padding(horizontal = Gutter)
                .fillMaxWidth()
                .background(ForewayColors.Card, RoundedCornerShape(20.dp))
                .border(1.dp, ForewayColors.Hairline, RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            state.assessed.forEach { CriterionRow(it) }
        }
    }

    if (state.specialisations.isNotEmpty()) {
        Spacer(Modifier.height(28.dp))
        Column(Modifier.padding(horizontal = Gutter)) {
            Text(stringResource(R.string.home_go_further), style = ForewayTypography.titleLarge, color = ForewayColors.Ink)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.home_go_further_body), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
            state.specialisations.forEach { c ->
                Spacer(Modifier.height(8.dp))
                CareerCard(c, specialisations = 0, onOpen = { onOpenCareer(c.id) })
            }
        }
    }
}

@Composable
private fun NoGoal(careers: List<CareerSummary>, onOpenCareer: (String) -> Unit, onBrowse: () -> Unit) {
    Text(
        text = stringResource(R.string.home_no_goal_title),
        style = ForewayTypography.headlineLarge,
        color = ForewayColors.Ink,
        modifier = Modifier.padding(horizontal = Gutter),
    )
    Spacer(Modifier.height(16.dp))

    if (careers.isEmpty()) {
        Text(
            text = stringResource(R.string.home_no_goal_empty),
            style = ForewayTypography.bodyLarge,
            color = ForewayColors.InkMuted,
            modifier = Modifier.padding(horizontal = Gutter),
        )
        return
    }

    Column(Modifier.padding(horizontal = Gutter)) {
        careers.forEach { career ->
            CareerCard(career, specialisations = 0, onOpen = { onOpenCareer(career.id) }, showFamily = true)
            Spacer(Modifier.height(8.dp))
        }
    }
    Spacer(Modifier.height(8.dp))
    SecondaryAction(stringResource(R.string.browse_all), onBrowse, Modifier.padding(horizontal = Gutter))
}

@Composable
private fun Message(title: String, body: String) {
    Column(Modifier.padding(horizontal = Gutter)) {
        Text(title, style = ForewayTypography.headlineLarge, color = ForewayColors.Ink)
        Spacer(Modifier.height(12.dp))
        Text(body, style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
    }
}

@Composable
private fun railLabel(stop: RailStop): String = stringResource(
    when (stop) {
        RailStop.AfterSchool -> R.string.home_rail_after_school
        is RailStop.InClass -> when (stop.schoolClass) {
            SchoolClass.CLASS_8 -> R.string.class_8
            SchoolClass.CLASS_9 -> R.string.class_9
            SchoolClass.CLASS_10 -> R.string.class_10
            SchoolClass.CLASS_11 -> R.string.class_11
            SchoolClass.CLASS_12 -> R.string.class_12
            SchoolClass.PASSED_12 -> R.string.home_rail_after_school
        }
    },
)

@Preview(name = "Home", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HomePreview() {
    HomeContent(HomeFixture.ready, onOpenCareer = {})
}

/** The layout must survive a user running large text. DESIGN.md asks for 200%. */
@Preview(name = "Home at 200% font", showBackground = true, widthDp = 360, heightDp = 780, fontScale = 2.0f)
@Composable
private fun HomeLargeTextPreview() {
    HomeContent(HomeFixture.ready, onOpenCareer = {})
}

@Preview(name = "No goal", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun NoGoalPreview() {
    HomeContent(HomeUiState.NoGoal(listOf(HomeFixture.ready.career)), onOpenCareer = {})
}
