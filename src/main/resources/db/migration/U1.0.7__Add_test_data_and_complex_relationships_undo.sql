-- U1.0.7__Add_test_data_and_complex_relationships_undo.sql
-- Undo complex relationships and test data

-- Archive all data before dropping
CREATE TABLE IF NOT EXISTS archive_department_summary AS SELECT * FROM department_summary;
CREATE TABLE IF NOT EXISTS archive_project_assignments AS SELECT * FROM project_assignments;
CREATE TABLE IF NOT EXISTS archive_projects AS SELECT * FROM projects;
CREATE TABLE IF NOT EXISTS archive_employee_details AS SELECT * FROM employee_details;
CREATE TABLE IF NOT EXISTS archive_departments AS SELECT * FROM departments;

-- Drop indexes
DROP INDEX IF EXISTS idx_project_assignments_employee;
DROP INDEX IF EXISTS idx_project_assignments_project;
DROP INDEX IF EXISTS idx_projects_status;
DROP INDEX IF EXISTS idx_projects_dept;
DROP INDEX IF EXISTS idx_employee_details_manager;
DROP INDEX IF EXISTS idx_employee_details_dept;
DROP INDEX IF EXISTS idx_departments_manager;
DROP INDEX IF EXISTS idx_departments_parent;

-- Drop tables in correct order due to foreign key constraints
DROP TABLE IF EXISTS department_summary;
DROP TABLE IF EXISTS project_assignments;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS employee_details;
DROP TABLE IF EXISTS departments;

-- Remove test users that were added (be careful not to remove original users)
DELETE FROM users WHERE username IN ('john.smith', 'jane.doe', 'bob.wilson', 'alice.johnson', 'charlie.brown');