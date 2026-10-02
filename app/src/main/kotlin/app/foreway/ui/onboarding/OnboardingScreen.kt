package app.foreway.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.foreway.R
import app.foreway.domain.model.SchoolClass
import app.foreway.ui.components.Gutter
import app.foreway.ui.components.OptionRow
import app.foreway.ui.components.PrimaryCta
import app.foreway.ui.components.TextAction
import app.foreway.ui.format.familyLabel
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTheme
import app.foreway.ui.theme.ForewayTypography

@Composable
fun OnboardingScreen(vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()

    // Honour the system back gesture between questions; at the welcome screen it leaves.
    BackHandler(enabled = state.step != OnboardingStep.WELCOME && !state.saving) { vm.back() }

    OnboardingContent(
        state = state,
        actions = OnboardingActions(
            next = vm::next,
            skip = vm::skip,
            back = { vm.back() },
            selectClass = vm::selectClass,
            selectBoard = vm::selectBoard,
            selectState = vm::selectState,
            selectGoal = vm::selectGoal,
            dobChanged = vm::dobChanged,
        ),
    )
}

class OnboardingActions(
    val next: () -> Unit,
    val skip: () -> Unit,
    val back: () -> Unit,
    val selectClass: (SchoolClass) -> Unit,
    val selectBoard: (String) -> Unit,
    val selectState: (String) -> Unit,
    val selectGoal: (String) -> Unit,
    val dobChanged: (String, String, String) -> Unit,
)

@Composable
private fun OnboardingContent(state: OnboardingUiState, actions: OnboardingActions) {
    // No career is in context yet, so the neutral CTA colour (DESIGN.md).
    ForewayTheme(family = null) {
        Column(
            Modifier
                .fillMaxSize()
                .background(ForewayColors.Canvas)
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding(),
        ) {
            AnimatedContent(
                targetState = state.step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    // Horizontal push, 220ms, ease-out. Nothing bounces (DESIGN.md, motion).
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    val spec = tween<IntOffset>(220, easing = LinearOutSlowInEasing)
                    slideInHorizontally(spec) { it * dir } togetherWith slideOutHorizontally(spec) { -it * dir }
                },
                label = "onboarding-step",
            ) { step ->
                when (step) {
                    OnboardingStep.WELCOME -> Welcome()
                    OnboardingStep.CLASS -> Question(step, R.string.onb_class_title, R.string.onb_class_why, actions) {
                        items(classOptions) { (c, label) ->
                            OptionRow(stringResource(label), state.currentClass == c, { actions.selectClass(c) })
                        }
                    }
                    OnboardingStep.BOARD -> Question(step, R.string.onb_board_title, R.string.onb_board_why, actions) {
                        items(boardOptions) { (code, label) ->
                            OptionRow(stringResource(label), state.board == code, { actions.selectBoard(code) })
                        }
                        item { TextAction(stringResource(R.string.onb_board_skip), actions.skip) }
                    }
                    OnboardingStep.STATE -> Question(step, R.string.onb_state_title, R.string.onb_state_why, actions) {
                        items(IndianStateNames.all) { (code, label) ->
                            OptionRow(stringResource(label), state.state == code, { actions.selectState(code) })
                        }
                        item { TextAction(stringResource(R.string.onb_state_skip), actions.skip) }
                    }
                    OnboardingStep.DATE_OF_BIRTH -> Question(step, R.string.onb_dob_title, R.string.onb_dob_why, actions) {
                        item {
                            DateOfBirthFields(
                                day = state.dobDay,
                                month = state.dobMonth,
                                year = state.dobYear,
                                problem = state.dobProblem,
                                onChange = actions.dobChanged,
                                onDone = actions.next,
                            )
                        }
                        item { TextAction(stringResource(R.string.onb_dob_skip), actions.skip) }
                    }
                    OnboardingStep.GOAL -> Question(step, R.string.onb_goal_title, R.string.onb_goal_why, actions) {
                        if (state.careers.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.onb_goal_empty),
                                    style = ForewayTypography.bodyLarge,
                                    color = ForewayColors.InkMuted,
                                )
                            }
                        } else {
                            items(state.careers, key = { it.id }) { career ->
                                OptionRow(
                                    label = career.title,
                                    eyebrow = stringResource(familyLabel(career.family)),
                                    selected = state.goalCareerId == career.id,
                                    onSelect = { actions.selectGoal(career.id) },
                                )
                            }
                            item { TextAction(stringResource(R.string.onb_goal_skip), actions.skip) }
                        }
                    }
                }
            }

            PrimaryCta(
                label = stringResource(
                    when (state.step) {
                        OnboardingStep.WELCOME -> R.string.onb_start
                        OnboardingStep.GOAL -> R.string.onb_finish
                        else -> R.string.onb_continue
                    },
                ),
                onClick = actions.next,
                enabled = state.canContinue,
            )
        }
    }
}

@Composable
private fun Welcome() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Gutter),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            style = ForewayTypography.labelMedium,
            color = ForewayColors.InkMuted,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onb_welcome_title), style = ForewayTypography.headlineLarge, color = ForewayColors.Ink)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.onb_welcome_body), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.onb_welcome_privacy), style = ForewayTypography.labelLarge, color = ForewayColors.Ink)
    }
}

/**
 * One question: a back affordance, where we are, the question, why we ask, the answers.
 * The CTA lives outside, so it never moves between screens.
 */
@Composable
private fun Question(
    step: OnboardingStep,
    @StringRes title: Int,
    @StringRes why: Int,
    actions: OnboardingActions,
    answers: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextAction(stringResource(R.string.onb_back), actions.back)
                Spacer(Modifier.weight(1f))
                // Position, not progress: "2 of 5", never a bar filling up.
                Text(
                    text = stringResource(R.string.onb_step, step.ordinal, OnboardingViewModel.questionCount),
                    style = ForewayTypography.labelSmall,
                    color = ForewayColors.InkFaint,
                )
            }
        }
        item {
            Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                Text(stringResource(title), style = ForewayTypography.headlineLarge, color = ForewayColors.Ink)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(why), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
            }
        }
        answers()
    }
}

private val noActions = OnboardingActions({}, {}, {}, {}, {}, {}, {}, { _, _, _ -> })

@Preview(name = "Class", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun ClassPreview() {
    OnboardingContent(OnboardingUiState(step = OnboardingStep.CLASS, currentClass = SchoolClass.CLASS_10), noActions)
}

@Preview(name = "Date of birth, 200% font", showBackground = true, widthDp = 360, heightDp = 780, fontScale = 2.0f)
@Composable
private fun DobLargeTextPreview() {
    OnboardingContent(
        OnboardingUiState(
            step = OnboardingStep.DATE_OF_BIRTH,
            dobDay = "31",
            dobMonth = "04",
            dobYear = "2011",
            dobProblem = DateOfBirthInput.Result.NotADate,
        ),
        noActions,
    )
}

@Preview(name = "Welcome", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun WelcomePreview() {
    OnboardingContent(OnboardingUiState(), noActions)
}
