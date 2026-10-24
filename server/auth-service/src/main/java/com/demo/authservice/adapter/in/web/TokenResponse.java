package com.demo.authservice.adapter.in.web;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

/** Response of POST /api/auth/token: the issued token and its TTL in seconds. */
@Builder
@Jacksonized
public record TokenResponse(String token, long expiresIn) {}
