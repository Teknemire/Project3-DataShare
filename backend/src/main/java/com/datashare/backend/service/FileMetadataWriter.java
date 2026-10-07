package com.datashare.backend.service;

import com.datashare.backend.dto.FileResponse;
import com.datashare.backend.entity.FileMetadata;
import com.datashare.backend.repository.FileMetadataRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FileMetadataWriter {

	private final FileMetadataRepository repository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public FileResponse save(FileMetadata file, String publicUrl, Instant now) {
		return FileResponse.from(repository.saveAndFlush(file), publicUrl, now);
	}
}
