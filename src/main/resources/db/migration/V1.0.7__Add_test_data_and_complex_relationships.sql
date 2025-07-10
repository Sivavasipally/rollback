-- V1.0.7__Add_test_data_and_complex_relationships.sql
-- Add comprehensive test data and complex relationships for thorough testing

-- Create a complex relationship structure
CREATE TABLE departments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    code VARCHAR(10) NOT NULL UNIQUE,
    parent_department_id BIGINT,
    manager_user_id BIGINT,
    budget DECIMAL(15,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_department_id) REFERENCES departments(id) ON DELETE SET NULL,
    FOREIGN KEY (manager_user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Create employee details table
CREATE TABLE employee_details (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    employee_id VARCHAR(20) NOT NULL UNIQUE,
    department_id BIGINT,
    hire_date DATE NOT NULL,
    job_title VARCHAR(100),
    salary DECIMAL(10,2),
    commission_pct DECIMAL(5,2),
    manager_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL,
    FOREIGN KEY (manager_id) REFERENCES employee_details(id) ON DELETE SET NULL
);

-- Create projects table
CREATE TABLE projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(20) NOT NULL UNIQUE,
    department_id BIGINT,
    start_date DATE,
    end_date DATE,
    budget DECIMAL(15,2),
    status VARCHAR(20) DEFAULT 'PLANNING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL,
    CONSTRAINT chk_project_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_project_status CHECK (status IN ('PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED'))
);

-- Create project assignments
CREATE TABLE project_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    role VARCHAR(50),
    allocation_percentage INT DEFAULT 100,
    start_date DATE,
    end_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (employee_id) REFERENCES employee_details(id) ON DELETE CASCADE,
    UNIQUE KEY uk_project_employee (project_id, employee_id),
    CONSTRAINT chk_allocation CHECK (allocation_percentage > 0 AND allocation_percentage <= 100)
);

-- Insert test departments
INSERT INTO departments (name, code, parent_department_id, budget) VALUES
('Executive', 'EXEC', NULL, 5000000.00),
('Technology', 'TECH', 1, 2000000.00),
('Human Resources', 'HR', 1, 500000.00),
('Finance', 'FIN', 1, 800000.00),
('Development', 'DEV', 2, 1200000.00),
('Operations', 'OPS', 2, 800000.00);

-- Update department managers
UPDATE departments SET manager_user_id = 1 WHERE code = 'EXEC';
UPDATE departments SET manager_user_id = 2 WHERE code = 'TECH';

-- Insert more test users
INSERT INTO users (username, email, first_name, last_name) VALUES
('john.smith', 'john.smith@company.com', 'John', 'Smith'),
('jane.doe', 'jane.doe@company.com', 'Jane', 'Doe'),
('bob.wilson', 'bob.wilson@company.com', 'Bob', 'Wilson'),
('alice.johnson', 'alice.johnson@company.com', 'Alice', 'Johnson'),
('charlie.brown', 'charlie.brown@company.com', 'Charlie', 'Brown');

-- Insert employee details
INSERT INTO employee_details (user_id, employee_id, department_id, hire_date, job_title, salary, manager_id) VALUES
(1, 'EMP001', 1, '2020-01-15', 'CEO', 250000.00, NULL),
(2, 'EMP002', 2, '2020-03-01', 'CTO', 200000.00, 1),
(3, 'EMP003', 5, '2021-06-15', 'Senior Developer', 120000.00, 2),
(4, 'EMP004', 5, '2021-09-01', 'Developer', 90000.00, 2),
(5, 'EMP005', 3, '2022-01-10', 'HR Manager', 95000.00, 1),
(6, 'EMP006', 4, '2022-03-20', 'Financial Analyst', 85000.00, 1),
(7, 'EMP007', 6, '2022-06-01', 'DevOps Engineer', 110000.00, 2);

-- Insert projects
INSERT INTO projects (name, code, department_id, start_date, end_date, budget, status) VALUES
('Digital Transformation', 'DT2023', 2, '2023-01-01', '2023-12-31', 1500000.00, 'ACTIVE'),
('Mobile App Development', 'MOB2023', 5, '2023-03-01', '2023-09-30', 500000.00, 'ACTIVE'),
('Infrastructure Upgrade', 'INFRA2023', 6, '2023-02-01', '2023-07-31', 800000.00, 'COMPLETED'),
('AI Research Initiative', 'AI2024', 5, '2024-01-01', NULL, 2000000.00, 'PLANNING'),
('Cloud Migration', 'CLOUD2023', 6, '2023-04-01', '2023-10-31', 600000.00, 'ACTIVE');

-- Insert project assignments
INSERT INTO project_assignments (project_id, employee_id, role, allocation_percentage, start_date) VALUES
(1, 2, 'Project Sponsor', 20, '2023-01-01'),
(1, 3, 'Technical Lead', 80, '2023-01-01'),
(1, 4, 'Developer', 100, '2023-01-15'),
(2, 3, 'Lead Developer', 50, '2023-03-01'),
(2, 4, 'Developer', 100, '2023-03-01'),
(3, 7, 'Infrastructure Lead', 100, '2023-02-01'),
(5, 7, 'Cloud Architect', 80, '2023-04-01');

-- Create indexes for performance
CREATE INDEX idx_departments_parent ON departments(parent_department_id);
CREATE INDEX idx_departments_manager ON departments(manager_user_id);
CREATE INDEX idx_employee_details_dept ON employee_details(department_id);
CREATE INDEX idx_employee_details_manager ON employee_details(manager_id);
CREATE INDEX idx_projects_dept ON projects(department_id);
CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_project_assignments_project ON project_assignments(project_id);
CREATE INDEX idx_project_assignments_employee ON project_assignments(employee_id);

-- Create a materialized view simulation (as a table with triggers)
CREATE TABLE department_summary (
    department_id BIGINT PRIMARY KEY,
    department_name VARCHAR(100),
    employee_count INT DEFAULT 0,
    total_salary DECIMAL(15,2) DEFAULT 0,
    active_projects INT DEFAULT 0,
    total_project_budget DECIMAL(15,2) DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE CASCADE
);

-- Populate department summary
INSERT INTO department_summary (department_id, department_name, employee_count, total_salary, active_projects, total_project_budget)
SELECT 
    d.id,
    d.name,
    COUNT(DISTINCT ed.id),
    COALESCE(SUM(ed.salary), 0),
    COUNT(DISTINCT CASE WHEN p.status = 'ACTIVE' THEN p.id END),
    COALESCE(SUM(CASE WHEN p.status = 'ACTIVE' THEN p.budget ELSE 0 END), 0)
FROM departments d
LEFT JOIN employee_details ed ON d.id = ed.department_id
LEFT JOIN projects p ON d.id = p.department_id
GROUP BY d.id, d.name;