package com.demo.authservice.adapter.out;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {

  Optional<UserAccount> findByUsername(String username);
}
