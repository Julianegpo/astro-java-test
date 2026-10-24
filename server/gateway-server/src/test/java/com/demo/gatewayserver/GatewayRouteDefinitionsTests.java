package com.demo.gatewayserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import reactor.core.publisher.Flux;

/**
 * En produccion las 4 rutas (token, demo y los 2 bucles api-docs) viajan del config server; aqui se
 * declaran en el shadow de test con el MISMO namespace para validar que parsean.
 */
@SpringBootTest
class GatewayRouteDefinitionsTests {

  @Autowired private List<RouteDefinitionLocator> locators;

  @Test
  void fourRoutesAreRegisteredFromConfigNamespace() {
    List<String> ids =
        Flux.fromIterable(locators)
            .flatMap(locator -> locator.getRouteDefinitions())
            .map(RouteDefinition::getId)
            .distinct()
            .collectList()
            .block(Duration.ofSeconds(10));
    assertThat(ids)
        .contains("auth-token", "demo-api", "api-docs-auth-service", "api-docs-demo-service");
  }
}
