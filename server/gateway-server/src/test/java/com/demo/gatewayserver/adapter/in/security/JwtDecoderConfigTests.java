package com.demo.gatewayserver.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.demo.gatewayserver.TestTokens;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Direct tests of the gateway's RS256 decoder (D14/D21): the test public key accepts tokens signed
 * with the test private key and rejects foreign signatures, expiry and a foreign issuer.
 */
@SpringBootTest
class JwtDecoderConfigTests {

  @Autowired private ReactiveJwtDecoder decoder;

  @Value("${app.jwt.private-key}")
  private String privateKeyPem;

  @Test
  void decodesTokenSignedWithTheTestKey() throws Exception {
    PrivateKey testKey = TestTokens.parsePrivateKey(privateKeyPem);
    String token = TestTokens.sign(testKey, "auth-service", Instant.now().plusSeconds(3600));

    Jwt decoded = decoder.decode(token).block(Duration.ofSeconds(5));

    assertThat(decoded).isNotNull();
    assertThat(decoded.getSubject()).isEqualTo("demo");
    assertThat(decoded.getClaimAsString("iss")).isEqualTo("auth-service");
  }

  @Test
  void rejectsTokenSignedWithAForeignKey() throws Exception {
    PrivateKey testKey = TestTokens.parsePrivateKey(privateKeyPem);
    PrivateKey foreignKey = foreignKey();
    String token = TestTokens.sign(foreignKey, "auth-service", Instant.now().plusSeconds(3600));

    assertThatThrownBy(() -> decoder.decode(token).block(Duration.ofSeconds(5)))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsExpiredToken() throws Exception {
    PrivateKey testKey = TestTokens.parsePrivateKey(privateKeyPem);
    String token = TestTokens.sign(testKey, "auth-service", Instant.now().minusSeconds(60));

    assertThatThrownBy(() -> decoder.decode(token).block(Duration.ofSeconds(5)))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsTokenIssuedByAnotherIssuer() throws Exception {
    PrivateKey testKey = TestTokens.parsePrivateKey(privateKeyPem);
    String token = TestTokens.sign(testKey, "someone-else", Instant.now().plusSeconds(3600));

    assertThatThrownBy(() -> decoder.decode(token).block(Duration.ofSeconds(5)))
        .isInstanceOf(JwtException.class);
  }

  private static PrivateKey foreignKey() throws Exception {
    java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair().getPrivate();
  }
}
