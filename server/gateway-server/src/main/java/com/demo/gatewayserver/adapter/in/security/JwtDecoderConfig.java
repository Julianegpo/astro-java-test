package com.demo.gatewayserver.adapter.in.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Builds the gateway's RS256 decoder (D14/D21) from the public key that travels in the config
 * server (`gateway-server.yml`). Timestamps and the `iss=auth-service` claim are validated (D19).
 */
@Configuration
public class JwtDecoderConfig {

  /** Issuer that only auth-service can use when signing tokens. */
  private static final String EXPECTED_ISSUER = "auth-service";

  @Bean
  ReactiveJwtDecoder reactiveJwtDecoder(@Value("${app.jwt.public-key}") String publicKeyPem)
      throws GeneralSecurityException {
    NimbusReactiveJwtDecoder decoder = new NimbusReactiveJwtDecoder(parsePublicKey(publicKeyPem));
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(EXPECTED_ISSUER));
    return decoder;
  }

  private static RSAPublicKey parsePublicKey(String pem) throws GeneralSecurityException {
    String body =
        pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    byte[] der = Base64.getDecoder().decode(body);
    return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
  }
}
