package com.demo.gatewayserver;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Chain E2E (spec success criterion 3): the gateway must reject requests without a valid RS256 JWT
 * and let the D20 whitelist through. The auth and demo backends are replaced by a local stub, so
 * the test runs cold with no Eureka, no config server and no real service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityE2ETests {

  private static HttpServer stub;

  @Autowired private WebTestClient webTestClient;

  @Value("${app.jwt.private-key}")
  private String privateKeyPem;

  /**
   * Resolves the `gateway.stub.base-url` placeholder used by the test routes to this test's local
   * stub, so every request is answered without Eureka nor a real service. The default in the yml
   * (lb://...) keeps the routes-definition test shaped exactly like production.
   */
  @DynamicPropertySource
  static void pointRoutesAtTheStub(DynamicPropertyRegistry registry) throws Exception {
    stub = HttpServer.create(new InetSocketAddress(0), 0);
    stub.createContext(
        "/",
        exchange -> {
          byte[] body = "Hello world!".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/plain");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    stub.start();
    registry.add("gateway.stub.base-url", () -> "http://localhost:" + stub.getAddress().getPort());
  }

  @AfterAll
  static void stopStub() {
    if (stub != null) {
      stub.stop(0);
    }
  }

  @Test
  void protectedRouteWithoutTokenIsRejected() {
    webTestClient
        .get()
        .uri("/api/demo/hello")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void protectedRouteWithValidTokenReturnsHelloWorld() throws Exception {
    PrivateKey testKey = TestTokens.parsePrivateKey(privateKeyPem);
    String token = TestTokens.sign(testKey, "auth-service", Instant.now().plusSeconds(3600));

    webTestClient
        .get()
        .uri("/api/demo/hello")
        .header("Authorization", "Bearer " + token)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("Hello world!");
  }

  @Test
  void protectedRouteWithForeignKeyIsRejected() throws Exception {
    PrivateKey foreignKey = foreignKey();
    String token = TestTokens.sign(foreignKey, "auth-service", Instant.now().plusSeconds(3600));

    webTestClient
        .get()
        .uri("/api/demo/hello")
        .header("Authorization", "Bearer " + token)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void protectedRouteWithExpiredTokenIsRejected() throws Exception {
    PrivateKey testKey = TestTokens.parsePrivateKey(privateKeyPem);
    String token = TestTokens.sign(testKey, "auth-service", Instant.now().minusSeconds(60));

    webTestClient
        .get()
        .uri("/api/demo/hello")
        .header("Authorization", "Bearer " + token)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void protectedRouteWithForeignIssuerIsRejected() throws Exception {
    PrivateKey testKey = TestTokens.parsePrivateKey(privateKeyPem);
    String token = TestTokens.sign(testKey, "someone-else", Instant.now().plusSeconds(3600));

    webTestClient
        .get()
        .uri("/api/demo/hello")
        .header("Authorization", "Bearer " + token)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void tokenEndpointIsWhitelisted() {
    webTestClient.post().uri("/api/auth/token").exchange().expectStatus().isOk();
  }

  @Test
  void healthIsWhitelisted() {
    webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
  }

  @Test
  void swaggerAndApiDocsAreWhitelisted() {
    webTestClient
        .get()
        .uri("/swagger-ui.html")
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isNotEqualTo(401));
    webTestClient.get().uri("/v3/api-docs/demo-service").exchange().expectStatus().isOk();
  }

  private static PrivateKey foreignKey() throws Exception {
    java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair().getPrivate();
  }
}
