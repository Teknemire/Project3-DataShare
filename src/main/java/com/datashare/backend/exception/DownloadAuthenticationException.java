package com.datashare.backend.exception;

public class DownloadAuthenticationException extends RuntimeException {

	public DownloadAuthenticationException() {
		super("Authentification du téléchargement refusée.");
	}
}
