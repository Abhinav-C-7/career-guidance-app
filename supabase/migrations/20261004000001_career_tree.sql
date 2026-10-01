-- Careers form a tree: doctor -> specialist -> surgeon -> neurosurgeon.
--
-- A specialisation inherits its parent's pathway, so each level stores only its own steps
-- and criteria. The app walks parent_id to assemble the chain (Lineage in :domain).

alter table careers
    add column parent_id text references careers (id) on delete restrict,
    add column summary text,
    -- False for careers with no licence or mandatory exam (software). Every step of such a
    -- career is a common route, and the app says so.
    add column regulated boolean not null default true,
    add constraint career_not_its_own_parent check (parent_id is null or parent_id <> id);

create index careers_parent_idx on careers (parent_id);

-- Longer cycles are rejected by tools/publish and the content tests before they get here.

grant select (parent_id, summary, regulated) on careers to anon, authenticated;

-- New columns go at the end, so this is a compatible replacement of the client contract.
create or replace view published_careers
with (security_invoker = true)
as
select id, title, family, updated_at, parent_id, summary, regulated
from careers;
