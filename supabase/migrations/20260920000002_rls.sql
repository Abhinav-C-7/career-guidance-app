-- Row level security.
--
-- This is the strongest of the three gates that keep unreviewed content away from
-- students. The other two (the publish script, and GateEvaluator.assess) are our own
-- code and can be bypassed by a bug. This one means the rows never cross the wire.
--
-- It matters more than it looks: the Android app ships with the anon key embedded in the
-- APK, and anything in an APK is public — extracting it takes a minute. These policies
-- are the only thing between a stranger and this table.

-- ---------------------------------------------------------------------------
-- Default deny. Revoke first, then grant back only what is needed.
-- ---------------------------------------------------------------------------

-- The project is configured with "automatically expose new tables" off, which is
-- correct, but it can also leave the client roles without USAGE on the schema. Without
-- this, every query returns empty with no error worth reading. Granting it explicitly
-- costs nothing and is idempotent.
grant usage on schema public to anon, authenticated;

alter table careers            enable row level security;
alter table criteria           enable row level security;
alter table source_documents   enable row level security;
alter table criteria_history   enable row level security;

revoke all on careers          from anon, authenticated;
revoke all on criteria         from anon, authenticated;
revoke all on source_documents from anon, authenticated;
revoke all on criteria_history from anon, authenticated;

-- ---------------------------------------------------------------------------
-- Careers: readable, minus the internal notes column.
--
-- notes holds review commentary and model gaps ("BLOCKS PUBLICATION of the two
-- height criteria…"). Useful to us, confusing and occasionally alarming to a
-- student. Column-level grants keep it server-side; RLS alone would not, because
-- RLS filters rows and never columns.
-- ---------------------------------------------------------------------------

grant select (id, title, family, updated_at) on careers to anon, authenticated;

create policy careers_are_public on careers
    for select
    to anon, authenticated
    using (true);

-- ---------------------------------------------------------------------------
-- Criteria: two review states reach a student, and only two.
--
-- VERIFIED is the obvious one. KNOWN_UNSOURCED is included deliberately: it is
-- how we say "this gate exists and we cannot yet tell you the figure", which is
-- information a student needs (see ReviewState.KNOWN_UNSOURCED and DESIGN.md's
-- source-gap treatment). It carries no requirement value by construction — the
-- criterion_shape constraint in 0001 guarantees that — so admitting it leaks no
-- unreviewed number.
--
-- DRAFT and NEEDS_REVIEW never leave the server.
--
-- verified_by is withheld: DESIGN.md requires we show the source and the
-- verification date, not the name of the person who signed it.
-- ---------------------------------------------------------------------------

grant select (
    id,
    career_id,
    label,
    review,
    verification_window,
    requirement,
    source_url,
    source_authority,
    effective_from,
    effective_to,
    last_verified_at,
    look_up_at,
    applies_to,
    updated_at
) on criteria to anon, authenticated;

create policy criteria_published_only on criteria
    for select
    to anon, authenticated
    using (review in ('VERIFIED', 'KNOWN_UNSOURCED'));

-- ---------------------------------------------------------------------------
-- Internal tables: no client access at all.
--
-- No policy is written for these, and with RLS enabled that means every client
-- read returns zero rows regardless of grants. Writers use the service role,
-- which bypasses RLS.
-- ---------------------------------------------------------------------------

comment on table source_documents is
    'Internal. No anon policy exists, so clients see nothing. Service role only.';

comment on table criteria_history is
    'Internal audit trail. No anon policy exists. Service role only.';

-- ---------------------------------------------------------------------------
-- No write path for clients.
--
-- There is deliberately no insert, update or delete policy for anon or
-- authenticated on any table here. Content is published by a script running with
-- the service key, from files reviewed in the repository. A client must never be
-- able to write an eligibility value.
-- ---------------------------------------------------------------------------
