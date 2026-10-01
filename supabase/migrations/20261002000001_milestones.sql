-- Milestones: the steps of a pathway.
--
-- Mirrors app.foreway.domain.model.Milestone. Same protections as criteria, because a
-- milestone states facts too ("the SSB carries 900 marks", "training is three years"):
-- sourced, reviewed, signed by a person, versioned, withdrawn visibly.
--
-- One difference: a milestone can never be KNOWN_UNSOURCED. A step we cannot source is a
-- step we do not show; gaps belong to criteria.

create table milestones (
    id                      text primary key,
    career_id               text not null references careers (id) on delete cascade,
    -- Order on the pathway. Explicit, because rows have no order of their own.
    position                integer not null,
    review                  review_state not null,
    verification_window     verification_window not null default 'ANNUAL',

    -- Title, detail, kind, timing and gate ids, exactly as kotlinx.serialization emits
    -- them. Nothing queries inside; the app decodes it strictly.
    body                    jsonb not null,

    source_url              text not null,
    source_authority        source_authority not null,
    source_document_sha256  text references source_documents (sha256),
    effective_from          date not null,
    effective_to            date,
    last_verified_at        date not null,
    verified_by             text not null,

    applies_to              jsonb not null default '{}'::jsonb,

    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now(),

    constraint milestone_never_a_gap check (review <> 'KNOWN_UNSOURCED'),

    constraint milestone_effective_range_sane check (
        effective_to is null or effective_to >= effective_from
    ),

    -- Same rule as criteria: an extraction run can never sign for its own output.
    constraint milestone_verified_requires_a_human check (
        review <> 'VERIFIED' or verified_by <> 'automated-extraction-unreviewed'
    ),

    constraint milestone_position_unique unique (career_id, position)
        deferrable initially deferred
);

-- Deferred so a publish can reorder steps inside one transaction without tripping the
-- uniqueness check halfway through.
comment on constraint milestone_position_unique on milestones is
    'Deferred: reordering swaps positions within a single transaction.';

create index milestones_career_idx on milestones (career_id);
create index milestones_published_idx on milestones (updated_at) where review = 'VERIFIED';

-- ---------------------------------------------------------------------------
-- History and updated_at, reusing the criteria machinery.
-- ---------------------------------------------------------------------------

create table milestones_history (
    history_id    bigserial primary key,
    milestone_id  text        not null,
    operation     text        not null,
    changed_at    timestamptz not null default now(),
    changed_by    text        not null,
    row_before    jsonb,
    row_after     jsonb
);

create index milestones_history_idx on milestones_history (milestone_id, changed_at desc);

create or replace function record_milestone_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into milestones_history (milestone_id, operation, changed_by, row_before, row_after)
    values (
        coalesce(new.id, old.id),
        tg_op,
        current_user,
        case when tg_op in ('UPDATE', 'DELETE') then to_jsonb(old) end,
        case when tg_op in ('INSERT', 'UPDATE') then to_jsonb(new) end
    );
    return coalesce(new, old);
end;
$$;

create trigger milestones_audit
    after insert or update or delete on milestones
    for each row execute function record_milestone_change();

create trigger milestones_touch
    before update on milestones
    for each row execute function touch_updated_at();

-- ---------------------------------------------------------------------------
-- Withdrawals, for the same reason criteria have them: incremental sync cannot see a
-- row that RLS has stopped returning.
-- ---------------------------------------------------------------------------

create table milestone_withdrawals (
    milestone_id  text        primary key,
    career_id     text        not null,
    withdrawn_at  timestamptz not null default now()
);

create index milestone_withdrawals_since_idx on milestone_withdrawals (withdrawn_at);

create or replace function track_milestone_withdrawal()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if tg_op = 'DELETE' then
        if old.review = 'VERIFIED' then
            insert into milestone_withdrawals (milestone_id, career_id)
            values (old.id, old.career_id)
            on conflict (milestone_id) do update set withdrawn_at = now();
        end if;
        return old;
    end if;

    if new.review = 'VERIFIED' then
        delete from milestone_withdrawals where milestone_id = new.id;
    elsif tg_op = 'UPDATE' and old.review = 'VERIFIED' then
        insert into milestone_withdrawals (milestone_id, career_id)
        values (new.id, new.career_id)
        on conflict (milestone_id) do update set withdrawn_at = now();
    end if;

    return new;
end;
$$;

create trigger milestones_track_withdrawal
    after insert or update or delete on milestones
    for each row execute function track_milestone_withdrawal();

-- ---------------------------------------------------------------------------
-- Row level security. Only VERIFIED milestones reach a client.
-- ---------------------------------------------------------------------------

alter table milestones             enable row level security;
alter table milestones_history     enable row level security;
alter table milestone_withdrawals  enable row level security;

revoke all on milestones            from anon, authenticated;
revoke all on milestones_history    from anon, authenticated;
revoke all on milestone_withdrawals from anon, authenticated;

-- verified_by and source_document_sha256 withheld, as for criteria.
grant select (
    id,
    career_id,
    position,
    review,
    verification_window,
    body,
    source_url,
    source_authority,
    effective_from,
    effective_to,
    last_verified_at,
    applies_to,
    updated_at
) on milestones to anon, authenticated;

create policy milestones_published_only on milestones
    for select
    to anon, authenticated
    using (review = 'VERIFIED');

grant select on milestone_withdrawals to anon, authenticated;

create policy milestone_withdrawals_are_public on milestone_withdrawals
    for select
    to anon, authenticated
    using (true);

-- milestones_history: no policy, so clients see nothing. Service role only.

create view published_milestones
with (security_invoker = true)
as
select
    id,
    career_id,
    position,
    review,
    verification_window,
    body,
    source_url,
    source_authority,
    effective_from,
    effective_to,
    last_verified_at,
    applies_to,
    updated_at
from milestones
where review = 'VERIFIED';

grant select on published_milestones to anon, authenticated;

comment on view published_milestones is
    'Client contract. Sync with updated_at > last_sync, then apply milestone_withdrawals since last_sync.';
