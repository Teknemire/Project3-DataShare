package com.datashare.backend.storage;

import java.io.InputStream;

public interface StorageService {

	void save(String storageKey, InputStream content, long contentLength, String contentType);

	InputStream open(String storageKey);

	void delete(String storageKey);
}
