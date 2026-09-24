package com.datashare.backend.exception;

public class ShareExpiredException extends RuntimeException {

	public ShareExpiredException() {
		super("Ce fichier n'est plus disponible au téléchargement car il a expiré.");
	}
}
