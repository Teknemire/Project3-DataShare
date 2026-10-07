package com.datashare.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(
		@NotBlank(message = "Le mot de passe est obligatoire.")
		@Size(max = 72, message = "Le mot de passe ne peut pas dépasser 72 caractères.")
		String password
) {
	@Override
	public String toString() {
		return "DeleteAccountRequest[password=***]";
	}
}
