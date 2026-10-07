package com.datashare.backend.service;

import com.datashare.backend.dto.FileResponse;
import com.datashare.backend.entity.FileMetadata;
import com.datashare.backend.exception.FileNotFoundException;
import com.datashare.backend.repository.FileMetadataRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import com.datashare.backend.dto.FileStatus;

@Service
@RequiredArgsConstructor
public class FileQueryService {

	private final FileMetadataRepository fileMetadataRepository;

	@Value("${app.frontend.public-url}")
	private String frontendPublicUrl;

	@Transactional(readOnly = true)
	public List<FileResponse> listOwnedFiles(String authenticatedEmail, int page, int size, FileStatus status) {
		Instant now = Instant.now();
		return fileMetadataRepository.findOwnedPage(authenticatedEmail, status == null ? "ALL" : status.name(),
				now, PageRequest.of(page, size))
				.stream()
				.map(file -> FileResponse.from(file, frontendPublicUrl, now))
				.toList();
	}

	@Transactional(readOnly = true)
	public FileResponse getOwnedFile(String authenticatedEmail, UUID fileId) {
		FileMetadata file = fileMetadataRepository.findByIdAndUser_Email(fileId, authenticatedEmail)
				.orElseThrow(FileNotFoundException::new);
		return FileResponse.from(file, frontendPublicUrl, Instant.now());
	}
}
