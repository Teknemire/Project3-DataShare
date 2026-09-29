package com.datashare.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datashare.backend.dto.FileResponse;
import com.datashare.backend.dto.FileStatus;
import com.datashare.backend.entity.FileMetadata;
import com.datashare.backend.entity.User;
import com.datashare.backend.exception.InvalidFileException;
import com.datashare.backend.repository.FileMetadataRepository;
import com.datashare.backend.repository.UserRepository;
import com.datashare.backend.storage.StorageService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private FileMetadataRepository fileMetadataRepository;

	@Mock
	private StorageService storageService;

	private FileUploadService service;

	@BeforeEach
	void setUp() {
		service = new FileUploadService(
				userRepository,
				fileMetadataRepository,
				new FileValidationService(),
				storageService,
				new BCryptPasswordEncoder());
		ReflectionTestUtils.setField(service, "frontendPublicUrl", "http://localhost:4200/");
	}

	@Test
	void storesMetadataAndReturnsTheShareLink() {
		User owner = new User("user@example.com", "hash");
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(owner));
		when(fileMetadataRepository.saveAndFlush(any(FileMetadata.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		FileResponse response = service.upload("user@example.com", file, 7, "secret1");

		ArgumentCaptor<FileMetadata> metadataCaptor = ArgumentCaptor.forClass(FileMetadata.class);
		verify(fileMetadataRepository).saveAndFlush(metadataCaptor.capture());
		FileMetadata metadata = metadataCaptor.getValue();
		assertThat(metadata.getOriginalName()).isEqualTo("photo.jpg");
		assertThat(metadata.getExpiresAt()).isEqualTo(metadata.getCreatedAt().plusSeconds(7 * 86_400L));
		assertThat(new BCryptPasswordEncoder().matches("secret1", metadata.getDownloadPasswordHash())).isTrue();
		assertThat(response.shareUrl()).startsWith("http://localhost:4200/share/");
		assertThat(response.status()).isEqualTo(FileStatus.ACTIVE);
		assertThat(response.passwordProtected()).isTrue();
	}

	@Test
	void storesAnAnonymousUploadWithoutAnOwner() {
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});
		when(fileMetadataRepository.saveAndFlush(any(FileMetadata.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		FileResponse response = service.upload(null, file, null, null);

		ArgumentCaptor<FileMetadata> metadataCaptor = ArgumentCaptor.forClass(FileMetadata.class);
		verify(fileMetadataRepository).saveAndFlush(metadataCaptor.capture());
		FileMetadata metadata = metadataCaptor.getValue();
		assertThat(metadata.getUser()).isNull();
		assertThat(metadata.getExpiresAt()).isEqualTo(metadata.getCreatedAt().plusSeconds(7 * 86_400L));
		assertThat(response.shareUrl()).startsWith("http://localhost:4200/share/");
		verifyNoInteractions(userRepository);
	}

	@Test
	void rejectsAnExpirationOutsideTheAllowedRangeBeforeStorage() {
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});

		assertThatThrownBy(() -> service.upload("user@example.com", file, 8, null))
				.isInstanceOf(InvalidFileException.class);
		verify(storageService, never()).save(any(), any(), anyLong(), any());
	}

	@Test
	void removesStoredContentWhenMetadataPersistenceFails() {
		User owner = new User("user@example.com", "hash");
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(owner));
		when(fileMetadataRepository.saveAndFlush(any(FileMetadata.class)))
				.thenThrow(new IllegalStateException("database unavailable"));

		assertThatThrownBy(() -> service.upload("user@example.com", file, 7, null))
				.isInstanceOf(IllegalStateException.class);
		verify(storageService).delete(any());
	}
}
