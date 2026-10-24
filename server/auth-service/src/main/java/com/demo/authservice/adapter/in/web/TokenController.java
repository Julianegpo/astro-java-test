package com.demo.authservice.adapter.in.web;

import com.demo.authservice.application.service.TokenService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Token issuing (D6/D19): the service's single entry point to obtain a JWT. */
@RestController
@RequestMapping("/api/auth")
public class TokenController {

  private final TokenService tokenService;

  public TokenController(TokenService tokenService) {
    this.tokenService = tokenService;
  }

  @PostMapping("/token")
  public TokenResponse token(@RequestBody TokenRequest request) {
    return tokenService.issue(request.username(), request.password());
  }

  public record TokenRequest(String username, String password) {}
}
