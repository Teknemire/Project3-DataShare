package com.datashare.backend.repository;

import com.datashare.backend.entity.FileMetadata;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {

	List<FileMetadata> findAllByExpiresAtLessThanEqual(Instant expirationLimit);

	Optional<FileMetadata> findByDownloadToken(String downloadToken);
}
