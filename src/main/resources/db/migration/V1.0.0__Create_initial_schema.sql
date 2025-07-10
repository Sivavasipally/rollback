-- V1.0.0__Create_initial_schema.sql
-- Initial database schema creation

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL,
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- Insert initial data
INSERT INTO roles (name, description) VALUES 
('ADMIN', 'System Administrator'),
('USER', 'Regular User'),
('GUEST', 'Guest User');

INSERT INTO users (username, email, first_name, last_name) VALUES 
('admin', 'admin@test.com', 'Admin', 'User'),
('testuser', 'test@test.com', 'Test', 'User');

INSERT INTO user_roles (user_id, role_id) VALUES 
(1, 1), -- admin has ADMIN role
(2, 2); -- testuser has USER role
