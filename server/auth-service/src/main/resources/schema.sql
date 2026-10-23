CREATE TABLE users (
    username      VARCHAR(64)  PRIMARY KEY,
    password_hash VARCHAR(100) NOT NULL,
    roles         VARCHAR(255) NOT NULL
);
