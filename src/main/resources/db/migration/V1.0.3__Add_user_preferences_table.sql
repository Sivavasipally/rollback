-- V1.0.3__Add_user_preferences_table.sql
-- Add user preferences table for storing user settings

CREATE TABLE user_preferences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    preference_key VARCHAR(100) NOT NULL,
    preference_value TEXT,
    value_type VARCHAR(20) DEFAULT 'STRING', -- STRING, NUMBER, BOOLEAN, JSON
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_preference (user_id, preference_key)
);

-- Add sample preferences
INSERT INTO user_preferences (user_id, preference_key, preference_value, value_type) VALUES 
(1, 'theme', 'dark', 'STRING'),
(1, 'language', 'en', 'STRING'),
(1, 'notifications_enabled', 'true', 'BOOLEAN'),
(2, 'theme', 'light', 'STRING'),
(2, 'timezone', 'UTC', 'STRING');

-- Create indexes for performance
CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);
CREATE INDEX idx_user_preferences_key ON user_preferences(preference_key);