# Extraction pipeline

Turns a career name into reviewed, sourced criteria. Offline, asynchronous, and incapable of
publishing on its own.

Read `ARCHITECTURE.md` first — this implements its Ingestion layer.

## Goal and non-goals

**Goal:** convert "one career takes a person a day" into "one career takes a person twenty
minutes of review." The pipeline does the fetching, reading and structuring. A human does the
signing.

**Non-goals:** deciding eligibility, publishing content, answering student questions, or
reaching a coverage number. Coverage that outruns review is not coverage.

## Stages

```
discover → fetch & archive → extract → span check → self-check → stage → [HUMAN] → publish
```

### 1. Discover

Given a career, find candidate primary sources.

Each career carries a **source allowlist**: the domains that can speak authoritatively for it
(`upsc.gov.in`, `nta.ac.in`, `joinindianarmy.nic.in`, `nmc.org.in`, `dgshipping.gov.in`, the
relevant board). Confirmed once per career by a human — a cheap, one-time decision.

Anything outside the allowlist is `SourceAuthority.SECONDARY` and **cannot be staged at all**.
Without this the pipeline will quietly fill up with coaching-site numbers, which is the exact
failure this product exists to avoid.

### 2. Fetch and archive

Download and store immutably, keyed by SHA-256 of the bytes. Record the fetch timestamp and
the final URL after redirects.

**Never extract from a live URL.** A `source_url` that 404s later is a criterion you can no
longer defend, and government PDFs are replaced in place every cycle. The archive is the
evidence; the URL is a convenience.

### 3. Extract

An LLM reads the archived document and emits candidate `Criterion` records.

Every extracted field must carry an **evidence span**:

```json
{
  "criterionId": "nda-dob-window-ii-2026",
  "requirement": { "type": "born_between", "earliest": "2008-01-01", "latest": "2011-01-01" },
  "evidence": {
    "quote": "born not earlier than 01st January 2008 and not later than 01st January 2011",
    "locator": { "page": 7, "section": "3(b)" },
    "documentHash": "sha256:…"
  },
  "confidence": 0.94
}
```

The model is explicitly instructed that a requirement it cannot quote is a requirement it must
not emit. Missing is always better than invented.

### 4. Span check — the mechanical hallucination test

Normalise whitespace and assert that `evidence.quote` appears **verbatim** in the archived
document text. If it does not, reject the record automatically with reason `FABRICATED_SPAN`.
No human involved.

This is the single highest-value check in the pipeline. It costs a string search and it makes
the most dangerous failure mode — a confident, plausible, invented number — mechanically
detectable rather than a matter of reviewer vigilance.

### 5. Self-check

A second, independent pass answers one question: *does the structured value actually follow
from this quote?* It sees the quote and the parsed record, not the original document.

This catches paraphrase drift — the class of error where "born not earlier than 1 Jan 2008"
becomes an age range and silently loses months. A disagreement does not reject the record; it
raises its review priority.

### 6. Stage

Write as `ReviewState.NEEDS_REVIEW` with `verifiedBy: "automated-extraction-unreviewed"`.

`confidence` orders the review queue and **nothing else**. Self-reported model confidence is
not calibrated and must never gate publication.

Where a document delegates rather than states — "physical standards shall be as per the Joint
Order Manual" — emit a `KNOWN_UNSOURCED` criterion with a `LookupPointer` rather than emitting
nothing. A known gap is information; silence is not.

### 7. Review — human, and never otherwise

The reviewer sees the extracted record beside the highlighted span in its surrounding context.

Requirements:

- Approve, reject, or edit-then-approve. Keyboard driven. Target under 30 seconds per record.
- Approving sets `review = VERIFIED`, `verifiedBy` to the reviewer's own identity, and
  `lastVerifiedAt` to today.
- The source pane must have actually rendered before approve is enabled. Rubber-stamping a
  queue is the way this system fails, and the UI should make it inconvenient.
- Rejections record a reason and feed back into prompt and allowlist tuning.

### 8. Publish

`VERIFIED` and `KNOWN_UNSOURCED` records sync to the app. Nothing else, ever.

`KNOWN_UNSOURCED` is included on purpose: it is how a student is told "this gate exists and we
cannot yet give you the figure", and by construction it carries no requirement value, so
admitting it leaks no unreviewed number. `DRAFT` and `NEEDS_REVIEW` never leave the server.

