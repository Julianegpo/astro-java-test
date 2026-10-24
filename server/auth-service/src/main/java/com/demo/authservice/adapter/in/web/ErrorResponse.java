package com.demo.authservice.adapter.in.web;

import java.time.Instant;

/** Single error body for the service: HTTP code + readable message + timestamp. */
public record ErrorResponse(int code, String message, Instant timestamp) {}
