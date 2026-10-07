package com.datashare.backend.service;

import com.datashare.backend.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

	public static final String ISSUER = "datashare";

	private final JwtEncoder jwtEncoder;

	@Value("${app.jwt.expiration}")
	private Duration expiration;

	public GeneratedToken generate(User user) {
		Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		Instant expiresAt = issuedAt.plus(expiration);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.subject(user.getEmail())
				.claim("uid", user.getId().toString())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new GeneratedToken(token, expiresAt);
	}

	public record GeneratedToken(String value, Instant expiresAt) {
	}
}
