package com.datashare.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datashare.backend.config.JwtConfig;
import com.datashare.backend.config.RestAuthenticationEntryPoint;
import com.datashare.backend.config.SecurityConfig;
import com.datashare.backend.dto.FileResponse;
import com.datashare.backend.dto.FileStatus;
import com.datashare.backend.exception.FileTypeNotAllowedException;
import com.datashare.backend.exception.FileNotFoundException;
import com.datashare.backend.exception.TagAuthenticationRequiredException;
import com.datashare.backend.service.FileDeletionService;
import com.datashare.backend.service.FileQueryService;
import com.datashare.backend.service.FileUploadService;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
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

	@MockitoBean
	private FileQueryService fileQueryService;

	@MockitoBean
	private FileDeletionService fileDeletionService;

	@Test
	void listsOnlyFilesRequestedForTheAuthenticatedIdentity() throws Exception {
		FileResponse response = new FileResponse(
				UUID.fromString("b94ff329-ff57-4d38-b931-c013c494cc79"),
				"photo.jpg", "image/jpeg", 4,
				Instant.parse("2026-09-22T12:00:00Z"),
				Instant.parse("2026-09-29T12:00:00Z"),
				true, "http://localhost:4200/share/token", FileStatus.ACTIVE, List.of("Projet"));
		when(fileQueryService.listOwnedFiles("user@example.com")).thenReturn(List.of(response));

		mockMvc.perform(get("/api/files")
					.with(jwt().jwt(token -> token.subject("user@example.com"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].originalName").value("photo.jpg"))
				.andExpect(jsonPath("$[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$[0].passwordProtected").value(true))
				.andExpect(jsonPath("$[0].tags[0]").value("Projet"));
	}

	@Test
	void rejectsHistoryWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/files"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void returnsNotFoundForAnAbsentOrForeignFile() throws Exception {
		UUID fileId = UUID.fromString("b94ff329-ff57-4d38-b931-c013c494cc79");
		when(fileQueryService.getOwnedFile("user@example.com", fileId))
				.thenThrow(new FileNotFoundException());

		mockMvc.perform(get("/api/files/{id}", fileId)
					.with(jwt().jwt(token -> token.subject("user@example.com"))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
	}

	@Test
	void deletesAFileForTheAuthenticatedOwner() throws Exception {
		UUID fileId = UUID.fromString("b94ff329-ff57-4d38-b931-c013c494cc79");

		mockMvc.perform(delete("/api/files/{id}", fileId)
					.with(jwt().jwt(token -> token.subject("user@example.com"))))
				.andExpect(status().isNoContent());

		verify(fileDeletionService).deleteOwnedFile("user@example.com", fileId);
	}

	@Test
	void rejectsDeletionWithoutAuthentication() throws Exception {
		mockMvc.perform(delete("/api/files/{id}", UUID.randomUUID()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void returnsNotFoundWhenDeletingAnAbsentOrForeignFile() throws Exception {
		UUID fileId = UUID.fromString("b94ff329-ff57-4d38-b931-c013c494cc79");
		org.mockito.Mockito.doThrow(new FileNotFoundException())
				.when(fileDeletionService).deleteOwnedFile("user@example.com", fileId);

		mockMvc.perform(delete("/api/files/{id}", fileId)
					.with(jwt().jwt(token -> token.subject("user@example.com"))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
	}

	@Test
	void uploadsAFileForTheAuthenticatedUser() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});
		FileResponse response = new FileResponse(
				UUID.fromString("b94ff329-ff57-4d38-b931-c013c494cc79"),
				"photo.jpg", "image/jpeg", 4,
				Instant.parse("2026-09-22T12:00:00Z"),
				Instant.parse("2026-09-29T12:00:00Z"),
				false, "http://localhost:4200/share/token", FileStatus.ACTIVE, List.of("Projet", "Urgent"));
		when(fileUploadService.upload(eq("user@example.com"), any(), eq(7), eq(null),
				eq(List.of("Projet", "Urgent"))))
				.thenReturn(response);

		mockMvc.perform(multipart("/api/files")
					.file(file)
					.param("expirationDays", "7")
					.param("tags", "Projet", "Urgent")
					.with(jwt().jwt(token -> token.subject("user@example.com"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.originalName").value("photo.jpg"))
				.andExpect(jsonPath("$.shareUrl").value("http://localhost:4200/share/token"))
				.andExpect(jsonPath("$.tags.length()").value(2))
				.andExpect(jsonPath("$.storageKey").doesNotExist())
				.andExpect(jsonPath("$.downloadPasswordHash").doesNotExist());
	}

	@Test
	void uploadsAFileWithoutAuthentication() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});
		FileResponse response = new FileResponse(
				UUID.fromString("b94ff329-ff57-4d38-b931-c013c494cc79"),
				"photo.jpg", "image/jpeg", 4,
				Instant.parse("2026-09-22T12:00:00Z"),
				Instant.parse("2026-09-29T12:00:00Z"),
				false, "http://localhost:4200/share/token", FileStatus.ACTIVE, List.of());
		when(fileUploadService.upload(isNull(), any(), eq(7), isNull(), isNull()))
				.thenReturn(response);

		mockMvc.perform(multipart("/api/files").file(file))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shareUrl").value("http://localhost:4200/share/token"));

		verify(fileUploadService).upload(isNull(), any(), eq(7), isNull(), isNull());
	}

	@Test
	void rejectsAnonymousTagsWithAnAuthenticationError() throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});
		when(fileUploadService.upload(isNull(), any(), eq(7), isNull(), eq(List.of("Projet"))))
				.thenThrow(new TagAuthenticationRequiredException());

		mockMvc.perform(multipart("/api/files")
					.file(file)
					.param("tags", "Projet"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("TAG_AUTHENTICATION_REQUIRED"));
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
		when(fileUploadService.upload(eq("user@example.com"), any(), eq(7), eq(null), isNull()))
				.thenThrow(new FileTypeNotAllowedException());

		mockMvc.perform(multipart("/api/files")
					.file(file)
					.with(jwt().jwt(token -> token.subject("user@example.com"))))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));
	}
}
