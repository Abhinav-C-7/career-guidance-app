package app.foreway.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.foreway.R
import app.foreway.domain.engine.PathwayStep
import app.foreway.domain.engine.Position
import app.foreway.domain.model.Necessity
import app.foreway.domain.model.SchoolClass
import app.foreway.ui.art.CareerTile
import app.foreway.ui.art.FieldMosaic
import app.foreway.ui.art.HeroCard
import app.foreway.ui.art.HeroEyebrow
import app.foreway.ui.art.HeroText
import app.foreway.ui.art.HeroTitle
import app.foreway.ui.art.IconBadge
import app.foreway.ui.art.IconTile
import app.foreway.ui.art.LineageStrip
import app.foreway.ui.art.careerIcon
import app.foreway.ui.components.CardHeader
import app.foreway.ui.components.CardLink
import app.foreway.ui.components.CriterionRow
import app.foreway.ui.components.EmptyCard
import app.foreway.ui.components.ForewayCard
import app.foreway.ui.components.Gutter
import app.foreway.ui.components.Reveal
import app.foreway.ui.components.SourceLine
import app.foreway.ui.components.TimelineRail
import app.foreway.ui.components.UnregulatedNote
import app.foreway.ui.components.tappable
import app.foreway.ui.format.chainLabel
import app.foreway.ui.format.classLabel
import app.foreway.ui.format.familyLabel
import app.foreway.ui.format.timingLabel
import app.foreway.ui.goal.Goal
import app.foreway.ui.goal.GoalFixture
import app.foreway.ui.goal.GoalViewModel
import app.foreway.ui.shell.ForewayTopBar
import app.foreway.ui.shell.Tab
import app.foreway.ui.shell.TabScaffold
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTheme
import app.foreway.ui.theme.ForewayTypography
import app.foreway.ui.theme.LocalAccent
import app.foreway.ui.theme.LocalStrongAccent

/** How many gates Today shows before "See all". Closed doors sort first, so they always make it. */
private const val GATES_ON_TODAY = 3

private val Pill = RoundedCornerShape(percent = 50)

class TodayActions(
    val selectTab: (Tab) -> Unit,
    val openCareer: (careerId: String) -> Unit,
    val openGates: () -> Unit,
)

@Composable
fun TodayScreen(actions: TodayActions, vm: GoalViewModel = viewModel(factory = GoalViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    TodayContent(state, actions)
}

/**
 * The dashboard. It answers one question in two seconds: where am I, and what matters now?
 * The goal as a picture with the student's place in time, the next step, the gates to watch,
 * then what the goal leads to.
 *
 * Read from the local store only, so it opens with no network. No streak, no percentage,
 * no badge (DESIGN.md, anti-patterns).
 */
@Composable
fun TodayContent(state: Goal, actions: TodayActions) {
    val family = (state as? Goal.Ready)?.career?.family
    val scroll = rememberScrollState()
    ForewayTheme(family = family) {
        TabScaffold(
            topBar = { ForewayTopBar(stringResource(R.string.tab_today), scrolled = scroll.value > 0) },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(top = 8.dp, bottom = 24.dp),
            ) {
                when (state) {
                    Goal.Loading -> Unit
                    is Goal.None -> Reveal(0) {
                        EmptyCard(
                            title = stringResource(R.string.today_no_goal_title),
                            body = stringResource(if (state.roots.isEmpty()) R.string.home_no_goal_empty else R.string.today_no_goal_body),
                            action = if (state.roots.isEmpty()) null else stringResource(R.string.today_explore),
                            onAction = { actions.selectTab(Tab.EXPLORE) },
                            art = { FieldMosaic() },
                        )
                    }
                    Goal.NotDownloaded -> EmptyCard(
                        title = stringResource(R.string.home_not_downloaded_title),
                        body = stringResource(R.string.home_not_downloaded_body),
                        action = null,
                        onAction = {},
                    )
                    is Goal.Ready -> Ready(state, actions)
                }
            }
        }
    }
}

