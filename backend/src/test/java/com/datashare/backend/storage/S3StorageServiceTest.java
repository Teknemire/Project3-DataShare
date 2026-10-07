package com.datashare.backend.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datashare.backend.exception.StorageException;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

	@Mock
	private S3Client s3Client;

	private S3StorageService storage;

	@BeforeEach
	void setUp() {
		storage = new S3StorageService(s3Client, "datashare-test");
	}

	@Test
	void validatesTheBucketAndStoresTheObject() {
		when(s3Client.headBucket(any(HeadBucketRequest.class)))
				.thenReturn(HeadBucketResponse.builder().build());
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
				.thenReturn(PutObjectResponse.builder().build());

		storage.validateBucketAccess();
		storage.save("storage-key", new ByteArrayInputStream(new byte[] {1, 2}), 2, "image/jpeg");

		verify(s3Client).headBucket(any(HeadBucketRequest.class));
		verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
	}

	@Test
	void reportsAnS3FailureWithoutUsingAnotherStorage() {
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
				.thenThrow(S3Exception.builder().message("S3 unavailable").build());

		assertThatThrownBy(() -> storage.save(
				"storage-key", new ByteArrayInputStream(new byte[] {1, 2}), 2, "image/jpeg"))
				.isInstanceOf(StorageException.class);
	}
}
