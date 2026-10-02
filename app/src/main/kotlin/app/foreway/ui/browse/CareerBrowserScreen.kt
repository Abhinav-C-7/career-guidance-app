package app.foreway.ui.browse

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.foreway.R
import app.foreway.data.CareerSummary
import app.foreway.domain.model.CareerFamily
import app.foreway.ui.art.FieldTile
import app.foreway.ui.art.HeroCard
import app.foreway.ui.art.HeroEyebrow
import app.foreway.ui.art.HeroText
import app.foreway.ui.art.HeroTitle
import app.foreway.ui.art.IconBadge
import app.foreway.ui.art.IconTile
import app.foreway.ui.art.LineageStrip
import app.foreway.ui.art.careerIcon
import app.foreway.ui.art.familyIcon
import app.foreway.ui.components.CardLabel
import app.foreway.ui.components.CareerCard
import app.foreway.ui.components.EmptyCard
import app.foreway.ui.components.ForewayCard
import app.foreway.ui.components.Gutter
import app.foreway.ui.components.NavRow
import app.foreway.ui.components.PrimaryCta
import app.foreway.ui.components.Reveal
import app.foreway.ui.components.UnregulatedNote
import app.foreway.ui.format.familyLabel
import app.foreway.ui.shell.ForewayTopBar
import app.foreway.ui.shell.PushedScaffold
import app.foreway.ui.shell.Tab
import app.foreway.ui.shell.TabScaffold
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTheme
import app.foreway.ui.theme.ForewayTypography
import app.foreway.ui.theme.accentFor
import app.foreway.ui.theme.strongAccentFor
import kotlinx.coroutines.launch

/** The Explore tab's root: every field, and the top-level careers in each. */
@Composable
fun ExploreScreen(
    onSelectTab: (Tab) -> Unit,
    onOpen: (careerId: String) -> Unit,
    vm: CareerBrowserViewModel = viewModel(factory = CareerBrowserViewModel.factory(null)),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    ExploreContent(state, onSelectTab, onOpen)
}

@Suppress("UNUSED_PARAMETER") // onSelectTab: kept so every tab root takes the same actions.
@Composable
fun ExploreContent(state: BrowseUiState, onSelectTab: (Tab) -> Unit, onOpen: (String) -> Unit) {
    val scroll = rememberScrollState()
    ForewayTheme(family = null) {
        TabScaffold(
            topBar = { ForewayTopBar(stringResource(R.string.tab_explore), scrolled = scroll.value > 0) },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(top = 8.dp, bottom = 24.dp),
            ) {
                if (state is BrowseUiState.Fields) Fields(state, onOpen)
            }
        }
    }
}

/**
 * One career, pushed over the tabs: its picture, what it builds on and what it leads to.
 * The single CTA makes it the goal — browsing is free, choosing is a deliberate act.
 */
@Composable
fun CareerScreen(
    careerId: String,
    onOpen: (careerId: String) -> Unit,
    onGoalChosen: () -> Unit,
    onBack: () -> Unit,
    vm: CareerBrowserViewModel = viewModel(factory = CareerBrowserViewModel.factory(careerId)),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    CareerContent(
        state = state,
        onOpen = onOpen,
        onMakeGoal = { scope.launch { vm.makeGoal(); onGoalChosen() } },
        onBack = onBack,
    )
}

