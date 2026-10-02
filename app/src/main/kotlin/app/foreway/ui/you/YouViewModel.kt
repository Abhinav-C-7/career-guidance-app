package app.foreway.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.foreway.ForewayApp
import app.foreway.data.CareerSummary
import app.foreway.data.ContentRepository
import app.foreway.data.profile.ProfileRepository
import app.foreway.domain.model.StudentProfile
import app.foreway.sync.ContentSyncWorker
import app.foreway.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The questions a student can change from "You". One screen each (DESIGN.md). */
enum class ProfileQuestion { CLASS, BOARD, STATE, DATE_OF_BIRTH }

data class YouUiState(
    val profile: StudentProfile? = null,
    val goal: CareerSummary? = null,
    /** The date the phone last finished checking for content. Null before the first check. */
    val lastChecked: LocalDate? = null,
    /** A check the student asked for is queued or running. */
    val checking: Boolean = false,
    val canCheck: Boolean = false,
)

/**
 * What the student has told us, and nothing we were not told. Every answer here can be
 * changed or taken back; "Start over" forgets all of them.
 */
class YouViewModel(
    content: ContentRepository,
    private val profiles: ProfileRepository,
    syncStatus: SyncStatus,
    checks: Flow<List<WorkInfo>>,
    private val canCheck: Boolean,
    private val checkNow: () -> Unit,
) : ViewModel() {

    val state: StateFlow<YouUiState> = combine(
        profiles.saved,
        content.careers(),
        syncStatus.lastChecked,
        checks.map { infos -> infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING } },
    ) { saved, careers, checked, checking ->
        YouUiState(
            profile = saved?.profile,
            goal = saved?.goalCareerId?.let { id -> careers.firstOrNull { it.id == id } },
            lastChecked = checked?.toLocalDateTime(TimeZone.currentSystemDefault())?.date,
            checking = checking,
            canCheck = canCheck,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YouUiState())

    fun checkForUpdates() = checkNow()

    /** The root sees no profile and returns to onboarding. */
    fun startOver() {
        viewModelScope.launch { profiles.clear() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ForewayApp
                YouViewModel(
                    content = app.data.content,
                    profiles = app.data.profile,
                    syncStatus = SyncStatus(app),
                    checks = WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(ContentSyncWorker.CHECK_NOW),
                    // A build with no server configured has nothing to check.
                    canCheck = app.data.sync != null,
                    checkNow = { ContentSyncWorker.checkNow(app) },
                )
            }
        }
    }
}
