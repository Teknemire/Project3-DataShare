package com.datashare.backend.controller;

import com.datashare.backend.dto.FileResponse;
import com.datashare.backend.service.FileDeletionService;
import com.datashare.backend.service.FileQueryService;
import com.datashare.backend.service.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/files")
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
			@ApiResponse(responseCode = "401", description = "Authentification requise")
	})
	public List<FileResponse> list(Authentication authentication) {
		return fileQueryService.listOwnedFiles(authentication.getName());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Consulter un fichier appartenant à l'utilisateur connecté")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Métadonnées du fichier"),
			@ApiResponse(responseCode = "401", description = "Authentification requise"),
			@ApiResponse(responseCode = "404", description = "Fichier absent ou appartenant à un autre utilisateur")
	})
	public FileResponse get(@PathVariable UUID id, Authentication authentication) {
		return fileQueryService.getOwnedFile(authentication.getName(), id);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Supprimer définitivement un fichier appartenant à l'utilisateur connecté")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Fichier supprimé"),
			@ApiResponse(responseCode = "401", description = "Authentification requise"),
			@ApiResponse(responseCode = "404", description = "Fichier absent ou appartenant à un autre utilisateur"),
			@ApiResponse(responseCode = "503", description = "Stockage indisponible")
	})
	public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
		fileDeletionService.deleteOwnedFile(authentication.getName(), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Téléverser un fichier avec ou sans compte")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Fichier téléversé et lien créé"),
			@ApiResponse(responseCode = "400", description = "Paramètres invalides"),
			@ApiResponse(responseCode = "401", description = "JWT invalide ou tags envoyés sans authentification"),
			@ApiResponse(responseCode = "413", description = "Fichier supérieur à 1 Go"),
			@ApiResponse(responseCode = "415", description = "Type de fichier interdit"),
			@ApiResponse(responseCode = "503", description = "Stockage indisponible")
	})
	public ResponseEntity<FileResponse> upload(
			@RequestPart("file") MultipartFile file,
			@RequestParam(defaultValue = "7") Integer expirationDays,
			@RequestParam(required = false) String password,
			@RequestParam(required = false) List<String> tags,
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
