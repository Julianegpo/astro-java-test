package com.demo.authservice.application.service;

import com.demo.authservice.adapter.in.web.TokenResponse;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Emite JWT RS256 (D14): valida credenciales contra H2 (D16) y firma con la clave privada del
 * config server (D21); TTL = app.jwt.expiration-seconds (D19).
 */
@Service
public final class TokenService {

  static final String ISSUER = "auth-service";

  private final UserAccounts userAccounts;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
  private final RSASSASigner signer;
  private final long expirationSeconds;

  public TokenService(
      UserAccounts userAccounts,
      @Value("${app.jwt.private-key}") String privateKeyPem,
      @Value("${app.jwt.expiration-seconds}") long expirationSeconds) {
    this.userAccounts = userAccounts;
    this.signer = new RSASSASigner(parsePrivateKey(privateKeyPem));
    this.expirationSeconds = expirationSeconds;
  }

  /** Valida credenciales y emite el token; entradas inválidas o desconocidas → 401 vía advice. */
  public TokenResponse issue(String username, String rawPassword) {
    if (username == null || username.isBlank() || rawPassword == null) {
      throw new InvalidCredentialsException();
    }
    UserAccounts.Credentials credentials =
        userAccounts
            .findByUsername(username)
            .filter(candidate -> encoder.matches(rawPassword, candidate.passwordHash()))
            .orElseThrow(InvalidCredentialsException::new);

    Instant now = Instant.now();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(username)
            .issuer(ISSUER)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(expirationSeconds)))
            .claim("roles", credentials.roles())
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
    try {
      jwt.sign(signer);
    } catch (JOSEException e) {
      throw new IllegalStateException("Could not sign the JWT", e);
    }
    return new TokenResponse(jwt.serialize(), expirationSeconds);
  }

  private static PrivateKey parsePrivateKey(String privateKeyPem) {
    try {
      String base64 =
          privateKeyPem
              .replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s", "");
      byte[] der = Base64.getDecoder().decode(base64);
      return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("app.jwt.private-key no es una clave RSA PKCS#8 valida", e);
    }
  }
}
