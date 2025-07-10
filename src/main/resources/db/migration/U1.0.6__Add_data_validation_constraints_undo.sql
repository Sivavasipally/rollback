-- U1.0.6__Add_data_validation_constraints_undo.sql
-- Remove data validation constraints and related objects

-- Drop the view
DROP VIEW IF EXISTS user_summary;

-- Archive metrics data
CREATE TABLE IF NOT EXISTS archive_user_metrics AS 
SELECT * FROM user_metrics;

-- Drop user_metrics table and indexes
DROP INDEX IF EXISTS idx_user_metrics_date;
DROP INDEX IF EXISTS idx_user_metrics_user_date;
DROP TABLE IF EXISTS user_metrics;

-- Remove constraints from user_profiles
ALTER TABLE user_profiles DROP CONSTRAINT IF EXISTS chk_phone_format;

-- Archive status values before removing
CREATE TABLE IF NOT EXISTS archive_user_status AS
SELECT id, status FROM users;

-- Remove status column and constraints from users
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_username_length;
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_email_format;
ALTER TABLE users DROP COLUMN IF EXISTS status;