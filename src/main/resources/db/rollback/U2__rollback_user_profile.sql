-- src/main/resources/db/rollback/U2__rollback_user_profile.sql
-- Rollback script for V2__add_user_profile.sql
-- Archive profile data
CREATE TABLE IF NOT EXISTS archive_user_profiles AS SELECT * FROM user_profiles;

-- Drop profile table
DROP TABLE IF EXISTS user_profiles;

-- Remove sample users added in V2
DELETE FROM users WHERE username IN ('admin', 'john_doe', 'jane_smith');