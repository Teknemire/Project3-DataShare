package com.datashare.backend.dto;

import com.datashare.backend.entity.FileMetadata;
import java.time.Instant;

public record SharedFileResponse(
		String originalName,
		String mimeType,
		long size,
		Instant expiresAt,
		boolean passwordProtected
) {
	public static SharedFileResponse from(FileMetadata file) {
		return new SharedFileResponse(
				file.getOriginalName(),
				file.getMimeType(),
				file.getSize(),
				file.getExpiresAt(),
				file.getDownloadPasswordHash() != null
		);
	}
}
