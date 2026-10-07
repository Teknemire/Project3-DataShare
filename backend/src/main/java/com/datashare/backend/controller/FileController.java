package com.datashare.backend.controller;

import com.datashare.backend.dto.FileResponse;
import com.datashare.backend.exception.ApiError;
import com.datashare.backend.service.FileDeletionService;
import com.datashare.backend.service.FileQueryService;
import com.datashare.backend.service.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(value = "/api/files", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class FileController {

	private final FileUploadService fileUploadService;
	private final FileQueryService fileQueryService;
	private final FileDeletionService fileDeletionService;

	@GetMapping
	@Operation(summary = "Lister les fichiers de l'utilisateur connecté")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Historique personnel, éventuellement vide"),
			@ApiResponse(responseCode = "401", description = "Authentification requise",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
	})
	public List<FileResponse> list(Authentication authentication) {
		return fileQueryService.listOwnedFiles(authentication.getName());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Consulter un fichier appartenant à l'utilisateur connecté")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Métadonnées du fichier"),
			@ApiResponse(responseCode = "401", description = "Authentification requise",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "Fichier absent ou appartenant à un autre utilisateur",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
	})
	public FileResponse get(@PathVariable UUID id, Authentication authentication) {
		return fileQueryService.getOwnedFile(authentication.getName(), id);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Supprimer définitivement un fichier appartenant à l'utilisateur connecté")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Fichier supprimé", content = @Content),
			@ApiResponse(responseCode = "401", description = "Authentification requise",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "404", description = "Fichier absent ou appartenant à un autre utilisateur",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "503", description = "Stockage indisponible",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
	})
	public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
		fileDeletionService.deleteOwnedFile(authentication.getName(), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(
			summary = "Téléverser un fichier avec ou sans compte",
			description = "JWT facultatif. Les options sont des champs multipart, jamais des paramètres d'URL.",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
					required = true,
					content = @Content(
							mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
							schema = @Schema(type = "object", requiredProperties = {"file"}),
							schemaProperties = {
									@SchemaProperty(name = "file", schema = @Schema(type = "string", format = "binary")),
									@SchemaProperty(name = "expirationDays", schema = @Schema(
											type = "integer", defaultValue = "7", minimum = "1", maximum = "7")),
									@SchemaProperty(name = "password", schema = @Schema(
											type = "string", format = "password",
											description = "Facultatif ; au moins 6 caractères si renseigné, au plus 72 octets UTF-8.")),
									@SchemaProperty(name = "tags", array = @ArraySchema(
											arraySchema = @Schema(description = "Champ répétable, réservé aux utilisateurs connectés."),
											schema = @Schema(type = "string", maxLength = 30)))
							}
					)
			)
	)
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Fichier téléversé et lien créé"),
			@ApiResponse(responseCode = "400", description = "Paramètres invalides",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "JWT invalide ou tags envoyés sans authentification",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "413", description = "Fichier supérieur à 1 Go",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "415", description = "Type de fichier interdit",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "503", description = "Stockage indisponible",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
	})
	public ResponseEntity<FileResponse> upload(
			@Parameter(hidden = true) @RequestPart("file") MultipartFile file,
			@Parameter(hidden = true) @RequestParam(defaultValue = "7") Integer expirationDays,
			@Parameter(hidden = true) @RequestParam(required = false) String password,
			@Parameter(hidden = true) @RequestParam(required = false) List<String> tags,
			Authentication authentication
	) {
		FileResponse response = fileUploadService.upload(
				authentication == null ? null : authentication.getName(),
				file,
				expirationDays,
				password,
				tags
		);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
}
