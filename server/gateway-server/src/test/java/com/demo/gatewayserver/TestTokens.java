package com.demo.gatewayserver;

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

/** Test fixture: signs disposable RS256 tokens exactly the way auth-service would. */
public final class TestTokens {

  private TestTokens() {}

  /** Parses a PKCS#8 PEM private key (the shape stored in the test application.yml). */
  public static PrivateKey parsePrivateKey(String pem) throws GeneralSecurityException {
    String body =
        pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
    byte[] der = Base64.getDecoder().decode(body);
    return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
  }

  /** Signs a token with the given key, issuer and expiration (subject demo, ROLE_USER). */
  public static String sign(PrivateKey key, String issuer, Instant expiration)
      throws JOSEException {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("demo")
            .claim("roles", "ROLE_USER")
            .issuer(issuer)
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(expiration))
            .build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }
}
