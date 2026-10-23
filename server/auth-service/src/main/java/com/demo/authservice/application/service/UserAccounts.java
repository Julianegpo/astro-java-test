package com.demo.authservice.application.service;

import java.util.Optional;

/** Puerto de salida (D2): el caso de uso no conoce JPA, H2 ni la entidad de persistencia. */
public interface UserAccounts {

  Optional<Credentials> findByUsername(String username);

  /** Credenciales inmutables que el adaptador extrae del almacén. */
  record Credentials(String passwordHash, String roles) {}
}
