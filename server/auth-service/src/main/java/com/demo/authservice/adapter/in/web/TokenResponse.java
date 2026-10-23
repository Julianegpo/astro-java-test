package com.demo.authservice.adapter.in.web;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

/** Respuesta de POST /api/auth/token: el token emitido y su TTL en segundos. */
@Builder
@Jacksonized
public record TokenResponse(String token, long expiresIn) {}
