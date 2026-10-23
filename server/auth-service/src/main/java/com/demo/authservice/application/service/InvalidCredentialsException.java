package com.demo.authservice.application.service;

/** Usuario desconocido o password incorrecta; el advice web la traduce a HTTP 401. */
public class InvalidCredentialsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidCredentialsException() {
    super("Invalid credentials");
  }
}
