package com.demo.authservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.authservice.adapter.out.UserAccount;
import com.demo.authservice.adapter.out.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Verifies the data.sql seed (D16): user demo/demo with a real BCrypt hash. */
@SpringBootTest
class UserAccountSeedTests {

  @Autowired private UserAccountRepository repository;

  @Test
  void seedsDemoUserWithBcryptHashMatchingPlainPassword() {
    UserAccount demo = repository.findByUsername("demo").orElseThrow();

    assertThat(new BCryptPasswordEncoder().matches("demo", demo.getPasswordHash())).isTrue();
    assertThat(demo.getRoles()).contains("ROLE_USER");
  }

  @Test
  void doesNotSeedAnyOtherUser() {
    assertThat(repository.count()).isEqualTo(1);
  }
}
