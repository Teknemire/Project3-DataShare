package com.datashare.backend.service;

import com.datashare.backend.exception.FileTooLargeException;
import com.datashare.backend.exception.FileTypeNotAllowedException;
import com.datashare.backend.exception.InvalidFileException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileValidationService {

	public static final long MAX_FILE_SIZE = 1_000_000_000L;

	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
			"jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "tif", "tiff", "heic", "heif",
			"mp4", "mov", "avi", "mkv", "webm", "mpeg", "mpg", "m4v",
			"mp3", "wav", "ogg", "m4a", "aac", "flac", "wma",
			"zip", "7z", "rar", "tar", "gz", "gzip", "bz2", "xz",
			"pdf", "txt", "csv", "rtf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
			"odt", "ods", "odp"
	);

	private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
			"exe", "bat", "cmd", "com", "msi", "msix", "scr", "ps1",
			"app", "command", "dmg", "pkg",
			"sh", "run", "bin", "deb", "rpm", "appimage",
			"jar", "apk"
	);

	private static final Set<String> BLOCKED_MIME_TYPES = Set.of(
			"application/x-msdownload",
			"application/x-msdos-program",
			"application/vnd.microsoft.portable-executable",
			"application/x-executable",
			"application/x-sharedlib",
			"application/x-mach-binary",
			"application/x-sh",
			"application/x-bat"
	);

	public ValidatedFile validate(MultipartFile file) {
		if (file == null || file.isEmpty() || file.getSize() <= 0) {
			throw new InvalidFileException("Le fichier est obligatoire et ne doit pas être vide.");
		}
		if (file.getSize() > MAX_FILE_SIZE) {
			throw new FileTooLargeException();
		}

		String originalName = sanitizeName(file.getOriginalFilename());
		String extension = extensionOf(originalName);
		String contentType = normalizeContentType(file.getContentType());

		if (BLOCKED_EXTENSIONS.contains(extension)
				|| !ALLOWED_EXTENSIONS.contains(extension)
				|| BLOCKED_MIME_TYPES.contains(contentType)
				|| hasExecutableSignature(file)) {
			throw new FileTypeNotAllowedException();
		}

		return new ValidatedFile(originalName, contentType);
	}

	private String sanitizeName(String submittedName) {
		if (submittedName == null || submittedName.isBlank()) {
			throw new InvalidFileException("Le nom du fichier est obligatoire.");
		}
		String normalized = submittedName.replace('\\', '/');
		String fileName = normalized.substring(normalized.lastIndexOf('/') + 1)
				.replaceAll("[\\p{Cntrl}]", "")
				.trim();
		if (fileName.isEmpty() || fileName.length() > 255) {
			throw new InvalidFileException("Le nom du fichier est invalide.");
		}
		return fileName;
	}

	private String extensionOf(String fileName) {
		int separator = fileName.lastIndexOf('.');
		if (separator < 1 || separator == fileName.length() - 1) {
			throw new FileTypeNotAllowedException();
		}
		return fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
	}

	private String normalizeContentType(String contentType) {
		return contentType == null || contentType.isBlank()
				? "application/octet-stream"
				: contentType.toLowerCase(Locale.ROOT);
	}

	private boolean hasExecutableSignature(MultipartFile file) {
		byte[] header = new byte[4];
		try (InputStream input = file.getInputStream()) {
			int read = input.read(header);
			if (read < 2) {
				return false;
			}
		} catch (IOException exception) {
			throw new InvalidFileException("Impossible de lire le fichier envoyé.");
		}

		if (header[0] == 'M' && header[1] == 'Z') {
			return true;
		}
		if (header[0] == 0x7f && header[1] == 'E' && header[2] == 'L' && header[3] == 'F') {
			return true;
		}

		int signature = ((header[0] & 0xff) << 24)
				| ((header[1] & 0xff) << 16)
				| ((header[2] & 0xff) << 8)
				| (header[3] & 0xff);
		return signature == 0xFEEDFACE
				|| signature == 0xFEEDFACF
				|| signature == 0xCEFAEDFE
				|| signature == 0xCFFAEDFE
				|| signature == 0xCAFEBABE
				|| signature == 0xBEBAFECA;
	}

	public record ValidatedFile(String originalName, String contentType) {
	}
}
