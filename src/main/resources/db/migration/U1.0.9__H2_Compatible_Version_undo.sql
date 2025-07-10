-- U1.0.9__H2_Compatible_Version_undo.sql
-- Undo script for H2-compatible database objects

-- Drop the stored procedure
DROP ALIAS IF EXISTS AUDIT_USER_CHANGE;

-- Drop views
DROP VIEW IF EXISTS v_department_hierarchy;
DROP VIEW IF EXISTS v_role_permissions;
DROP VIEW IF EXISTS v_user_activity;

-- Archive statistics and tasks before dropping
CREATE TABLE IF NOT EXISTS archive_user_statistics AS SELECT * FROM user_statistics;
CREATE TABLE IF NOT EXISTS archive_scheduled_tasks AS SELECT * FROM scheduled_tasks;

-- Drop tables
DROP TABLE IF EXISTS scheduled_tasks;
DROP TABLE IF EXISTS user_statistics;