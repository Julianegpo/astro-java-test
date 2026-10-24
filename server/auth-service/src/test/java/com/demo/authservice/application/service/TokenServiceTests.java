package com.demo.authservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.demo.authservice.adapter.in.web.TokenResponse;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Unit tests (Mockito, no Spring) of RS256 JWT issuing (D14/D19). */
class TokenServiceTests {

  private static final long TTL_SECONDS = 3600;

  private static String privateKeyPem;
  private static String publicKeyPem;

  private UserAccounts userAccounts;
  private TokenService tokenService;

  @BeforeAll
  static void generateEphemeralKeyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    privateKeyPem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
    publicKeyPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());
  }

  @BeforeEach
  void setUp() {
    userAccounts = mock(UserAccounts.class);
    tokenService = new TokenService(userAccounts, privateKeyPem, TTL_SECONDS);
  }

  @Test
  void issuesRs256SignedJwtWithSubIssRolesAndExp() throws Exception {
    when(userAccounts.findByUsername("demo"))
        .thenReturn(Optional.of(new UserAccounts.Credentials(passwordHash(), "ROLE_USER")));

    TokenResponse response = tokenService.issue("demo", "demo");

    SignedJWT jwt = SignedJWT.parse(response.token());
    assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
    assertThat(jwt.verify(new RSASSAVerifier(publicKey()))).isTrue();

    JWTClaimsSet claims = jwt.getJWTClaimsSet();
    assertThat(claims.getSubject()).isEqualTo("demo");
    assertThat(claims.getIssuer()).isEqualTo("auth-service");
    assertThat(claims.getClaim("roles")).isEqualTo("ROLE_USER");
    assertThat(response.expiresIn()).isEqualTo(TTL_SECONDS);
    Date issuedAt = claims.getIssueTime();
    Date expiresAt = claims.getExpirationTime();
    assertThat(expiresAt.toInstant().getEpochSecond() - issuedAt.toInstant().getEpochSecond())
        .isEqualTo(TTL_SECONDS);
  }

  @Test
  void rejectsWrongPassword() {
    when(userAccounts.findByUsername("demo"))
        .thenReturn(Optional.of(new UserAccounts.Credentials(passwordHash(), "ROLE_USER")));

    assertThatThrownBy(() -> tokenService.issue("demo", "wrong-password"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void rejectsUnknownUser() {
    when(userAccounts.findByUsername("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> tokenService.issue("ghost", "demo"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void rejectsBlankOrMissingInputsWithoutTouchingTheStore() {
    assertThatThrownBy(() -> tokenService.issue(" ", "demo"))
        .isInstanceOf(InvalidCredentialsException.class);
    assertThatThrownBy(() -> tokenService.issue("demo", null))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(userAccounts, never()).findByUsername(anyString());
  }

  @Test
  void honorsConfiguredExpiration() throws Exception {
    TokenService shortLived = new TokenService(userAccounts, privateKeyPem, 60);
    when(userAccounts.findByUsername("demo"))
        .thenReturn(Optional.of(new UserAccounts.Credentials(passwordHash(), "ROLE_USER")));

    TokenResponse response = shortLived.issue("demo", "demo");
    JWTClaimsSet claims = SignedJWT.parse(response.token()).getJWTClaimsSet();

    assertThat(response.expiresIn()).isEqualTo(60);
    assertThat(
            claims.getExpirationTime().toInstant().getEpochSecond()
                - claims.getIssueTime().toInstant().getEpochSecond())
        .isEqualTo(60);
  }

  private static String passwordHash() {
    return new BCryptPasswordEncoder().encode("demo");
  }

  private static String pem(String type, byte[] der) {
    String base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
    return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----";
  }

  private static RSAPublicKey publicKey() throws Exception {
    String base64 =
        publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    return (RSAPublicKey)
        java.security.KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
  }
}