@Composable
private fun Ready(state: Goal.Ready, actions: TodayActions) {
    Reveal(0) { GoalCard(state, onChange = { actions.selectTab(Tab.EXPLORE) }) }

    state.nextStep?.let { step ->
        Spacer(Modifier.height(12.dp))
        Reveal(1) { NextStepCard(step, onSeePathway = { actions.selectTab(Tab.PATHWAY) }) }
    }

    Spacer(Modifier.height(12.dp))
    Reveal(2) { GatesCard(state, onSeeAll = actions.openGates) }

    if (state.specialisations.isNotEmpty()) {
        Spacer(Modifier.height(28.dp))
        Reveal(3) {
            Column {
                Row(Modifier.padding(horizontal = Gutter), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(R.drawable.ic_go_further, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.home_go_further),
                        style = ForewayTypography.titleLarge,
                        color = ForewayColors.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.home_go_further_body),
                    style = ForewayTypography.bodyLarge,
                    color = ForewayColors.InkMuted,
                    modifier = Modifier.padding(horizontal = Gutter),
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    // Sideways swipes scroll the tiles, not the system back gesture.
                    modifier = Modifier.systemGestureExclusion(),
                    contentPadding = PaddingValues(horizontal = Gutter),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.specialisations, key = { it.id }) { c ->
                        CareerTile(c, onClick = { actions.openCareer(c.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalCard(state: Goal.Ready, onChange: () -> Unit) {
    val accent = LocalAccent.current
    val strong = LocalStrongAccent.current
    val current = state.profile.currentClass
    val hasBody = !state.career.regulated || current != null

    HeroCard(
        family = state.career.family,
        seed = state.career.id,
        body = if (!hasBody) null else {
            {
                if (!state.career.regulated) {
                    UnregulatedNote()
                    if (current != null) Spacer(Modifier.height(20.dp))
                }
                current?.let {
                    val rail = railFor(it)
                    Text(stringResource(R.string.today_where_you_are), style = ForewayTypography.labelLarge, color = ForewayColors.InkMuted)
                    Spacer(Modifier.height(14.dp))
                    // Position in time, never progress (DESIGN.md).
                    TimelineRail(stages = rail.stops.map { s -> railLabel(s) }, currentIndex = rail.currentIndex, accent = accent)
                }
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroEyebrow(stringResource(familyLabel(state.career.family)), Modifier.weight(1f))
            // Never a dead end: the goal can always be changed from here.
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .padding(start = 12.dp)
                    .tappable(shape = Pill, onClick = onChange),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.today_change_goal),
                    style = ForewayTypography.labelLarge,
                    color = strong,
                    modifier = Modifier
                        .background(ForewayColors.Canvas, Pill)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                HeroTitle(state.career.title)
                state.career.summary?.let {
                    Spacer(Modifier.height(8.dp))
                    HeroText(it)
                }
            }
            Spacer(Modifier.width(12.dp))
            IconBadge(careerIcon(state.career))
        }
        if (state.buildsOn.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            LineageStrip(state.buildsOn + state.career)
            Spacer(Modifier.height(10.dp))
            HeroText(
                stringResource(R.string.pathway_builds_on, chainLabel(state.buildsOn)),
                small = true,
            )
        }
    }
}

@Composable
private fun NextStepCard(step: PathwayStep, onSeePathway: () -> Unit) {
    val m = step.milestone
    val now = step.position == Position.NOW
    ForewayCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(R.drawable.ic_flag, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            StepChip(
                text = stringResource(if (now) R.string.today_step_now else R.string.today_step_next, timingLabel(m.timing)),
                now = now,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(m.title, style = ForewayTypography.titleLarge, color = ForewayColors.Ink)
        if (m.necessity == Necessity.TYPICAL) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.pathway_common_route), style = ForewayTypography.labelSmall, color = ForewayColors.InkMuted)
        }
        Spacer(Modifier.height(8.dp))
        SourceLine(m.provenance, step.isStale)
        Spacer(Modifier.height(4.dp))
        CardLink(stringResource(R.string.today_see_pathway), onSeePathway)
    }
}

/** "Now · class 12" in the accent, or "Next · …" quietly: where this step sits in time. */
@Composable
private fun StepChip(text: String, now: Boolean) {
    Text(
        text = text.uppercase(),
        style = ForewayTypography.labelMedium,
        color = if (now) LocalStrongAccent.current else ForewayColors.InkMuted,
        modifier = Modifier
            .background(if (now) LocalAccent.current.copy(alpha = 0.12f) else ForewayColors.Surface, Pill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun GatesCard(state: Goal.Ready, onSeeAll: () -> Unit) {
    ForewayCard {
        CardHeader(R.drawable.ic_gate, stringResource(R.string.today_gates_title))
        if (state.gates.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                // An unregulated career has no gates by nature; a regulated one with none shown
                // has gates we have not yet verified. The two must never read the same.
                text = stringResource(if (state.career.regulated) R.string.home_no_criteria else R.string.home_no_gates_unregulated),
                style = ForewayTypography.bodyLarge,
                color = ForewayColors.InkMuted,
            )
        } else {
            Spacer(Modifier.height(4.dp))
            state.gates.take(GATES_ON_TODAY).forEach { CriterionRow(it) }
            CardLink(pluralStringResource(R.plurals.today_see_all_gates, state.gates.size, state.gates.size), onSeeAll)
        }
    }
}

@Composable
private fun railLabel(stop: RailStop): String = stringResource(
    when (stop) {
        RailStop.AfterSchool -> R.string.home_rail_after_school
        is RailStop.InClass ->
            if (stop.schoolClass == SchoolClass.PASSED_12) R.string.home_rail_after_school else classLabel(stop.schoolClass)
    },
)

private val noActions = TodayActions({}, {}, {})

@Preview(name = "Today", showBackground = true, widthDp = 360, heightDp = 1500)
@Composable
private fun TodayPreview() {
    TodayContent(GoalFixture.ready(SchoolClass.CLASS_10), noActions)
}

@Preview(name = "Today at 200% font", showBackground = true, widthDp = 360, heightDp = 2600, fontScale = 2.0f)
@Composable
private fun TodayLargeTextPreview() {
    TodayContent(GoalFixture.ready(SchoolClass.CLASS_10), noActions)
}

@Preview(name = "No goal", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun NoGoalPreview() {
    TodayContent(Goal.None(listOf(GoalFixture.career)), noActions)
}
