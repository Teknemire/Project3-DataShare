package com.datashare.backend.controller;

import com.datashare.backend.dto.FileResponse;
import com.datashare.backend.service.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(summary = "Téléverser un fichier")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Fichier téléversé et lien créé"),
			@ApiResponse(responseCode = "400", description = "Paramètres invalides"),
			@ApiResponse(responseCode = "401", description = "Authentification requise"),
			@ApiResponse(responseCode = "413", description = "Fichier supérieur à 1 Go"),
			@ApiResponse(responseCode = "415", description = "Type de fichier interdit"),
			@ApiResponse(responseCode = "503", description = "Stockage indisponible")
	})
	public ResponseEntity<FileResponse> upload(
			@RequestPart("file") MultipartFile file,
			@RequestParam(defaultValue = "7") Integer expirationDays,
			@RequestParam(required = false) String password,
			Authentication authentication
	) {
		FileResponse response = fileUploadService.upload(
				authentication.getName(),
				file,
				expirationDays,
				password
		);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
}
