package app.foreway.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.foreway.R
import app.foreway.data.CareerSummary
import app.foreway.domain.model.CareerFamily
import app.foreway.ui.components.CareerCard
import app.foreway.ui.components.Gutter
import app.foreway.ui.components.PrimaryCta
import app.foreway.ui.components.BackAction
import app.foreway.ui.components.UnregulatedNote
import app.foreway.ui.format.familyLabel
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTheme
import app.foreway.ui.theme.ForewayTypography
import app.foreway.ui.theme.LocalAccent
import kotlinx.coroutines.launch

@Composable
fun CareerBrowserScreen(
    onOpen: (careerId: String) -> Unit,
    onGoalChosen: () -> Unit,
    onBack: () -> Unit,
    vm: CareerBrowserViewModel = viewModel(factory = CareerBrowserViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    CareerBrowserContent(
        state = state,
        onOpen = onOpen,
        onMakeGoal = { scope.launch { vm.makeGoal(); onGoalChosen() } },
        onBack = onBack,
    )
}

/**
 * Two shapes, one screen: the list of fields, or one career with what it builds on and what
 * it leads to. The single CTA makes the open career the goal — browsing is free, choosing is
 * a deliberate act.
 */
@Composable
fun CareerBrowserContent(
    state: BrowseUiState,
    onOpen: (String) -> Unit,
    onMakeGoal: () -> Unit,
    onBack: () -> Unit,
) {
    val family = (state as? BrowseUiState.Career)?.career?.family
    ForewayTheme(family = family) {
        Column(
            Modifier
                .fillMaxSize()
                .background(ForewayColors.Canvas)
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                BackAction(stringResource(R.string.pathway_back), onBack, Modifier.padding(horizontal = 16.dp))
                when (state) {
                    BrowseUiState.Loading -> Unit
                    is BrowseUiState.Fields -> Fields(state, onOpen)
                    is BrowseUiState.Career -> Career(state, onOpen)
                    BrowseUiState.NotFound -> Padded {
                        Text(stringResource(R.string.home_not_downloaded_body), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
                    }
                }
            }

            when (state) {
                is BrowseUiState.Career -> PrimaryCta(
                    label = stringResource(if (state.isGoal) R.string.browse_is_goal else R.string.browse_make_goal),
                    onClick = onMakeGoal,
                    enabled = !state.isGoal,
                )
                else -> Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun Fields(state: BrowseUiState.Fields, onOpen: (String) -> Unit) {
    Padded {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.browse_title), style = ForewayTypography.headlineLarge, color = ForewayColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.browse_body), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
    }
    if (state.groups.isEmpty()) {
        Padded {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.home_no_goal_empty), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
        }
        return
    }
    state.groups.forEach { (family, nodes) ->
        Padded {
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(familyLabel(family)).uppercase(),
                style = ForewayTypography.labelMedium,
                color = ForewayColors.InkMuted,
            )
            nodes.forEach { n ->
                Spacer(Modifier.height(8.dp))
                CareerCard(n.career, n.specialisations, onOpen = { onOpen(n.career.id) })
            }
        }
    }
}

@Composable
private fun Career(state: BrowseUiState.Career, onOpen: (String) -> Unit) {
    val accent = LocalAccent.current
    Padded {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(familyLabel(state.career.family)).uppercase(),
            style = ForewayTypography.labelMedium,
            color = accent,
        )
        Spacer(Modifier.height(4.dp))
        Text(state.career.title, style = ForewayTypography.headlineLarge, color = ForewayColors.Ink)
        state.career.summary?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
        }
        if (state.buildsOn.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.pathway_builds_on, state.buildsOn.joinToString(" → ") { it.title }),
                style = ForewayTypography.labelSmall,
                color = ForewayColors.InkMuted,
            )
        }
        if (!state.career.regulated) {
            Spacer(Modifier.height(16.dp))
            UnregulatedNote()
        }
    }

    if (state.specialisations.isNotEmpty()) {
        Padded {
            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.home_go_further), style = ForewayTypography.titleLarge, color = ForewayColors.Ink)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.home_go_further_body), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
            state.specialisations.forEach { n ->
                Spacer(Modifier.height(8.dp))
                CareerCard(n.career, n.specialisations, onOpen = { onOpen(n.career.id) })
            }
        }
    }
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = Gutter)) { content() }
}

private val doctor = CareerSummary("doctor-mbbs", "Doctor (MBBS)", CareerFamily.MEDICAL, summary = "Diagnoses and treats patients.")
private val specialist = CareerSummary("specialist-md-ms", "Specialist doctor (MD or MS)", CareerFamily.MEDICAL, parentId = "doctor-mbbs")
private val surgeon = CareerSummary(
    "surgeon-general-surgery", "General surgeon (MS)", CareerFamily.MEDICAL,
    parentId = "specialist-md-ms", summary = "Operates across the body.",
)

@Preview(name = "Fields", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun FieldsPreview() {
    CareerBrowserContent(
        BrowseUiState.Fields(
            listOf(
                CareerFamily.DEFENCE to listOf(CareerNode(CareerSummary("nda", "Armed forces officer (NDA entry)", CareerFamily.DEFENCE), 0)),
                CareerFamily.MEDICAL to listOf(CareerNode(doctor, 1)),
                CareerFamily.ENGINEERING to listOf(
                    CareerNode(CareerSummary("se", "Software engineer", CareerFamily.ENGINEERING, regulated = false, summary = "Designs, builds and maintains software."), 2),
                ),
            ),
        ),
        onOpen = {}, onMakeGoal = {}, onBack = {},
    )
}

@Preview(name = "Career, 200% font", showBackground = true, widthDp = 360, heightDp = 1200, fontScale = 2.0f)
@Composable
private fun CareerPreview() {
    CareerBrowserContent(
        BrowseUiState.Career(surgeon, listOf(doctor, specialist), listOf(CareerNode(CareerSummary("neurosurgeon", "Neurosurgeon (MCh)", CareerFamily.MEDICAL, summary = "Operates on the brain, spine and nerves."), 0)), isGoal = false),
        onOpen = {}, onMakeGoal = {}, onBack = {},
    )
}
