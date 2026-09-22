package com.datashare.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datashare.backend.exception.FileTooLargeException;
import com.datashare.backend.exception.FileTypeNotAllowedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class FileValidationServiceTest {

	private final FileValidationService service = new FileValidationService();

	@Test
	void acceptsAnAllowedImage() {
		MockMultipartFile file = new MockMultipartFile(
				"file", "vacances.jpg", "image/jpeg", new byte[] {1, 2, 3, 4});

		FileValidationService.ValidatedFile result = service.validate(file);

		assertThat(result.originalName()).isEqualTo("vacances.jpg");
		assertThat(result.contentType()).isEqualTo("image/jpeg");
	}

	@Test
	void rejectsAWindowsExecutableExtension() {
		MockMultipartFile file = new MockMultipartFile(
				"file", "programme.exe", "application/octet-stream", new byte[] {1, 2});

		assertThatThrownBy(() -> service.validate(file))
				.isInstanceOf(FileTypeNotAllowedException.class);
	}

	@Test
	void rejectsAnExecutableRenamedAsAnImage() {
		MockMultipartFile file = new MockMultipartFile(
				"file", "programme.jpg", "image/jpeg", new byte[] {'M', 'Z', 0, 0});

		assertThatThrownBy(() -> service.validate(file))
				.isInstanceOf(FileTypeNotAllowedException.class);
	}

	@Test
	void rejectsAFileOverOneBillionBytes() {
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(file.getSize()).thenReturn(FileValidationService.MAX_FILE_SIZE + 1);

		assertThatThrownBy(() -> service.validate(file))
				.isInstanceOf(FileTooLargeException.class);
	}
}
