-- Fixed user demo/demo (D16). Precomputed BCrypt hash (strength 10) of "demo".
INSERT INTO users (username, password_hash, roles)
VALUES ('demo', '$2a$10$Geix9psUsjPfnTMmzgdDM.xIyzrQoXgzBqVnWMraOnHzQaG5cKVau', 'ROLE_USER');
