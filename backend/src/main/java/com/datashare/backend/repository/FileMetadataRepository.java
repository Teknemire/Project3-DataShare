package com.datashare.backend.repository;

import com.datashare.backend.entity.FileMetadata;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {

	List<FileMetadata> findTop100ByContentDeletedFalseAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(Instant expirationLimit);

	@Modifying
	@Transactional
	@Query("update FileMetadata f set f.contentDeleted = true where f.id = :id")
	void markContentDeleted(UUID id);

	@Query("""
			select f from FileMetadata f where f.user.email = :email
			and (:status = 'ALL' or (:status = 'ACTIVE' and f.expiresAt > :now)
			or (:status = 'EXPIRED' and f.expiresAt <= :now))
			order by f.createdAt desc, f.id desc
			""")
	List<FileMetadata> findOwnedPage(String email, String status, Instant now, Pageable pageable);

	Optional<FileMetadata> findByDownloadToken(String downloadToken);

	List<FileMetadata> findAllByUser_EmailOrderByCreatedAtDescIdDesc(String email);

	Optional<FileMetadata> findByIdAndUser_Email(UUID id, String email);
}
