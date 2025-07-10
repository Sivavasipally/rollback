-- src/main/resources/db/rollback/U1__rollback_init.sql
-- Rollback script for V1__init.sql
-- Archive data before dropping
CREATE TABLE IF NOT EXISTS archive_users AS SELECT * FROM users;
CREATE TABLE IF NOT EXISTS archive_flyway_rollback_audit AS SELECT * FROM flyway_rollback_audit;

-- Drop tables
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS flyway_rollback_audit;

