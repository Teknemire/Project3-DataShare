package com.datashare.backend.exception;

public class InvalidPasswordException extends RuntimeException {

	public InvalidPasswordException() {
		super("Le mot de passe est trop long. Les caractères accentués et les emojis comptent davantage.");
	}
}
