package com.demo.demoservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/** Contexto en frio: sin config server ni Eureka (shadow de src/test/resources). */
@SpringBootTest
class DemoServiceApplicationTests {

  @Autowired private Environment env;

  @Test
  void contextLoadsWithRandomPortAndServiceName() {
    assertThat(env.getProperty("spring.application.name")).isEqualTo("demo-service");
    assertThat(env.getProperty("server.port")).isNotNull();
  }
}
