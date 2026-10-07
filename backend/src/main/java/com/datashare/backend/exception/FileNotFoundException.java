package com.datashare.backend.exception;

public class FileNotFoundException extends RuntimeException {

	public FileNotFoundException() {
		super("Fichier introuvable.");
	}
}
