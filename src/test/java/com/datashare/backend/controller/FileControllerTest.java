package com.datashare.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datashare.backend.config.JwtConfig;
import com.datashare.backend.config.RestAuthenticationEntryPoint;
import com.datashare.backend.config.SecurityConfig;
import com.datashare.backend.dto.FileResponse;
import com.datashare.backend.dto.FileStatus;
import com.datashare.backend.exception.FileTypeNotAllowedException;
import com.datashare.backend.service.FileUploadService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
		value = FileController.class,
		properties = {
				"app.jwt.secret=test-secret-with-at-least-32-bytes-long",
				"app.jwt.expiration=PT1H"
		}
)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class})
class FileControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private FileUploadService fileUploadService;

	@Test
	void uploadsAFileForTheAuthenticatedUser() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});
		FileResponse response = new FileResponse(
				UUID.fromString("b94ff329-ff57-4d38-b931-c013c494cc79"),
				"photo.jpg", "image/jpeg", 4,
				Instant.parse("2026-09-22T12:00:00Z"),
				Instant.parse("2026-09-29T12:00:00Z"),
				false, "http://localhost:4200/share/token", FileStatus.ACTIVE);
		when(fileUploadService.upload(eq("user@example.com"), any(), eq(7), eq(null)))
				.thenReturn(response);

		mockMvc.perform(multipart("/api/files")
					.file(file)
					.param("expirationDays", "7")
					.with(jwt().jwt(token -> token.subject("user@example.com"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.originalName").value("photo.jpg"))
				.andExpect(jsonPath("$.shareUrl").value("http://localhost:4200/share/token"))
				.andExpect(jsonPath("$.storageKey").doesNotExist())
				.andExpect(jsonPath("$.downloadPasswordHash").doesNotExist());
	}

	@Test
	void rejectsUploadWithoutAuthentication() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});

		mockMvc.perform(multipart("/api/files").file(file))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void rejectsANonNumericExpiration() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});

		mockMvc.perform(multipart("/api/files")
					.file(file)
					.param("expirationDays", "tomorrow")
					.with(jwt().jwt(token -> token.subject("user@example.com"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void returnsUnsupportedMediaTypeForAForbiddenFile() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "program.exe", "application/octet-stream", new byte[] {'M', 'Z'});
		when(fileUploadService.upload(eq("user@example.com"), any(), eq(7), eq(null)))
				.thenThrow(new FileTypeNotAllowedException());

		mockMvc.perform(multipart("/api/files")
					.file(file)
					.with(jwt().jwt(token -> token.subject("user@example.com"))))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));
	}
}
