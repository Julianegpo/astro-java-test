package com.demo.demoservice.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HelloControllerTests {

  private final HelloController controller = new HelloController();

  @Test
  void helloReturnsHelloWorld() {
    assertThat(controller.hello()).isEqualTo("Hello world!");
  }
}
