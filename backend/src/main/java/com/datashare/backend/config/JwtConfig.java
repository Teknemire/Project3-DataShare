package com.datashare.backend.config;

import com.datashare.backend.repository.UserRepository;
import com.datashare.backend.service.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

	@Bean
	SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String secret) {
		byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (secretBytes.length < 32) {
			throw new IllegalStateException("JWT_SECRET doit contenir au moins 32 octets.");
		}
		return new SecretKeySpec(secretBytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey secretKey) {
		return NimbusJwtEncoder.withSecretKey(secretKey)
				.algorithm(MacAlgorithm.HS256)
				.build();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey secretKey, UserRepository userRepository) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		var defaultValidator = JwtValidators.createDefaultWithIssuer(JwtService.ISSUER);
		decoder.setJwtValidator(jwt -> {
			var result = defaultValidator.validate(jwt);
			if (result.hasErrors()) {
				return result;
			}
			String userId = jwt.getClaimAsString("uid");
			if (userId != null) {
				try {
					// Un email peut être réutilisé après suppression, mais jamais l'identifiant du compte.
					if (userRepository.existsByIdAndEmail(UUID.fromString(userId), jwt.getSubject())) {
						return OAuth2TokenValidatorResult.success();
					}
				} catch (IllegalArgumentException exception) {
					return OAuth2TokenValidatorResult.failure(
							new OAuth2Error("invalid_token", "Authentification invalide.", null));
				}
			}
			return OAuth2TokenValidatorResult.failure(
					new OAuth2Error("invalid_token", "Authentification invalide.", null));
		});
		return decoder;
	}
}
