package app.foreway.ui.pathway

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import app.foreway.domain.model.Timing
import app.foreway.ui.components.CriterionRow
import app.foreway.ui.components.Gutter
import app.foreway.ui.components.SourceLine
import app.foreway.ui.components.BackAction
import app.foreway.ui.components.UnregulatedNote
import app.foreway.ui.format.familyLabel
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTheme
import app.foreway.ui.theme.ForewayTypography
import app.foreway.ui.theme.LocalAccent

@Composable
fun PathwayScreen(
    onBack: () -> Unit,
    vm: PathwayViewModel = viewModel(factory = PathwayViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    PathwayContent(state, onBack)
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
fun PathwayContent(state: PathwayUiState, onBack: () -> Unit) {
    val family = (state as? PathwayUiState.Ready)?.career?.family
    ForewayTheme(family = family) {
        Column(
            Modifier
                .fillMaxSize()
                .background(ForewayColors.Canvas)
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 32.dp),
        ) {
            BackAction(stringResource(R.string.pathway_back), onBack, Modifier.padding(horizontal = 16.dp))
            when (state) {
                PathwayUiState.Loading -> Unit
                PathwayUiState.NotDownloaded -> Padded {
                    Text(stringResource(R.string.home_not_downloaded_title), style = ForewayTypography.headlineLarge, color = ForewayColors.Ink)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.home_not_downloaded_body), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
                }
                is PathwayUiState.Ready -> Ready(state)
            }
        }
    }
}

@Composable
private fun Ready(state: PathwayUiState.Ready) {
    val accent = LocalAccent.current
    val steps = state.pathway.steps

    Padded {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(familyLabel(state.career.family)).uppercase(),
            style = ForewayTypography.labelMedium,
            color = accent,
        )
        Spacer(Modifier.height(4.dp))
        Text(state.career.title, style = ForewayTypography.headlineLarge, color = ForewayColors.Ink)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.pathway_intro), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
        if (state.buildsOn.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
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
    val hereIndex = if (state.studentClass != null && steps.none { it.position == Position.NOW }) {
        steps.indexOfFirst { it.position == Position.AHEAD }.takeIf { it >= 0 }
    } else {
        null
    }

    Column(Modifier.padding(horizontal = Gutter)) {
        steps.forEachIndexed { i, step ->
            if (i == hereIndex) {
                YouAreHere(state.studentClass!!, isFirst = i == 0)
            }
            StepNode(step, isFirst = i == 0 && hereIndex != 0, isLast = i == steps.lastIndex)
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
            text = stringResource(R.string.pathway_you_are_here, classLabel(current)).uppercase(),
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

@Composable
private fun timingLabel(t: Timing): String = when (t) {
    is Timing.SchoolYears -> when {
        t.from == SchoolClass.PASSED_12 -> stringResource(R.string.timing_after_school)
        t.from == t.to -> stringResource(R.string.timing_one_year, t.from.number)
        t.to == SchoolClass.PASSED_12 -> stringResource(R.string.timing_from_stage, t.from.number)
        else -> stringResource(R.string.timing_school_years, t.from.number, t.to.number)
    }
    is Timing.FromStage ->
        if (t.stage == SchoolClass.PASSED_12) {
            stringResource(R.string.timing_after_school)
        } else {
            stringResource(R.string.timing_from_stage, t.stage.number)
        }
    Timing.Follows -> stringResource(R.string.timing_follows)
}

@Composable
private fun classLabel(c: SchoolClass): String = stringResource(
    when (c) {
        SchoolClass.CLASS_8 -> R.string.class_8
        SchoolClass.CLASS_9 -> R.string.class_9
        SchoolClass.CLASS_10 -> R.string.class_10
        SchoolClass.CLASS_11 -> R.string.class_11
        SchoolClass.CLASS_12 -> R.string.class_12
        SchoolClass.PASSED_12 -> R.string.class_passed_12
    },
)

@Preview(name = "Pathway, class 10", showBackground = true, widthDp = 360, heightDp = 1600)
@Composable
private fun PathwayPreview() {
    PathwayContent(PathwayFixture.forClass(SchoolClass.CLASS_10), onBack = {})
}

@Preview(name = "Pathway, class 12", showBackground = true, widthDp = 360, heightDp = 1600)
@Composable
private fun PathwayClass12Preview() {
    PathwayContent(PathwayFixture.forClass(SchoolClass.CLASS_12), onBack = {})
}

@Preview(name = "Pathway at 200% font", showBackground = true, widthDp = 360, heightDp = 2400, fontScale = 2.0f)
@Composable
private fun PathwayLargeTextPreview() {
    PathwayContent(PathwayFixture.forClass(SchoolClass.CLASS_10), onBack = {})
}
