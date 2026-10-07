package com.datashare.backend.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.datashare.backend.entity.FileMetadata;
import com.datashare.backend.entity.User;
import com.datashare.backend.exception.StorageException;
import com.datashare.backend.repository.FileMetadataRepository;
import com.datashare.backend.storage.StorageService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileExpirationServiceTest {

	@Mock
	private FileMetadataRepository repository;

	@Mock
	private StorageService storageService;

	@InjectMocks
	private FileExpirationService service;

	@Test
	void attemptsEveryExpiredContentEvenWhenOneDeletionFails() {
		User owner = new User("user@example.com", "hash");
		FileMetadata first = expiredFile("first", owner);
		FileMetadata second = expiredFile("second", owner);
		when(repository.findTop100ByContentDeletedFalseAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(ArgumentMatchers.any(Instant.class)))
				.thenReturn(List.of(first, second));
		org.mockito.Mockito.doThrow(new StorageException("unavailable"))
				.when(storageService).delete("first");

		service.deleteExpiredContents();

		verify(storageService).delete("first");
		verify(storageService).delete("second");
		verify(repository, never()).delete(first);
		verify(repository, never()).delete(second);
		verify(repository).markContentDeleted(second.getId());
	}

	@Test
	void removesAnonymousMetadataAfterItsExpiredContent() {
		FileMetadata anonymousFile = expiredFile("anonymous", null);
		when(repository.findTop100ByContentDeletedFalseAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(ArgumentMatchers.any(Instant.class)))
				.thenReturn(List.of(anonymousFile));

		service.deleteExpiredContents();

		verify(storageService).delete("anonymous");
		verify(repository).delete(anonymousFile);
	}

	private FileMetadata expiredFile(String key, User owner) {
		return new FileMetadata(
				key + ".jpg", key, "image/jpeg", 4, "token-" + key, null,
				Instant.now().minusSeconds(100), Instant.now().minusSeconds(10), owner);
	}
}
