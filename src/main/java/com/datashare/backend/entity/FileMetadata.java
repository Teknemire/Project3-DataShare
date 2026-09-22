package com.datashare.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
		name = "file_metadata",
		indexes = {
				@Index(name = "idx_file_metadata_user_id", columnList = "user_id"),
				@Index(name = "idx_file_metadata_expires_at", columnList = "expires_at")
		}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileMetadata {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "original_name", nullable = false, length = 255)
	private String originalName;

	@Column(name = "storage_key", nullable = false, unique = true, length = 100)
	private String storageKey;

	@Column(name = "mime_type", nullable = false, length = 150)
	private String mimeType;

	@Column(name = "size_bytes", nullable = false)
	private long size;

	@Column(name = "download_token", nullable = false, unique = true, length = 64)
	private String downloadToken;

	@Column(name = "download_password_hash")
	private String downloadPasswordHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	public FileMetadata(
			String originalName,
			String storageKey,
			String mimeType,
			long size,
			String downloadToken,
			String downloadPasswordHash,
			Instant createdAt,
			Instant expiresAt,
			User user
	) {
		this.originalName = originalName;
		this.storageKey = storageKey;
		this.mimeType = mimeType;
		this.size = size;
		this.downloadToken = downloadToken;
		this.downloadPasswordHash = downloadPasswordHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
		this.user = user;
	}
}
