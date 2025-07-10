-- U1.0.5__Normalize_user_addresses_undo.sql
-- Undo address normalization

-- Archive normalized data before reverting
CREATE TABLE IF NOT EXISTS archive_addresses AS SELECT * FROM addresses;
CREATE TABLE IF NOT EXISTS archive_user_addresses AS SELECT * FROM user_addresses;

-- Restore the address comment in user_profiles
ALTER TABLE user_profiles 
MODIFY COLUMN address TEXT COMMENT '';

-- Note: The original address data is still in user_profiles.address
-- so we don't need to restore it

-- Drop indexes
DROP INDEX IF EXISTS idx_user_addresses_default;
DROP INDEX IF EXISTS idx_user_addresses_user;
DROP INDEX IF EXISTS idx_addresses_postal;
DROP INDEX IF EXISTS idx_addresses_type;

-- Drop tables in correct order due to foreign keys
DROP TABLE IF EXISTS user_addresses;
DROP TABLE IF EXISTS addresses;