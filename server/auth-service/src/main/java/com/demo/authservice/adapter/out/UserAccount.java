package com.demo.authservice.adapter.out;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** User persisted in H2; seeded via schema.sql/data.sql (D16). */
@Entity
@Table(name = "users")
@Getter
@Setter
public class UserAccount {

  @Id
  @Column(length = 64)
  private String username;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(nullable = false, length = 255)
  private String roles;
}
