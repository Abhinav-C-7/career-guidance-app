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

-- --------------------------------------------------------------------------
-- The client contract: the views show exactly what RLS allows, and a demotion
-- leaves a tombstone the phone can sync.
-- --------------------------------------------------------------------------

set local role anon;

do $$
declare
    visible text[];
begin
    select array_agg(id order by id) into visible
    from published_criteria where career_id = 'rls-test';

    if visible is distinct from array['rls-gap', 'rls-verified'] then
        raise exception 'published_criteria should match RLS, saw: %',
            coalesce(visible::text, 'nothing');
    end if;
end $$;

reset role;

-- A reviewer pulls a figure back because the source changed.
update criteria set review = 'NEEDS_REVIEW' where id = 'rls-verified';

set local role anon;

do $$
begin
    if exists (select 1 from published_criteria where id = 'rls-verified') then
        raise exception 'a demoted criterion is still published';
    end if;
    if not exists (select 1 from criteria_withdrawals where criterion_id = 'rls-verified') then
        raise exception 'demotion left no withdrawal; phones would keep the retracted figure';
    end if;
end $$;

reset role;

-- Re-verified: the tombstone must go, or it would outrank the fresh row on next sync.
update criteria set review = 'VERIFIED' where id = 'rls-verified';

do $$
begin
    if exists (select 1 from criteria_withdrawals where criterion_id = 'rls-verified') then
        raise exception 'republished criterion still has a withdrawal';
    end if;
end $$;

delete from criteria where id = 'rls-gap';

do $$
begin
    if not exists (select 1 from criteria_withdrawals where criterion_id = 'rls-gap') then
        raise exception 'deleting a published criterion left no withdrawal';
    end if;
end $$;

-- --------------------------------------------------------------------------
-- Milestones: only VERIFIED reaches a client, a demotion leaves a tombstone, and the
-- database refuses both a self-signed step and a step posing as a gap.
-- --------------------------------------------------------------------------

insert into milestones (id, career_id, position, review, body, source_url, source_authority,
                        effective_from, last_verified_at, verified_by)
values
    ('rls-step-verified', 'rls-test', 1, 'VERIFIED',
     '{"title":"verified step","kind":"TRAINING","timing":{"type":"follows"}}'::jsonb,
     'https://example.gov.in/x', 'OFFICIAL_NOTIFICATION', '2026-01-01', '2026-01-01', 'a-real-person'),
    ('rls-step-unreviewed', 'rls-test', 2, 'NEEDS_REVIEW',
     '{"title":"unreviewed step","kind":"TRAINING","timing":{"type":"follows"}}'::jsonb,
     'https://example.gov.in/x', 'OFFICIAL_NOTIFICATION', '2026-01-01', '2026-01-01',
     'automated-extraction-unreviewed');

set local role anon;

do $$
declare
    visible text[];
begin
    select array_agg(id order by id) into visible from published_milestones where career_id = 'rls-test';
    if visible is distinct from array['rls-step-verified'] then
        raise exception 'anon should see only the verified step, saw: %', coalesce(visible::text, 'nothing');
    end if;

    select array_agg(id order by id) into visible from milestones where career_id = 'rls-test';
    if visible is distinct from array['rls-step-verified'] then
        raise exception 'RLS on milestones leaks unreviewed steps: %', coalesce(visible::text, 'nothing');
    end if;
end $$;

do $$
declare
    n int;
begin
    select count(*) into n from milestones_history;
    if n <> 0 then
        raise exception 'anon can see % milestone history rows; expected none', n;
    end if;
end $$;

reset role;

update milestones set review = 'NEEDS_REVIEW' where id = 'rls-step-verified';

do $$
begin
    if not exists (select 1 from milestone_withdrawals where milestone_id = 'rls-step-verified') then
        raise exception 'demoting a step left no withdrawal';
    end if;
end $$;

do $$
begin
    update milestones set review = 'VERIFIED' where id = 'rls-step-unreviewed';
    raise exception 'a step signed by the extraction pass was allowed to become VERIFIED';
exception
    when check_violation then
        null; -- expected
end $$;

do $$
begin
    update milestones set review = 'KNOWN_UNSOURCED' where id = 'rls-step-unreviewed';
    raise exception 'a milestone was allowed to become a declared gap';
exception
    when check_violation then
        null; -- expected
end $$;

-- --------------------------------------------------------------------------
-- The review service can sign and return records, and nothing else.
-- --------------------------------------------------------------------------

set local role content_service;

do $$
declare
    n int;
begin
    select count(*) into n from criteria where career_id = 'rls-test' and review in ('DRAFT', 'NEEDS_REVIEW');
    if n = 0 then
        raise exception 'content_service cannot see the review queue';
    end if;
end $$;

do $$
declare
    n int;
begin
    update criteria set review = 'DRAFT', review_note = 'returned in test' where id = 'rls-needs-review';
    get diagnostics n = row_count;
    if n <> 1 then
        raise exception 'content_service could not return a criterion for correction';
    end if;
end $$;

do $$
begin
    update criteria set requirement = '{"type":"attempt_limit","maxAttempts":9}'::jsonb where id = 'rls-verified';
    raise exception 'content_service was able to change an eligibility value';
exception
    when insufficient_privilege then
        null; -- expected: no UPDATE grant on requirement
end $$;

do $$
begin
    update milestones set body = '{}'::jsonb where id = 'rls-step-verified';
    raise exception 'content_service was able to change a step';
exception
    when insufficient_privilege then
        null; -- expected
end $$;

do $$
begin
    delete from criteria where id = 'rls-draft';
    raise exception 'content_service was able to delete a criterion';
exception
    when insufficient_privilege then
        null; -- expected
end $$;

reset role;

rollback;
