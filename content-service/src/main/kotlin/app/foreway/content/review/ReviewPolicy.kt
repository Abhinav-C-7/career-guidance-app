package app.foreway.content.review

import app.foreway.domain.model.ReviewState

/**
 * What a reviewer may do to a record in a given state. Pure, so the rules are tested
 * directly rather than through a browser.
 *
 * The screen hides buttons a reviewer cannot use, but the service checks these again on
 * every POST. A hidden button is a convenience; this is the rule.
 */
object ReviewPolicy {

    sealed interface Decision {
        data object Allowed : Decision
        data class Refused(val reason: String) : Decision
    }

    fun approve(state: ReviewState, readableByApp: Boolean): Decision = when {
        state == ReviewState.VERIFIED ->
            Decision.Refused("Already verified.")
        state == ReviewState.KNOWN_UNSOURCED ->
            Decision.Refused("A declared gap has no value to verify. It is already shown to students as a gap.")
        state == ReviewState.DRAFT ->
            Decision.Refused(
                "Drafts are blocked: either the model cannot express this rule yet, or a reviewer " +
                    "returned it. Fix the record in the repo and publish it again first.",
            )
        !readableByApp ->
            Decision.Refused(
                "The app cannot read this record. Approving it would put something on students' " +
                    "phones that renders as \"update the app\". Fix the record first.",
            )
        else -> Decision.Allowed
    }

    fun returnForCorrection(state: ReviewState): Decision =
        if (state == ReviewState.NEEDS_REVIEW) {
            Decision.Allowed
        } else {
            Decision.Refused("Only records waiting for review can be returned.")
        }

    /** Pull a verified record back off students' phones, for example when its source changed. */
    fun withdraw(state: ReviewState): Decision =
        if (state == ReviewState.VERIFIED) {
            Decision.Allowed
        } else {
            Decision.Refused("Only verified records are on students' phones.")
        }
}
