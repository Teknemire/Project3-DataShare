package com.datashare.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "L'adresse email est obligatoire.")
		@Email(message = "L'adresse email n'est pas valide.")
		@Size(max = 320, message = "L'adresse email est trop longue.")
		String email,

		@NotBlank(message = "Le mot de passe est obligatoire.")
		String password
) {
}
