package com.demo.authservice.adapter.in.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demo.authservice.application.service.InvalidCredentialsException;
import com.demo.authservice.application.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TokenController.class)
@Import(SecurityConfig.class)
class TokenControllerTests {

  private static final String TOKEN_URL = "/api/auth/token";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TokenService tokenService;

  @Test
  void issuesTokenForValidCredentials() throws Exception {
    when(tokenService.issue("demo", "demo"))
        .thenReturn(TokenResponse.builder().token("jwt-token").expiresIn(3600).build());

    mockMvc
        .perform(
            post(TOKEN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"demo\",\"password\":\"demo\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("jwt-token"))
        .andExpect(jsonPath("$.expiresIn").value(3600));
  }

  @Test
  void rejectsInvalidCredentialsWith401AndErrorBody() throws Exception {
    when(tokenService.issue("demo", "wrong")).thenThrow(new InvalidCredentialsException());

    mockMvc
        .perform(
            post(TOKEN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"demo\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.message").value("Invalid credentials"));
  }

  @Test
  void rejectsMissingBodyWith400() throws Exception {
    mockMvc
        .perform(post(TOKEN_URL).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }
}
