package com.datashare.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datashare.backend.entity.User;
import com.datashare.backend.service.JwtService;
import java.time.Duration;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class JwtConfigTest {

	private final JwtConfig jwtConfig = new JwtConfig();

	@Test
	void generatesAndValidatesSignedJwt() {
		SecretKey secretKey = jwtConfig.jwtSecretKey("test-secret-with-at-least-32-bytes-long");
		JwtEncoder encoder = jwtConfig.jwtEncoder(secretKey);
		JwtDecoder decoder = jwtConfig.jwtDecoder(secretKey);
		JwtService jwtService = new JwtService(encoder);
		ReflectionTestUtils.setField(jwtService, "expiration", Duration.ofHours(1));
		User user = new User("user@example.com", "stored-hash");
		UUID userId = UUID.fromString("d52f8f24-6363-469c-91cb-5a4f3bd99369");
		ReflectionTestUtils.setField(user, "id", userId);

		JwtService.GeneratedToken generatedToken = jwtService.generate(user);
		Jwt decodedToken = decoder.decode(generatedToken.value());

		assertThat(decodedToken.getSubject()).isEqualTo("user@example.com");
		assertThat(decodedToken.getClaimAsString("uid")).isEqualTo(userId.toString());
		assertThat(decodedToken.getExpiresAt()).isEqualTo(generatedToken.expiresAt());
	}

	@Test
	void rejectsSecretShorterThan32Bytes() {
		assertThatThrownBy(() -> jwtConfig.jwtSecretKey("too-short"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32 octets");
	}
}
