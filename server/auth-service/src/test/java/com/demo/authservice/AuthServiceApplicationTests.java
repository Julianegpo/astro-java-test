package com.demo.authservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.demo.authservice.adapter.in.web.TokenResponse;
import com.demo.authservice.application.service.TokenService;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AuthServiceApplicationTests {

  @Autowired private TokenService tokenService;

  @Value("${app.jwt.public-key}")
  private String publicKeyPem;

  @Test
  void contextLoads() {}

  /** Integra en frío: seed H2 + BCrypt + claves de test del classpath de test. */
  @Test
  void issuesTokenForSeededDemoUserVerifiableWithTestPublicKey() throws Exception {
    TokenResponse response = tokenService.issue("demo", "demo");

    SignedJWT jwt = SignedJWT.parse(response.token());
    assertThat(jwt.verify(new RSASSAVerifier(publicKey()))).isTrue();
    assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("demo");
    assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("auth-service");
  }

  private RSAPublicKey publicKey() throws Exception {
    String base64 =
        publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    return (RSAPublicKey)
        KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
  }
}
