package app.foreway.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.foreway.R
import app.foreway.domain.model.SchoolClass
import app.foreway.ui.components.CardHeader
import app.foreway.ui.components.CriterionRow
import app.foreway.ui.components.Reveal
import app.foreway.ui.components.ForewayCard
import app.foreway.ui.components.Gutter
import app.foreway.ui.goal.Goal
import app.foreway.ui.goal.GoalFixture
import app.foreway.ui.goal.GoalViewModel
import app.foreway.ui.shell.ForewayTopBar
import app.foreway.ui.shell.PushedScaffold
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTheme
import app.foreway.ui.theme.ForewayTypography

@Composable
fun GatesScreen(onBack: () -> Unit, vm: GoalViewModel = viewModel(factory = GoalViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    GatesContent(state, onBack)
}

/**
 * Every gate for the goal, in one list: closed doors and risks first, each with its receipt.
 * The pathway shows the same gates hung on their steps; this is the plain checklist.
 */
@Composable
fun GatesContent(state: Goal, onBack: () -> Unit) {
    val ready = state as? Goal.Ready
    val scroll = rememberScrollState()
    ForewayTheme(family = ready?.career?.family) {
        PushedScaffold(
            topBar = { ForewayTopBar(stringResource(R.string.gates_title), onBack = onBack, scrolled = scroll.value > 0) },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(top = 8.dp, bottom = 24.dp),
            ) {
                if (ready == null) return@Column
                Text(
                    stringResource(R.string.gates_body, ready.career.title),
                    style = ForewayTypography.bodyLarge,
                    color = ForewayColors.InkMuted,
                    modifier = Modifier.padding(horizontal = Gutter),
                )
                Spacer(Modifier.height(12.dp))
                Reveal(0) {
                    ForewayCard {
                        CardHeader(R.drawable.ic_gate, stringResource(R.string.today_gates_title))
                        Spacer(Modifier.height(4.dp))
                        if (ready.gates.isEmpty()) {
                            Text(
                                stringResource(if (ready.career.regulated) R.string.home_no_criteria else R.string.home_no_gates_unregulated),
                                style = ForewayTypography.bodyLarge,
                                color = ForewayColors.InkMuted,
                            )
                        } else {
                            ready.gates.forEach { CriterionRow(it) }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "All gates", showBackground = true, widthDp = 360, heightDp = 1400)
@Composable
private fun GatesPreview() {
    GatesContent(GoalFixture.ready(SchoolClass.CLASS_10), onBack = {})
}
