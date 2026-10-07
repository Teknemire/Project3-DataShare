package com.datashare.backend.service;

import com.datashare.backend.dto.DeleteAccountRequest;
import com.datashare.backend.entity.FileMetadata;
import com.datashare.backend.entity.User;
import com.datashare.backend.exception.CurrentUserNotFoundException;
import com.datashare.backend.exception.InvalidCredentialsException;
import com.datashare.backend.repository.FileMetadataRepository;
import com.datashare.backend.repository.UserRepository;
import com.datashare.backend.storage.StorageService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountDeletionService {

	private final UserRepository userRepository;
	private final FileMetadataRepository fileMetadataRepository;
	private final StorageService storageService;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public void deleteCurrentAccount(String email, DeleteAccountRequest request) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(CurrentUserNotFoundException::new);

		if (request.password().getBytes(StandardCharsets.UTF_8).length > 72
				|| !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		List<FileMetadata> files = fileMetadataRepository
				.findAllByUser_EmailOrderByCreatedAtDescIdDesc(email);
		files.forEach(file -> storageService.delete(file.getStorageKey()));

		fileMetadataRepository.deleteAll(files);
		userRepository.delete(user);
	}
}
