package com.datashare.backend;

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
