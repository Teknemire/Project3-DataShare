package com.datashare.backend.service;

import com.datashare.backend.dto.DownloadAccessResponse;
import com.datashare.backend.dto.SharedFileResponse;
import com.datashare.backend.entity.FileMetadata;
import com.datashare.backend.exception.DownloadAuthenticationException;
import com.datashare.backend.exception.ShareExpiredException;
import com.datashare.backend.exception.ShareNotFoundException;
import com.datashare.backend.repository.FileMetadataRepository;
import com.datashare.backend.storage.StorageService;
import java.io.InputStream;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class FileShareService {

	private final FileMetadataRepository fileMetadataRepository;
	private final StorageService storageService;
	private final PasswordEncoder passwordEncoder;
	private final DownloadTicketService downloadTicketService;

	public SharedFileResponse getSharedFile(String downloadToken) {
		return SharedFileResponse.from(findActiveFile(downloadToken));
	}

	public DownloadAccessResponse authorizeDownload(String downloadToken, String password) {
		FileMetadata file = findActiveFile(downloadToken);
		if (file.getDownloadPasswordHash() != null
				&& (password == null || password.isEmpty()
				|| !passwordEncoder.matches(password, file.getDownloadPasswordHash()))) {
			throw new DownloadAuthenticationException();
		}

		DownloadTicketService.GeneratedTicket ticket = downloadTicketService.generate(
				downloadToken,
				file.getExpiresAt()
		);
		String downloadUrl = UriComponentsBuilder
				.fromPath("/api/shares/{token}/content")
				.queryParam("ticket", ticket.value())
				.buildAndExpand(downloadToken)
				.toUriString();
		return new DownloadAccessResponse(downloadUrl, ticket.expiresAt());
	}

	public DownloadContent openDownload(String downloadToken, String ticket) {
		FileMetadata file = findActiveFile(downloadToken);
		downloadTicketService.validate(downloadToken, ticket);
		InputStream content = storageService.open(file.getStorageKey());
		return new DownloadContent(file.getOriginalName(), file.getMimeType(), file.getSize(), content);
	}

	private FileMetadata findActiveFile(String downloadToken) {
		FileMetadata file = fileMetadataRepository.findByDownloadToken(downloadToken)
				.orElseThrow(ShareNotFoundException::new);
		if (!file.getExpiresAt().isAfter(Instant.now())) {
			throw new ShareExpiredException();
		}
		return file;
	}

	public record DownloadContent(String originalName, String mimeType, long size, InputStream content) {
	}
}
