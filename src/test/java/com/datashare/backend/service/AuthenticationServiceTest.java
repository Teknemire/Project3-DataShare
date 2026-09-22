package com.datashare.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datashare.backend.dto.LoginRequest;
import com.datashare.backend.dto.LoginResponse;
import com.datashare.backend.entity.User;
import com.datashare.backend.exception.InvalidCredentialsException;
import com.datashare.backend.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	private AuthenticationService authenticationService;

	@BeforeEach
	void setUp() {
		authenticationService = new AuthenticationService(userRepository, passwordEncoder, jwtService);
	}

	@Test
	void logsInWithNormalizedEmailAndReturnsJwt() {
		User user = new User("user@example.com", "stored-hash");
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("password123", "stored-hash")).thenReturn(true);
		when(jwtService.generate(user)).thenReturn(new JwtService.GeneratedToken(
				"signed.jwt.token",
				Instant.parse("2026-09-20T18:00:00Z")));

		LoginResponse response = authenticationService.login(
				new LoginRequest("  User@Example.COM ", "password123"));

		assertThat(response.accessToken()).isEqualTo("signed.jwt.token");
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.user().email()).isEqualTo("user@example.com");
	}

	@Test
	void rejectsWrongPasswordWithoutCreatingToken() {
		User user = new User("user@example.com", "stored-hash");
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

		assertThatThrownBy(() -> authenticationService.login(
				new LoginRequest("user@example.com", "wrong-password")))
				.isInstanceOf(InvalidCredentialsException.class);

		verify(jwtService, never()).generate(user);
	}

	@Test
	void rejectsUnknownEmailWithTheSameException() {
		when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authenticationService.login(
				new LoginRequest("unknown@example.com", "password123")))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessage("Email ou mot de passe incorrect.");
	}
}
