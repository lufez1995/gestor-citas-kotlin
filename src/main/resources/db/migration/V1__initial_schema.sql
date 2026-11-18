CREATE TABLE app_users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
);
CREATE TABLE clients (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(40)
);
CREATE TABLE professionals (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL
);
CREATE TABLE appointments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    client_id BIGINT NOT NULL,
    professional_id BIGINT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT fk_appointment_client FOREIGN KEY (client_id) REFERENCES clients(id),
    CONSTRAINT fk_appointment_professional FOREIGN KEY (professional_id) REFERENCES professionals(id),
    INDEX idx_appointment_professional_time (professional_id, starts_at, ends_at),
    INDEX idx_appointment_starts_at (starts_at)
);
