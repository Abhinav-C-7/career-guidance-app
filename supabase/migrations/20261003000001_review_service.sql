-- The review service's access.
--
-- The content service (Spring Boot, on Railway) runs the human review queue. It connects
-- as its own role, content_service, never as postgres, and that role can do exactly one
-- thing to an eligibility record: change who signed it and in what state it sits.
--
-- It cannot change a value. The column-level UPDATE grants below cover review state,
-- signature, verification date and the reviewer's note — not requirement, not body, not
-- the source. So even a bug in the review service, or a stolen session, cannot put a
-- wrong number in front of a student. Values change only through the publish path, in
-- reviewed files.

-- ---------------------------------------------------------------------------
-- Reviewer notes. Internal: never granted to anon or authenticated.
-- ---------------------------------------------------------------------------

alter table criteria   add column review_note text;
alter table milestones add column review_note text;

-- ---------------------------------------------------------------------------
-- The role. Created without a login: a migration must never carry a password. Enable it
-- from the SQL editor with:  alter role content_service with login password '...';
-- ---------------------------------------------------------------------------

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'content_service') then
        create role content_service nologin;
    end if;
end $$;

grant usage on schema public to content_service;

grant select on careers, criteria, milestones, source_documents to content_service;

grant update (review, verified_by, last_verified_at, review_note) on criteria   to content_service;
grant update (review, verified_by, last_verified_at, review_note) on milestones to content_service;

-- No insert, no delete, and nothing on the history or withdrawal tables: the triggers
-- that write those run as their owner (security definer), so the service never needs to.

-- RLS applies to this role like any other, so it needs its own policies to see and sign
-- rows that anon cannot.
create policy content_service_reads_careers on careers
    for select to content_service using (true);

create policy content_service_reads_criteria on criteria
    for select to content_service using (true);

create policy content_service_signs_criteria on criteria
    for update to content_service using (true) with check (true);

create policy content_service_reads_milestones on milestones
    for select to content_service using (true);

create policy content_service_signs_milestones on milestones
    for update to content_service using (true) with check (true);

create policy content_service_reads_sources on source_documents
    for select to content_service using (true);

comment on role content_service is
    'Review service. Can sign or return records; cannot change any value.';
