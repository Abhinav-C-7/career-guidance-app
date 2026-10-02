package app.foreway.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.foreway.R
import app.foreway.data.CareerSummary
import app.foreway.domain.model.CareerFamily
import app.foreway.domain.model.SchoolClass
import app.foreway.domain.model.StudentProfile
import app.foreway.ui.art.HeroCard
import app.foreway.ui.art.IconBadge
import app.foreway.ui.art.careerIcon
import app.foreway.ui.components.CardHeader
import app.foreway.ui.components.ForewayCard
import app.foreway.ui.components.Gutter
import app.foreway.ui.components.NavRow
import app.foreway.ui.components.Reveal
import app.foreway.ui.components.SecondaryAction
import app.foreway.ui.components.SectionTitle
import app.foreway.ui.components.tappable
import app.foreway.ui.format.classLabel
import app.foreway.ui.format.shortDate
import app.foreway.ui.onboarding.IndianStateNames
import app.foreway.ui.onboarding.boardOptions
import app.foreway.ui.shell.ForewayTopBar
import app.foreway.ui.shell.Tab
import app.foreway.ui.shell.TabScaffold
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTheme
import app.foreway.ui.theme.ForewayTypography
import app.foreway.ui.theme.LocalStrongAccent
import kotlinx.datetime.LocalDate

class YouActions(
    val selectTab: (Tab) -> Unit,
    val edit: (ProfileQuestion) -> Unit,
    val openAbout: () -> Unit,
    val checkForUpdates: () -> Unit,
    val startOver: () -> Unit,
)

@Composable
fun YouScreen(
    onSelectTab: (Tab) -> Unit,
    onEdit: (ProfileQuestion) -> Unit,
    onOpenAbout: () -> Unit,
    vm: YouViewModel = viewModel(factory = YouViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    YouContent(state, YouActions(onSelectTab, onEdit, onOpenAbout, vm::checkForUpdates, vm::startOver))
}

/**
 * What the student has told us — each answer, or "Not told" — and the few things a person
 * expects to find under their own name: the goal, when content was last checked, what the
 * app is, and a way to start over.
 */
@Composable
fun YouContent(state: YouUiState, actions: YouActions) {
    val scroll = rememberScrollState()
    var confirmStartOver by rememberSaveable { mutableStateOf(false) }
    val notTold = stringResource(R.string.you_not_told)
    val p = state.profile

    // The goal's colour, as everywhere else in the shell; neutral until there is a goal.
    ForewayTheme(family = state.goal?.family) {
        TabScaffold(
            topBar = { ForewayTopBar(stringResource(R.string.tab_you), scrolled = scroll.value > 0) },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(top = 8.dp, bottom = 24.dp),
            ) {
                Reveal(0) { ProfileHeader(state, onGoal = { actions.selectTab(Tab.EXPLORE) }) }
                Spacer(Modifier.height(28.dp))
                SectionTitle(stringResource(R.string.you_told_us))
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.you_told_us_body),
                    style = ForewayTypography.bodyLarge,
                    color = ForewayColors.InkMuted,
                    modifier = Modifier.padding(horizontal = Gutter),
                )
                Spacer(Modifier.height(12.dp))
                ForewayCard(padded = false) {
                    NavRow(
                        stringResource(R.string.you_class),
                        { actions.edit(ProfileQuestion.CLASS) },
                        value = p?.currentClass?.let { stringResource(classLabel(it)) } ?: notTold,
                        icon = R.drawable.ic_class,
                    )
                    NavRow(
                        stringResource(R.string.you_board),
                        { actions.edit(ProfileQuestion.BOARD) },
                        value = p?.board?.let { boardLabel(it) } ?: notTold,
                        divider = true,
                        icon = R.drawable.ic_board,
                    )
                    NavRow(
                        stringResource(R.string.you_state),
                        { actions.edit(ProfileQuestion.STATE) },
                        value = p?.state?.let { stateLabel(it) } ?: notTold,
                        divider = true,
                        icon = R.drawable.ic_place,
                    )
                    NavRow(
                        stringResource(R.string.you_dob),
                        { actions.edit(ProfileQuestion.DATE_OF_BIRTH) },
                        value = p?.dateOfBirth?.shortDate() ?: notTold,
                        divider = true,
                        icon = R.drawable.ic_calendar,
                    )
                }

                Spacer(Modifier.height(12.dp))
                ForewayCard(padded = false) {
                    NavRow(
                        stringResource(R.string.you_goal),
                        { actions.selectTab(Tab.EXPLORE) },
                        value = state.goal?.title ?: stringResource(R.string.you_no_goal),
                        icon = state.goal?.let { careerIcon(it) } ?: R.drawable.ic_flag,
                    )
                }

                Spacer(Modifier.height(24.dp))
                SectionTitle(stringResource(R.string.you_content))
                Spacer(Modifier.height(12.dp))
                ForewayCard {
                    CardHeader(R.drawable.ic_refresh, stringResource(R.string.you_last_checked))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = when {
                            state.checking -> stringResource(R.string.you_checking)
                            state.lastChecked != null -> state.lastChecked.shortDate()
                            else -> stringResource(R.string.you_never_checked)
                        },
                        style = ForewayTypography.bodyLarge,
                        color = ForewayColors.Ink,
                    )
                    if (state.canCheck && !state.checking) {
                        Spacer(Modifier.height(12.dp))
                        SecondaryAction(stringResource(R.string.you_check_now), actions.checkForUpdates)
                    }
                }

                Spacer(Modifier.height(12.dp))
                ForewayCard(padded = false) {
                    NavRow(stringResource(R.string.you_about), actions.openAbout, icon = R.drawable.ic_info)
                    NavRow(stringResource(R.string.you_start_over), { confirmStartOver = true }, divider = true, icon = R.drawable.ic_restart)
                }
            }
        }

        if (confirmStartOver) {
            AlertDialog(
                onDismissRequest = { confirmStartOver = false },
                title = { Text(stringResource(R.string.you_start_over_title), style = ForewayTypography.titleLarge) },
                text = { Text(stringResource(R.string.you_start_over_body), style = ForewayTypography.bodyLarge) },
                confirmButton = {
                    TextButton(onClick = { confirmStartOver = false; actions.startOver() }) {
                        Text(stringResource(R.string.you_start_over_confirm), color = ForewayColors.Ink)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmStartOver = false }) {
                        Text(stringResource(R.string.you_keep), color = ForewayColors.Ink)
                    }
                },
                containerColor = ForewayColors.Card,
            )
        }
    }
}

