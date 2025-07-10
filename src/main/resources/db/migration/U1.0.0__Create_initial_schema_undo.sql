-- U1.0.0__Create_initial_schema_undo.sql
-- Undo script for initial schema creation

-- Drop tables in reverse order due to foreign key constraints
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS users;
