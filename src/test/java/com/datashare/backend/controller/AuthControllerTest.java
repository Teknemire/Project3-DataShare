package com.datashare.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datashare.backend.config.JwtConfig;
import com.datashare.backend.config.RestAuthenticationEntryPoint;
import com.datashare.backend.config.SecurityConfig;
import com.datashare.backend.dto.LoginResponse;
import com.datashare.backend.dto.RegisterRequest;
import com.datashare.backend.dto.UserResponse;
import com.datashare.backend.exception.EmailAlreadyUsedException;
import com.datashare.backend.exception.InvalidCredentialsException;
import com.datashare.backend.service.AuthenticationService;
import com.datashare.backend.service.UserService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
		value = AuthController.class,
		properties = {
				"app.jwt.secret=test-secret-with-at-least-32-bytes-long",
				"app.jwt.expiration=PT1H"
		}
)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class})
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private AuthenticationService authenticationService;

	@Test
	void shouldRejectMalformedLoginRequest() throws Exception {
		mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{invalid-json}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void allowsPublicRegistrationAndReturnsCreatedUser() throws Exception {
		UserResponse response = new UserResponse(
				UUID.fromString("d52f8f24-6363-469c-91cb-5a4f3bd99369"),
				"user@example.com",
				Instant.parse("2026-09-20T17:00:00Z"));
		when(userService.register(any(RegisterRequest.class))).thenReturn(response);

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "user@example.com",
								  "password": "password123"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("d52f8f24-6363-469c-91cb-5a4f3bd99369"))
				.andExpect(jsonPath("$.email").value("user@example.com"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void rejectsAnInvalidRegistration() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "invalid-email",
								  "password": "short"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.email").exists())
				.andExpect(jsonPath("$.fieldErrors.password").exists());
	}

	@Test
	void returnsConflictWhenEmailAlreadyExists() throws Exception {
		when(userService.register(any(RegisterRequest.class))).thenThrow(new EmailAlreadyUsedException());

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "user@example.com",
								  "password": "password123"
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
	}

	@Test
	void logsInWithValidCredentials() throws Exception {
		UserResponse user = new UserResponse(
				UUID.fromString("d52f8f24-6363-469c-91cb-5a4f3bd99369"),
				"user@example.com",
				Instant.parse("2026-09-20T17:00:00Z"));
		when(authenticationService.login(any())).thenReturn(new LoginResponse(
				"signed.jwt.token",
				"Bearer",
				Instant.parse("2026-09-20T18:00:00Z"),
				user));

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "user@example.com",
								  "password": "password123"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("signed.jwt.token"))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.user.email").value("user@example.com"));
	}

	@Test
	void returnsSameErrorForInvalidCredentials() throws Exception {
		when(authenticationService.login(any())).thenThrow(new InvalidCredentialsException());

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email": "unknown@example.com",
								  "password": "wrong-password"
								}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void returnsCurrentUserForAValidJwt() throws Exception {
		UserResponse user = new UserResponse(
				UUID.fromString("d52f8f24-6363-469c-91cb-5a4f3bd99369"),
				"user@example.com",
				Instant.parse("2026-09-20T17:00:00Z"));
		when(authenticationService.getCurrentUser("user@example.com")).thenReturn(user);

		mockMvc.perform(get("/api/auth/me").with(jwt().jwt(token -> token.subject("user@example.com"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("user@example.com"));
	}

	@Test
	void rejectsCurrentUserRequestWithoutJwt() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}
}
