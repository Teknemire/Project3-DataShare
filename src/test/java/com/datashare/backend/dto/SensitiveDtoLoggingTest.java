package com.datashare.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SensitiveDtoLoggingTest {

	@Test
	void requestRepresentationsDoNotRevealPasswordsOrEmails() {
		RegisterRequest register = new RegisterRequest("private@example.com", "registration-secret");
		LoginRequest login = new LoginRequest("private@example.com", "login-secret");
		DownloadRequest download = new DownloadRequest("download-secret");
		DeleteAccountRequest deleteAccount = new DeleteAccountRequest("account-secret");

		assertThat(register.toString())
				.doesNotContain("private@example.com", "registration-secret");
		assertThat(login.toString())
				.doesNotContain("private@example.com", "login-secret");
		assertThat(download.toString()).doesNotContain("download-secret");
		assertThat(deleteAccount.toString()).doesNotContain("account-secret");
	}

	@Test
	void responseRepresentationsDoNotRevealTokens() {
		UserResponse user = new UserResponse(
				UUID.randomUUID(), "private@example.com", Instant.parse("2026-09-22T12:00:00Z"));
		LoginResponse login = new LoginResponse(
				"secret-jwt", "Bearer", Instant.parse("2026-09-22T13:00:00Z"), user);
		FileResponse file = new FileResponse(
				UUID.randomUUID(), "private.pdf", "application/pdf", 100,
				Instant.parse("2026-09-22T12:00:00Z"), Instant.parse("2026-09-29T12:00:00Z"),
				false, "http://localhost:4200/share/secret-share-token", FileStatus.ACTIVE);
		DownloadAccessResponse download = new DownloadAccessResponse(
				"/api/shares/secret-share-token/content?ticket=secret-ticket",
				Instant.parse("2026-09-22T12:01:00Z"));

		assertThat(login.toString())
				.doesNotContain("secret-jwt", "private@example.com");
		assertThat(file.toString())
				.doesNotContain("secret-share-token", "private.pdf");
		assertThat(download.toString())
				.doesNotContain("secret-share-token", "secret-ticket");
	}
}
