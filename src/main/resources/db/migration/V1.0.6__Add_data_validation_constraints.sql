-- V1.0.6__Add_data_validation_constraints.sql
-- Add data validation constraints and business rules

-- Add check constraints to users table
ALTER TABLE users 
ADD CONSTRAINT chk_email_format 
CHECK (email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$');

ALTER TABLE users 
ADD CONSTRAINT chk_username_length 
CHECK (CHAR_LENGTH(username) >= 3 AND CHAR_LENGTH(username) <= 50);

-- Add status column with constraint
ALTER TABLE users 
ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE' 
CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED')) 
AFTER two_factor_secret;

-- Update existing users
UPDATE users SET status = 'ACTIVE' WHERE status IS NULL;

-- Add validation for user_profiles (H2-compatible)
ALTER TABLE user_profiles
ADD CONSTRAINT chk_phone_format
CHECK (phone IS NULL OR LENGTH(phone) BETWEEN 8 AND 15);

-- Create a table for tracking user activity metrics
CREATE TABLE user_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    metric_date DATE NOT NULL,
    login_count INT DEFAULT 0,
    api_calls_count INT DEFAULT 0,
    data_usage_mb DECIMAL(10,2) DEFAULT 0,
    active_minutes INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_metric_date (user_id, metric_date),
    CONSTRAINT chk_positive_metrics CHECK (
        login_count >= 0 AND 
        api_calls_count >= 0 AND 
        data_usage_mb >= 0 AND 
        active_minutes >= 0 AND 
        active_minutes <= 1440 -- max minutes in a day
    )
);

-- Add sample metrics
INSERT INTO user_metrics (user_id, metric_date, login_count, api_calls_count, data_usage_mb, active_minutes) VALUES
(1, CURDATE(), 5, 150, 25.5, 240),
(1, DATEADD('DAY', -1, CURDATE()), 3, 100, 15.0, 180),
(2, CURDATE(), 2, 50, 10.0, 120);

-- Create indexes for metrics queries
CREATE INDEX idx_user_metrics_user_date ON user_metrics(user_id, metric_date);
CREATE INDEX idx_user_metrics_date ON user_metrics(metric_date);

-- Add a view for user summary
CREATE VIEW user_summary AS
SELECT 
    u.id,
    u.username,
    u.email,
    u.status,
    u.created_at,
    up.phone,
    COUNT(DISTINCT ur.role_id) as role_count,
    COUNT(DISTINCT ua.address_id) as address_count,
    COALESCE(MAX(lh.login_time), u.created_at) as last_activity
FROM users u
LEFT JOIN user_profiles up ON u.id = up.user_id
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN user_addresses ua ON u.id = ua.user_id
LEFT JOIN login_history lh ON u.id = lh.user_id AND lh.login_status = 'SUCCESS'
GROUP BY u.id, u.username, u.email, u.status, u.created_at, up.phone;