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
the publish script, `GateEvaluator.assess`, and the row-level security policy in
`supabase/migrations/0002_rls.sql`. The last is the strongest — the rows never cross the
wire — and `supabase/tests/rls_test.sql` proves it holds.

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
