package com.datashare.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datashare.backend.dto.FileResponse;
import com.datashare.backend.dto.FileStatus;
import com.datashare.backend.entity.FileMetadata;
import com.datashare.backend.entity.User;
import com.datashare.backend.exception.FileNotFoundException;
import com.datashare.backend.repository.FileMetadataRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FileQueryServiceTest {

	@Mock
	private FileMetadataRepository fileMetadataRepository;

	private FileQueryService service;

	@BeforeEach
	void setUp() {
		service = new FileQueryService(fileMetadataRepository);
		ReflectionTestUtils.setField(service, "frontendPublicUrl", "http://localhost:4200/");
	}

	@Test
	void listsOnlyTheAuthenticatedUsersFilesWithCalculatedStatuses() {
		FileMetadata active = file("active.pdf", "active-token", Instant.now().plusSeconds(3_600));
		FileMetadata expired = file("expired.pdf", "expired-token", Instant.now().minusSeconds(60));
		when(fileMetadataRepository.findAllByUser_EmailOrderByCreatedAtDescIdDesc("owner@example.com"))
				.thenReturn(List.of(active, expired));

		List<FileResponse> response = service.listOwnedFiles("owner@example.com");

		verify(fileMetadataRepository).findAllByUser_EmailOrderByCreatedAtDescIdDesc("owner@example.com");
		assertThat(response).extracting(FileResponse::originalName)
				.containsExactly("active.pdf", "expired.pdf");
		assertThat(response).extracting(FileResponse::status)
				.containsExactly(FileStatus.ACTIVE, FileStatus.EXPIRED);
		assertThat(response.getFirst().shareUrl())
				.isEqualTo("http://localhost:4200/share/active-token");
	}

	@Test
	void returnsAnOwnedFile() {
		UUID fileId = UUID.randomUUID();
		FileMetadata file = file("document.pdf", "share-token", Instant.now().plusSeconds(3_600));
		when(fileMetadataRepository.findByIdAndUser_Email(fileId, "owner@example.com"))
				.thenReturn(Optional.of(file));

		FileResponse response = service.getOwnedFile("owner@example.com", fileId);

		assertThat(response.originalName()).isEqualTo("document.pdf");
	}

	@Test
	void hidesAnAbsentOrForeignFileBehindTheSameNotFoundError() {
		UUID fileId = UUID.randomUUID();
		when(fileMetadataRepository.findByIdAndUser_Email(fileId, "owner@example.com"))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getOwnedFile("owner@example.com", fileId))
				.isInstanceOf(FileNotFoundException.class);
	}

	private FileMetadata file(String name, String token, Instant expiresAt) {
		return new FileMetadata(
				name,
				UUID.randomUUID().toString(),
				"application/pdf",
				1_500,
				token,
				null,
				Instant.now().minusSeconds(120),
				expiresAt,
				new User("owner@example.com", "hash")
		);
	}
}
