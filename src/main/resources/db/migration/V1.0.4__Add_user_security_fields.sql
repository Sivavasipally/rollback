-- V1.0.4__Add_user_security_fields.sql
-- Add security-related fields to users table

-- Add new columns to users table (H2-compatible)
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN login_attempts INT DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN two_factor_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN two_factor_secret VARCHAR(255) NULL;

-- Update existing users with default password hash (for testing)
UPDATE users SET password_hash = '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG' -- password: "password123"
WHERE password_hash IS NULL;

-- Create index for login performance
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_last_login ON users(last_login_at);

-- Add login history table
CREATE TABLE login_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    login_ip VARCHAR(45),
    user_agent TEXT,
    login_status VARCHAR(20) NOT NULL, -- SUCCESS, FAILED, LOCKED
    failure_reason VARCHAR(100),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Add some sample login history (H2-compatible date arithmetic)
INSERT INTO login_history (user_id, login_time, login_ip, login_status) VALUES
(1, DATEADD('DAY', -1, NOW()), '192.168.1.1', 'SUCCESS'),
(1, DATEADD('HOUR', -2, NOW()), '192.168.1.1', 'SUCCESS'),
(2, DATEADD('HOUR', -3, NOW()), '10.0.0.1', 'FAILED'),
(2, DATEADD('HOUR', -1, NOW()), '10.0.0.1', 'SUCCESS');

-- Create index for login history queries
CREATE INDEX idx_login_history_user_id ON login_history(user_id);
CREATE INDEX idx_login_history_time ON login_history(login_time);