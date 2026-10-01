-- The client contract.
--
-- The app reads these two views and one table, and nothing else. The base tables are free
-- to change shape behind them; a view that keeps its columns is a migration the app never
-- notices. Without this, every schema change is a Play Store release.
--
-- Both views are security_invoker, so they run as the calling role and the RLS policies
-- and column grants in 0002 still apply underneath. A view that bypassed RLS would turn
-- the strongest of our three gates into a single CREATE VIEW away from being disabled.

-- ---------------------------------------------------------------------------
-- Withdrawals
--
-- The app syncs incrementally: "give me everything with updated_at after my last sync".
-- That has a hole. When a criterion is demoted (VERIFIED -> NEEDS_REVIEW because the
-- source changed) or deleted, RLS simply stops returning it. The phone is never told, and
-- keeps showing a figure we have retracted — indefinitely, offline, to a student who
-- trusts it.
--
-- So leaving the published set is itself an event the client can see. A row here means
-- "drop this criterion from your local store". It carries no content, only the id.
-- ---------------------------------------------------------------------------

create table criteria_withdrawals (
    criterion_id  text        primary key,
    career_id     text        not null,
    withdrawn_at  timestamptz not null default now()
);

create index criteria_withdrawals_since_idx on criteria_withdrawals (withdrawn_at);

create or replace function is_published(state review_state)
returns boolean
language sql
immutable
as $$ select state in ('VERIFIED', 'KNOWN_UNSOURCED') $$;

create or replace function track_withdrawal()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if tg_op = 'DELETE' then
        if is_published(old.review) then
            insert into criteria_withdrawals (criterion_id, career_id)
            values (old.id, old.career_id)
            on conflict (criterion_id) do update set withdrawn_at = now();
        end if;
        return old;
    end if;

    if is_published(new.review) then
        -- Back in the published set: the upsert the client receives supersedes any
        -- earlier withdrawal, so the tombstone must go or it would win on next sync.
        delete from criteria_withdrawals where criterion_id = new.id;
    elsif tg_op = 'UPDATE' and is_published(old.review) then
        insert into criteria_withdrawals (criterion_id, career_id)
        values (new.id, new.career_id)
        on conflict (criterion_id) do update set withdrawn_at = now();
    end if;

    return new;
end;
$$;

create trigger criteria_track_withdrawal
    after insert or update or delete on criteria
    for each row execute function track_withdrawal();

alter table criteria_withdrawals enable row level security;
revoke all on criteria_withdrawals from anon, authenticated;
grant select on criteria_withdrawals to anon, authenticated;

-- Ids only, so it leaks nothing but the fact that something was withdrawn.
create policy withdrawals_are_public on criteria_withdrawals
    for select
    to anon, authenticated
    using (true);

-- ---------------------------------------------------------------------------
-- Views
-- ---------------------------------------------------------------------------

create view published_careers
with (security_invoker = true)
as
select id, title, family, updated_at
from careers;

create view published_criteria
with (security_invoker = true)
as
select
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
from criteria
-- Redundant with the RLS policy, deliberately. If someone ever loosens the policy for an
-- admin tool, the app's contract still does not widen with it.
where is_published(review);

grant select on published_careers  to anon, authenticated;
grant select on published_criteria to anon, authenticated;

comment on view published_criteria is
    'Client contract. Sync with updated_at > last_sync, then apply criteria_withdrawals since last_sync.';

-- ---------------------------------------------------------------------------
-- The hot-path index in 0001 only covered VERIFIED, but declared gaps sync too.
-- ---------------------------------------------------------------------------

drop index criteria_published_idx;

create index criteria_published_idx on criteria (updated_at)
    where review in ('VERIFIED', 'KNOWN_UNSOURCED');
