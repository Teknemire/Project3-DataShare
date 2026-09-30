package com.datashare.backend.exception;

public class TagAuthenticationRequiredException extends RuntimeException {

	public TagAuthenticationRequiredException() {
		super("Connectez-vous pour ajouter des tags.");
	}
}
