package com.datashare.backend.service;

import com.datashare.backend.dto.RegisterRequest;
import com.datashare.backend.dto.UserResponse;
import com.datashare.backend.entity.User;
import com.datashare.backend.exception.EmailAlreadyUsedException;
import com.datashare.backend.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserResponse register(RegisterRequest request) {
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

		if (userRepository.existsByEmail(normalizedEmail)) {
			throw new EmailAlreadyUsedException();
		}

		User user = new User(normalizedEmail, passwordEncoder.encode(request.password()));

		try {
			User savedUser = userRepository.save(user);
			return UserResponse.from(savedUser);
		}
		catch (DataIntegrityViolationException exception) {
			throw new EmailAlreadyUsedException();
		}
	}
}
