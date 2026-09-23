package com.datashare.backend.storage;

import com.datashare.backend.exception.StorageException;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

	private final S3Client s3Client;
	private final String bucket;

	public S3StorageService(
			S3Client s3Client,
			@Value("${app.storage.s3.bucket}") String bucket
	) {
		if (bucket == null || bucket.isBlank()) {
			throw new IllegalStateException("S3_BUCKET est obligatoire lorsque le stockage S3 est sélectionné.");
		}
		this.s3Client = s3Client;
		this.bucket = bucket;
	}

	@PostConstruct
	void validateBucketAccess() {
		try {
			s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
		} catch (RuntimeException exception) {
			throw new IllegalStateException("Le bucket S3 configuré est inaccessible.", exception);
		}
	}

	@Override
	public void save(String storageKey, InputStream content, long contentLength, String contentType) {
		PutObjectRequest request = PutObjectRequest.builder()
				.bucket(bucket)
				.key(storageKey)
				.contentType(contentType)
				.build();
		try (InputStream source = content) {
			s3Client.putObject(request, RequestBody.fromInputStream(source, contentLength));
		} catch (Exception exception) {
			throw new StorageException("Impossible d'enregistrer le fichier dans S3.", exception);
		}
	}

	@Override
	public InputStream open(String storageKey) {
		GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(storageKey).build();
		try {
			ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
			return response;
		} catch (RuntimeException exception) {
			throw new StorageException("Impossible de lire le fichier dans S3.", exception);
		}
	}

	@Override
	public void delete(String storageKey) {
		DeleteObjectRequest request = DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build();
		try {
			s3Client.deleteObject(request);
		} catch (RuntimeException exception) {
			throw new StorageException("Impossible de supprimer le fichier dans S3.", exception);
		}
	}
}
