-- V1.0.5__Normalize_user_addresses.sql
-- Normalize addresses from user_profiles into separate table

-- Create addresses table
CREATE TABLE addresses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    street_line1 VARCHAR(255),
    street_line2 VARCHAR(255),
    city VARCHAR(100),
    state_province VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100),
    address_type VARCHAR(20) DEFAULT 'HOME', -- HOME, WORK, BILLING, SHIPPING
    is_primary BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create user_addresses junction table
CREATE TABLE user_addresses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    address_id BIGINT NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (address_id) REFERENCES addresses(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_address (user_id, address_id)
);

-- Migrate existing address data from user_profiles
INSERT INTO addresses (street_line1, city, country, address_type, is_primary)
SELECT 
    address as street_line1,
    CASE 
        WHEN address LIKE '%Admin City%' THEN 'Admin City'
        WHEN address LIKE '%User Town%' THEN 'User Town'
        ELSE 'Unknown City'
    END as city,
    'USA' as country,
    'HOME' as address_type,
    TRUE as is_primary
FROM user_profiles
WHERE address IS NOT NULL AND address != '';

-- Link addresses to users
INSERT INTO user_addresses (user_id, address_id, is_default)
SELECT 
    up.user_id,
    a.id,
    TRUE
FROM user_profiles up
JOIN addresses a ON (
    (up.address LIKE '%Admin%' AND a.street_line1 LIKE '%Admin%') OR
    (up.address LIKE '%User%' AND a.street_line1 LIKE '%User%')
)
WHERE up.address IS NOT NULL AND up.address != '';

-- Add more sample addresses
INSERT INTO addresses (street_line1, city, state_province, postal_code, country, address_type) VALUES
('789 Work Plaza', 'Tech City', 'CA', '94000', 'USA', 'WORK'),
('321 Billing Ave', 'Finance Town', 'NY', '10001', 'USA', 'BILLING');

-- Link additional addresses to admin user
INSERT INTO user_addresses (user_id, address_id, is_default) VALUES
(1, (SELECT id FROM addresses WHERE street_line1 = '789 Work Plaza'), FALSE),
(1, (SELECT id FROM addresses WHERE street_line1 = '321 Billing Ave'), FALSE);

-- Create indexes
CREATE INDEX idx_addresses_type ON addresses(address_type);
CREATE INDEX idx_addresses_postal ON addresses(postal_code);
CREATE INDEX idx_user_addresses_user ON user_addresses(user_id);
CREATE INDEX idx_user_addresses_default ON user_addresses(is_default);

-- Mark old address column as deprecated (don't drop yet for safety)
ALTER TABLE user_profiles 
MODIFY COLUMN address TEXT COMMENT 'DEPRECATED - Use addresses table instead';