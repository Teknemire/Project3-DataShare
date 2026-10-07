package com.datashare.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datashare.backend.dto.RegisterRequest;
import com.datashare.backend.dto.UserResponse;
import com.datashare.backend.entity.User;
import com.datashare.backend.exception.EmailAlreadyUsedException;
import com.datashare.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	private UserService userService;

	@BeforeEach
	void setUp() {
		userService = new UserService(userRepository, passwordEncoder);
	}

	@Test
	void registersUserWithNormalizedEmailAndHashedPassword() {
		RegisterRequest request = new RegisterRequest("  User@Example.COM ", "password123");
		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserResponse response = userService.register(request);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
		assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-password");
		assertThat(response.email()).isEqualTo("user@example.com");
		assertThat(response.createdAt()).isNotNull();
	}

	@Test
	void rejectsAnEmailThatAlreadyExists() {
		RegisterRequest request = new RegisterRequest("user@example.com", "password123");
		when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

		assertThatThrownBy(() -> userService.register(request))
				.isInstanceOf(EmailAlreadyUsedException.class);

		verify(passwordEncoder, never()).encode(any());
		verify(userRepository, never()).save(any());
	}
}
