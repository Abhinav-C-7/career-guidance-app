package app.foreway.ui.you

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.foreway.ForewayApp
import app.foreway.R
import app.foreway.data.profile.ProfileRepository
import app.foreway.domain.model.SchoolClass
import app.foreway.domain.model.StudentProfile
import app.foreway.ui.components.OptionRow
import app.foreway.ui.components.PrimaryCta
import app.foreway.ui.components.TextAction
import app.foreway.ui.onboarding.DateOfBirthFields
import app.foreway.ui.onboarding.DateOfBirthInput
import app.foreway.ui.onboarding.IndianStateNames
import app.foreway.ui.onboarding.boardOptions
import app.foreway.ui.onboarding.classOptions
import app.foreway.ui.shell.ForewayTopBar
import app.foreway.ui.shell.PushedScaffold
import app.foreway.ui.theme.ForewayColors
import app.foreway.ui.theme.ForewayTheme
import app.foreway.ui.theme.ForewayTypography
import app.foreway.ui.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class EditAnswerUiState(
    val question: ProfileQuestion,
    val loaded: Boolean = false,
    val currentClass: SchoolClass? = null,
    /** Board or state code, for the two pick-one questions. */
    val code: String? = null,
    val dobDay: String = "",
    val dobMonth: String = "",
    val dobYear: String = "",
    val dobProblem: DateOfBirthInput.Result? = null,
    val done: Boolean = false,
) {
    val canSave: Boolean
        get() = loaded && when (question) {
            ProfileQuestion.CLASS -> currentClass != null
            ProfileQuestion.BOARD, ProfileQuestion.STATE -> code != null
            ProfileQuestion.DATE_OF_BIRTH -> dobDay.isNotEmpty() && dobMonth.isNotEmpty() && dobYear.length == 4
        }

    /** The class is the one answer the pathway cannot do without; the rest can be taken back. */
    val canClear: Boolean get() = question != ProfileQuestion.CLASS
}

/** Changes one answer. Saves only on "Save" or "Clear", never while the student is choosing. */
class EditAnswerViewModel(
    question: ProfileQuestion,
    private val profiles: ProfileRepository,
    private val today: () -> LocalDate,
) : ViewModel() {

    private val _state = MutableStateFlow(EditAnswerUiState(question))
    val state: StateFlow<EditAnswerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val p = profiles.saved.first()?.profile ?: StudentProfile()
            _state.update {
                it.copy(
                    loaded = true,
                    currentClass = p.currentClass,
                    code = when (question) {
                        ProfileQuestion.BOARD -> p.board
                        ProfileQuestion.STATE -> p.state
                        else -> null
                    },
                    dobDay = p.dateOfBirth?.dayOfMonth?.toString().orEmpty(),
                    dobMonth = p.dateOfBirth?.monthNumber?.toString().orEmpty(),
                    dobYear = p.dateOfBirth?.year?.toString().orEmpty(),
                )
            }
        }
    }

    fun selectClass(c: SchoolClass) = _state.update { it.copy(currentClass = c) }
    fun selectCode(code: String) = _state.update { it.copy(code = code) }
    fun dobChanged(day: String, month: String, year: String) = _state.update {
        it.copy(
            dobDay = day.filter(Char::isDigit).take(2),
            dobMonth = month.filter(Char::isDigit).take(2),
            dobYear = year.filter(Char::isDigit).take(4),
            dobProblem = null,
        )
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        when (s.question) {
            ProfileQuestion.CLASS -> write { copy(currentClass = s.currentClass) }
            ProfileQuestion.BOARD -> write { copy(board = s.code) }
            ProfileQuestion.STATE -> write { copy(state = s.code) }
            ProfileQuestion.DATE_OF_BIRTH ->
                when (val parsed = DateOfBirthInput.parse(s.dobDay, s.dobMonth, s.dobYear, today())) {
                    is DateOfBirthInput.Result.Valid -> write { copy(dateOfBirth = parsed.date) }
                    else -> _state.update { it.copy(dobProblem = parsed) }
                }
        }
    }

    /** "I would rather not say": back to not told, which the rules engine shows as "tell us". */
    fun clear() {
        when (_state.value.question) {
            ProfileQuestion.CLASS -> Unit
            ProfileQuestion.BOARD -> write { copy(board = null) }
            ProfileQuestion.STATE -> write { copy(state = null) }
            ProfileQuestion.DATE_OF_BIRTH -> write { copy(dateOfBirth = null) }
        }
    }

    private fun write(change: StudentProfile.() -> StudentProfile) {
        viewModelScope.launch {
            val saved = profiles.saved.first() ?: return@launch
            profiles.save(saved.copy(profile = saved.profile.change()))
            _state.update { it.copy(done = true) }
        }
    }

    companion object {
        fun factory(question: ProfileQuestion): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val data = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ForewayApp).data
                EditAnswerViewModel(question, data.profile, ::today)
            }
        }
    }
}

