package com.datashare.backend.exception;

public class FileTooLargeException extends RuntimeException {

	public FileTooLargeException() {
		super("Le fichier dépasse la taille maximale de 1 Go.");
	}
}
