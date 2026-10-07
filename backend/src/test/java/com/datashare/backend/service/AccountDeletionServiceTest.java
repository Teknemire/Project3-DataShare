package com.datashare.backend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datashare.backend.dto.DeleteAccountRequest;
import com.datashare.backend.entity.FileMetadata;
import com.datashare.backend.entity.User;
import com.datashare.backend.exception.CurrentUserNotFoundException;
import com.datashare.backend.exception.InvalidCredentialsException;
import com.datashare.backend.exception.StorageException;
import com.datashare.backend.repository.FileMetadataRepository;
import com.datashare.backend.repository.UserRepository;
import com.datashare.backend.storage.StorageService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private FileMetadataRepository fileMetadataRepository;

	@Mock
	private StorageService storageService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private AccountDeletionService service;

	@Test
	void deletesEveryContentThenMetadataAndAccount() {
		User user = new User("owner@example.com", "stored-hash");
		FileMetadata firstFile = file("first-key", user);
		FileMetadata secondFile = file("second-key", user);
		List<FileMetadata> files = List.of(firstFile, secondFile);
		when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("current-password", "stored-hash")).thenReturn(true);
		when(fileMetadataRepository.findAllByUser_EmailOrderByCreatedAtDescIdDesc("owner@example.com"))
				.thenReturn(files);

		service.deleteCurrentAccount(
				"owner@example.com", new DeleteAccountRequest("current-password"));

		InOrder order = inOrder(storageService, fileMetadataRepository, userRepository);
		order.verify(storageService).delete("first-key");
		order.verify(storageService).delete("second-key");
		order.verify(fileMetadataRepository).deleteAll(files);
		order.verify(userRepository).delete(user);
	}

	@Test
	void rejectsAnIncorrectPasswordBeforeReadingOrDeletingFiles() {
		User user = new User("owner@example.com", "stored-hash");
		when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

		assertThatThrownBy(() -> service.deleteCurrentAccount(
				"owner@example.com", new DeleteAccountRequest("wrong-password")))
				.isInstanceOf(InvalidCredentialsException.class);

		verifyNoInteractions(fileMetadataRepository, storageService);
		verify(userRepository, never()).delete(user);
	}

	@Test
	void rejectsADeletedOrUnknownCurrentUser() {
		when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.deleteCurrentAccount(
				"owner@example.com", new DeleteAccountRequest("current-password")))
				.isInstanceOf(CurrentUserNotFoundException.class);

		verifyNoInteractions(passwordEncoder, fileMetadataRepository, storageService);
	}

	@Test
	void keepsMetadataAndAccountWhenStorageDeletionFails() {
		User user = new User("owner@example.com", "stored-hash");
		FileMetadata file = file("storage-key", user);
		when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("current-password", "stored-hash")).thenReturn(true);
		when(fileMetadataRepository.findAllByUser_EmailOrderByCreatedAtDescIdDesc("owner@example.com"))
				.thenReturn(List.of(file));
		doThrow(new StorageException("unavailable")).when(storageService).delete("storage-key");

		assertThatThrownBy(() -> service.deleteCurrentAccount(
				"owner@example.com", new DeleteAccountRequest("current-password")))
				.isInstanceOf(StorageException.class);

		verify(fileMetadataRepository, never()).deleteAll(List.of(file));
		verify(userRepository, never()).delete(user);
	}

	private FileMetadata file(String storageKey, User user) {
		return new FileMetadata(
				"document.pdf",
				storageKey,
				"application/pdf",
				42,
				"download-token-" + storageKey,
				null,
				Instant.now(),
				Instant.now().plusSeconds(3600),
				user
		);
	}
}
