package app.foreway.content.review

import java.time.Clock
import java.time.LocalDate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Sign-off, return and withdrawal. Every action re-checks [ReviewPolicy] against the row
 * as it is now, not as the page showed it, and lands only if the row has not changed since
 * the reviewer opened it.
 */
@Service
class ReviewService(
    private val repo: ReviewRepository,
    private val clock: Clock,
) {

    sealed interface Outcome {
        data class Done(val message: String) : Outcome
        data class Refused(val reason: String) : Outcome
    }

    private val changedMeanwhile = Outcome.Refused(
        "This record changed after you opened it — someone else reviewed it, or it was republished. " +
            "Nothing was saved. Look at it again.",
    )

    @Transactional
    fun approve(kind: Kind, id: String, version: String, confirmed: Boolean, reviewer: String): Outcome {
        if (!confirmed) {
            return Outcome.Refused("Tick the box to confirm you checked this against the source document.")
        }
        val record = repo.find(kind, id) ?: return Outcome.Refused("No such record.")
        when (val d = ReviewPolicy.approve(record.review, isReadableByApp(record))) {
            is ReviewPolicy.Decision.Refused -> return Outcome.Refused(d.reason)
            ReviewPolicy.Decision.Allowed -> Unit
        }
        return if (record.version == version && repo.sign(kind, id, version, reviewer, today())) {
            Outcome.Done("Verified and signed as $reviewer. Students see it after their next sync.")
        } else {
            changedMeanwhile
        }
    }

    @Transactional
    fun returnForCorrection(kind: Kind, id: String, version: String, note: String, reviewer: String): Outcome {
        val reason = note.trim().takeIf { it.isNotEmpty() }
            ?: return Outcome.Refused("Say what is wrong, so whoever fixes it knows what to change.")
        val record = repo.find(kind, id) ?: return Outcome.Refused("No such record.")
        when (val d = ReviewPolicy.returnForCorrection(record.review)) {
            is ReviewPolicy.Decision.Refused -> return Outcome.Refused(d.reason)
            ReviewPolicy.Decision.Allowed -> Unit
        }
        val signed = "Returned by $reviewer on ${today()}: ${reason.take(MAX_NOTE)}"
        return if (repo.returnForCorrection(kind, id, version, signed)) {
            Outcome.Done("Returned for correction. It stays off students' phones until it is fixed and reviewed.")
        } else {
            changedMeanwhile
        }
    }

    @Transactional
    fun withdraw(kind: Kind, id: String, version: String, note: String, reviewer: String): Outcome {
        val reason = note.trim().takeIf { it.isNotEmpty() }
            ?: return Outcome.Refused("Say why it is being withdrawn.")
        val record = repo.find(kind, id) ?: return Outcome.Refused("No such record.")
        when (val d = ReviewPolicy.withdraw(record.review)) {
            is ReviewPolicy.Decision.Refused -> return Outcome.Refused(d.reason)
            ReviewPolicy.Decision.Allowed -> Unit
        }
        val signed = "Withdrawn by $reviewer on ${today()}: ${reason.take(MAX_NOTE)}"
        return if (repo.withdraw(kind, id, version, signed)) {
            Outcome.Done("Withdrawn. Phones drop it on their next sync.")
        } else {
            changedMeanwhile
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    companion object {
        private const val MAX_NOTE = 1000

        /** Would a phone decode this record? Uses the app's own model, strictly. */
        fun isReadableByApp(r: Record): Boolean {
            val applies = RecordReader.applicability(r.appliesTo) is RecordReader.Read.Ok
            val payload = when (r.kind) {
                Kind.CRITERION -> r.payload == null || RecordReader.requirement(r.payload) is RecordReader.Read.Ok
                Kind.STEP -> r.payload != null && RecordReader.stepBody(r.payload) is RecordReader.Read.Ok
            }
            return applies && payload
        }
    }
}
