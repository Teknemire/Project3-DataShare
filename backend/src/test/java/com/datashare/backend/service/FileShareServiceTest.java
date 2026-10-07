package com.datashare.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datashare.backend.dto.DownloadAccessResponse;
import com.datashare.backend.dto.SharedFileResponse;
import com.datashare.backend.entity.FileMetadata;
import com.datashare.backend.entity.User;
import com.datashare.backend.exception.DownloadAuthenticationException;
import com.datashare.backend.exception.ShareExpiredException;
import com.datashare.backend.exception.ShareNotFoundException;
import com.datashare.backend.repository.FileMetadataRepository;
import com.datashare.backend.storage.StorageService;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class FileShareServiceTest {

	@Mock
	private FileMetadataRepository fileMetadataRepository;

	@Mock
	private StorageService storageService;

	@Mock
	private DownloadTicketService downloadTicketService;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private FileShareService service;

	@BeforeEach
	void setUp() {
		service = new FileShareService(
				fileMetadataRepository,
				storageService,
				passwordEncoder,
				downloadTicketService
		);
	}

	@Test
	void returnsOnlyPublicMetadataForAnActiveShare() {
		FileMetadata file = file(null, Instant.now().plusSeconds(3_600));
		when(fileMetadataRepository.findByDownloadToken("share-token")).thenReturn(Optional.of(file));

		SharedFileResponse response = service.getSharedFile("share-token");

		assertThat(response.originalName()).isEqualTo("document.pdf");
		assertThat(response.passwordProtected()).isFalse();
	}

	@Test
	void rejectsAMissingAndAnIncorrectPasswordWithTheSameException() {
		FileMetadata file = file(passwordEncoder.encode("correct-password"), Instant.now().plusSeconds(3_600));
		when(fileMetadataRepository.findByDownloadToken("share-token")).thenReturn(Optional.of(file));

		assertThatThrownBy(() -> service.authorizeDownload("share-token", null))
				.isExactlyInstanceOf(DownloadAuthenticationException.class)
				.hasMessage("Authentification du téléchargement refusée.");
		assertThatThrownBy(() -> service.authorizeDownload("share-token", "wrong-password"))
				.isExactlyInstanceOf(DownloadAuthenticationException.class)
				.hasMessage("Authentification du téléchargement refusée.");
		verify(downloadTicketService, never()).generate(any(), any());
	}

	@Test
	void createsAShortLivedDownloadUrlAfterPasswordValidation() {
		Instant shareExpiration = Instant.now().plusSeconds(3_600);
		Instant ticketExpiration = Instant.now().plusSeconds(60);
		FileMetadata file = file(passwordEncoder.encode("correct-password"), shareExpiration);
		when(fileMetadataRepository.findByDownloadToken("share-token")).thenReturn(Optional.of(file));
		when(downloadTicketService.generate("share-token", shareExpiration))
				.thenReturn(new DownloadTicketService.GeneratedTicket("signed-ticket", ticketExpiration));

		DownloadAccessResponse response = service.authorizeDownload("share-token", "correct-password");

		assertThat(response.downloadUrl())
				.isEqualTo("/api/shares/share-token/content?ticket=signed-ticket");
		assertThat(response.expiresAt()).isEqualTo(ticketExpiration);
	}

	@Test
	void validatesTheTicketBeforeOpeningTheStoredContent() {
		FileMetadata file = file(null, Instant.now().plusSeconds(3_600));
		when(fileMetadataRepository.findByDownloadToken("share-token")).thenReturn(Optional.of(file));
		when(storageService.open("storage-key"))
				.thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

		FileShareService.DownloadContent content = service.openDownload("share-token", "signed-ticket");

		verify(downloadTicketService).validate("share-token", "signed-ticket");
		verify(storageService).open("storage-key");
		assertThat(content.size()).isEqualTo(3);
	}

	@Test
	void distinguishesAnExpiredShareFromAnUnknownShare() {
		when(fileMetadataRepository.findByDownloadToken("expired-token"))
				.thenReturn(Optional.of(file(null, Instant.now().minusSeconds(1))));
		when(fileMetadataRepository.findByDownloadToken("unknown-token"))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getSharedFile("expired-token"))
				.isInstanceOf(ShareExpiredException.class);
		assertThatThrownBy(() -> service.getSharedFile("unknown-token"))
				.isInstanceOf(ShareNotFoundException.class);
	}

	private FileMetadata file(String passwordHash, Instant expiresAt) {
		return new FileMetadata(
				"document.pdf",
				"storage-key",
				"application/pdf",
				3,
				"share-token",
				passwordHash,
				Instant.now().minusSeconds(60),
				expiresAt,
				new User("owner@example.com", "hash")
		);
	}
}
