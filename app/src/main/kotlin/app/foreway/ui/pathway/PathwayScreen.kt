package app.foreway.ui.pathway

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.foreway.R
import app.foreway.domain.engine.PathwayStep
import app.foreway.domain.engine.Position
import app.foreway.domain.model.AssessedCriterion
import app.foreway.domain.model.Necessity
import app.foreway.domain.model.SchoolClass
import app.foreway.ui.art.FieldMosaic
import app.foreway.ui.art.HeroCard
import app.foreway.ui.art.HeroEyebrow
import app.foreway.ui.art.HeroText
import app.foreway.ui.art.HeroTitle
import app.foreway.ui.art.IconBadge
import app.foreway.ui.art.LineageStrip
import app.foreway.ui.art.careerIcon
import app.foreway.ui.components.CriterionRow
import app.foreway.ui.components.Reveal
import app.foreway.ui.components.Gutter
import app.foreway.ui.components.SourceLine
import app.foreway.ui.components.EmptyCard
import app.foreway.ui.components.UnregulatedNote
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

@Composable
fun PathwayScreen(
    onSelectTab: (Tab) -> Unit,
    vm: GoalViewModel = viewModel(factory = GoalViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    PathwayContent(state, onSelectTab)
}

/**
 * The pathway spine (DESIGN.md, "Timeline"): vertical, steps as nodes, the student's
 * position marked. Past is faint, now is the career accent and larger, ahead is hairline.
 * It scrolls; nothing animates on load.
 *
 * No percentage and no "completed" ticks anywhere. We were never told what the student has
 * done, so the screen only ever says where they are in time.
 */
@Composable
fun PathwayContent(state: Goal, onSelectTab: (Tab) -> Unit) {
    val family = (state as? Goal.Ready)?.career?.family
    val scroll = rememberScrollState()
    ForewayTheme(family = family) {
        TabScaffold(
            topBar = { ForewayTopBar(stringResource(R.string.tab_pathway), scrolled = scroll.value > 0) },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(top = 8.dp, bottom = 32.dp),
            ) {
                when (state) {
                    Goal.Loading -> Unit
                    is Goal.None -> Reveal(0) {
                        EmptyCard(
                            title = stringResource(R.string.pathway_no_goal_title),
                            body = stringResource(R.string.pathway_no_goal_body),
                            action = stringResource(R.string.today_explore),
                            onAction = { onSelectTab(Tab.EXPLORE) },
                            art = { FieldMosaic() },
                        )
                    }
                    Goal.NotDownloaded -> EmptyCard(
                        title = stringResource(R.string.home_not_downloaded_title),
                        body = stringResource(R.string.home_not_downloaded_body),
                        action = null,
                        onAction = {},
                    )
                    is Goal.Ready -> Ready(state)
                }
            }
        }
    }
}

@Composable
private fun Ready(state: Goal.Ready) {
    val steps = state.pathway.steps
    val studentClass = state.profile.currentClass

    Reveal(0) {
        HeroCard(family = state.career.family, seed = state.career.id) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    HeroEyebrow(stringResource(familyLabel(state.career.family)))
                    Spacer(Modifier.height(4.dp))
                    HeroTitle(state.career.title)
                }
                Spacer(Modifier.width(12.dp))
                IconBadge(careerIcon(state.career), size = 48.dp)
            }
            if (state.buildsOn.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LineageStrip(state.buildsOn + state.career)
                Spacer(Modifier.height(10.dp))
                HeroText(
                    stringResource(R.string.pathway_builds_on, chainLabel(state.buildsOn)),
                    small = true,
                )
            }
        }
    }
    Padded {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.pathway_intro), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
        if (!state.career.regulated) {
            Spacer(Modifier.height(16.dp))
            UnregulatedNote()
        }
        Spacer(Modifier.height(24.dp))
    }

    if (steps.isEmpty()) {
        Padded {
            Text(stringResource(R.string.pathway_no_steps), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
        }
        if (state.pathway.otherGates.isNotEmpty()) {
            GateGroup(stringResource(R.string.pathway_so_far), state.pathway.otherGates)
        }
        Unreadable(state.unreadableSteps)
        return
    }

    // "You are here" sits before the first step still ahead — but only when no step is
    // under way, since a NOW step already marks the student's place, and only when we
    // know their class at all.
    val hereIndex = if (studentClass != null && steps.none { it.position == Position.NOW }) {
        steps.indexOfFirst { it.position == Position.AHEAD }.takeIf { it >= 0 }
    } else {
        null
    }

    Column(Modifier.padding(horizontal = Gutter)) {
        steps.forEachIndexed { i, step ->
            Reveal(i + 1) {
                Column {
                    if (i == hereIndex) {
                        YouAreHere(studentClass!!, isFirst = i == 0)
                    }
                    StepNode(step, isFirst = i == 0 && hereIndex != 0, isLast = i == steps.lastIndex)
                }
            }
        }
    }

    if (state.pathway.otherGates.isNotEmpty()) {
        GateGroup(stringResource(R.string.pathway_other_gates), state.pathway.otherGates)
    }
    Unreadable(state.unreadableSteps)
}

