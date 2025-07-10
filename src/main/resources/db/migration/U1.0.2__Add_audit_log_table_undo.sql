-- U1.0.2__Add_audit_log_table_undo.sql
-- Undo script for audit log table

-- Drop indexes first
DROP INDEX IF EXISTS idx_audit_logs_created_at;
DROP INDEX IF EXISTS idx_audit_logs_table_name;
DROP INDEX IF EXISTS idx_audit_logs_action;
DROP INDEX IF EXISTS idx_audit_logs_user_id;

-- Drop the table
DROP TABLE IF EXISTS audit_logs;
