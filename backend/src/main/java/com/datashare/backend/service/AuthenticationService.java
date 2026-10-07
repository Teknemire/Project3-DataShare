package com.datashare.backend.service;

import com.datashare.backend.dto.LoginRequest;
import com.datashare.backend.dto.LoginResponse;
import com.datashare.backend.dto.UserResponse;
import com.datashare.backend.entity.User;
import com.datashare.backend.exception.CurrentUserNotFoundException;
import com.datashare.backend.exception.InvalidCredentialsException;
import com.datashare.backend.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public LoginResponse login(LoginRequest request) {
		if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new InvalidCredentialsException();
		}
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
		User user = userRepository.findByEmail(normalizedEmail)
				.filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
				.orElseThrow(InvalidCredentialsException::new);

		JwtService.GeneratedToken token = jwtService.generate(user);
		return new LoginResponse(token.value(), "Bearer", token.expiresAt(), UserResponse.from(user));
	}

	public UserResponse getCurrentUser(String email) {
		return userRepository.findByEmail(email)
				.map(UserResponse::from)
				.orElseThrow(CurrentUserNotFoundException::new);
	}
}
