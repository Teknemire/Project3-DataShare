package com.datashare.backend.dto;

import com.datashare.backend.entity.FileMetadata;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FileResponse(
		UUID id,
		String originalName,
		String mimeType,
		long size,
		Instant createdAt,
		Instant expiresAt,
		boolean passwordProtected,
		String shareUrl,
		FileStatus status,
		List<String> tags
) {
	public static FileResponse from(FileMetadata file, String publicUrl, Instant now) {
		String normalizedPublicUrl = publicUrl.endsWith("/")
				? publicUrl.substring(0, publicUrl.length() - 1)
				: publicUrl;
		return new FileResponse(
				file.getId(),
				file.getOriginalName(),
				file.getMimeType(),
				file.getSize(),
				file.getCreatedAt(),
				file.getExpiresAt(),
				file.getDownloadPasswordHash() != null,
				normalizedPublicUrl + "/share/" + file.getDownloadToken(),
				file.getExpiresAt().isAfter(now) ? FileStatus.ACTIVE : FileStatus.EXPIRED,
				List.copyOf(file.getTags())
		);
	}

	@Override
	public String toString() {
		return "FileResponse[id=" + id + ", status=" + status + ", shareUrl=***]";
	}
}
