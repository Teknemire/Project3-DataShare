package com.datashare.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
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

	@Column(name = "content_deleted", nullable = false)
	private boolean contentDeleted;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@ElementCollection
	@CollectionTable(
			name = "file_tag",
			joinColumns = @JoinColumn(name = "file_id"),
			uniqueConstraints = @UniqueConstraint(
					name = "uk_file_tag_file_id_tag",
					columnNames = {"file_id", "tag"}
			)
	)
	@Column(name = "tag", nullable = false, length = 30)
	private Set<String> tags = new LinkedHashSet<>();

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
		this(originalName, storageKey, mimeType, size, downloadToken, downloadPasswordHash,
				createdAt, expiresAt, user, Set.of());
	}

	public FileMetadata(
			String originalName,
			String storageKey,
			String mimeType,
			long size,
			String downloadToken,
			String downloadPasswordHash,
			Instant createdAt,
			Instant expiresAt,
			User user,
			Set<String> tags
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
		this.tags = new LinkedHashSet<>(tags);
	}
}
