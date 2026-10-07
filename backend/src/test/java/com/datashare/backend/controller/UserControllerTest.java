package com.datashare.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datashare.backend.config.JwtConfig;
import com.datashare.backend.config.RestAuthenticationEntryPoint;
import com.datashare.backend.config.SecurityConfig;
import com.datashare.backend.dto.DeleteAccountRequest;
import com.datashare.backend.exception.InvalidCredentialsException;
import com.datashare.backend.repository.UserRepository;
import com.datashare.backend.service.AccountDeletionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
		value = UserController.class,
		properties = {
				"app.jwt.secret=test-secret-with-at-least-32-bytes-long",
				"app.jwt.expiration=PT1H"
		}
)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class})
class UserControllerTest {

	@MockitoBean
	private UserRepository userRepository;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AccountDeletionService accountDeletionService;

	@Test
	void deletesTheAuthenticatedUsersAccount() throws Exception {
		mockMvc.perform(delete("/api/users/me")
						.with(jwt().jwt(token -> token.subject("owner@example.com")))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"password\":\"current-password\"}"))
				.andExpect(status().isNoContent());

		verify(accountDeletionService).deleteCurrentAccount(
				"owner@example.com", new DeleteAccountRequest("current-password"));
	}

	@Test
	void rejectsAccountDeletionWithoutAuthentication() throws Exception {
		mockMvc.perform(delete("/api/users/me")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"password\":\"current-password\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void rejectsAccountDeletionWithoutPassword() throws Exception {
		mockMvc.perform(delete("/api/users/me")
						.with(jwt().jwt(token -> token.subject("owner@example.com")))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"password\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.password").exists());
	}

	@Test
	void returnsUnauthorizedForAnIncorrectCurrentPassword() throws Exception {
		doThrow(new InvalidCredentialsException())
				.when(accountDeletionService).deleteCurrentAccount(
						any(String.class), any(DeleteAccountRequest.class));

		mockMvc.perform(delete("/api/users/me")
						.with(jwt().jwt(token -> token.subject("owner@example.com")))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}
}
