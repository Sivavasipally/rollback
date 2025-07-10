-- V1.0.2__Add_audit_log_table.sql
-- Add audit logging table for tracking user activities

CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    action VARCHAR(100) NOT NULL,
    table_name VARCHAR(50),
    record_id BIGINT,
    old_values TEXT,
    new_values TEXT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Add some sample audit data
INSERT INTO audit_logs (user_id, action, table_name, record_id, new_values, ip_address) VALUES 
(1, 'CREATE', 'users', 1, '{"username":"admin","email":"admin@test.com"}', '127.0.0.1'),
(1, 'CREATE', 'users', 2, '{"username":"testuser","email":"test@test.com"}', '127.0.0.1'),
(1, 'CREATE', 'user_profiles', 1, '{"user_id":1,"phone":"+1-555-0001"}', '127.0.0.1'),
(2, 'UPDATE', 'user_profiles', 2, '{"bio":"Updated test user profile"}', '192.168.1.100');

-- Add indexes for performance
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_table_name ON audit_logs(table_name);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
