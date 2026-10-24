package com.demo.gatewayserver.adapter.in.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security at the gateway (D6/D20): every exchange demands a valid RS256 Bearer JWT, except the
 * whitelist (token issuing, swagger, api docs and health). Missing, expired or badly signed tokens
 * end in 401. Stateless: CSRF and form/basic logins are off (D18).
 */
@Configuration
public class SecurityConfig {

  @Bean
  SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .authorizeExchange(
            exchanges ->
                exchanges
                    .pathMatchers(HttpMethod.POST, "/api/auth/token")
                    .permitAll()
                    .pathMatchers(
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health")
                    .permitAll()
                    .anyExchange()
                    .authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .build();
  }
}
