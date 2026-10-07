package com.datashare.backend.exception;

public class FileTypeNotAllowedException extends RuntimeException {

	public FileTypeNotAllowedException() {
		super("Ce type de fichier n'est pas autorisé.");
	}
}