@Composable
fun CareerContent(
    state: BrowseUiState,
    onOpen: (String) -> Unit,
    onMakeGoal: () -> Unit,
    onBack: () -> Unit,
) {
    val career = (state as? BrowseUiState.Career)?.career
    val scroll = rememberScrollState()
    // The title moves into the bar once the one in the hero has scrolled away.
    val titleGone = with(LocalDensity.current) { scroll.value > 150.dp.toPx() }
    ForewayTheme(family = career?.family) {
        PushedScaffold(
            topBar = {
                ForewayTopBar(
                    title = if (titleGone) career?.title.orEmpty() else "",
                    onBack = onBack,
                    scrolled = scroll.value > 0,
                )
            },
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(top = 8.dp, bottom = 24.dp),
            ) {
                when (state) {
                    is BrowseUiState.Career -> Career(state, onOpen)
                    BrowseUiState.NotFound -> EmptyCard(
                        title = stringResource(R.string.home_not_downloaded_title),
                        body = stringResource(R.string.home_not_downloaded_body),
                        action = null,
                        onAction = {},
                    )
                    else -> Unit
                }
            }

            if (state is BrowseUiState.Career) {
                PrimaryCta(
                    label = stringResource(if (state.isGoal) R.string.browse_is_goal else R.string.browse_make_goal),
                    onClick = onMakeGoal,
                    enabled = !state.isGoal,
                )
            } else {
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }
}

/**
 * Fields as a row of coloured tiles that filter the list below — "All" first, so nothing is
 * ever hidden by default — then each field's top-level careers.
 */
@Composable
private fun Fields(state: BrowseUiState.Fields, onOpen: (String) -> Unit) {
    // The chosen field's name, or null for all of them. Survives leaving the tab and coming back.
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val total = state.groups.sumOf { it.careers }

    Padded {
        Text(stringResource(R.string.browse_body), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
    }
    if (state.groups.isEmpty()) {
        Padded {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.home_no_goal_empty), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
        }
        return
    }

    Spacer(Modifier.height(16.dp))
    Reveal(0) {
        LazyRow(
            // A sideways swipe here scrolls the tiles; without this, a swipe that starts near
            // the edge is taken as the system back gesture and leaves Explore.
            modifier = Modifier.selectableGroup().systemGestureExclusion(),
            // The tiles carry a 5dp ring margin of their own; this lines their faces up with the gutter.
            contentPadding = PaddingValues(horizontal = Gutter - 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "all") {
                FieldTile(
                    label = stringResource(R.string.explore_all),
                    detail = pluralStringResource(R.plurals.explore_careers, total, total),
                    icon = R.drawable.ic_tab_explore,
                    family = null,
                    selected = chosen == null,
                    onClick = { chosen = null },
                )
            }
            items(state.groups, key = { it.key }) { g ->
                FieldTile(
                    label = stringResource(familyLabel(g.family)),
                    detail = pluralStringResource(R.plurals.explore_careers, g.careers, g.careers),
                    icon = familyIcon(g.family),
                    family = g.family,
                    selected = chosen == g.key,
                    onClick = { chosen = if (chosen == g.key) null else g.key },
                )
            }
        }
    }

    AnimatedContent(
        targetState = chosen,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
        label = "field-filter",
    ) { pick ->
        Column {
            state.groups.filter { pick == null || it.key == pick }.forEachIndexed { i, g ->
                Reveal(i + 1) { Group(g, onOpen) }
            }
        }
    }
}

private val FieldGroup.key: String get() = family?.name ?: "OTHER"

@Composable
private fun Group(group: FieldGroup, onOpen: (String) -> Unit) {
    val family = group.family
    Padded {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                icon = familyIcon(family),
                accent = family?.let { accentFor(it) } ?: ForewayColors.CtaDark,
                strong = strongAccentFor(family),
                size = 32.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(familyLabel(family)).uppercase(),
                style = ForewayTypography.labelMedium,
                color = ForewayColors.InkMuted,
                modifier = Modifier.semantics { heading() },
            )
        }
        group.roots.forEach { n ->
            Spacer(Modifier.height(10.dp))
            CareerCard(n.career, n.specialisations, onOpen = { onOpen(n.career.id) })
        }
    }
}

@Composable
private fun Career(state: BrowseUiState.Career, onOpen: (String) -> Unit) {
    val career = state.career
    Reveal(0) {
        HeroCard(family = career.family, seed = career.id) {
            HeroEyebrow(stringResource(familyLabel(career.family)))
            Spacer(Modifier.height(14.dp))
            IconBadge(careerIcon(career), size = 64.dp)
            Spacer(Modifier.height(16.dp))
            HeroTitle(career.title)
            career.summary?.let {
                Spacer(Modifier.height(8.dp))
                HeroText(it)
            }
            if (state.buildsOn.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                LineageStrip(state.buildsOn + career)
            }
        }
    }

    // What it builds on, each one a step back up the tree.
    if (state.buildsOn.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Reveal(1) {
            ForewayCard(padded = false) {
                CardLabel(
                    stringResource(R.string.career_builds_on_title),
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp),
                )
                state.buildsOn.forEachIndexed { i, c ->
                    NavRow(c.title, { onOpen(c.id) }, icon = careerIcon(c), divider = i > 0)
                }
            }
        }
    }

    if (!career.regulated) {
        Padded {
            Spacer(Modifier.height(12.dp))
            UnregulatedNote()
        }
    }

    if (state.specialisations.isNotEmpty()) {
        Reveal(2) {
            Padded {
                Spacer(Modifier.height(28.dp))
                Text(stringResource(R.string.home_go_further), style = ForewayTypography.titleLarge, color = ForewayColors.Ink)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.home_go_further_body), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
                state.specialisations.forEach { n ->
                    Spacer(Modifier.height(10.dp))
                    CareerCard(n.career, n.specialisations, onOpen = { onOpen(n.career.id) })
                }
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

@Preview(name = "Explore", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun ExplorePreview() {
    ExploreContent(
        BrowseUiState.Fields(
            listOf(
                FieldGroup(CareerFamily.DEFENCE, listOf(CareerNode(CareerSummary("nda-officer-entry", "Armed forces officer (NDA entry)", CareerFamily.DEFENCE), 0)), 1),
                FieldGroup(CareerFamily.MEDICAL, listOf(CareerNode(doctor, 1)), 6),
                FieldGroup(
                    CareerFamily.ENGINEERING,
                    listOf(CareerNode(CareerSummary("software-engineer", "Software engineer", CareerFamily.ENGINEERING, regulated = false, summary = "Designs, builds and maintains software."), 2)),
                    3,
                ),
            ),
        ),
        onSelectTab = {}, onOpen = {},
    )
}

@Preview(name = "Career, 200% font", showBackground = true, widthDp = 360, heightDp = 1600, fontScale = 2.0f)
@Composable
private fun CareerPreview() {
    CareerContent(
        BrowseUiState.Career(surgeon, listOf(doctor, specialist), listOf(CareerNode(CareerSummary("neurosurgeon", "Neurosurgeon (MCh)", CareerFamily.MEDICAL, summary = "Operates on the brain, spine and nerves."), 0)), isGoal = false),
        onOpen = {}, onMakeGoal = {}, onBack = {},
    )
}
