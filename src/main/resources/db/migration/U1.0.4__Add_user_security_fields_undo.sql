-- U1.0.4__Add_user_security_fields_undo.sql
-- Undo script for user security fields

-- Archive login history before dropping
CREATE TABLE IF NOT EXISTS archive_login_history AS 
SELECT * FROM login_history;

-- Drop login history table and indexes
DROP INDEX IF EXISTS idx_login_history_time;
DROP INDEX IF EXISTS idx_login_history_user_id;
DROP TABLE IF EXISTS login_history;

-- Remove indexes from users table
DROP INDEX IF EXISTS idx_users_last_login;
DROP INDEX IF EXISTS idx_users_email;

-- Archive security field values before removing (for recovery if needed)
CREATE TABLE IF NOT EXISTS archive_user_security_fields AS
SELECT id, password_hash, last_login_at, login_attempts, locked_until, 
       two_factor_enabled, two_factor_secret
FROM users;

-- Remove columns from users table
ALTER TABLE users DROP COLUMN IF EXISTS two_factor_secret;
ALTER TABLE users DROP COLUMN IF EXISTS two_factor_enabled;
ALTER TABLE users DROP COLUMN IF EXISTS locked_until;
ALTER TABLE users DROP COLUMN IF EXISTS login_attempts;
ALTER TABLE users DROP COLUMN IF EXISTS last_login_at;
ALTER TABLE users DROP COLUMN IF EXISTS password_hash;