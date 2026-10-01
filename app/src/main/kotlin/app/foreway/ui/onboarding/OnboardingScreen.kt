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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.foreway.R
import app.foreway.domain.model.BoardCodes
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
                        item { DateOfBirthFields(state, actions) }
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

@Composable
private fun DateOfBirthFields(state: OnboardingUiState, actions: OnboardingActions) {
    val month = remember { FocusRequester() }
    val year = remember { FocusRequester() }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DigitField(
                label = stringResource(R.string.onb_dob_day),
                value = state.dobDay,
                onValueChange = { v ->
                    actions.dobChanged(v, state.dobMonth, state.dobYear)
                    if (v.length == 2) month.requestFocus()
                },
                modifier = Modifier.weight(1f),
            )
            DigitField(
                label = stringResource(R.string.onb_dob_month),
                value = state.dobMonth,
                onValueChange = { v ->
                    actions.dobChanged(state.dobDay, v, state.dobYear)
                    if (v.length == 2) year.requestFocus()
                },
                modifier = Modifier.weight(1f).focusRequester(month),
            )
            DigitField(
                label = stringResource(R.string.onb_dob_year),
                value = state.dobYear,
                onValueChange = { v -> actions.dobChanged(state.dobDay, state.dobMonth, v) },
                imeAction = ImeAction.Done,
                onDone = actions.next,
                modifier = Modifier.weight(1.5f).focusRequester(year),
            )
        }

        val problem = when (state.dobProblem) {
            DateOfBirthInput.Result.NotADate -> R.string.onb_dob_not_a_date
            DateOfBirthInput.Result.InTheFuture -> R.string.onb_dob_future
            DateOfBirthInput.Result.Implausible -> R.string.onb_dob_implausible
            else -> null
        }
        problem?.let {
            Spacer(Modifier.height(8.dp))
            // Bad input is one of the few places the error colour is allowed (DESIGN.md).
            Text(stringResource(it), style = ForewayTypography.bodyLarge, color = ForewayColors.StateError)
        }
    }
}

@Composable
private fun DigitField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    Column(modifier) {
        Text(label, style = ForewayTypography.labelSmall, color = ForewayColors.InkMuted)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = ForewayTypography.titleLarge.copy(color = ForewayColors.Ink),
            cursorBrush = SolidColor(ForewayColors.Ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .border(1.dp, ForewayColors.Hairline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
    }
}

private val classOptions = listOf(
    SchoolClass.CLASS_8 to R.string.class_8,
    SchoolClass.CLASS_9 to R.string.class_9,
    SchoolClass.CLASS_10 to R.string.class_10,
    SchoolClass.CLASS_11 to R.string.class_11,
    SchoolClass.CLASS_12 to R.string.class_12,
    SchoolClass.PASSED_12 to R.string.class_passed_12,
)

private val boardOptions = listOf(
    BoardCodes.CBSE to R.string.board_cbse,
    BoardCodes.CISCE to R.string.board_cisce,
    BoardCodes.STATE to R.string.board_state,
    BoardCodes.NIOS to R.string.board_nios,
    BoardCodes.INTERNATIONAL to R.string.board_international,
    BoardCodes.OTHER to R.string.board_other,
)

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
