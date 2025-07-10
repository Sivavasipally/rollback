-- V1.0.1__Add_user_profile_table.sql
-- Add user profile table with additional user information

CREATE TABLE user_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    phone VARCHAR(20),
    address TEXT,
    date_of_birth DATE,
    profile_picture_url VARCHAR(500),
    bio TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Add some test profile data
INSERT INTO user_profiles (user_id, phone, address, bio, is_active) VALUES 
(1, '+1-555-0001', '123 Admin Street, Admin City', 'System Administrator Profile', TRUE),
(2, '+1-555-0002', '456 User Avenue, User Town', 'Test User Profile', TRUE);

-- Add index for better performance
CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX idx_user_profiles_active ON user_profiles(is_active);
