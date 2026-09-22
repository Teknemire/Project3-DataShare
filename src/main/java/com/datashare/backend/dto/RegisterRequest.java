package com.datashare.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank(message = "L'adresse email est obligatoire.")
		@Email(message = "L'adresse email n'est pas valide.")
		@Size(max = 320, message = "L'adresse email est trop longue.")
		String email,

		@NotBlank(message = "Le mot de passe est obligatoire.")
		@Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères.")
		String password
) {
	@Override
	public String toString() {
		return "RegisterRequest[password=***]";
	}
}
