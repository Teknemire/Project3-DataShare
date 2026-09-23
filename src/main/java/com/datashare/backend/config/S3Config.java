package com.datashare.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3Config {

	@Bean
	S3Client s3Client(@Value("${app.storage.s3.region}") String region) {
		if (region == null || region.isBlank()) {
			throw new IllegalStateException("S3_REGION est obligatoire lorsque le stockage S3 est sélectionné.");
		}
		return S3Client.builder()
				.region(Region.of(region))
				.build();
	}
}
