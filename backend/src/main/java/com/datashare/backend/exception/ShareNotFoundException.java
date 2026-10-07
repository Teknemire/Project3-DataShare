package com.datashare.backend.exception;

public class ShareNotFoundException extends RuntimeException {

	public ShareNotFoundException() {
		super("Lien invalide, expiré ou supprimé.");
	}
}
