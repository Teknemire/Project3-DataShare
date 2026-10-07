package com.datashare.backend.controller;

import com.datashare.backend.dto.LoginRequest;
import com.datashare.backend.dto.LoginResponse;
import com.datashare.backend.dto.RegisterRequest;
import com.datashare.backend.dto.UserResponse;
import com.datashare.backend.exception.ApiError;
import com.datashare.backend.service.AuthenticationService;
import com.datashare.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AuthController {

	private final UserService userService;
	private final AuthenticationService authenticationService;

	@PostMapping("/register")
	@Operation(summary = "Créer un compte")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Compte créé"),
			@ApiResponse(responseCode = "400", description = "Données invalides",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "409", description = "Adresse email déjà utilisée",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
	})
	public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
	}

	@PostMapping("/login")
	@Operation(summary = "Se connecter")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Connexion réussie"),
			@ApiResponse(responseCode = "400", description = "Données invalides",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class))),
			@ApiResponse(responseCode = "401", description = "Identifiants incorrects",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
	})
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authenticationService.login(request);
	}

	@GetMapping("/me")
	@Operation(summary = "Récupérer l'utilisateur connecté")
	@SecurityRequirement(name = "bearerAuth")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Utilisateur connecté"),
			@ApiResponse(responseCode = "401", description = "JWT absent, invalide ou expiré",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiError.class)))
	})
	public UserResponse me(Authentication authentication) {
		return authenticationService.getCurrentUser(authentication.getName());
	}
}
