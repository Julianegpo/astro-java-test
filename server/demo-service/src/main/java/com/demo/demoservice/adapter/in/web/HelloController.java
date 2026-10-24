package com.demo.demoservice.adapter.in.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only endpoint of the sample service (D6): behind the gateway it answers "Hello world!". */
@RestController
@RequestMapping("/api/demo")
public class HelloController {

  @GetMapping(value = "/hello", produces = MediaType.TEXT_PLAIN_VALUE)
  public String hello() {
    return "Hello world!";
  }
}
