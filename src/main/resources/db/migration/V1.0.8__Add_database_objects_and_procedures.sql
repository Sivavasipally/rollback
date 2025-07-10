-- V1.0.8__Add_database_objects_and_procedures.sql
-- Add complex database objects like views, functions, and triggers

-- Create a comprehensive user activity view
CREATE VIEW v_user_activity AS
SELECT 
    u.id as user_id,
    u.username,
    u.email,
    u.status,
    u.created_at as user_created_at,
    u.last_login_at,
    up.phone,
    up.is_active as profile_active,
    COUNT(DISTINCT lh.id) as total_logins,
    COUNT(DISTINCT CASE WHEN lh.login_status = 'SUCCESS' THEN lh.id END) as successful_logins,
    COUNT(DISTINCT CASE WHEN lh.login_status = 'FAILED' THEN lh.id END) as failed_logins,
    MAX(lh.login_time) as last_login_attempt,
    COUNT(DISTINCT al.id) as audit_entries,
    COUNT(DISTINCT upref.id) as preference_count
FROM users u
LEFT JOIN user_profiles up ON u.id = up.user_id
LEFT JOIN login_history lh ON u.id = lh.user_id
LEFT JOIN audit_logs al ON u.id = al.user_id
LEFT JOIN user_preferences upref ON u.id = upref.user_id
GROUP BY u.id, u.username, u.email, u.status, u.created_at, u.last_login_at, up.phone, up.is_active;

-- Create a view for role permissions summary
CREATE VIEW v_role_permissions AS
SELECT 
    r.id as role_id,
    r.name as role_name,
    r.description,
    COUNT(DISTINCT ur.user_id) as user_count,
    GROUP_CONCAT(DISTINCT u.username ORDER BY u.username SEPARATOR ', ') as users_list
FROM roles r
LEFT JOIN user_roles ur ON r.id = ur.role_id
LEFT JOIN users u ON ur.user_id = u.id
GROUP BY r.id, r.name, r.description;

-- Create audit trigger for users table
DELIMITER //

CREATE TRIGGER trg_users_audit_update
AFTER UPDATE ON users
FOR EACH ROW
BEGIN
    INSERT INTO audit_logs (user_id, action, table_name, record_id, old_values, new_values, created_at)
    VALUES (
        NEW.id,
        'UPDATE',
        'users',
        NEW.id,
        JSON_OBJECT('username', OLD.username, 'email', OLD.email, 'status', OLD.status),
        JSON_OBJECT('username', NEW.username, 'email', NEW.email, 'status', NEW.status),
        NOW()
    );
END//

CREATE TRIGGER trg_users_audit_delete
AFTER DELETE ON users
FOR EACH ROW
BEGIN
    INSERT INTO audit_logs (user_id, action, table_name, record_id, old_values, created_at)
    VALUES (
        OLD.id,
        'DELETE',
        'users',
        OLD.id,
        JSON_OBJECT('username', OLD.username, 'email', OLD.email),
        NOW()
    );
END//

DELIMITER ;

-- Create a table for storing computed statistics
CREATE TABLE user_statistics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    computation_date DATE NOT NULL,
    total_users INT DEFAULT 0,
    active_users INT DEFAULT 0,
    new_users_today INT DEFAULT 0,
    new_users_week INT DEFAULT 0,
    new_users_month INT DEFAULT 0,
    avg_logins_per_user DECIMAL(10,2) DEFAULT 0,
    total_failed_logins INT DEFAULT 0,
    computed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_computation_date (computation_date)
);

-- Create a scheduled event table (for databases that support events)
CREATE TABLE scheduled_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(100) NOT NULL UNIQUE,
    task_type VARCHAR(50) NOT NULL,
    schedule_expression VARCHAR(100),
    last_run TIMESTAMP NULL,
    next_run TIMESTAMP NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Insert sample scheduled tasks
INSERT INTO scheduled_tasks (task_name, task_type, schedule_expression) VALUES
('compute_user_statistics', 'DAILY', '0 2 * * *'),
('cleanup_old_logs', 'WEEKLY', '0 3 * * 0'),
('generate_monthly_report', 'MONTHLY', '0 4 1 * *');

-- Create a complex query view that might be affected by rollback
CREATE VIEW v_department_hierarchy AS
WITH RECURSIVE dept_hierarchy AS (
    -- Anchor: top-level departments
    SELECT 
        id,
        name,
        code,
        parent_department_id,
        manager_user_id,
        0 as level,
        name as path
    FROM departments
    WHERE parent_department_id IS NULL
    
    UNION ALL
    
    -- Recursive: child departments
    SELECT 
        d.id,
        d.name,
        d.code,
        d.parent_department_id,
        d.manager_user_id,
        dh.level + 1,
        CONCAT(dh.path, ' > ', d.name) as path
    FROM departments d
    INNER JOIN dept_hierarchy dh ON d.parent_department_id = dh.id
)
SELECT 
    dh.*,
    u.username as manager_name,
    COUNT(DISTINCT ed.id) as employee_count
FROM dept_hierarchy dh
LEFT JOIN users u ON dh.manager_user_id = u.id
LEFT JOIN employee_details ed ON dh.id = ed.department_id
GROUP BY dh.id, dh.name, dh.code, dh.parent_department_id, dh.manager_user_id, dh.level, dh.path, u.username;

-- Create indexes on audit_logs for JSON queries (if supported)
CREATE INDEX idx_audit_logs_old_values ON audit_logs((CAST(old_values AS CHAR(255))));
CREATE INDEX idx_audit_logs_new_values ON audit_logs((CAST(new_values AS CHAR(255))));