/**
 * The student at a glance, on the goal's colour: only what they told us, and the goal, which
 * opens Explore to change it.
 */
@Composable
private fun ProfileHeader(state: YouUiState, onGoal: () -> Unit) {
    val p = state.profile
    val summary = listOfNotNull(
        p?.currentClass?.let { stringResource(classLabel(it)) },
        p?.board?.let { boardLabel(it) },
        p?.state?.let { stateLabel(it) },
    ).joinToString(" · ").ifEmpty { stringResource(R.string.you_header_empty) }
    val strong = LocalStrongAccent.current
    val pill = RoundedCornerShape(percent = 50)

    HeroCard(family = state.goal?.family, seed = state.goal?.id ?: "you") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(R.drawable.ic_tab_you, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            Text(summary, style = ForewayTypography.titleLarge, color = ForewayColors.Canvas, modifier = Modifier.weight(1f))
        }
        state.goal?.let { goal ->
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .tappable(shape = pill, onClick = onGoal)
                    .background(ForewayColors.Canvas, pill)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(careerIcon(goal)), contentDescription = null, tint = strong, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.you_goal_chip, goal.title), style = ForewayTypography.labelLarge, color = strong)
            }
        }
    }
}

@Composable
private fun boardLabel(code: String): String =
    boardOptions.firstOrNull { it.first == code }?.let { stringResource(it.second) } ?: code

@Composable
private fun stateLabel(code: String): String =
    IndianStateNames.all.firstOrNull { it.first == code }?.let { stringResource(it.second) } ?: code

@Preview(name = "You", showBackground = true, widthDp = 360, heightDp = 1100)
@Composable
private fun YouPreview() {
    YouContent(
        YouUiState(
            profile = StudentProfile(currentClass = SchoolClass.CLASS_11, board = "CBSE", state = "IN-KL"),
            goal = CareerSummary("neurosurgeon", "Neurosurgeon (MCh)", CareerFamily.MEDICAL),
            lastChecked = LocalDate(2026, 10, 2),
            canCheck = true,
        ),
        YouActions({}, {}, {}, {}, {}),
    )
}
