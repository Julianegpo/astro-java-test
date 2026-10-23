package com.demo.authservice.adapter.in.web;

import java.time.Instant;

/** Cuerpo de error único del servicio: código HTTP + mensaje legible + timestamp. */
public record ErrorResponse(int code, String message, Instant timestamp) {}
