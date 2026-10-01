package app.foreway.content.web

import app.foreway.content.review.Career
import app.foreway.content.review.Kind
import app.foreway.content.review.Record
import app.foreway.content.review.RecordReader
import app.foreway.content.review.ReviewPolicy
import app.foreway.content.review.ReviewRepository
import app.foreway.content.review.ReviewService
import app.foreway.content.review.Summary
import app.foreway.domain.model.Necessity
import app.foreway.domain.model.ReviewState
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** A row in the queue. */
data class QueueItem(val kind: String, val id: String, val label: String, val signedBy: String?, val note: String?)

data class CareerQueue(
    val id: String,
    val title: String,
    val needsReview: List<QueueItem>,
    val blocked: List<QueueItem>,
    val published: List<QueueItem>,
)

/** Everything the record page shows, already worked out. Templates only display. */
data class RecordView(
    val record: Record,
    val kindLabel: String,
    val careerTitle: String,
    val careerNotes: List<String>,
    val summary: String?,
    val timing: String?,
    /** For a step: "Rule" or "Common route, not a rule". The reviewer signs for which it is. */
    val necessity: String?,
    val detail: String?,
    val gates: List<String>,
    val appliesTo: String,
    val unreadableReason: String?,
    val payloadJson: String?,
    val lookUpAtJson: String?,
    val approve: ReviewPolicy.Decision,
    val canReturn: Boolean,
    val canWithdraw: Boolean,
) {
    val approveAllowed: Boolean get() = approve is ReviewPolicy.Decision.Allowed
    val approveRefusal: String? get() = (approve as? ReviewPolicy.Decision.Refused)?.reason
}

@Controller
class ReviewController(
    private val repo: ReviewRepository,
    private val service: ReviewService,
) {

    @GetMapping("/login")
    fun login(): String = "login"

    @GetMapping("/")
    fun queue(model: Model): String {
        val byCareer = repo.all().groupBy { it.careerId }
        model.addAttribute(
            "careers",
            repo.careers().map { career ->
                val records = byCareer[career.id].orEmpty()
                fun of(vararg states: ReviewState) = records.filter { it.review in states }.map(::item)
                CareerQueue(
                    id = career.id,
                    title = career.title,
                    needsReview = of(ReviewState.NEEDS_REVIEW),
                    blocked = of(ReviewState.DRAFT),
                    published = of(ReviewState.VERIFIED, ReviewState.KNOWN_UNSOURCED),
                )
            },
        )
        return "queue"
    }

    @GetMapping("/review/{kind}/{id}")
    fun record(@PathVariable kind: String, @PathVariable id: String, model: Model): String {
        val k = Kind.fromPath(kind) ?: return "redirect:/"
        val record = repo.find(k, id) ?: return "redirect:/"
        val career = repo.careers().firstOrNull { it.id == record.careerId }
        model.addAttribute("view", view(record, career))
        return "record"
    }

    @PostMapping("/review/{kind}/{id}/approve")
    fun approve(
        @PathVariable kind: String,
        @PathVariable id: String,
        @RequestParam version: String,
        @RequestParam(defaultValue = "false") confirmed: Boolean,
        auth: Authentication,
        redirect: RedirectAttributes,
    ): String = act(kind, id, redirect) { k -> service.approve(k, id, version, confirmed, auth.name) }

    @PostMapping("/review/{kind}/{id}/return")
    fun returnForCorrection(
        @PathVariable kind: String,
        @PathVariable id: String,
        @RequestParam version: String,
        @RequestParam(defaultValue = "") note: String,
        auth: Authentication,
        redirect: RedirectAttributes,
    ): String = act(kind, id, redirect) { k -> service.returnForCorrection(k, id, version, note, auth.name) }

    @PostMapping("/review/{kind}/{id}/withdraw")
    fun withdraw(
        @PathVariable kind: String,
        @PathVariable id: String,
        @RequestParam version: String,
        @RequestParam(defaultValue = "") note: String,
        auth: Authentication,
        redirect: RedirectAttributes,
    ): String = act(kind, id, redirect) { k -> service.withdraw(k, id, version, note, auth.name) }

    private fun act(kind: String, id: String, redirect: RedirectAttributes, action: (Kind) -> ReviewService.Outcome): String {
        val k = Kind.fromPath(kind) ?: return "redirect:/"
        when (val outcome = action(k)) {
            is ReviewService.Outcome.Done -> redirect.addFlashAttribute("done", outcome.message)
            is ReviewService.Outcome.Refused -> redirect.addFlashAttribute("refused", outcome.reason)
        }
        return "redirect:/review/${k.path}/$id"
    }

    private fun item(r: Record) = QueueItem(r.kind.path, r.id, r.label, r.verifiedBy, r.note)

    private fun view(r: Record, career: Career?): RecordView {
        val readable = ReviewService.isReadableByApp(r)
        var summary: String? = null
        var timing: String? = null
        var necessity: String? = null
        var detail: String? = null
        var gates = emptyList<String>()
        var unreadable: String? = null

        when (r.kind) {
            Kind.CRITERION -> when (val read = RecordReader.requirement(r.payload)) {
                is RecordReader.Read.Ok -> summary = Summary.of(read.value)
                is RecordReader.Read.Unreadable -> if (r.payload != null) unreadable = read.why
            }
            Kind.STEP -> when (val read = RecordReader.stepBody(r.payload.orEmpty())) {
                is RecordReader.Read.Ok -> {
                    summary = read.value.title
                    timing = Summary.of(read.value.timing)
                    necessity = when (read.value.necessity) {
                        Necessity.REQUIRED -> "Rule: every student on this pathway must do this"
                        Necessity.TYPICAL -> "Common route, not a rule: other routes are valid"
                    }
                    detail = read.value.detail
                    gates = read.value.gates
                }
                is RecordReader.Read.Unreadable -> unreadable = read.why
            }
        }

        val applies = when (val a = RecordReader.applicability(r.appliesTo)) {
            is RecordReader.Read.Ok -> Summary.of(a.value)
            is RecordReader.Read.Unreadable -> {
                unreadable = unreadable ?: "applies_to: ${a.why}"
                "unreadable"
            }
        }

        return RecordView(
            record = r,
            kindLabel = if (r.kind == Kind.CRITERION) "Criterion" else "Pathway step",
            careerTitle = career?.title ?: r.careerId,
            careerNotes = career?.notes.orEmpty(),
            summary = summary,
            timing = timing,
            necessity = necessity,
            detail = detail,
            gates = gates,
            appliesTo = applies,
            unreadableReason = unreadable,
            payloadJson = RecordReader.pretty(r.payload),
            lookUpAtJson = RecordReader.pretty(r.lookUpAt),
            approve = ReviewPolicy.approve(r.review, readable),
            canReturn = ReviewPolicy.returnForCorrection(r.review) is ReviewPolicy.Decision.Allowed,
            canWithdraw = ReviewPolicy.withdraw(r.review) is ReviewPolicy.Decision.Allowed,
        )
    }
}
