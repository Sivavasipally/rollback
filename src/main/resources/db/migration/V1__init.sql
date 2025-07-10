-- src/main/resources/db/migration/V1__init.sql
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create rollback audit table
CREATE TABLE IF NOT EXISTS flyway_rollback_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rollback_id VARCHAR(50) NOT NULL,
    version VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    performed_at TIMESTAMP NOT NULL
);

