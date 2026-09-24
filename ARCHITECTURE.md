# ARCHITECTURE.md

How AI is used in this product, and where it is forbidden. Binding — read before adding any
model call.

## The one rule

**AI builds the database. AI never answers from it.**

Every eligibility fact is produced offline, by a pipeline, reviewed by a person, and stored.
At request time a deterministic rules engine reads those stored records. No model call sits
between a student and a number.

## The four layers

| Layer | Who does it | May | Must not |
|---|---|---|---|
| **Ingestion** | AI | Find sources, fetch them, extract candidate records with citations | Publish anything. Output is always `NEEDS_REVIEW` |
| **Review** | Human only | Move a record to `VERIFIED`, signing it with their own name | Be skipped, batched blindly, or automated |
| **Serving** | Deterministic code | Evaluate verified criteria against a profile | Call a model. At all |
| **Presentation** | AI, bounded | Explain, rephrase, translate, answer follow-ups over records already retrieved | Introduce any fact not in a retrieved record |

## Why RAG is not the fact layer

This was considered and rejected. The reasons are concrete, not stylistic:

1. **Retrieval has no notion of what is in force.** The repository already holds a 2019 DGMS
   manual and a 2026 UPSC notification describing the same standards, where one supersedes
   part of the other. They are near-identical to an embedding model. Recency is not semantic
   similarity, and no prompt fixes that. `effective_from` / `effective_to` on a record does.
2. **Retrieval returns text; text still has to be interpreted.** "Born not earlier than
   01 January 2008 and not later than 01 January 2011" becomes "16.5 to 19.5 years" in every
   secondary source, losing months at both edges. RAG reintroduces that lossy step on every
   request instead of once, under review.
3. **Top-k always returns something.** A retriever cannot say "we have nothing verified for
   this." `ReviewState.KNOWN_UNSOURCED` — the honest admission that a gate exists and we
   cannot quantify it — is structurally impossible in a retrieval-answers-questions design.
4. **A chunk does not know who is asking.** Sex-specific heights, community relaxations,
   branch-specific standards. That is `Applicability` logic over a profile, not similarity.
5. **It cannot be tested.** The rules engine has unit tests pinning its behaviour. "Did the
   model read the table correctly this time" is not a test.

## Where retrieval does belong

Three jobs, none of which state a fact:

- **Career search.** "I want to work with planes" → pilot, AME, air traffic control. Embedding
  search over career names and descriptions in `pgvector`. Low stakes, high value.
- **Reviewer assistance.** Pull the source passage and show it beside the extracted record so
  a human can approve in seconds. See `docs/extraction-pipeline.md`.
- **Grounded follow-ups.** "Why do they need PCM?" answered over the verified records already
  on screen — a small, known record set, not a document corpus.

## Coverage tiers

A student can type any career. What they get back depends on what we have earned the right
to say. The line that makes this safe:

> **AI may describe shape. Only verified data may state thresholds.**

| Tier | Shown | Rules |
|---|---|---|
| **Verified** | The full pathway: criteria, gates, sources, verification dates | The product |
| **Sketch** | Structure only — "this route runs stream → entrance exam → degree → licence" — plus links to the bodies involved | **No numbers.** No age, height, mark, cutoff or date. Visually distinct enough that nobody mistakes it for a verified pathway. Offers "notify me" |
| **Absent** | "We don't cover this yet. Tell us and we'll add it." | Queues an ingestion job |

Being slightly wrong about the shape of a route is recoverable. Being wrong about 157 cm is
not. The tiers exist because those two errors are not the same size.

Every search for a career we lack is a demand signal. The most requested absent careers are
the content roadmap — read them off the queue, do not guess.

## Runtime rules

**No model call renders the pathway screen.** Explanation copy is drafted by AI at authoring
time, reviewed once, and stored with the record. Reasons, in order of severity:

- The app is local-first over Room. A screen that needs a network round trip cannot open on a
  patchy connection, which is the connection our users have.
- Cost per view is charged against zero revenue. Deterministic serving is free.
- A UI that renders differently on each open cannot be designed, tested, or screenshotted.
- Latency. The pathway screen must be instant.

Runtime model calls are permitted for exactly two things: free-form follow-up questions
grounded in retrieved records, and on-demand translation. Both are opt-in, both degrade to a
clear "needs a connection" state, and neither may alter a criterion.

## Provider policy

The model is the most replaceable component in this system, by design — it explains, it does
not decide. Keep it that way:

- One interface in `:ai`. Provider chosen by config. No provider SDK types in domain code.
- A free tier is fine while building. **The switching trigger is data terms, not model
  quality**: free tiers commonly permit retention and human review of prompts, which is
  unacceptable once a real student's marks, school and medical details are in them. Check the
  current terms before the first real user, not after.
- Nothing in the MVP depends on a model. If the AI layer were deleted tomorrow, the product
  would still generate correct pathways from verified content.
