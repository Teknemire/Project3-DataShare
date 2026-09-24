package com.datashare.backend.controller;

import com.datashare.backend.dto.DownloadAccessResponse;
import com.datashare.backend.dto.DownloadRequest;
import com.datashare.backend.dto.SharedFileResponse;
import com.datashare.backend.service.FileShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class ShareController {

	private final FileShareService fileShareService;

	@GetMapping("/{token}")
	@Operation(summary = "Consulter les informations publiques d'un partage")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Informations publiques du fichier"),
			@ApiResponse(responseCode = "404", description = "Lien introuvable ou supprimé"),
			@ApiResponse(responseCode = "410", description = "Partage expiré")
	})
	public SharedFileResponse getSharedFile(@PathVariable String token) {
		return fileShareService.getSharedFile(token);
	}

	@PostMapping(path = "/{token}/download", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Vérifier la protection et créer une autorisation de téléchargement temporaire")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "URL de téléchargement temporaire créée"),
			@ApiResponse(responseCode = "401", description = "Authentification du téléchargement refusée"),
			@ApiResponse(responseCode = "404", description = "Lien introuvable ou supprimé"),
			@ApiResponse(responseCode = "410", description = "Partage expiré")
	})
	public DownloadAccessResponse authorizeDownload(
			@PathVariable String token,
			@RequestBody(required = false) DownloadRequest request
	) {
		return fileShareService.authorizeDownload(token, request == null ? null : request.password());
	}

	@GetMapping("/{token}/content")
	@Operation(summary = "Télécharger en flux avec une autorisation temporaire")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Contenu du fichier"),
			@ApiResponse(responseCode = "401", description = "Autorisation temporaire invalide ou expirée"),
			@ApiResponse(responseCode = "404", description = "Lien introuvable ou supprimé"),
			@ApiResponse(responseCode = "410", description = "Partage expiré"),
			@ApiResponse(responseCode = "503", description = "Stockage indisponible")
	})
	public ResponseEntity<StreamingResponseBody> download(
			@PathVariable String token,
			@RequestParam(required = false) String ticket
	) {
		FileShareService.DownloadContent download = fileShareService.openDownload(token, ticket);
		StreamingResponseBody body = outputStream -> {
			try (var inputStream = download.content()) {
				inputStream.transferTo(outputStream);
			} catch (IOException exception) {
				throw new IOException("Le flux de téléchargement a été interrompu.", exception);
			}
		};

		MediaType contentType;
		try {
			contentType = MediaType.parseMediaType(download.mimeType());
		} catch (IllegalArgumentException exception) {
			contentType = MediaType.APPLICATION_OCTET_STREAM;
		}

		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(download.originalName(), StandardCharsets.UTF_8)
						.build().toString())
				.header("X-Content-Type-Options", "nosniff")
				.header("Referrer-Policy", "no-referrer")
				.contentType(contentType)
				.contentLength(download.size())
				.body(body);
	}
}
