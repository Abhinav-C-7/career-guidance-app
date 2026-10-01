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
import app.foreway.ui.components.CriterionRow
import app.foreway.ui.components.Gutter
import app.foreway.ui.components.TimelineRail
import app.foreway.ui.format.familyLabel
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTheme
import app.foreway.ui.theme.ForewayTypography
import app.foreway.ui.theme.LocalAccent

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    HomeContent(state, onChooseCareer = vm::chooseGoal)
}

/**
 * Home answers one question in two seconds: where am I, and is anything live?
 *
 * It reads only from the local store, so it opens with no network. There is no streak, no
 * percentage, no badge (DESIGN.md, anti-patterns).
 *
 * No primary CTA yet: its job is "view full pathway", and that screen does not exist. A
 * button that leads nowhere is worse than no button.
 */
@Composable
fun HomeContent(state: HomeUiState, onChooseCareer: (String) -> Unit) {
    val family = (state as? HomeUiState.Ready)?.career?.family
    ForewayTheme(family = family) {
        Box(
            Modifier
                .fillMaxSize()
                .background(ForewayColors.Canvas),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    // Inset padding must sit OUTSIDE the scroll, or it scrolls away and
                    // content slides under the status bar.
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp),
            ) {
                Spacer(Modifier.height(24.dp))
                when (state) {
                    HomeUiState.Loading -> Unit
                    is HomeUiState.Ready -> Ready(state)
                    is HomeUiState.NoGoal -> NoGoal(state.careers, onChooseCareer)
                    HomeUiState.NotDownloaded -> Message(
                        stringResource(R.string.home_not_downloaded_title),
                        stringResource(R.string.home_not_downloaded_body),
                    )
                }
            }
        }
    }
}

@Composable
private fun Ready(state: HomeUiState.Ready) {
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
            text = stringResource(R.string.home_no_criteria),
            style = ForewayTypography.bodyLarge,
            color = ForewayColors.InkMuted,
            modifier = Modifier.padding(horizontal = Gutter),
        )
        return
    }

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

@Composable
private fun NoGoal(careers: List<CareerSummary>, onChoose: (String) -> Unit) {
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

    careers.forEach { career ->
        Row(
            Modifier
                .padding(horizontal = Gutter, vertical = 4.dp)
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(ForewayColors.Card, RoundedCornerShape(16.dp))
                .border(1.dp, ForewayColors.Hairline, RoundedCornerShape(16.dp))
                .clickable(role = Role.Button) { onChoose(career.id) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(familyLabel(career.family)).uppercase(),
                    style = ForewayTypography.labelMedium,
                    color = ForewayColors.InkMuted,
                )
                Text(career.title, style = ForewayTypography.labelLarge, color = ForewayColors.Ink)
            }
            Text("›", style = ForewayTypography.titleLarge, color = ForewayColors.InkFaint)
        }
    }
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
    HomeContent(HomeFixture.ready, onChooseCareer = {})
}

/** The layout must survive a user running large text. DESIGN.md asks for 200%. */
@Preview(name = "Home at 200% font", showBackground = true, widthDp = 360, heightDp = 780, fontScale = 2.0f)
@Composable
private fun HomeLargeTextPreview() {
    HomeContent(HomeFixture.ready, onChooseCareer = {})
}

@Preview(name = "No goal", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun NoGoalPreview() {
    HomeContent(HomeUiState.NoGoal(listOf(HomeFixture.ready.career)), onChooseCareer = {})
}
