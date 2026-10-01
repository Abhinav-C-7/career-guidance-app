# CLAUDE.md

Guidance for Claude Code working in this repository.

## What this is

A mobile-only (Android-first) app for Indian students, roughly classes 8–12 and early
college. The student tells us where they are today (class, board, state, stream if
already chosen) and what they want to become. We return a **concrete, dated, sourced
pathway** to that career.

A pathway answers, for one career:

- Which **stream / subject combination** to take in 11th–12th, and by when the choice locks
- Which **entrance exams** stand between the student and the goal
- **Eligibility gates**: age limits, attempt limits, subject prerequisites, board marks
  minimums, domicile/state-quota rules, nationality
- **Physical and medical standards**: height, weight, chest expansion, vision and colour
  vision, hearing, tattoos, medical disqualifiers — the criteria students discover far too
  late to act on
- **Score targets**: what exam score / rank / percentile has historically been needed, and
  what board percentage matters where it is a criterion
- **Books and preparation resources** actually used for that exam
- **Deadlines**: registration windows, exam dates, document cutoffs — with reminders

## Product principles (these are engineering constraints, not marketing)

1. **We are not a lead-gen business.** No commissions, no sponsored placements, no selling
   student data to colleges or coaching centres. Nothing in the codebase should make this
   easy to add later — no `partner_id`, no `sponsored` flag, no referral tracking on
   institutions.
2. **We do not run a psychometric test and hand back a job title.** The student names the
   goal, or browses careers. We work backwards from the goal. There is no "what should I
   be?" quiz in the core flow.
3. **Colleges are pull, never push.** College information is shown only when the student
   explicitly asks for it. Never surface a college as a recommendation, suggestion, upsell,
   or notification.
4. **Surfacing a disqualifier early is the product.** A student who learns in class 9 that
   their colour vision rules out a commercial pilot licence has been served well, even
   though the news is bad. Never soften or hide an eligibility gate to keep engagement up.
5. **Honesty about uncertainty.** Historical cutoffs are trends, not promises. Say so in the
   UI. Never state a future cutoff as fact.

## The rule that matters most: facts come from the database, not the model

Every eligibility fact, number, date, and standard is served from our **verified content
store**. The LLM never invents them.

- The model may: rephrase, explain, personalise ordering, answer follow-ups grounded in
  retrieved records, help a student compare two pathways, translate.
- The model may **not**: produce an age limit, height requirement, cutoff, fee, exam date,
  or eligibility rule that is not present in a retrieved record.
- Any generated answer that references a criterion must carry the record's `source_url` and
  `last_verified_at` through to the UI.
- If no record exists, the correct answer is "we don't have verified data for this yet" —
  never a plausible-sounding guess. A hallucinated age limit can cost a student a year of
  their life.

Enforce this in code: pathway assembly is deterministic (rules engine over the content
store). The LLM sits on top as an explanation and conversation layer, downstream of
retrieval, with a tool/schema-constrained interface.

## Content store: the actual product

Treat content as first-class engineering, not as seed data.

- Every criterion row carries: `source_url`, `source_authority` (official notification, PIB,
  board circular, reputable secondary), `effective_from`, `effective_to`, `last_verified_at`,
  `verified_by`.
- Criteria are **versioned, never overwritten**. Rules change yearly; a student mid-pathway
  must be able to see what changed.
- Anything past its re-verification window is flagged stale in the UI, not silently served.
- Prefer official sources: UPSC, NTA, exam-conducting bodies, NMC, DGCA, service HQs, state
  boards, CBSE. Secondary sources are a fallback and must be marked as such.
- NEP-era stream rules are in flux across boards and states — model subject combinations per
  board/state, not as a hardcoded Science/Commerce/Arts trichotomy.

## Stack

Android-only, native. No iOS, no cross-platform layer.

