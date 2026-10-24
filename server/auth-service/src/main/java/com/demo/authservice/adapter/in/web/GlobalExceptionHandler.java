package com.demo.authservice.adapter.in.web;

import com.demo.authservice.application.service.InvalidCredentialsException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates use-case exceptions into consistent JSON responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCredentials(
      InvalidCredentialsException exception) {
    ErrorResponse body =
        new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), exception.getMessage(), Instant.now());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
  }
}
