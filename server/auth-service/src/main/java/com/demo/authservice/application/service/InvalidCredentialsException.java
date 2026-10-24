package com.demo.authservice.application.service;

/** Unknown user or wrong password; the web advice translates it into HTTP 401. */
public class InvalidCredentialsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidCredentialsException() {
    super("Invalid credentials");
  }
}