Three independent gates enforce this, because any one of them is a single point of failure:

1. **The publish tool** (`tools/publish`) parses content with the app's own `ContentLoader`,
   refuses any `VERIFIED` value signed `automated-extraction-unreviewed`, and prints exactly
   which criteria students will start and stop seeing. Dry run by default.
2. **`GateEvaluator.assess`** drops anything unpublished even if it reaches the device.
3. **Row-level security** (`supabase/migrations/20260920000002_rls.sql`). The strongest — the
   rows never cross the wire — and `supabase/tests/rls_test.sql` proves it holds.

The tool writes **every** review state, not only published ones. A criterion demoted in the
repo must be demoted on the server too, or phones keep the retracted figure; unreviewed rows
on the server are invisible to clients. Unchanged rows are skipped, because every write bumps
`updated_at` and forces every phone to re-sync.

```bash
./gradlew :tools:publish:run                                 # dry run
./gradlew :tools:publish:run --args="--apply"                # write
./gradlew :tools:publish:run --args="--apply --allow-delete" # also delete removed criteria
```

### Who signs

A person, in the review screen (`content-service`), never a file edit and never a script.
The screen decodes every record with the app's own model first, and refuses to let a
record be signed if a phone could not read it. Each sign-off lands only if the row has not
changed since the reviewer opened it. Returning a record needs a reason, and the reason is
signed. `tools/publish` keeps any server-side review whose value the repo has not changed.

### What the app reads

The client contract is `published_careers`, `published_criteria`, `published_milestones`,
`criteria_withdrawals` and `milestone_withdrawals` (`supabase/migrations/20260925000001_published_views.sql`
and `20261002000001_milestones.sql`) — never the base tables. Milestones publish only when
VERIFIED; unlike criteria they can never be a declared gap. Sync is:
pull rows with `updated_at` after the last sync, then drop every id in `criteria_withdrawals`
withdrawn after it. Without withdrawals, a demoted or deleted criterion would simply stop
arriving, and a phone would go on showing it offline indefinitely.

## Change detection

This is also the retention engine — "the rules changed and we told you" (see `CLAUDE.md`).

A scheduled job re-fetches each archived source on its `VerificationWindow` cadence
(`VOLATILE` 90 days, `ANNUAL` 365, `SLOW` 730) and compares content hashes.

- **Hash unchanged** → bump `lastVerifiedAt`. No human needed; the document is provably the
  same one a person already signed for.
- **Hash changed** → re-extract, diff against current records, and queue only the criteria
  whose values moved. Unchanged criteria are not re-reviewed.
- **Fetch failed** → do not bump anything. A source we cannot reach goes stale on schedule and
  starts rendering flagged, which is the correct outcome.

Criteria whose values changed drive the student-facing notification. Nothing notifies until a
human has approved the new value.

## Failure modes seen in practice

Documented from the first real run, against NDA:

| Failure | Handling |
|---|---|
| **Site behind a CAPTCHA** (joinindianarmy.nic.in) | Flag `NEEDS_HUMAN_FETCH` with the URL and what is wanted. Never attempt to solve it. A person fetches it once and drops it in the archive |
| **Scanned PDF, poor OCR** | Measure text-layer quality before extracting. Below threshold, refuse and flag. Transcribing figures from mangled OCR is worse than having none |
| **Document delegates to another document** | Emit `KNOWN_UNSOURCED` with a `LookupPointer` naming the cited document, and queue discovery for it |
| **Right scope, wrong edition** (a 2019 manual where the notification cites a 2025 one) | Not automatically detectable. The extractor must record the document's own stated date and edition, and the reviewer must confirm it matches what the citing source names. This is a human check by design |
| **Rule stated only as an absence** (NDA imposes no attempt limit) | Emit a note on the career, not a criterion. An absent criterion is ambiguous; a recorded "the source imposes none" is not |

## Never automated

- The transition to `VERIFIED`.
- Solving a CAPTCHA, or any other access control.
- Staging from a source outside the career's allowlist.
- Publishing a value whose evidence span failed the check, at any confidence.

## Metrics worth watching

Records staged per week · review throughput per hour · auto-reject rate by reason · share of
published criteria past their verification window · careers by coverage tier.

If staged records grow faster than reviewed ones, the queue is the problem — slow ingestion
down rather than loosening review.
