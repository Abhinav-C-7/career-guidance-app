package app.foreway.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.foreway.ForewayApp
import app.foreway.data.CareerSummary
import app.foreway.data.ContentRepository
import app.foreway.data.profile.ProfileRepository
import app.foreway.data.profile.SavedProfile
import app.foreway.domain.model.SchoolClass
import app.foreway.domain.model.StudentProfile
import app.foreway.ui.today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

enum class OnboardingStep { WELCOME, CLASS, BOARD, STATE, DATE_OF_BIRTH, GOAL }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val currentClass: SchoolClass? = null,
    val board: String? = null,
    val state: String? = null,
    val dobDay: String = "",
    val dobMonth: String = "",
    val dobYear: String = "",
    /** Shown only after Continue is pressed. Nobody wants an error while still typing. */
    val dobProblem: DateOfBirthInput.Result? = null,
    val dateOfBirth: LocalDate? = null,
    val goalCareerId: String? = null,
    val careers: List<CareerSummary> = emptyList(),
    val saving: Boolean = false,
) {
    val canContinue: Boolean
        get() = when (step) {
            OnboardingStep.WELCOME -> true
            OnboardingStep.CLASS -> currentClass != null
            OnboardingStep.BOARD -> board != null
            OnboardingStep.STATE -> state != null
            OnboardingStep.DATE_OF_BIRTH -> dobDay.isNotEmpty() && dobMonth.isNotEmpty() && dobYear.length == 4
            // With nothing downloaded yet there is nothing to pick; let them through.
            OnboardingStep.GOAL -> goalCareerId != null || careers.isEmpty()
        } && !saving
}

/**
 * Collects the minimum needed to compute a pathway, one question at a time.
 *
 * Only the class is required. Every other answer can be skipped, and a skipped answer is
 * stored as null — "not told" — which the rules engine renders as "tell us", never as a
 * guess. Nothing is saved until the end, and nothing ever leaves the phone.
 */
class OnboardingViewModel(
    content: ContentRepository,
    private val profiles: ProfileRepository,
    private val today: () -> LocalDate,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        // Careers can arrive mid-onboarding if the first sync finishes while they type.
        viewModelScope.launch {
            content.careers().collect { list -> _state.update { it.copy(careers = list) } }
        }
    }

    fun selectClass(c: SchoolClass) = _state.update { it.copy(currentClass = c) }
    fun selectBoard(code: String) = _state.update { it.copy(board = code) }
    fun selectState(code: String) = _state.update { it.copy(state = code) }
    fun selectGoal(careerId: String) = _state.update { it.copy(goalCareerId = careerId) }

    fun dobChanged(day: String, month: String, year: String) = _state.update {
        it.copy(
            dobDay = day.filter(Char::isDigit).take(2),
            dobMonth = month.filter(Char::isDigit).take(2),
            dobYear = year.filter(Char::isDigit).take(4),
            dobProblem = null,
        )
    }

    fun next() {
        val s = _state.value
        if (!s.canContinue) return
        when (s.step) {
            OnboardingStep.DATE_OF_BIRTH -> {
                when (val parsed = DateOfBirthInput.parse(s.dobDay, s.dobMonth, s.dobYear, today())) {
                    is DateOfBirthInput.Result.Valid -> advance { copy(dateOfBirth = parsed.date) }
                    else -> _state.update { it.copy(dobProblem = parsed) }
                }
            }
            OnboardingStep.GOAL -> finish()
            else -> advance()
        }
    }

    /** "Not sure", "Skip", "I would rather not say". Clears the answer rather than keeping a half-typed one. */
    fun skip() {
        when (_state.value.step) {
            OnboardingStep.BOARD -> advance { copy(board = null) }
            OnboardingStep.STATE -> advance { copy(state = null) }
            OnboardingStep.DATE_OF_BIRTH -> advance {
                copy(dobDay = "", dobMonth = "", dobYear = "", dobProblem = null, dateOfBirth = null)
            }
            OnboardingStep.GOAL -> {
                _state.update { it.copy(goalCareerId = null) }
                finish()
            }
            else -> Unit
        }
    }

    /** Returns false at the first screen, so the system back gesture can leave the app. */
    fun back(): Boolean {
        val s = _state.value
        if (s.step == OnboardingStep.WELCOME || s.saving) return false
        _state.update { it.copy(step = OnboardingStep.entries[s.step.ordinal - 1]) }
        return true
    }

    private fun advance(change: OnboardingUiState.() -> OnboardingUiState = { this }) =
        _state.update {
            it.change().copy(step = OnboardingStep.entries[it.step.ordinal + 1])
        }

    private fun finish() {
        val s = _state.value
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            profiles.save(
                SavedProfile(
                    profile = StudentProfile(
                        dateOfBirth = s.dateOfBirth,
                        currentClass = s.currentClass,
                        board = s.board,
                        state = s.state,
                    ),
                    goalCareerId = s.goalCareerId,
                ),
            )
            // No navigation call: the root observes the saved profile and moves to home.
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val data = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ForewayApp).data
                OnboardingViewModel(data.content, data.profile, ::today)
            }
        }

        /** Questions only — the welcome screen is not one. */
        val questionCount: Int = OnboardingStep.entries.size - 1
    }
}
