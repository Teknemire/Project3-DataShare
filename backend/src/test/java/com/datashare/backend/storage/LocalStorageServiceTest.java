package com.datashare.backend.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datashare.backend.exception.StorageException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageServiceTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void savesOpensAndDeletesAFile() throws Exception {
		LocalStorageService storage = new LocalStorageService(temporaryDirectory.toString());
		byte[] content = {1, 2, 3, 4};

		storage.save("safe-key", new ByteArrayInputStream(content), content.length, "image/jpeg");

		assertThat(storage.open("safe-key").readAllBytes()).containsExactly(content);
		storage.delete("safe-key");
		assertThat(Files.exists(temporaryDirectory.resolve("safe-key"))).isFalse();
	}

	@Test
	void rejectsAKeyOutsideTheStorageDirectory() {
		LocalStorageService storage = new LocalStorageService(temporaryDirectory.toString());

		assertThatThrownBy(() -> storage.delete("../outside"))
				.isInstanceOf(StorageException.class);
	}
}
