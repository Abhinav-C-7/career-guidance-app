-- Grants for service_role, the role the secret key authenticates as.
--
-- With "automatically expose new tables" turned off (deliberately, see 0002), Supabase does
-- not grant service_role anything on tables we create. service_role bypasses RLS, but RLS
-- is not privileges: without these grants every request fails with "permission denied",
-- and tools/publish cannot read or write content at all.
--
-- Scoped to what the publishing path needs. History and withdrawal tables are written by
-- security-definer triggers, so service_role is only given read access there, for audits.

grant usage on schema public to service_role;

-- tools/publish: diff against current rows, then upsert and delete.
grant select, insert, update, delete on careers, criteria, milestones to service_role;

-- The extraction pipeline archives fetched documents here.
grant select, insert on source_documents to service_role;

-- Read-only: these are written by triggers, never directly.
grant select on criteria_history, milestones_history, criteria_withdrawals, milestone_withdrawals
    to service_role;
