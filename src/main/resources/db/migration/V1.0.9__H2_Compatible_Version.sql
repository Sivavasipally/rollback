-- V1.0.9__H2_Compatible_Version.sql
-- H2-compatible version of database objects and procedures
-- Use this instead of V1.0.8 when running tests with H2

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
    LISTAGG(DISTINCT u.username, ', ') WITHIN GROUP (ORDER BY u.username) as users_list
FROM roles r
LEFT JOIN user_roles ur ON r.id = ur.role_id
LEFT JOIN users u ON ur.user_id = u.id
GROUP BY r.id, r.name, r.description;

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

-- Create a scheduled event table
CREATE TABLE scheduled_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(100) NOT NULL UNIQUE,
    task_type VARCHAR(50) NOT NULL,
    schedule_expression VARCHAR(100),
    last_run TIMESTAMP NULL,
    next_run TIMESTAMP NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert sample scheduled tasks
INSERT INTO scheduled_tasks (task_name, task_type, schedule_expression) VALUES
('compute_user_statistics', 'DAILY', '0 2 * * *'),
('cleanup_old_logs', 'WEEKLY', '0 3 * * 0'),
('generate_monthly_report', 'MONTHLY', '0 4 1 * *');

-- Create a complex query view using H2-compatible syntax
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
    dh.id,
    dh.name,
    dh.code,
    dh.parent_department_id,
    dh.manager_user_id,
    dh.level,
    dh.path,
    u.username as manager_name,
    COUNT(DISTINCT ed.id) as employee_count
FROM dept_hierarchy dh
LEFT JOIN users u ON dh.manager_user_id = u.id
LEFT JOIN employee_details ed ON dh.id = ed.department_id
GROUP BY dh.id, dh.name, dh.code, dh.parent_department_id, dh.manager_user_id, dh.level, dh.path, u.username;

-- Note: H2 doesn't support the same trigger syntax as MySQL
-- For testing purposes, we'll create a simpler audit mechanism using a stored procedure
-- This is a placeholder - in production, use database-specific trigger syntax

-- Create a simple audit logging procedure for H2
CREATE ALIAS AUDIT_USER_CHANGE AS $$
void auditUserChange(Connection conn, Long userId, String action, String tableName, 
                    Long recordId, String oldValues, String newValues) throws SQLException {
    String sql = "INSERT INTO audit_logs (user_id, action, table_name, record_id, " +
                "old_values, new_values, created_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setLong(1, userId);
        ps.setString(2, action);
        ps.setString(3, tableName);
        ps.setLong(4, recordId);
        ps.setString(5, oldValues);
        ps.setString(6, newValues);
        ps.executeUpdate();
    }
}
$$;