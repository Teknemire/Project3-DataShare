package com.datashare.backend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datashare.backend.entity.FileMetadata;
import com.datashare.backend.entity.User;
import com.datashare.backend.exception.FileNotFoundException;
import com.datashare.backend.exception.StorageException;
import com.datashare.backend.repository.FileMetadataRepository;
import com.datashare.backend.storage.StorageService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileDeletionServiceTest {

	@Mock
	private FileMetadataRepository repository;

	@Mock
	private StorageService storageService;

	@InjectMocks
	private FileDeletionService service;

	@Test
	void deletesTheStoredContentBeforeTheOwnedMetadata() {
		UUID fileId = UUID.randomUUID();
		FileMetadata file = file("storage-key");
		when(repository.findByIdAndUser_Email(fileId, "owner@example.com"))
				.thenReturn(Optional.of(file));

		service.deleteOwnedFile("owner@example.com", fileId);

		InOrder order = inOrder(storageService, repository);
		order.verify(storageService).delete("storage-key");
		order.verify(repository).delete(file);
	}

	@Test
	void hidesWhetherAnAbsentFileBelongsToAnotherUser() {
		UUID fileId = UUID.randomUUID();
		when(repository.findByIdAndUser_Email(fileId, "owner@example.com"))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.deleteOwnedFile("owner@example.com", fileId))
				.isInstanceOf(FileNotFoundException.class);

		verifyNoInteractions(storageService);
		verify(repository, never()).delete(any());
	}

	@Test
	void keepsTheMetadataWhenStorageDeletionFails() {
		UUID fileId = UUID.randomUUID();
		FileMetadata file = file("storage-key");
		when(repository.findByIdAndUser_Email(fileId, "owner@example.com"))
				.thenReturn(Optional.of(file));
		doThrow(new StorageException("unavailable"))
				.when(storageService).delete("storage-key");

		assertThatThrownBy(() -> service.deleteOwnedFile("owner@example.com", fileId))
				.isInstanceOf(StorageException.class);

		verify(repository, never()).delete(file);
	}

	private FileMetadata file(String storageKey) {
		return new FileMetadata(
				"document.pdf",
				storageKey,
				"application/pdf",
				42,
				"download-token",
				null,
				Instant.now(),
				Instant.now().plusSeconds(3600),
				new User("owner@example.com", "hash")
		);
	}
}
