package com.demo.authservice.adapter.out;

import com.demo.authservice.application.service.UserAccounts;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Implementación JPA (Spring Data) del puerto {@link UserAccounts}. */
@Service
public class JpaUserAccounts implements UserAccounts {

  private final UserAccountRepository repository;

  public JpaUserAccounts(UserAccountRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<Credentials> findByUsername(String username) {
    return repository.findByUsername(username).map(JpaUserAccounts::toCredentials);
  }

  private static Credentials toCredentials(UserAccount account) {
    return new Credentials(account.getPasswordHash(), account.getRoles());
  }
}
