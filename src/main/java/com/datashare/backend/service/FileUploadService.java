package com.datashare.backend.service;

import com.datashare.backend.dto.FileResponse;
import com.datashare.backend.entity.FileMetadata;
import com.datashare.backend.entity.User;
import com.datashare.backend.exception.CurrentUserNotFoundException;
import com.datashare.backend.exception.InvalidFileException;
import com.datashare.backend.repository.FileMetadataRepository;
import com.datashare.backend.repository.UserRepository;
import com.datashare.backend.storage.StorageService;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {

	private static final int DEFAULT_EXPIRATION_DAYS = 7;
	private static final int MIN_EXPIRATION_DAYS = 1;
	private static final int MAX_EXPIRATION_DAYS = 7;
	private static final int MIN_DOWNLOAD_PASSWORD_LENGTH = 6;
	private static final int MAX_DOWNLOAD_PASSWORD_LENGTH = 72;
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final FileMetadataRepository fileMetadataRepository;
	private final FileValidationService fileValidationService;
	private final StorageService storageService;
	private final PasswordEncoder passwordEncoder;

	@Value("${app.frontend.public-url}")
	private String frontendPublicUrl;

	@Transactional
	public FileResponse upload(
			String authenticatedEmail,
			MultipartFile multipartFile,
			Integer expirationDays,
			String downloadPassword
	) {
		FileValidationService.ValidatedFile validatedFile = fileValidationService.validate(multipartFile);
		int validatedExpirationDays = validateExpiration(expirationDays);
		String passwordHash = validateAndHashPassword(downloadPassword);
		User owner = userRepository.findByEmail(authenticatedEmail)
				.orElseThrow(CurrentUserNotFoundException::new);

		String storageKey = UUID.randomUUID().toString();
		String downloadToken = generateDownloadToken();
		Instant createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		Instant expiresAt = createdAt.plus(validatedExpirationDays, ChronoUnit.DAYS);

		try {
			storageService.save(
					storageKey,
					multipartFile.getInputStream(),
					multipartFile.getSize(),
					validatedFile.contentType()
			);
			FileMetadata savedFile = fileMetadataRepository.saveAndFlush(new FileMetadata(
					validatedFile.originalName(),
					storageKey,
					validatedFile.contentType(),
					multipartFile.getSize(),
					downloadToken,
					passwordHash,
					createdAt,
					expiresAt,
					owner
			));
			return FileResponse.from(savedFile, frontendPublicUrl, createdAt);
		} catch (IOException exception) {
			throw new InvalidFileException("Impossible de lire le fichier envoyé.");
		} catch (RuntimeException exception) {
			tryCleanup(storageKey);
			throw exception;
		}
	}

	private int validateExpiration(Integer expirationDays) {
		int value = expirationDays == null ? DEFAULT_EXPIRATION_DAYS : expirationDays;
		if (value < MIN_EXPIRATION_DAYS || value > MAX_EXPIRATION_DAYS) {
			throw new InvalidFileException("L'expiration doit être comprise entre 1 et 7 jours.");
		}
		return value;
	}

	private String validateAndHashPassword(String password) {
		if (password == null || password.isEmpty()) {
			return null;
		}
		if (password.length() < MIN_DOWNLOAD_PASSWORD_LENGTH
				|| password.length() > MAX_DOWNLOAD_PASSWORD_LENGTH) {
			throw new InvalidFileException(
					"Le mot de passe de téléchargement doit contenir entre 6 et 72 caractères."
			);
		}
		return passwordEncoder.encode(password);
	}

	private String generateDownloadToken() {
		byte[] tokenBytes = new byte[32];
		SECURE_RANDOM.nextBytes(tokenBytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
	}

	private void tryCleanup(String storageKey) {
		try {
			storageService.delete(storageKey);
		} catch (RuntimeException cleanupException) {
			log.warn("Le nettoyage du contenu a échoué après l'échec d'un upload.", cleanupException);
		}
	}
}