@Composable
fun EditAnswerScreen(
    question: ProfileQuestion,
    onDone: () -> Unit,
    vm: EditAnswerViewModel = viewModel(factory = EditAnswerViewModel.factory(question)),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    EditAnswerContent(
        state = state,
        onBack = onDone,
        selectClass = vm::selectClass,
        selectCode = vm::selectCode,
        dobChanged = vm::dobChanged,
        save = vm::save,
        clear = vm::clear,
    )
}

/** One question, as in onboarding: the question, why we ask, the answers, one CTA. */
@Composable
fun EditAnswerContent(
    state: EditAnswerUiState,
    onBack: () -> Unit,
    selectClass: (SchoolClass) -> Unit,
    selectCode: (String) -> Unit,
    dobChanged: (String, String, String) -> Unit,
    save: () -> Unit,
    clear: () -> Unit,
) {
    val list = rememberLazyListState()
    val q = state.question
    ForewayTheme(family = null) {
        PushedScaffold(
            topBar = {
                ForewayTopBar(
                    title = stringResource(labelFor(q)),
                    onBack = onBack,
                    scrolled = list.canScrollBackward,
                )
            },
        ) {
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth().imePadding(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Column(Modifier.padding(bottom = 12.dp)) {
                        Text(stringResource(titleFor(q)), style = ForewayTypography.headlineLarge, color = ForewayColors.Ink)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(whyFor(q)), style = ForewayTypography.bodyLarge, color = ForewayColors.InkMuted)
                    }
                }
                if (state.loaded) {
                    when (q) {
                        ProfileQuestion.CLASS -> items(classOptions) { (c, label) ->
                            OptionRow(stringResource(label), state.currentClass == c, { selectClass(c) })
                        }
                        ProfileQuestion.BOARD -> items(boardOptions) { (code, label) ->
                            OptionRow(stringResource(label), state.code == code, { selectCode(code) })
                        }
                        ProfileQuestion.STATE -> items(IndianStateNames.all) { (code, label) ->
                            OptionRow(stringResource(label), state.code == code, { selectCode(code) })
                        }
                        ProfileQuestion.DATE_OF_BIRTH -> item {
                            DateOfBirthFields(
                                day = state.dobDay,
                                month = state.dobMonth,
                                year = state.dobYear,
                                problem = state.dobProblem,
                                onChange = dobChanged,
                                onDone = save,
                            )
                        }
                    }
                    if (state.canClear) {
                        item { TextAction(stringResource(R.string.you_clear_answer), clear) }
                    }
                }
            }
            PrimaryCta(label = stringResource(R.string.you_save), onClick = save, enabled = state.canSave)
        }
    }
}

@StringRes
private fun labelFor(q: ProfileQuestion): Int = when (q) {
    ProfileQuestion.CLASS -> R.string.you_class
    ProfileQuestion.BOARD -> R.string.you_board
    ProfileQuestion.STATE -> R.string.you_state
    ProfileQuestion.DATE_OF_BIRTH -> R.string.you_dob
}

@StringRes
private fun titleFor(q: ProfileQuestion): Int = when (q) {
    ProfileQuestion.CLASS -> R.string.onb_class_title
    ProfileQuestion.BOARD -> R.string.onb_board_title
    ProfileQuestion.STATE -> R.string.onb_state_title
    ProfileQuestion.DATE_OF_BIRTH -> R.string.onb_dob_title
}

@StringRes
private fun whyFor(q: ProfileQuestion): Int = when (q) {
    ProfileQuestion.CLASS -> R.string.onb_class_why
    ProfileQuestion.BOARD -> R.string.onb_board_why
    ProfileQuestion.STATE -> R.string.onb_state_why
    ProfileQuestion.DATE_OF_BIRTH -> R.string.onb_dob_why
}
