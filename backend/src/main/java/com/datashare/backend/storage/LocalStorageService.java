package com.datashare.backend.storage;

import com.datashare.backend.exception.StorageException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

	private final Path rootDirectory;

	public LocalStorageService(@Value("${app.storage.local.path}") String storagePath) {
		this.rootDirectory = Path.of(storagePath).toAbsolutePath().normalize();
		try {
			Files.createDirectories(rootDirectory);
		} catch (IOException exception) {
			throw new StorageException("Impossible d'initialiser le stockage local.", exception);
		}
	}

	@Override
	public void save(String storageKey, InputStream content, long contentLength, String contentType) {
		Path destination = resolveSafely(storageKey);
		try (InputStream source = content) {
			Files.copy(source, destination);
		} catch (IOException exception) {
			throw new StorageException("Impossible d'enregistrer le fichier.", exception);
		}
	}

	@Override
	public InputStream open(String storageKey) {
		try {
			return Files.newInputStream(resolveSafely(storageKey));
		} catch (IOException exception) {
			throw new StorageException("Impossible de lire le fichier.", exception);
		}
	}

	@Override
	public void delete(String storageKey) {
		try {
			Files.deleteIfExists(resolveSafely(storageKey));
		} catch (IOException exception) {
			throw new StorageException("Impossible de supprimer le fichier.", exception);
		}
	}

	private Path resolveSafely(String storageKey) {
		Path resolved = rootDirectory.resolve(storageKey).normalize();
		if (!resolved.startsWith(rootDirectory)) {
			throw new StorageException("Clé de stockage locale invalide.");
		}
		return resolved;
	}
}
