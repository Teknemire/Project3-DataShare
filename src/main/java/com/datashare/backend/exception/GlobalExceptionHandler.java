package com.datashare.backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler({FileTooLargeException.class, MaxUploadSizeExceededException.class})
	public ResponseEntity<ApiError> handleFileTooLarge(Exception exception) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body(new ApiError("FILE_TOO_LARGE", "Le fichier dépasse la taille maximale de 1 Go."));
	}

	@ExceptionHandler(FileTypeNotAllowedException.class)
	public ResponseEntity<ApiError> handleFileTypeNotAllowed(FileTypeNotAllowedException exception) {
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body(new ApiError("FILE_TYPE_NOT_ALLOWED", exception.getMessage()));
	}

	@ExceptionHandler(InvalidFileException.class)
	public ResponseEntity<ApiError> handleInvalidFile(InvalidFileException exception) {
		return ResponseEntity.badRequest()
				.body(new ApiError("INVALID_FILE", exception.getMessage()));
	}

	@ExceptionHandler(StorageException.class)
	public ResponseEntity<ApiError> handleStorageFailure(StorageException exception) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(new ApiError("STORAGE_UNAVAILABLE", "Le stockage des fichiers est indisponible."));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadableRequest(HttpMessageNotReadableException exception) {
		return ResponseEntity.badRequest()
				.body(new ApiError("INVALID_REQUEST", "Le corps de la requête est invalide."));
	}

	@ExceptionHandler({
			MissingServletRequestPartException.class,
			MissingServletRequestParameterException.class,
			MethodArgumentTypeMismatchException.class
	})
	public ResponseEntity<ApiError> handleInvalidRequestParameter(Exception exception) {
		return ResponseEntity.badRequest()
				.body(new ApiError("INVALID_REQUEST", "Les paramètres de la requête sont invalides."));
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ApiError("INVALID_CREDENTIALS", exception.getMessage()));
	}

	@ExceptionHandler(CurrentUserNotFoundException.class)
	public ResponseEntity<ApiError> handleCurrentUserNotFound(CurrentUserNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(new ApiError("INVALID_TOKEN", "Authentification invalide."));
	}

	@ExceptionHandler(EmailAlreadyUsedException.class)
	public ResponseEntity<ApiError> handleEmailAlreadyUsed(EmailAlreadyUsedException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiError("EMAIL_ALREADY_USED", exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
				fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

		return ResponseEntity.badRequest()
				.body(new ApiError("VALIDATION_FAILED", "Les données envoyées ne sont pas valides.", fieldErrors));
	}
}
