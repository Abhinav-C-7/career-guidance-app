-- Proves the RLS policies actually hold. Run against a project after migrating:
--
--   psql "$DATABASE_URL" -f supabase/tests/rls_test.sql
--
-- or paste into the Supabase SQL editor. It seeds, asserts, and rolls back, so it
-- leaves nothing behind. Any failure raises and aborts.
--
-- This is not decoration. The claim "unreviewed content never reaches a student"
-- is the product's central promise, and an untested policy is a promise nobody checked.

begin;

insert into careers (id, title, family, notes)
values ('rls-test', 'RLS fixture', 'DEFENCE', '["internal note that must not leak"]'::jsonb);

insert into criteria (id, career_id, label, review, requirement, source_url, source_authority,
                      effective_from, last_verified_at, verified_by)
values
    ('rls-draft', 'rls-test', 'draft', 'DRAFT',
     '{"type":"attempt_limit","maxAttempts":2}'::jsonb,
     'https://example.gov.in/x', 'OFFICIAL_NOTIFICATION', '2026-01-01', '2026-01-01',
     'automated-extraction-unreviewed'),
    ('rls-needs-review', 'rls-test', 'needs review', 'NEEDS_REVIEW',
     '{"type":"attempt_limit","maxAttempts":3}'::jsonb,
     'https://example.gov.in/x', 'OFFICIAL_NOTIFICATION', '2026-01-01', '2026-01-01',
     'automated-extraction-unreviewed'),
    ('rls-verified', 'rls-test', 'verified', 'VERIFIED',
     '{"type":"attempt_limit","maxAttempts":4}'::jsonb,
     'https://example.gov.in/x', 'OFFICIAL_NOTIFICATION', '2026-01-01', '2026-01-01',
     'a-real-person');

insert into criteria (id, career_id, label, review, look_up_at)
values
    ('rls-gap', 'rls-test', 'declared gap', 'KNOWN_UNSOURCED',
     '{"describedAs":"Some manual","citedBy":"https://example.gov.in/x"}'::jsonb);

-- --------------------------------------------------------------------------
-- Everything below runs as an unauthenticated client.
-- --------------------------------------------------------------------------

set local role anon;

do $$
declare
    visible text[];
begin
    select array_agg(id order by id) into visible
    from criteria where career_id = 'rls-test';

    if visible is distinct from array['rls-gap', 'rls-verified'] then
        raise exception
            'anon should see exactly the verified row and the declared gap, saw: %',
            coalesce(visible::text, 'nothing');
    end if;
end $$;

do $$
declare
    leaked int;
begin
    -- The requirement payload of an unreviewed row must not be reachable by any route.
    select count(*) into leaked
    from criteria
    where review in ('DRAFT', 'NEEDS_REVIEW');

    if leaked <> 0 then
        raise exception 'anon can see % unreviewed criteria', leaked;
    end if;
end $$;

do $$
begin
    -- notes is withheld by column grant, not by RLS. Reading it must error.
    perform notes from careers where id = 'rls-test';
    raise exception 'anon was able to read careers.notes';
exception
    when insufficient_privilege then
        null; -- expected
end $$;

do $$
begin
    perform id, title, family from careers where id = 'rls-test';
exception
    when insufficient_privilege then
        raise exception 'anon should be able to read public career columns';
end $$;

do $$
begin
    update criteria set review = 'VERIFIED' where id = 'rls-needs-review';
    raise exception 'anon was able to promote a criterion to VERIFIED';
exception
    when insufficient_privilege or check_violation then
        null; -- expected: no write policy exists for anon
end $$;

do $$
declare
    n int;
begin
    select count(*) into n from source_documents;
    if n <> 0 then
        raise exception 'anon can see % source documents; expected none', n;
    end if;
end $$;

do $$
declare
    n int;
begin
    select count(*) into n from criteria_history;
    if n <> 0 then
        raise exception 'anon can see % history rows; expected none', n;
    end if;
end $$;

reset role;

-- --------------------------------------------------------------------------
-- The database itself must refuse to let an extraction job sign for its own work.
-- --------------------------------------------------------------------------

do $$
begin
    update criteria
    set review = 'VERIFIED'
    where id = 'rls-needs-review';
    raise exception 'a row signed by the extraction pass was allowed to become VERIFIED';
exception
    when check_violation then
        null; -- expected: verified_requires_a_human
end $$;

rollback;
