package com.datashare.backend.service;

import com.datashare.backend.entity.FileMetadata;
import com.datashare.backend.exception.FileNotFoundException;
import com.datashare.backend.repository.FileMetadataRepository;
import com.datashare.backend.storage.StorageService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FileDeletionService {

	private final FileMetadataRepository fileMetadataRepository;
	private final StorageService storageService;

	@Transactional
	public void deleteOwnedFile(String ownerEmail, UUID fileId) {
		FileMetadata file = fileMetadataRepository.findByIdAndUser_Email(fileId, ownerEmail)
				.orElseThrow(FileNotFoundException::new);

		storageService.delete(file.getStorageKey());
		fileMetadataRepository.delete(file);
	}
}
