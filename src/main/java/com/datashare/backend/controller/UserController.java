package com.datashare.backend.controller;

import com.datashare.backend.dto.DeleteAccountRequest;
import com.datashare.backend.service.AccountDeletionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final AccountDeletionService accountDeletionService;

	@DeleteMapping("/me")
	@Operation(summary = "Supprimer définitivement le compte de l'utilisateur connecté")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Compte supprimé"),
			@ApiResponse(responseCode = "400", description = "Requête invalide"),
			@ApiResponse(responseCode = "401", description = "Authentification ou mot de passe invalide"),
			@ApiResponse(responseCode = "503", description = "Stockage indisponible")
	})
	public ResponseEntity<Void> deleteCurrentAccount(
			@Valid @RequestBody DeleteAccountRequest request,
			Authentication authentication
	) {
		accountDeletionService.deleteCurrentAccount(authentication.getName(), request);
		return ResponseEntity.noContent().build();
	}
}