@Composable
private fun StepNode(step: PathwayStep, isFirst: Boolean, isLast: Boolean) {
    val accent = LocalAccent.current
    val now = step.position == Position.NOW
    val m = step.milestone

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Rail(
            dotSize = if (now) 15.dp else 11.dp,
            dotFill = when (step.position) {
                Position.NOW -> accent
                Position.PAST -> ForewayColors.InkFaint
                Position.AHEAD, Position.UNPLACED -> ForewayColors.Canvas
            },
            dotRing = when (step.position) {
                Position.NOW -> accent
                Position.PAST -> ForewayColors.InkFaint
                Position.AHEAD -> ForewayColors.Hairline
                Position.UNPLACED -> ForewayColors.InkFaint
            },
            lineAbove = !isFirst,
            lineBelow = !isLast,
            lineColour = if (step.position == Position.PAST) ForewayColors.InkFaint else ForewayColors.Hairline,
        )
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f).padding(bottom = 28.dp)) {
            val timing = timingLabel(m.timing)
            Text(
                text = (if (now) stringResource(R.string.pathway_now, timing) else timing).uppercase(),
                style = ForewayTypography.labelMedium,
                color = if (now) accent else ForewayColors.InkMuted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = m.title,
                style = ForewayTypography.titleLarge,
                color = if (step.position == Position.PAST) ForewayColors.InkMuted else ForewayColors.Ink,
            )
            if (m.necessity == Necessity.TYPICAL) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.pathway_common_route),
                    style = ForewayTypography.labelSmall,
                    color = ForewayColors.InkMuted,
                )
            }
            m.detail?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
            }

            if (step.gates.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.pathway_checked_here),
                    style = ForewayTypography.labelSmall,
                    color = ForewayColors.InkMuted,
                )
                Spacer(Modifier.height(4.dp))
                GateCard(step.gates)
            }

            Spacer(Modifier.height(8.dp))
            // The step's own receipt. Its gates carry theirs inside the card.
            SourceLine(m.provenance, step.isStale)
        }
    }
}

@Composable
private fun YouAreHere(current: SchoolClass, isFirst: Boolean) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Rail(
            dotSize = 15.dp,
            dotFill = accent,
            dotRing = accent,
            lineAbove = !isFirst,
            lineBelow = true,
            lineColour = ForewayColors.Hairline,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.pathway_you_are_here, stringResource(classLabel(current))).uppercase(),
            style = ForewayTypography.labelMedium,
            color = accent,
            modifier = Modifier.padding(top = 1.dp, bottom = 28.dp),
        )
    }
}

/** One cell of the spine: a line in, the node, a line out. Fills the row's height. */
@Composable
private fun Rail(
    dotSize: Dp,
    dotFill: Color,
    dotRing: Color,
    lineAbove: Boolean,
    lineBelow: Boolean,
    lineColour: Color,
) {
    Column(
        Modifier.width(16.dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(2.dp).height(4.dp).background(if (lineAbove) lineColour else Color.Transparent))
        Box(
            Modifier
                .size(dotSize)
                .background(dotFill, CircleShape)
                .border(2.dp, dotRing, CircleShape),
        )
        Box(Modifier.width(2.dp).weight(1f).background(if (lineBelow) lineColour else Color.Transparent))
    }
}

@Composable
private fun GateCard(gates: List<AssessedCriterion>) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(ForewayColors.Card, RoundedCornerShape(16.dp))
            .border(1.dp, ForewayColors.Hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        gates.forEach { CriterionRow(it) }
    }
}

@Composable
private fun GateGroup(title: String, gates: List<AssessedCriterion>) {
    Padded {
        Spacer(Modifier.height(12.dp))
        Text(title, style = ForewayTypography.titleLarge, color = ForewayColors.Ink)
        Spacer(Modifier.height(12.dp))
        GateCard(gates)
    }
}

@Composable
private fun Unreadable(count: Int) {
    if (count == 0) return
    Padded {
        Spacer(Modifier.height(16.dp))
        Text(
            pluralStringResource(R.plurals.pathway_unreadable, count, count),
            style = ForewayTypography.bodyLarge,
            color = ForewayColors.StateAttention,
        )
    }
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = Gutter)) { content() }
}

@Preview(name = "Pathway, class 10", showBackground = true, widthDp = 360, heightDp = 1600)
@Composable
private fun PathwayPreview() {
    PathwayContent(GoalFixture.ready(SchoolClass.CLASS_10), onSelectTab = {})
}

@Preview(name = "Pathway, class 12", showBackground = true, widthDp = 360, heightDp = 1600)
@Composable
private fun PathwayClass12Preview() {
    PathwayContent(GoalFixture.ready(SchoolClass.CLASS_12), onSelectTab = {})
}

@Preview(name = "Pathway at 200% font", showBackground = true, widthDp = 360, heightDp = 2400, fontScale = 2.0f)
@Composable
private fun PathwayLargeTextPreview() {
    PathwayContent(GoalFixture.ready(SchoolClass.CLASS_10), onSelectTab = {})
}
