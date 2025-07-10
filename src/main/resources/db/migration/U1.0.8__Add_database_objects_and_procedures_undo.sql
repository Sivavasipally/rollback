-- U1.0.8__Add_database_objects_and_procedures_undo.sql
-- Remove database objects and procedures

-- Drop indexes on JSON columns
DROP INDEX IF EXISTS idx_audit_logs_new_values;
DROP INDEX IF EXISTS idx_audit_logs_old_values;

-- Drop views
DROP VIEW IF EXISTS v_department_hierarchy;
DROP VIEW IF EXISTS v_role_permissions;
DROP VIEW IF EXISTS v_user_activity;

-- Drop triggers
DROP TRIGGER IF EXISTS trg_users_audit_delete;
DROP TRIGGER IF EXISTS trg_users_audit_update;

-- Archive statistics and tasks before dropping
CREATE TABLE IF NOT EXISTS archive_user_statistics AS SELECT * FROM user_statistics;
CREATE TABLE IF NOT EXISTS archive_scheduled_tasks AS SELECT * FROM scheduled_tasks;

-- Drop tables
DROP TABLE IF EXISTS scheduled_tasks;
DROP TABLE IF EXISTS user_statistics;       