| Layer | Choice | Why |
|---|---|---|
| App | **Kotlin + Jetpack Compose**, minSdk 24 | Best performance and smallest APK on the budget Android phones we actually target |
| Navigation | Navigation Compose | Type-safe routes; deep links for share and notification targets |
| State | ViewModel + StateFlow, unidirectional | One immutable UI state per screen, exposed as a single flow |
| DI | Hilt | |
| Local store | **Room** | Source of truth on device. The app is local-first: a pathway must open with no network |
| Backend (serving) | **Supabase** (Postgres, Storage, RLS), read over plain HTTPS from `:data` | Content store. The domain is deeply relational — careers, exams, criteria, pathways. The app reads only `published_careers`, `published_criteria` and `criteria_withdrawals`, never base tables |
| Backend (content service) | **Spring Boot (Kotlin) on Railway** | Fetches official sources on schedule, detects changes, extracts candidates with AI, runs the human review queue, publishes approved records. See "Two backends" below |
| Serialization | kotlinx.serialization | |
| Deadlines | WorkManager, inexact | Deadline reminders do not need minute precision; inexact work is far kinder to battery. Notifications are scheduled on-device from synced data — no push infrastructure in v1 |
| Auth | **None in v1** | Local-first. Ask for a phone number only when the student wants a reminder, never at first run |
| AI | **Not in the MVP.** Behind one interface (`:ai`) when it lands, provider swapped by config — Gemini free tier while building | The pathway is deterministic. The model only ever explains what retrieval already returned |
| Analytics | PostHog (EU or India region) | Student data minimisation |
| Payments (later) | Razorpay | UPI-first |
| Content authoring | Today: JSON in `content/careers/`, published by `tools/publish`. Later: the content service's review screen | Content must be diffable and reviewable, and signed by a person |

Target device is a low-end Android phone on a slow connection. Keep the APK small, budget
for cold starts, test on a throttled network at 360dp.

### No OTA — the consequence

Native Android means every client fix needs a Play Store review. So keep behaviour in
**data, not compiled code** wherever there is a choice. Eligibility rules, thresholds,
milestone ordering, and copy for criteria all live in the content store and sync down. A
rule encoded as a Kotlin `when` branch is a rule that needs a release to fix — and given
principle 4, a wrong rule is the one thing we cannot afford to be slow about.

### Two backends, two jobs

```
Official sites → content service (Spring Boot, Railway) → human approves → Supabase → app
                 fetch, diff, extract, review queue                        serves     syncs, works offline
```

- **Serving** is Supabase. The phone reads published views directly. No custom server sits
  in the student read path: if Railway is down, students notice nothing.
- **Gathering** is the content service. It checks sources daily (exam notifications) and on
  each record's verification window (rules), re-extracts only when a document's hash changes,
  and queues candidates for review. It **never publishes on its own** — the database refuses
  a VERIFIED row signed by automation (`verified_requires_a_human`).
- The schema is owned by `supabase/migrations` and the Supabase CLI. The content service
  must not run Flyway/Liquibase or `ddl-auto` against it.
- The secret key lives only in Railway's environment and a local, gitignored `.env`. Never in
  the app, never in the repo. The app ships only the publishable (anon) key; RLS is the
  boundary.

## Conventions

- No `supabase-kt` on the client. The app makes three read-only PostgREST queries; the SDK
  would add Ktor, `auth-kt` and `kotlin-reflect` to the APK and pin our Kotlin version to
  its own. Revisit only if the app ever needs auth or realtime.

- Kotlin, explicit API mode on library modules. No platform types leaking into domain.
- Domain model (`Career`, `Exam`, `Criterion`, `Pathway`, `Milestone`, `Deadline`) lives in
  a pure-Kotlin `:domain` module with **no Android dependencies**, so the rules engine is
  testable on the JVM without an emulator.
- The backend is TypeScript-free but the schema contract is shared: Postgres schema is the
  source of truth, and the Kotlin DTOs in `:data` must be regenerated, not hand-drifted,
  when a migration lands. A mismatch here is a silent wrong-number bug.
- Never hardcode an eligibility value in app code. A literal age, height, mark, or cutoff
  in a `.kt` file is a bug, not a shortcut.
- All user-facing criterion displays must render source + verification date. Build one
  component for this and reuse it everywhere.
- Write the rules engine pure and unit-tested — pathway generation must be reproducible and
  diffable.
- Student PII: collect the minimum needed to compute a pathway. No contact details shared
  with any third party, ever.

- Visual and interaction rules live in `DESIGN.md`. Read it before building any screen.
- How AI may and may not be used lives in `ARCHITECTURE.md`. Read it before adding any model
  call. Short version: AI builds the content store offline and never answers from it at
  request time, and no model call renders the pathway screen.
- The ingestion pipeline is specified in `docs/extraction-pipeline.md`.
- The app never asserts a fact about the student it was not told. We cannot see exam-body
  portals, and we never read SMS or ask for portal credentials. Registration status is
  self-reported, defaults to unknown, and is used only to stop sending a reminder.

## Scope guidance

Depth beats breadth. A small number of careers covered completely and verified is worth far
more than hundreds of shallow entries. Start with high-intent, high-gate careers where the
hidden criteria actually bite — defence services, medicine, engineering, merchant navy,
aviation, civil services, law, architecture, design, CA. Expand only after the content
pipeline is proven.
