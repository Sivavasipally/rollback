-- U1.0.3__Add_user_preferences_table_undo.sql
-- Undo script for user preferences table

-- Archive data before dropping (optional for recovery)
CREATE TABLE IF NOT EXISTS archive_user_preferences AS 
SELECT * FROM user_preferences;

-- Drop indexes first
DROP INDEX IF EXISTS idx_user_preferences_key;
DROP INDEX IF EXISTS idx_user_preferences_user_id;

-- Drop the table
DROP TABLE IF EXISTS user_preferences;