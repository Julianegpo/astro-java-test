package com.demo.authservice.application.service;

import java.util.Optional;

/** Output port (D2): the use case knows nothing about JPA, H2 nor the persistence entity. */
public interface UserAccounts {

  Optional<Credentials> findByUsername(String username);

  /** Immutable credentials the adapter extracts from the store. */
  record Credentials(String passwordHash, String roles) {}
}
