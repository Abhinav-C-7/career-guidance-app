-- Content store schema.
--
-- This mirrors the :domain Kotlin model. When one changes the other must change with it —
-- see CLAUDE.md, "the schema contract is shared". The constraints below deliberately
-- duplicate the invariants in Criterion's init block: a wrong eligibility value must be
-- rejected by the database even if it gets past the application.

-- ---------------------------------------------------------------------------
-- Enums. Values match the Kotlin enum names exactly, so JSON round-trips.
-- ---------------------------------------------------------------------------

create type review_state as enum (
    'DRAFT',
    'NEEDS_REVIEW',
    'KNOWN_UNSOURCED',
    'VERIFIED'
);

-- Declared weakest to strongest; when two records disagree the higher authority wins.
create type source_authority as enum (
    'SECONDARY',
    'BOARD_CIRCULAR',
    'GOVERNMENT_RELEASE',
    'OFFICIAL_NOTIFICATION'
);

create type verification_window as enum (
    'VOLATILE',
    'ANNUAL',
    'SLOW'
);

create type career_family as enum (
    'DEFENCE',
    'MEDICAL',
    'ENGINEERING',
    'COMMERCE',
    'CIVIC',
    'DESIGN',
    'MARITIME'
);

-- ---------------------------------------------------------------------------
-- Archived source documents.
--
-- The pipeline never extracts from a live URL: government PDFs are replaced in
-- place every cycle, and a source_url that 404s is a criterion we can no longer
-- defend. The bytes in storage are the evidence; the URL is a convenience.
-- ---------------------------------------------------------------------------

create table source_documents (
    sha256              text primary key,
    url                 text        not null,
    final_url           text,
    fetched_at          timestamptz not null default now(),
    storage_path        text        not null,
    media_type          text,
    byte_size           bigint,

    -- The document's own stated date and edition, recorded so a reviewer can
    -- confirm it is the edition the citing source actually names. This is the
    -- "right scope, wrong edition" failure and no automated check catches it.
    stated_date         date,
    stated_edition      text,

    -- Below a threshold the extractor must refuse rather than transcribe
    -- mangled OCR. See docs/extraction-pipeline.md.
    text_layer_quality  real
);

comment on table source_documents is
    'Immutable archive of fetched sources, content-addressed by SHA-256.';

-- ---------------------------------------------------------------------------
-- Careers
-- ---------------------------------------------------------------------------

create table careers (
    id          text primary key,
    title       text          not null,
    family      career_family not null,

    -- Internal only. Holds review notes and model gaps, never shown to a
    -- student — see the column grants in 0002.
    notes       jsonb         not null default '[]'::jsonb,

    created_at  timestamptz   not null default now(),
    updated_at  timestamptz   not null default now()
);

-- ---------------------------------------------------------------------------
-- Criteria
--
-- The requirement payload is JSONB on purpose. The Requirement hierarchy has a
-- dozen shapes, and nothing ever queries inside one — no caller asks "find
-- criteria where height > 150". We query by career, review state, effective
-- window and staleness, so those are real columns and the payload stays opaque.
-- ---------------------------------------------------------------------------

create table criteria (
    id                      text primary key,
    career_id               text not null references careers (id) on delete cascade,
    label                   text not null,
    review                  review_state not null,
    verification_window     verification_window not null default 'ANNUAL',

    -- Exactly what kotlinx.serialization emits for Requirement, discriminated
    -- by "type". Null only for a declared gap.
    requirement             jsonb,

    -- Provenance, flattened because every one of these is queried on.
    source_url              text,
    source_authority        source_authority,
    source_document_sha256  text references source_documents (sha256),
    effective_from          date,
    effective_to            date,
    last_verified_at        date,
    verified_by             text,

    -- Where the real figure lives when we cannot state it ourselves.
    look_up_at              jsonb,

    -- Applicability: sex, states, boards. Null members mean "everyone".
    applies_to              jsonb not null default '{}'::jsonb,

    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now(),

    -- Mirrors Criterion's init block. A criterion is either something we can
    -- state and stand behind, or an openly declared gap with a pointer. There
    -- is no third shape where a number sits in the table unsourced.
    constraint criterion_shape check (
        case review
            when 'KNOWN_UNSOURCED' then
                requirement is null
                and look_up_at is not null
            else
                requirement is not null
                and source_url is not null
                and source_authority is not null
                and effective_from is not null
                and last_verified_at is not null
                and verified_by is not null
        end
    ),

    constraint effective_range_sane check (
        effective_to is null or effective_to >= effective_from
    ),

    -- Only a person signs for a value. The extraction pass writes this literal
    -- string into verified_by, so this constraint makes it impossible for an
    -- automated run to promote its own output — even by accident, even with the
    -- service key.
    constraint verified_requires_a_human check (
        review <> 'VERIFIED' or verified_by <> 'automated-extraction-unreviewed'
    )
);

comment on constraint verified_requires_a_human on criteria is
    'An extraction job must never be able to sign for its own output.';

create index criteria_career_idx on criteria (career_id);
create index criteria_review_idx on criteria (review);
create index criteria_staleness_idx on criteria (last_verified_at);

-- The hot read path: the app only ever pulls verified rows, incrementally.
create index criteria_published_idx on criteria (updated_at)
    where review = 'VERIFIED';

-- ---------------------------------------------------------------------------
-- Change history
--
-- effective_from/effective_to record when a rule applied in the real world.
-- This table records when *we* changed our mind about what the rule was. Both
-- matter, and they are not the same thing. CLAUDE.md requires criteria be
-- versioned, never overwritten.
-- ---------------------------------------------------------------------------

create table criteria_history (
    history_id  bigserial primary key,
    criterion_id text       not null,
    operation   text        not null,
    changed_at  timestamptz not null default now(),
    changed_by  text        not null,
    row_before  jsonb,
    row_after   jsonb
);

create index criteria_history_criterion_idx on criteria_history (criterion_id, changed_at desc);

create or replace function record_criterion_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into criteria_history (criterion_id, operation, changed_by, row_before, row_after)
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

create trigger criteria_audit
    after insert or update or delete on criteria
    for each row execute function record_criterion_change();

-- ---------------------------------------------------------------------------
-- updated_at maintenance. The app syncs on this column, so it must never lie.
-- ---------------------------------------------------------------------------

create or replace function touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger careers_touch
    before update on careers
    for each row execute function touch_updated_at();

create trigger criteria_touch
    before update on criteria
    for each row execute function touch_updated_at();
