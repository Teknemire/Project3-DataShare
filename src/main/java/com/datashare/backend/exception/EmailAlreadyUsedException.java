package com.datashare.backend.exception;

public class EmailAlreadyUsedException extends RuntimeException {

	public EmailAlreadyUsedException() {
		super("Un compte existe déjà avec cette adresse email.");
	}
}
