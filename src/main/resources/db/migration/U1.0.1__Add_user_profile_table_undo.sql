-- U1.0.1__Add_user_profile_table_undo.sql
-- Undo script for user profile table

-- Drop indexes first
DROP INDEX IF EXISTS idx_user_profiles_active;
DROP INDEX IF EXISTS idx_user_profiles_user_id;

-- Drop the table
DROP TABLE IF EXISTS user_profiles;
