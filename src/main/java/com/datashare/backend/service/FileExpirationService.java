package com.datashare.backend.service;

import com.datashare.backend.entity.FileMetadata;
import com.datashare.backend.repository.FileMetadataRepository;
import com.datashare.backend.storage.StorageService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileExpirationService {

	private final FileMetadataRepository fileMetadataRepository;
	private final StorageService storageService;

	@Scheduled(fixedDelayString = "${app.files.expiration-cleanup-delay-ms}")
	public void deleteExpiredContents() {
		for (FileMetadata file : fileMetadataRepository.findAllByExpiresAtLessThanEqual(Instant.now())) {
			try {
				storageService.delete(file.getStorageKey());
			} catch (RuntimeException exception) {
				log.warn("La suppression d'un contenu expiré a échoué et sera retentée.", exception);
			}
		}
	}
}
