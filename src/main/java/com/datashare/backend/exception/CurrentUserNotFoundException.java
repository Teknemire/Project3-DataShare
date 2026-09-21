package com.datashare.backend.exception;

public class CurrentUserNotFoundException extends RuntimeException {

	public CurrentUserNotFoundException() {
		super("L'utilisateur authentifié n'existe plus.");
	}
}
