package com.datashare.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
class DataShareApplicationIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@org.springframework.test.context.bean.override.mockito.MockitoSpyBean
	private com.datashare.backend.storage.StorageService storageService;

	@Autowired
	private com.datashare.backend.repository.FileMetadataRepository fileRepository;

	@Autowired
	private com.datashare.backend.repository.UserRepository userRepository;

	@Autowired
	private com.datashare.backend.service.FileExpirationService expirationService;

	@Test
	void writesUploadContentWithoutAnActiveTransaction() throws Exception {
		org.mockito.Mockito.doAnswer(invocation -> {
			assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
					.isActualTransactionActive()).isFalse();
			return invocation.callRealMethod();
		}).when(storageService).save(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyString());
		mockMvc.perform(multipart("/api/files").file(new MockMultipartFile(
				"file", "outside-transaction.txt", "text/plain", new byte[] {1})))
				.andExpect(status().isCreated());
	}

	@Test
	void paginatesAndFiltersOnlyOwnedHistoryAndRejectsInvalidSizes() throws Exception {
		String token = registerAndLogin("pagination@datashare.test", "Password123!");
		var owner = userRepository.findByEmail("pagination@datashare.test").orElseThrow();
		java.time.Instant now = java.time.Instant.now();
		for (int i = 0; i < 3; i++) {
			fileRepository.saveAndFlush(new com.datashare.backend.entity.FileMetadata(
					"page-" + i + ".txt", "page-key-" + i, "text/plain", 1, "page-token-" + i,
					null, now.plusSeconds(i), now.plusSeconds(i == 0 ? -60 : 3600), owner));
		}
		mockMvc.perform(get("/api/files").param("size", "1").param("page", "1")
				.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].originalName").value("page-1.txt"));
		mockMvc.perform(get("/api/files").param("status", "EXPIRED")
				.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].originalName").value("page-0.txt"));
		mockMvc.perform(get("/api/files").param("size", "101")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/files").param("page", "-1")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isBadRequest());
	}

	@Test
	void purgesOwnedContentOnlyOnceAndRetainsItsHistory() {
		var owner = userRepository.saveAndFlush(new com.datashare.backend.entity.User("purge@datashare.test", "hash"));
		var now = java.time.Instant.now();
		var file = fileRepository.saveAndFlush(new com.datashare.backend.entity.FileMetadata(
				"expired.txt", "purge-key", "text/plain", 1, "purge-token", null,
				now.minusSeconds(120), now.minusSeconds(60), owner));
		expirationService.deleteExpiredContents();
		expirationService.deleteExpiredContents();
		org.mockito.Mockito.verify(storageService, org.mockito.Mockito.times(1)).delete("purge-key");
		assertThat(fileRepository.findById(file.getId()).orElseThrow().isContentDeleted()).isTrue();
	}

	@Test
	void exposesOnlyThePublicHealthStatus() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist());
		mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
	}

	@Test
	void deletedAccountTokenCannotAccessARecreatedAccountWithTheSameEmail() throws Exception {
		String email = "recreated@datashare.test";
		String oldToken = registerAndLogin(email, "OriginalPassword123!");
		mockMvc.perform(delete("/api/users/me")
				.header(HttpHeaders.AUTHORIZATION, bearer(oldToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"OriginalPassword123!\"}"))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(oldToken)))
				.andExpect(status().isUnauthorized());

		String newToken = registerAndLogin(email, "NewPassword123!");
		MockMultipartFile file = new MockMultipartFile("file", "private.txt", "text/plain", new byte[] {1, 2, 3});
		MvcResult upload = mockMvc.perform(multipart("/api/files").file(file)
				.header(HttpHeaders.AUTHORIZATION, bearer(newToken)))
				.andExpect(status().isCreated()).andReturn();
		String fileId = json(upload).path("id").asText();

		for (String path : new String[] {"/api/auth/me", "/api/files", "/api/files/" + fileId}) {
			mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(oldToken)))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(delete("/api/files/" + fileId).header(HttpHeaders.AUTHORIZATION, bearer(oldToken)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(multipart("/api/files").file(file).header(HttpHeaders.AUTHORIZATION, bearer(oldToken)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/files/" + fileId).header(HttpHeaders.AUTHORIZATION, bearer(newToken)))
				.andExpect(status().isOk());
		mockMvc.perform(delete("/api/users/me")
				.header(HttpHeaders.AUTHORIZATION, bearer(newToken))
				.contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"NewPassword123!\"}"))
				.andExpect(status().isNoContent());
	}

	@Test
	void unicodePasswordsRespectTheUtf8ByteLimitForAccountsAndShares() throws Exception {
		String accepted = "é".repeat(36);
		String tooLong = "é".repeat(40);
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(credentials("too-long@datashare.test", tooLong)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.password").exists());
		String token = registerAndLogin("unicode@datashare.test", accepted);
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(credentials("unicode@datashare.test", tooLong)))
				.andExpect(status().isUnauthorized());
		MockMultipartFile file = new MockMultipartFile("file", "unicode.txt", "text/plain", new byte[] {1, 2, 3});
		mockMvc.perform(multipart("/api/files").file(file).param("password", tooLong))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		MvcResult upload = mockMvc.perform(multipart("/api/files").file(file).param("password", accepted)
				.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isCreated()).andReturn();
		String url = json(upload).path("shareUrl").asText();
		String share = url.substring(url.lastIndexOf('/') + 1);
		MvcResult missing = mockMvc.perform(post("/api/shares/{token}/download", share)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized()).andReturn();
		MvcResult wrong = mockMvc.perform(post("/api/shares/{token}/download", share)
				.contentType(MediaType.APPLICATION_JSON).content(passwordBody(tooLong)))
				.andExpect(status().isUnauthorized()).andReturn();
		assertThat(json(wrong)).isEqualTo(json(missing));
		mockMvc.perform(post("/api/shares/{token}/download", share)
				.contentType(MediaType.APPLICATION_JSON).content(passwordBody(accepted)))
				.andExpect(status().isOk());
		mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(passwordBody(tooLong)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(passwordBody(accepted)))
				.andExpect(status().isNoContent());
	}

	@Test
	void openApiDescribesMultipartOptionalAuthenticationErrorsAndBinaryDownload() throws Exception {
		JsonNode specification = json(mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk()).andReturn());
		JsonNode upload = specification.path("paths").path("/api/files").path("post");
		assertThat(upload.path("parameters").size()).isZero();
		JsonNode schema = upload.path("requestBody").path("content").path("multipart/form-data").path("schema");
		assertThat(schema.path("required").get(0).asText()).isEqualTo("file");
		assertThat(schema.path("properties").size()).isEqualTo(4);
		assertThat(schema.path("properties").path("file").path("format").asText()).isEqualTo("binary");
		assertThat(schema.path("properties").path("tags").path("type").asText()).isEqualTo("array");
		assertThat(upload.path("security").size()).isEqualTo(2);
		assertThat(upload.path("security").get(0).size()).isZero();
		assertThat(upload.path("security").get(1).has("bearerAuth")).isTrue();
		assertThat(upload.path("responses").path("400").path("content").path("application/json")
				.path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/ApiError");
		JsonNode download = specification.path("paths").path("/api/shares/{token}/content").path("get");
		assertThat(download.path("responses").path("200").path("content").path("*/*")
				.path("schema").path("format").asText()).isEqualTo("binary");
		assertThat(download.path("responses").path("401").path("content").path("application/json")
				.path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/ApiError");
	}

	private String registerAndLogin(String email, String password) throws Exception {
		String body = credentials(email, password);
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		return json(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andReturn()).path("accessToken").asText();
	}

	private String credentials(String email, String password) throws Exception {
		return objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password));
	}

	private String passwordBody(String password) throws Exception {
		return objectMapper.writeValueAsString(java.util.Map.of("password", password));
	}

	@Test
	void authenticatedFileLifecycleWorksAcrossHttpSecurityDatabaseAndStorage() throws Exception {
		String email = "integration@datashare.test";
		String password = "Password123!";

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"integration@datashare.test","password":"Password123!"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value(email));

		MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"integration@datashare.test","password":"Password123!"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andReturn();
		String accessToken = json(loginResult).path("accessToken").asText();

		MockMultipartFile file = new MockMultipartFile(
				"file",
				"integration.txt",
				MediaType.TEXT_PLAIN_VALUE,
				"contenu integration".getBytes()
		);
		MvcResult uploadResult = mockMvc.perform(multipart("/api/files")
				.file(file)
				.param("expirationDays", "7")
				.param("password", "partage123")
				.param("tags", "Projet", "Urgent")
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.originalName").value("integration.txt"))
				.andExpect(jsonPath("$.passwordProtected").value(true))
				.andExpect(jsonPath("$.tags", containsInAnyOrder("Projet", "Urgent")))
				.andReturn();

		JsonNode uploadedFile = json(uploadResult);
		String fileId = uploadedFile.path("id").asText();
		String shareUrl = uploadedFile.path("shareUrl").asText();
		String shareToken = shareUrl.substring(shareUrl.lastIndexOf('/') + 1);

		mockMvc.perform(get("/api/files")
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(fileId))
				.andExpect(jsonPath("$[0].status").value("ACTIVE"));

		mockMvc.perform(get("/api/shares/{token}", shareToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.originalName").value("integration.txt"))
				.andExpect(jsonPath("$.passwordProtected").value(true));

		mockMvc.perform(post("/api/shares/{token}/download", shareToken)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("DOWNLOAD_AUTH_FAILED"));

		MvcResult authorizationResult = mockMvc.perform(post("/api/shares/{token}/download", shareToken)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"partage123\"}"))
				.andExpect(status().isOk())
				.andReturn();
		String downloadUrl = json(authorizationResult).path("downloadUrl").asText();

		MvcResult downloadResult = mockMvc.perform(get(downloadUrl))
				.andExpect(request().asyncStarted())
				.andReturn();
		mockMvc.perform(asyncDispatch(downloadResult))
				.andExpect(status().isOk())
				.andExpect(content().bytes("contenu integration".getBytes()));

		mockMvc.perform(delete("/api/files/{id}", fileId)
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/shares/{token}", shareToken))
				.andExpect(status().isNotFound());
	}

	private JsonNode json(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsByteArray());
	}

	private String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}
}
