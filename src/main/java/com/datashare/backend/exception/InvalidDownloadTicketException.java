package com.datashare.backend.exception;

public class InvalidDownloadTicketException extends RuntimeException {

	public InvalidDownloadTicketException() {
		super("L'autorisation de téléchargement est invalide ou expirée.");
	}
}
