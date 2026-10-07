package com.datashare.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.datashare.backend.config.JwtConfig;
import com.datashare.backend.config.RestAuthenticationEntryPoint;
import com.datashare.backend.config.SecurityConfig;
import com.datashare.backend.dto.DownloadAccessResponse;
import com.datashare.backend.dto.SharedFileResponse;
import com.datashare.backend.exception.DownloadAuthenticationException;
import com.datashare.backend.exception.ShareExpiredException;
import com.datashare.backend.repository.UserRepository;
import com.datashare.backend.service.FileShareService;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(
		value = ShareController.class,
		properties = {
				"app.jwt.secret=test-secret-with-at-least-32-bytes-long",
				"app.jwt.expiration=PT1H"
		}
)
@Import({SecurityConfig.class, JwtConfig.class, RestAuthenticationEntryPoint.class})
class ShareControllerTest {

	@MockitoBean
	private UserRepository userRepository;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private FileShareService fileShareService;

	@Test
	void exposesShareMetadataWithoutAccountAuthentication() throws Exception {
		when(fileShareService.getSharedFile("share-token")).thenReturn(new SharedFileResponse(
				"document.pdf", "application/pdf", 3,
				Instant.parse("2026-09-29T12:00:00Z"), true));

		mockMvc.perform(get("/api/shares/share-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.originalName").value("document.pdf"))
				.andExpect(jsonPath("$.passwordProtected").value(true))
				.andExpect(jsonPath("$.downloadPasswordHash").doesNotExist());
	}

	@Test
	void returnsTheSameUnauthorizedResponseForARejectedDownloadPassword() throws Exception {
		when(fileShareService.authorizeDownload("share-token", null))
				.thenThrow(new DownloadAuthenticationException());

		mockMvc.perform(post("/api/shares/share-token/download")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("DOWNLOAD_AUTH_FAILED"))
				.andExpect(jsonPath("$.message")
						.value("Authentification du téléchargement refusée."));
	}

	@Test
	void createsATemporaryDownloadUrlWithoutAccountAuthentication() throws Exception {
		when(fileShareService.authorizeDownload("share-token", null)).thenReturn(
				new DownloadAccessResponse(
						"/api/shares/share-token/content?ticket=signed-ticket",
						Instant.parse("2026-09-22T12:01:00Z")));

		mockMvc.perform(post("/api/shares/share-token/download")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.downloadUrl")
						.value("/api/shares/share-token/content?ticket=signed-ticket"));
	}

	@Test
	void streamsTheFileWithDownloadHeaders() throws Exception {
		when(fileShareService.openDownload("share-token", "signed-ticket"))
				.thenReturn(new FileShareService.DownloadContent(
						"document été.pdf",
						"application/pdf",
						3,
						new ByteArrayInputStream(new byte[] {1, 2, 3})));

		MvcResult result = mockMvc.perform(get("/api/shares/share-token/content")
					.param("ticket", "signed-ticket"))
				.andExpect(request().asyncStarted())
				.andReturn();

		mockMvc.perform(asyncDispatch(result))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/pdf"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Content-Disposition", containsString("attachment")))
				.andExpect(header().string("Content-Disposition",
						containsString("filename*=UTF-8''document%20%C3%A9t%C3%A9.pdf")))
				.andExpect(content().bytes(new byte[] {1, 2, 3}));
	}

	@Test
	void reportsAnExpiredShareAsGone() throws Exception {
		when(fileShareService.getSharedFile("expired-token"))
				.thenThrow(new ShareExpiredException());

		mockMvc.perform(get("/api/shares/expired-token"))
				.andExpect(status().isGone())
				.andExpect(jsonPath("$.code").value("SHARE_EXPIRED"));
	}
}
